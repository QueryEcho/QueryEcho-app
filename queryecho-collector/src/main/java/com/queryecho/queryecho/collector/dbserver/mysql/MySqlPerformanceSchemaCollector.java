package com.queryecho.queryecho.collector.dbserver.mysql;

import com.queryecho.queryecho.collector.dbserver.DbServerQueryPersistenceService;
import com.queryecho.queryecho.collector.dbserver.DbServerQueryCollector;
import com.queryecho.queryecho.collector.dbserver.DbServerQuerySample;
import com.queryecho.core.util.QueryFingerprint;
import com.queryecho.core.util.SqlNormalizer;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * MySQL performance_schema의 최근 statement 순환 버퍼를 읽는다.
 * SDK가 없는 DBeaver/CLI/다른 언어 클라이언트의 쿼리도 이 경로로 관찰된다.
 */
@Component
@ConditionalOnProperty(prefix = "queryecho.db-collector.mysql", name = "enabled", havingValue = "true")
public class MySqlPerformanceSchemaCollector implements DbServerQueryCollector {

    private static final Logger log = LoggerFactory.getLogger(MySqlPerformanceSchemaCollector.class);
    private static final long PICOSECONDS_PER_MICROSECOND = 1_000_000L;

    private static final String HISTORY_SQL = """
            SELECT e.THREAD_ID,
                   e.EVENT_ID,
                   e.CURRENT_SCHEMA,
                   e.DIGEST_TEXT,
                   e.SQL_TEXT,
                   e.EVENT_NAME,
                   e.TIMER_WAIT,
                   e.LOCK_TIME,
                   e.ROWS_AFFECTED,
                   e.ROWS_SENT,
                   e.ROWS_EXAMINED,
                   e.MYSQL_ERRNO,
                   e.RETURNED_SQLSTATE,
                   e.MESSAGE_TEXT,
                   t.PROCESSLIST_ID,
                   t.PROCESSLIST_USER,
                   t.PROCESSLIST_HOST,
                   attrs.CLIENT_PROGRAM
              FROM performance_schema.events_statements_history_long e
              LEFT JOIN performance_schema.threads t
                ON t.THREAD_ID = e.THREAD_ID
              LEFT JOIN (
                    SELECT PROCESSLIST_ID,
                           MAX(CASE
                                 WHEN ATTR_NAME IN ('program_name', '_client_name')
                                 THEN ATTR_VALUE
                               END) AS CLIENT_PROGRAM
                      FROM performance_schema.session_connect_attrs
                     GROUP BY PROCESSLIST_ID
              ) attrs ON attrs.PROCESSLIST_ID = t.PROCESSLIST_ID
             WHERE e.SQL_TEXT IS NOT NULL
               AND e.EVENT_NAME LIKE 'statement/sql/%'
               AND e.CURRENT_SCHEMA = ?
               AND (t.PROCESSLIST_ID IS NULL OR t.PROCESSLIST_ID <> ?)
             ORDER BY e.THREAD_ID DESC, e.EVENT_ID DESC
             LIMIT ?
            """;

    private final MySqlServerCollectorProperties properties;
    private final DbServerQueryPersistenceService persistenceService;
    private final AtomicBoolean failureLogged = new AtomicBoolean();

    public MySqlPerformanceSchemaCollector(MySqlServerCollectorProperties properties,
                                           DbServerQueryPersistenceService persistenceService) {
        this.properties = properties;
        this.persistenceService = persistenceService;
    }

    @Scheduled(
            fixedDelayString = "${queryecho.db-collector.mysql.poll-interval-ms:1000}",
            initialDelayString = "${queryecho.db-collector.mysql.initial-delay-ms:3000}")
    @Override
    public void collect() {
        try {
            List<DbServerQuerySample> samples = readRecentStatements();
            int added = persistenceService.saveNew(samples);
            if (added > 0) {
                log.debug("[QueryEcho] Collected {} new MySQL DB_SERVER query events", added);
            }
            failureLogged.set(false);
        } catch (SQLException ex) {
            if (failureLogged.compareAndSet(false, true)) {
                log.error("[QueryEcho] MySQL DB collector failed. Verify performance_schema history_long "
                        + "consumer and SELECT permission for user '{}': {}", properties.getUsername(), ex.getMessage());
            }
        } catch (RuntimeException ex) {
            if (failureLogged.compareAndSet(false, true)) {
                log.error("[QueryEcho] MySQL DB collector failed", ex);
            }
        }
    }

    @Override
    public String dbType() {
        return "mysql";
    }

    List<DbServerQuerySample> readRecentStatements() throws SQLException {
        try (Connection connection = DriverManager.getConnection(
                properties.getJdbcUrl(), properties.getUsername(), properties.getPassword())) {
            long ownConnectionId = connectionId(connection);
            try (PreparedStatement statement = connection.prepareStatement(HISTORY_SQL)) {
                statement.setString(1, properties.getSchema());
                statement.setLong(2, ownConnectionId);
                statement.setInt(3, Math.max(1, Math.min(properties.getBatchSize(), 10_000)));

                Instant observedAt = Instant.now();
                List<DbServerQuerySample> result = new ArrayList<>();
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) {
                        DbServerQuerySample sample = map(rows, observedAt);
                        if (sample != null) {
                            result.add(sample);
                        }
                    }
                }
                return result;
            }
        }
    }

    private long connectionId(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT CONNECTION_ID()")) {
            if (!result.next()) {
                throw new SQLException("MySQL did not return CONNECTION_ID()");
            }
            return result.getLong(1);
        }
    }

    private DbServerQuerySample map(ResultSet row, Instant observedAt) throws SQLException {
        long threadId = row.getLong("THREAD_ID");
        long eventId = row.getLong("EVENT_ID");
        String digestText = row.getString("DIGEST_TEXT");
        String sqlText = row.getString("SQL_TEXT");
        String normalizedSql = SqlNormalizer.normalize(
                digestText == null || digestText.isBlank() ? sqlText : digestText);
        if (normalizedSql == null || normalizedSql.isBlank()) {
            return null;
        }

        int errorCode = row.getInt("MYSQL_ERRNO");
        String sourceEventKey = properties.getInstanceId() + ":" + threadId + ":" + eventId;
        return new DbServerQuerySample(
                sourceEventKey,
                safe(properties.getInstanceId(), "mysql-unknown"),
                "mysql",
                truncate(row.getString("CURRENT_SCHEMA"), 100),
                truncate(row.getString("PROCESSLIST_USER"), 100),
                truncate(row.getString("PROCESSLIST_HOST"), 255),
                truncate(row.getString("CLIENT_PROGRAM"), 200),
                nullableLong(row, "PROCESSLIST_ID"),
                threadId,
                eventId,
                QueryFingerprint.sha256("mysql", normalizedSql),
                normalizedSql,
                statementType(row.getString("EVENT_NAME"), normalizedSql),
                toMicroseconds(row.getLong("TIMER_WAIT")),
                toMicroseconds(row.getLong("LOCK_TIME")),
                nonNegative(row.getLong("ROWS_AFFECTED")),
                nonNegative(row.getLong("ROWS_SENT")),
                nonNegative(row.getLong("ROWS_EXAMINED")),
                errorCode == 0,
                errorCode == 0 ? null : errorCode,
                truncate(row.getString("RETURNED_SQLSTATE"), 5),
                truncate(row.getString("MESSAGE_TEXT"), 1000),
                observedAt);
    }

    private static Long nullableLong(ResultSet result, String column) throws SQLException {
        long value = result.getLong(column);
        return result.wasNull() ? null : value;
    }

    private static long toMicroseconds(long picoseconds) {
        return nonNegative(picoseconds) / PICOSECONDS_PER_MICROSECOND;
    }

    private static long nonNegative(long value) {
        return Math.max(0, value);
    }

    private static String statementType(String eventName, String normalizedSql) {
        if (eventName != null && eventName.startsWith("statement/sql/")) {
            return truncate(eventName.substring("statement/sql/".length()).toUpperCase(Locale.ROOT), 32);
        }
        return truncate(normalizedSql.stripLeading().split("\\s+", 2)[0].toUpperCase(Locale.ROOT), 32);
    }

    private static String safe(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}

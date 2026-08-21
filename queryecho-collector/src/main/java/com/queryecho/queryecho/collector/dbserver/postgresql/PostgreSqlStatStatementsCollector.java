package com.queryecho.queryecho.collector.dbserver.postgresql;

import com.queryecho.queryecho.collector.dbserver.DbServerQueryAggregatePersistenceService;
import com.queryecho.queryecho.collector.dbserver.DbServerQueryAggregateSample;
import com.queryecho.queryecho.collector.dbserver.DbServerQueryCollector;
import com.queryecho.queryecho.sdk.util.QueryFingerprint;
import com.queryecho.queryecho.sdk.util.SqlNormalizer;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** PostgreSQL 누적 pg_stat_statements 값을 이전 poll과 비교해 실행 증가분만 저장한다. */
@Component
@ConditionalOnProperty(prefix = "queryecho.db-collector.postgresql", name = "enabled", havingValue = "true")
public class PostgreSqlStatStatementsCollector implements DbServerQueryCollector {

    private static final Logger log = LoggerFactory.getLogger(PostgreSqlStatStatementsCollector.class);
    private static final String STATEMENTS_SQL = """
            SELECT s.queryid::text AS query_id,
                   d.datname,
                   r.rolname,
                   s.query,
                   s.calls,
                   s.total_exec_time,
                   s.rows,
                   i.stats_reset
              FROM pg_stat_statements s
              JOIN pg_database d ON d.oid = s.dbid
              JOIN pg_roles r ON r.oid = s.userid
             CROSS JOIN pg_stat_statements_info i
             WHERE d.datname = ?
               AND r.rolname <> ?
               AND s.query IS NOT NULL
            """;

    private final PostgreSqlServerCollectorProperties properties;
    private final DbServerQueryAggregatePersistenceService persistenceService;
    private final Map<String, Counters> previous = new HashMap<>();
    private final AtomicBoolean failureLogged = new AtomicBoolean();
    private boolean baselineInitialized;

    public PostgreSqlStatStatementsCollector(PostgreSqlServerCollectorProperties properties,
                                             DbServerQueryAggregatePersistenceService persistenceService) {
        this.properties = properties;
        this.persistenceService = persistenceService;
    }

    @Override
    public String dbType() {
        return "postgresql";
    }

    @Override
    @Scheduled(
            fixedDelayString = "${queryecho.db-collector.postgresql.poll-interval-ms:5000}",
            initialDelayString = "${queryecho.db-collector.postgresql.initial-delay-ms:3000}")
    public synchronized void collect() {
        try (Connection connection = DriverManager.getConnection(
                properties.getJdbcUrl(), properties.getUsername(), properties.getPassword());
             PreparedStatement statement = connection.prepareStatement(STATEMENTS_SQL)) {
            statement.setString(1, properties.getDatabase());
            statement.setString(2, properties.getUsername());

            Map<String, CumulativeStatement> current = read(statement);
            if (!baselineInitialized) {
                current.forEach((key, value) -> previous.put(key, value.counters()));
                baselineInitialized = true;
                failureLogged.set(false);
                return;
            }

            for (Map.Entry<String, CumulativeStatement> entry : current.entrySet()) {
                CumulativeStatement value = entry.getValue();
                Counters before = previous.put(entry.getKey(), value.counters());
                long callsDelta = before == null ? value.calls() : value.calls() - before.calls();
                if (callsDelta <= 0) {
                    continue;
                }
                long durationDeltaUs = before == null
                        ? toMicroseconds(value.totalExecTimeMs())
                        : toMicroseconds(value.totalExecTimeMs() - before.totalExecTimeMs());
                long rowsDelta = before == null ? value.rows() : Math.max(0, value.rows() - before.rows());
                String sourceKey = properties.getInstanceId() + ":" + value.statsReset().toEpochMilli()
                        + ":" + value.fingerprint() + ":" + value.calls();
                persistenceService.save(new DbServerQueryAggregateSample(
                        sourceKey, properties.getInstanceId(), "postgresql", value.database(), value.dbUser(),
                        value.fingerprint(), value.normalizedSql(), value.statementType(), callsDelta,
                        durationDeltaUs, rowsDelta, Instant.now()));
            }
            failureLogged.set(false);
        } catch (SQLException ex) {
            if (failureLogged.compareAndSet(false, true)) {
                log.error("[QueryEcho] PostgreSQL DB collector failed. Verify pg_stat_statements and "
                        + "monitoring user '{}': {}", properties.getUsername(), ex.getMessage());
            }
        } catch (RuntimeException ex) {
            if (failureLogged.compareAndSet(false, true)) {
                log.error("[QueryEcho] PostgreSQL DB collector failed", ex);
            }
        }
    }

    private Map<String, CumulativeStatement> read(PreparedStatement statement) throws SQLException {
        Map<String, CumulativeStatement> result = new HashMap<>();
        try (ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                String normalized = SqlNormalizer.normalize(rows.getString("query"));
                if (normalized == null || normalized.isBlank()) {
                    continue;
                }
                String fingerprint = QueryFingerprint.sha256("postgresql", normalized);
                String database = rows.getString("datname");
                String dbUser = rows.getString("rolname");
                String key = database + ":" + dbUser + ":" + rows.getString("query_id");
                result.put(key, new CumulativeStatement(
                        database, dbUser, fingerprint, normalized, statementType(normalized),
                        rows.getLong("calls"), rows.getDouble("total_exec_time"), rows.getLong("rows"),
                        rows.getTimestamp("stats_reset").toInstant()));
            }
        }
        return result;
    }

    private static long toMicroseconds(double milliseconds) {
        return Math.max(0, Math.round(milliseconds * 1_000.0));
    }

    private static String statementType(String sql) {
        String first = sql.stripLeading().split("\\s+", 2)[0].toUpperCase(Locale.ROOT);
        return first.length() <= 32 ? first : first.substring(0, 32);
    }

    private record Counters(long calls, double totalExecTimeMs, long rows) {
    }

    private record CumulativeStatement(
            String database,
            String dbUser,
            String fingerprint,
            String normalizedSql,
            String statementType,
            long calls,
            double totalExecTimeMs,
            long rows,
            Instant statsReset
    ) {
        Counters counters() {
            return new Counters(calls, totalExecTimeMs, rows);
        }
    }
}

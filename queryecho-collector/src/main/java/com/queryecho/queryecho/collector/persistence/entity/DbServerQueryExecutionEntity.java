package com.queryecho.queryecho.collector.persistence.entity;

import com.queryecho.queryecho.collector.dbserver.DbServerQuerySample;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "db_server_query_execution")
public class DbServerQueryExecutionEntity {

    @Id
    private UUID id;

    @Column(name = "source_event_key", nullable = false, unique = true, length = 240)
    private String sourceEventKey;

    @Column(name = "source_type", nullable = false, length = 20)
    private String sourceType;

    @Column(name = "db_instance_id", nullable = false, length = 100)
    private String dbInstanceId;

    @Column(name = "db_type", nullable = false, length = 30)
    private String dbType;

    @Column(name = "schema_name", length = 100)
    private String schemaName;

    @Column(name = "db_user", length = 100)
    private String dbUser;

    @Column(name = "client_host", length = 255)
    private String clientHost;

    @Column(name = "client_program", length = 200)
    private String clientProgram;

    @Column(name = "connection_id")
    private Long connectionId;

    @Column(name = "thread_id", nullable = false)
    private long threadId;

    @Column(name = "source_event_id", nullable = false)
    private long sourceEventId;

    @Column(nullable = false, length = 64)
    private String fingerprint;

    @Column(name = "normalized_sql", nullable = false, columnDefinition = "text")
    private String normalizedSql;

    @Column(name = "statement_type", nullable = false, length = 32)
    private String statementType;

    @Column(name = "duration_us", nullable = false)
    private long durationUs;

    @Column(name = "lock_time_us", nullable = false)
    private long lockTimeUs;

    @Column(name = "rows_affected", nullable = false)
    private long rowsAffected;

    @Column(name = "rows_sent", nullable = false)
    private long rowsSent;

    @Column(name = "rows_examined", nullable = false)
    private long rowsExamined;

    @Column(nullable = false)
    private boolean succeeded;

    @Column(name = "error_code")
    private Integer errorCode;

    @Column(name = "sql_state", length = 5)
    private String sqlState;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "observed_at", nullable = false)
    private Instant observedAt;

    @Column(name = "ingested_at", nullable = false)
    private Instant ingestedAt;

    protected DbServerQueryExecutionEntity() {
    }

    public DbServerQueryExecutionEntity(UUID id, DbServerQuerySample sample, Instant ingestedAt) {
        this.id = id;
        this.sourceEventKey = sample.sourceEventKey();
        this.sourceType = "DB_SERVER";
        this.dbInstanceId = sample.dbInstanceId();
        this.dbType = sample.dbType();
        this.schemaName = sample.schemaName();
        this.dbUser = sample.dbUser();
        this.clientHost = sample.clientHost();
        this.clientProgram = sample.clientProgram();
        this.connectionId = sample.connectionId();
        this.threadId = sample.threadId();
        this.sourceEventId = sample.sourceEventId();
        this.fingerprint = sample.fingerprint();
        this.normalizedSql = sample.normalizedSql();
        this.statementType = sample.statementType();
        this.durationUs = sample.durationUs();
        this.lockTimeUs = sample.lockTimeUs();
        this.rowsAffected = sample.rowsAffected();
        this.rowsSent = sample.rowsSent();
        this.rowsExamined = sample.rowsExamined();
        this.succeeded = sample.succeeded();
        this.errorCode = sample.errorCode();
        this.sqlState = sample.sqlState();
        this.errorMessage = sample.errorMessage();
        this.observedAt = sample.observedAt();
        this.ingestedAt = ingestedAt;
    }

    public UUID getId() { return id; }
    public String getSourceEventKey() { return sourceEventKey; }
    public String getSourceType() { return sourceType; }
    public String getDbInstanceId() { return dbInstanceId; }
    public String getDbType() { return dbType; }
    public String getSchemaName() { return schemaName; }
    public String getDbUser() { return dbUser; }
    public String getClientHost() { return clientHost; }
    public String getClientProgram() { return clientProgram; }
    public Long getConnectionId() { return connectionId; }
    public long getThreadId() { return threadId; }
    public long getSourceEventId() { return sourceEventId; }
    public String getFingerprint() { return fingerprint; }
    public String getNormalizedSql() { return normalizedSql; }
    public String getStatementType() { return statementType; }
    public long getDurationUs() { return durationUs; }
    public long getLockTimeUs() { return lockTimeUs; }
    public long getRowsAffected() { return rowsAffected; }
    public long getRowsSent() { return rowsSent; }
    public long getRowsExamined() { return rowsExamined; }
    public boolean isSucceeded() { return succeeded; }
    public Integer getErrorCode() { return errorCode; }
    public String getSqlState() { return sqlState; }
    public String getErrorMessage() { return errorMessage; }
    public Instant getObservedAt() { return observedAt; }
    public Instant getIngestedAt() { return ingestedAt; }
}

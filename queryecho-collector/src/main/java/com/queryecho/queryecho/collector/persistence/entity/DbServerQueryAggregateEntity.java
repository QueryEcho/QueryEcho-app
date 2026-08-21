package com.queryecho.queryecho.collector.persistence.entity;

import com.queryecho.queryecho.collector.dbserver.DbServerQueryAggregateSample;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "db_server_query_aggregate")
public class DbServerQueryAggregateEntity {

    @Id
    private UUID id;
    @Column(name = "source_event_key", nullable = false, unique = true, length = 240)
    private String sourceEventKey;
    @Column(name = "source_type", nullable = false, length = 20)
    private String sourceType;
    @Column(name = "sample_type", nullable = false, length = 20)
    private String sampleType;
    @Column(name = "db_instance_id", nullable = false, length = 100)
    private String dbInstanceId;
    @Column(name = "db_type", nullable = false, length = 30)
    private String dbType;
    @Column(name = "schema_name", length = 100)
    private String schemaName;
    @Column(name = "db_user", length = 100)
    private String dbUser;
    @Column(nullable = false, length = 64)
    private String fingerprint;
    @Column(name = "normalized_sql", nullable = false, columnDefinition = "text")
    private String normalizedSql;
    @Column(name = "statement_type", nullable = false, length = 32)
    private String statementType;
    @Column(name = "execution_count", nullable = false)
    private long executionCount;
    @Column(name = "total_duration_us", nullable = false)
    private long totalDurationUs;
    @Column(name = "rows_processed", nullable = false)
    private long rowsProcessed;
    @Column(name = "observed_at", nullable = false)
    private Instant observedAt;
    @Column(name = "ingested_at", nullable = false)
    private Instant ingestedAt;

    protected DbServerQueryAggregateEntity() {
    }

    public DbServerQueryAggregateEntity(UUID id, DbServerQueryAggregateSample sample, Instant ingestedAt) {
        this.id = id;
        this.sourceEventKey = sample.sourceEventKey();
        this.sourceType = "DB_SERVER";
        this.sampleType = "AGGREGATE_DELTA";
        this.dbInstanceId = sample.dbInstanceId();
        this.dbType = sample.dbType();
        this.schemaName = sample.schemaName();
        this.dbUser = sample.dbUser();
        this.fingerprint = sample.fingerprint();
        this.normalizedSql = sample.normalizedSql();
        this.statementType = sample.statementType();
        this.executionCount = sample.executionCount();
        this.totalDurationUs = sample.totalDurationUs();
        this.rowsProcessed = sample.rowsProcessed();
        this.observedAt = sample.observedAt();
        this.ingestedAt = ingestedAt;
    }

    public UUID getId() { return id; }
    public String getSourceType() { return sourceType; }
    public String getSampleType() { return sampleType; }
    public String getDbInstanceId() { return dbInstanceId; }
    public String getDbType() { return dbType; }
    public String getSchemaName() { return schemaName; }
    public String getDbUser() { return dbUser; }
    public String getFingerprint() { return fingerprint; }
    public String getNormalizedSql() { return normalizedSql; }
    public String getStatementType() { return statementType; }
    public long getExecutionCount() { return executionCount; }
    public long getTotalDurationUs() { return totalDurationUs; }
    public long getRowsProcessed() { return rowsProcessed; }
    public Instant getObservedAt() { return observedAt; }
    public Instant getIngestedAt() { return ingestedAt; }
}

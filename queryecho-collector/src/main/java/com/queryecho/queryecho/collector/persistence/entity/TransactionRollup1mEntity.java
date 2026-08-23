package com.queryecho.queryecho.collector.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "transaction_rollup_1m")
public class TransactionRollup1mEntity {

    @EmbeddedId
    private TransactionRollup1mId id;

    @Column(name = "tx_count", nullable = false)
    private long txCount;
    @Column(name = "commit_count", nullable = false)
    private long commitCount;
    @Column(name = "rollback_count", nullable = false)
    private long rollbackCount;
    @Column(name = "unknown_count", nullable = false)
    private long unknownCount;
    @Column(name = "total_us", nullable = false)
    private long totalUs;
    @Column(name = "min_us", nullable = false)
    private long minUs;
    @Column(name = "max_us", nullable = false)
    private long maxUs;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "bucket_counts", nullable = false, columnDefinition = "jsonb")
    private Map<String, Long> bucketCounts;

    @Column(name = "histogram_version", nullable = false)
    private short histogramVersion;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected TransactionRollup1mEntity() {
    }

    public TransactionRollup1mEntity(TransactionRollup1mId id, long txCount,
                                     long commitCount, long rollbackCount, long unknownCount,
                                     long totalUs, long minUs, long maxUs,
                                     Map<String, Long> bucketCounts, short histogramVersion,
                                     Instant updatedAt) {
        this.id = id;
        this.txCount = txCount;
        this.commitCount = commitCount;
        this.rollbackCount = rollbackCount;
        this.unknownCount = unknownCount;
        this.totalUs = totalUs;
        this.minUs = minUs;
        this.maxUs = maxUs;
        this.bucketCounts = bucketCounts;
        this.histogramVersion = histogramVersion;
        this.updatedAt = updatedAt;
    }

    public TransactionRollup1mId getId() { return id; }
    public long getTxCount() { return txCount; }
    public long getCommitCount() { return commitCount; }
    public long getRollbackCount() { return rollbackCount; }
    public long getUnknownCount() { return unknownCount; }
    public long getTotalUs() { return totalUs; }
    public long getMinUs() { return minUs; }
    public long getMaxUs() { return maxUs; }
    public Map<String, Long> getBucketCounts() { return bucketCounts; }
    public short getHistogramVersion() { return histogramVersion; }
    public Instant getUpdatedAt() { return updatedAt; }
}

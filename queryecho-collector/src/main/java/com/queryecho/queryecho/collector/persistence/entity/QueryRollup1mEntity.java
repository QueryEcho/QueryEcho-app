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
@Table(name = "query_rollup_1m")
public class QueryRollup1mEntity {

    @EmbeddedId
    private QueryRollup1mId id;

    @Column(name = "exec_count", nullable = false)
    private long execCount;
    @Column(name = "error_count", nullable = false)
    private long errorCount;
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

    protected QueryRollup1mEntity() {
    }

    public QueryRollup1mEntity(QueryRollup1mId id, long execCount, long errorCount,
                              long totalUs, long minUs, long maxUs,
                              Map<String, Long> bucketCounts, short histogramVersion,
                              Instant updatedAt) {
        this.id = id;
        this.execCount = execCount;
        this.errorCount = errorCount;
        this.totalUs = totalUs;
        this.minUs = minUs;
        this.maxUs = maxUs;
        this.bucketCounts = bucketCounts;
        this.histogramVersion = histogramVersion;
        this.updatedAt = updatedAt;
    }

    public QueryRollup1mId getId() { return id; }
    public long getExecCount() { return execCount; }
    public long getErrorCount() { return errorCount; }
    public long getTotalUs() { return totalUs; }
    public long getMinUs() { return minUs; }
    public long getMaxUs() { return maxUs; }
    public Map<String, Long> getBucketCounts() { return bucketCounts; }
    public short getHistogramVersion() { return histogramVersion; }
    public Instant getUpdatedAt() { return updatedAt; }
}

package com.queryecho.queryecho.collector.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

@Embeddable
public class TransactionRollup1mId implements Serializable {

    @Column(name = "bucket_start", nullable = false)
    private Instant bucketStart;

    @Column(nullable = false, length = 30)
    private String environment;

    @Column(name = "pattern_id", nullable = false)
    private Long patternId;

    protected TransactionRollup1mId() {
    }

    public TransactionRollup1mId(Instant bucketStart, String environment, Long patternId) {
        this.bucketStart = bucketStart;
        this.environment = environment;
        this.patternId = patternId;
    }

    public Instant getBucketStart() { return bucketStart; }
    public String getEnvironment() { return environment; }
    public Long getPatternId() { return patternId; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof TransactionRollup1mId that)) return false;
        return Objects.equals(bucketStart, that.bucketStart)
                && Objects.equals(environment, that.environment)
                && Objects.equals(patternId, that.patternId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(bucketStart, environment, patternId);
    }
}

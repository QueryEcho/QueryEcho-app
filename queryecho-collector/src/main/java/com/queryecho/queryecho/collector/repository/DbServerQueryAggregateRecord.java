package com.queryecho.queryecho.collector.repository;

import java.time.Instant;
import java.util.UUID;

public record DbServerQueryAggregateRecord(
        UUID id,
        String sourceType,
        String sampleType,
        String dbInstanceId,
        String dbType,
        String schemaName,
        String dbUser,
        String fingerprint,
        String normalizedSql,
        String statementType,
        long executionCount,
        long totalDurationUs,
        long rowsProcessed,
        Instant observedAt
) {
    public long avgDurationUs() {
        return executionCount == 0 ? 0 : totalDurationUs / executionCount;
    }
}

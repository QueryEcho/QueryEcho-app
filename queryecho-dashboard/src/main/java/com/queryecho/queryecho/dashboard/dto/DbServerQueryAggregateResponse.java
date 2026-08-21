package com.queryecho.queryecho.dashboard.dto;

import com.queryecho.queryecho.collector.repository.DbServerQueryAggregateRecord;
import java.time.Instant;
import java.util.UUID;

public record DbServerQueryAggregateResponse(
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
        long avgDurationUs,
        long rowsProcessed,
        Instant observedAt
) {
    public static DbServerQueryAggregateResponse from(DbServerQueryAggregateRecord record) {
        return new DbServerQueryAggregateResponse(
                record.id(), record.sourceType(), record.sampleType(), record.dbInstanceId(), record.dbType(),
                record.schemaName(), record.dbUser(), record.fingerprint(), record.normalizedSql(),
                record.statementType(), record.executionCount(), record.totalDurationUs(),
                record.avgDurationUs(), record.rowsProcessed(), record.observedAt());
    }
}

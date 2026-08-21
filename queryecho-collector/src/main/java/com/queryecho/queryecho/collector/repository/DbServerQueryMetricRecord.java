package com.queryecho.queryecho.collector.repository;

import java.time.Instant;
import java.util.UUID;

public record DbServerQueryMetricRecord(
        UUID id,
        String sourceType,
        String dbInstanceId,
        String dbType,
        String schemaName,
        String dbUser,
        String clientHost,
        String clientProgram,
        Long connectionId,
        String fingerprint,
        String normalizedSql,
        String statementType,
        long durationUs,
        long lockTimeUs,
        long rowsAffected,
        long rowsSent,
        long rowsExamined,
        boolean succeeded,
        Integer errorCode,
        String sqlState,
        String errorMessage,
        Instant observedAt
) {
}

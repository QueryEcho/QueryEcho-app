package com.queryecho.queryecho.dashboard.dto;

import com.queryecho.queryecho.collector.repository.DbServerQueryMetricRecord;
import java.time.Instant;
import java.util.UUID;

public record DbServerQueryMetricResponse(
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
    public static DbServerQueryMetricResponse from(DbServerQueryMetricRecord record) {
        return new DbServerQueryMetricResponse(
                record.id(), record.sourceType(), record.dbInstanceId(), record.dbType(),
                record.schemaName(), record.dbUser(), record.clientHost(), record.clientProgram(),
                record.connectionId(), record.fingerprint(), record.normalizedSql(),
                record.statementType(), record.durationUs(), record.lockTimeUs(),
                record.rowsAffected(), record.rowsSent(), record.rowsExamined(),
                record.succeeded(), record.errorCode(), record.sqlState(), record.errorMessage(),
                record.observedAt());
    }
}

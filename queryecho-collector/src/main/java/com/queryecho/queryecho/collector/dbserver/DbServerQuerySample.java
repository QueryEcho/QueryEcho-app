package com.queryecho.queryecho.collector.dbserver;

import java.time.Instant;

/** DB 서버에서 직접 관찰한 한 번의 statement 실행. 원본 SQL/파라미터 값은 보관하지 않는다. */
public record DbServerQuerySample(
        String sourceEventKey,
        String dbInstanceId,
        String dbType,
        String schemaName,
        String dbUser,
        String clientHost,
        String clientProgram,
        Long connectionId,
        long threadId,
        long sourceEventId,
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

package com.queryecho.queryecho.collector.dbserver;

import java.time.Instant;

/** 개별 실행 이력을 제공하지 않는 DB에서 누적 통계의 증가분을 나타낸다. */
public record DbServerQueryAggregateSample(
        String sourceEventKey,
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
}

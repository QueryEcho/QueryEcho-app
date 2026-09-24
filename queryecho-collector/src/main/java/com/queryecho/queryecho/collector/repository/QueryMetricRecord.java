package com.queryecho.queryecho.collector.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** 원시 SDK 이벤트에 Collector 분석 결과를 추가한 조회 모델. */
public record QueryMetricRecord(
        UUID eventId,
        UUID transactionId,
        String environment,
        String appName,
        String instanceId,
        String datasourceName,
        String sql,
        String normalizedSql,
        List<Object> params,
        int paramCount,
        long durationUs,
        Instant executedAt,
        String threadName,
        boolean slow,
        int recentRepeatCount,
        boolean succeeded,
        String sqlState,
        String traceId,
        String requestId,
        String httpMethod,
        String httpPath,
        String handlerName
) {
}

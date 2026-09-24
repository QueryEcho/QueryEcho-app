package com.queryecho.queryecho.dashboard.dto;

import com.queryecho.queryecho.collector.repository.QueryMetricRecord;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** 내부 저장 모델과 분리된 쿼리 지표 API 응답. */
public record QueryMetricResponse(
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
    public static QueryMetricResponse from(QueryMetricRecord record) {
        return new QueryMetricResponse(
                record.eventId(),
                record.transactionId(),
                record.environment(),
                record.appName(),
                record.instanceId(),
                record.datasourceName(),
                record.sql(),
                record.normalizedSql(),
                record.params(),
                record.paramCount(),
                record.durationUs(),
                record.executedAt(),
                record.threadName(),
                record.slow(),
                record.recentRepeatCount(),
                record.succeeded(),
                record.sqlState(),
                record.traceId(),
                record.requestId(),
                record.httpMethod(),
                record.httpPath(),
                record.handlerName());
    }
}

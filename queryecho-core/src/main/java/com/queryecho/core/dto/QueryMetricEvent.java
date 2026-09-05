package com.queryecho.core.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** JDBC 실행 한 건을 Collector로 전달하는 버전 독립적인 wire contract. */
public record QueryMetricEvent(
        UUID eventId,
        UUID transactionId,
        String appName,
        String environment,
        String instanceId,
        String datasourceName,
        String dbType,
        String sql,
        String normalizedSql,
        List<Object> params,
        int paramCount,
        long durationUs,
        Instant executedAt,
        String threadName,
        boolean succeeded,
        String sqlState,
        String traceId,
        String requestId,
        String httpMethod,
        String httpPath,
        String handlerName
) {
}

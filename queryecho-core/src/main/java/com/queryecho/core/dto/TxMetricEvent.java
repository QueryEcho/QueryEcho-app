package com.queryecho.core.dto;

import java.time.Instant;
import java.util.UUID;

/** 논리적 이름과 실제 완료 결과를 가진 트랜잭션 실행 이벤트. */
public record TxMetricEvent(
        UUID transactionId,
        String appName,
        String environment,
        String instanceId,
        String transactionName,
        long durationUs,
        TxStatus status,
        Instant completedAt,
        String threadName,
        String failureType,
        String failureMessage,
        String traceId,
        String requestId,
        String httpMethod,
        String httpPath,
        String handlerName
) {
}

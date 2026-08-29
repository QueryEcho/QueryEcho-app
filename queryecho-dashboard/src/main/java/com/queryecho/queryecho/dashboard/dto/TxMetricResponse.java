package com.queryecho.queryecho.dashboard.dto;

import com.queryecho.queryecho.collector.repository.TxMetricRecord;
import com.queryecho.queryecho.sdk.dto.TxStatus;
import java.time.Instant;
import java.util.UUID;

public record TxMetricResponse(
        UUID transactionId,
        String appName,
        String environment,
        String instanceId,
        String transactionName,
        long durationUs,
        TxStatus status,
        Instant executedAt,
        String threadName,
        String failureType,
        String failureMessage,
        String failureReason,
        String traceId,
        String requestId,
        String httpMethod,
        String httpPath,
        String handlerName
) {
    public static TxMetricResponse from(TxMetricRecord record) {
        return new TxMetricResponse(
                record.transactionId(),
                record.appName(),
                record.environment(),
                record.instanceId(),
                record.transactionName(),
                record.durationUs(),
                record.status(),
                record.executedAt(),
                record.threadName(),
                record.failureType(),
                record.failureMessage(),
                record.failureReason(),
                record.traceId(),
                record.requestId(),
                record.httpMethod(),
                record.httpPath(),
                record.handlerName());
    }
}

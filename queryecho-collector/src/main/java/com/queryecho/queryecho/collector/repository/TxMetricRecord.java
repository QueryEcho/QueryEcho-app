package com.queryecho.queryecho.collector.repository;

import com.queryecho.queryecho.sdk.dto.TxStatus;
import java.time.Instant;
import java.util.UUID;

public record TxMetricRecord(
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
        String traceId,
        String requestId,
        String httpMethod,
        String httpPath,
        String handlerName
) {
    public String failureReason() {
        if (failureType == null) {
            return failureMessage;
        }
        return failureMessage == null || failureMessage.isBlank()
                ? failureType
                : failureType + ": " + failureMessage;
    }
}

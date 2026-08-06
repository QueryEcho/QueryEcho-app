package com.queryecho.queryecho.dashboard.dto;

import com.queryecho.queryecho.collector.repository.TxMetricRecord;
import com.queryecho.queryecho.sdk.dto.TxStatus;
import java.time.Instant;

public record TxMetricResponse(
        String transactionName,
        long durationMs,
        TxStatus status,
        Instant executedAt,
        String threadName,
        String failureReason
) {
    public static TxMetricResponse from(TxMetricRecord record) {
        return new TxMetricResponse(
                record.transactionName(),
                record.durationMs(),
                record.status(),
                record.executedAt(),
                record.threadName(),
                record.failureReason());
    }
}

package com.queryecho.queryecho.collector.repository;

import com.queryecho.queryecho.sdk.dto.TxStatus;
import java.time.Instant;

public record TxMetricRecord(
        String transactionName,
        long durationUs,
        TxStatus status,
        Instant executedAt,
        String threadName,
        String failureReason
) {
}

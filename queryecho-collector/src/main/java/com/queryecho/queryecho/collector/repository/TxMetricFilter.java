package com.queryecho.queryecho.collector.repository;

import com.queryecho.queryecho.sdk.dto.TxStatus;
import java.time.Instant;

public record TxMetricFilter(
        Instant from,
        Instant to,
        String environment,
        String appName,
        String instanceId,
        TxStatus status
) {
}

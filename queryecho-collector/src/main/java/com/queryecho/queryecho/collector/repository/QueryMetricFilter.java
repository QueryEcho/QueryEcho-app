package com.queryecho.queryecho.collector.repository;

import java.time.Instant;

public record QueryMetricFilter(
        Instant from,
        Instant to,
        String environment,
        String appName,
        String instanceId,
        String datasourceName,
        Boolean succeeded
) {
}

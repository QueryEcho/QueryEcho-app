package com.queryecho.queryecho.dashboard.dto;

import java.util.List;

public record SlowQueryListResponse(long thresholdMs, int count, List<QueryMetricResponse> items) {
}

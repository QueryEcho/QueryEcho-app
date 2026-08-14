package com.queryecho.queryecho.dashboard.dto;

import java.util.List;

/** thresholdUs: 목록의 durationUs와 같은 축에서 비교할 수 있도록 임계값도 마이크로초로 내려준다. */
public record SlowQueryListResponse(long thresholdUs, int count, List<QueryMetricResponse> items) {
}

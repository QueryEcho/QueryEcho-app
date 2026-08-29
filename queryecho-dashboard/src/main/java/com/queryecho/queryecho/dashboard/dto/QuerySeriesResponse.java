package com.queryecho.queryecho.dashboard.dto;

import java.time.Instant;
import java.util.List;

public record QuerySeriesResponse(int bucketSeconds, List<Bucket> buckets) {
    public record Bucket(
            Instant bucketStart,
            long executionCount,
            long errorCount,
            long totalDurationUs,
            long avgDurationUs,
            long minDurationUs,
            long maxDurationUs
    ) {
    }
}

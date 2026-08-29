package com.queryecho.queryecho.dashboard.dto;

import java.time.Instant;
import java.util.List;

public record TransactionSeriesResponse(int bucketSeconds, List<Bucket> buckets) {
    public record Bucket(
            Instant bucketStart,
            long transactionCount,
            long commitCount,
            long rollbackCount,
            long unknownCount,
            long totalDurationUs,
            long avgDurationUs,
            long minDurationUs,
            long maxDurationUs
    ) {
    }
}

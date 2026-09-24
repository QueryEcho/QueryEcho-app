package com.queryecho.queryecho.dashboard.dto;

import java.time.Instant;
import java.util.List;

/** 시간 구간별 쿼리 지연시간 분포를 반환한다. */
public record LatencyHeatmapResponse(int bucketSeconds, List<Bucket> buckets) {

    /** 소요 시간은 수집 단위 그대로 마이크로초로 내려주고, ms 표기 변환은 표시 계층이 담당한다. */
    public record Bucket(Instant bucketStart, int count, long avgDurationUs, long maxDurationUs) {
    }
}

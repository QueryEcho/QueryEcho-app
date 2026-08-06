package com.queryecho.queryecho.dashboard.controller;

import com.queryecho.queryecho.collector.repository.QueryMetricRecord;
import com.queryecho.queryecho.collector.repository.QueryMetricRepository;
import com.queryecho.queryecho.dashboard.dto.LatencyHeatmapResponse;
import com.queryecho.queryecho.dashboard.dto.QueryMetricResponse;
import com.queryecho.queryecho.collector.config.QueryEchoCollectorProperties;
import com.queryecho.queryecho.dashboard.dto.SlowQueryListResponse;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 쿼리 지표 조회 API.
 * 저장/분석 로직은 전부 collector 계층에 있고, 이 컨트롤러는 조회 + 응답 변환만 담당한다
 * (컨트롤러에 비즈니스 로직을 넣지 않는다는 원칙 - 나중에 GraphQL이나 배치 export 등
 * 다른 진입점이 추가돼도 collector 로직을 재사용할 수 있게).
 */
@RestController
@RequestMapping("/api/v1/metrics/queries")
public class QueryMetricsController {

    private final QueryMetricRepository repository;
    private final QueryEchoCollectorProperties properties;

    public QueryMetricsController(QueryMetricRepository repository, QueryEchoCollectorProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    @GetMapping
    public List<QueryMetricResponse> recent(@RequestParam(defaultValue = "100") int limit) {
        return repository.findRecent(limit).stream().map(QueryMetricResponse::from).toList();
    }

    @GetMapping("/slow")
    public SlowQueryListResponse slow(@RequestParam(defaultValue = "100") int limit) {
        List<QueryMetricResponse> items = repository.findSlow(limit).stream().map(QueryMetricResponse::from).toList();
        return new SlowQueryListResponse(properties.getSlowQueryThresholdMs(), items.size(), items);
    }

    @GetMapping("/heatmap")
    public LatencyHeatmapResponse heatmap(@RequestParam(defaultValue = "10") int bucketSeconds,
                                           @RequestParam(defaultValue = "500") int limit) {
        List<QueryMetricRecord> records = repository.findRecent(limit);

        // 왜 LinkedHashMap인가?
        //  - 버킷 시작 시각(bucketStart) 오름차순으로 응답을 내려줘야 차트 라이브러리가
        //    그대로 x축에 꽂아 쓸 수 있다. records는 findRecent()가 "최신 우선"으로 주므로,
        //    삽입 순서를 보존하는 LinkedHashMap에 굳이 정렬 없이 넣고 마지막에 뒤집으면
        //    별도 Comparator 없이 시간순 정렬을 얻을 수 있다.
        Map<Instant, List<QueryMetricRecord>> grouped = new LinkedHashMap<>();
        long bucketMillis = bucketSeconds * 1000L;
        for (QueryMetricRecord record : records) {
            long epochMs = record.executedAt().toEpochMilli();
            Instant bucketStart = Instant.ofEpochMilli(epochMs - (epochMs % bucketMillis));
            grouped.computeIfAbsent(bucketStart, k -> new java.util.ArrayList<>()).add(record);
        }

        List<LatencyHeatmapResponse.Bucket> buckets = grouped.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> {
                    List<QueryMetricRecord> bucketRecords = e.getValue();
                    long avg = (long) bucketRecords.stream().mapToLong(QueryMetricRecord::durationMs).average().orElse(0);
                    long max = bucketRecords.stream().mapToLong(QueryMetricRecord::durationMs).max().orElse(0);
                    return new LatencyHeatmapResponse.Bucket(e.getKey(), bucketRecords.size(), avg, max);
                })
                .toList();

        return new LatencyHeatmapResponse(bucketSeconds, buckets);
    }
}

package com.queryecho.queryecho.dashboard.controller;

import com.queryecho.queryecho.collector.repository.QueryMetricRecord;
import com.queryecho.queryecho.collector.repository.QueryMetricRepository;
import com.queryecho.queryecho.collector.repository.QueryMetricFilter;
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

/** 수집된 쿼리 지표를 조회 응답으로 변환하는 대시보드 API. */
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
    public List<QueryMetricResponse> recent(
            @RequestParam(defaultValue = "100") int limit,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false) String environment,
            @RequestParam(required = false) String appName,
            @RequestParam(required = false) String instanceId,
            @RequestParam(required = false) String datasourceName,
            @RequestParam(required = false) Boolean succeeded) {
        MetricTimeRange range = MetricTimeRange.resolve(from, to);
        QueryMetricFilter filter = new QueryMetricFilter(range.from(), range.to(), environment,
                appName, instanceId, datasourceName, succeeded);
        return repository.findRecent(limit, filter).stream().map(QueryMetricResponse::from).toList();
    }

    @GetMapping("/slow")
    public SlowQueryListResponse slow(
            @RequestParam(defaultValue = "100") int limit,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false) String environment,
            @RequestParam(required = false) String appName,
            @RequestParam(required = false) String instanceId,
            @RequestParam(required = false) String datasourceName) {
        MetricTimeRange range = MetricTimeRange.resolve(from, to);
        QueryMetricFilter filter = new QueryMetricFilter(range.from(), range.to(), environment,
                appName, instanceId, datasourceName, null);
        List<QueryMetricResponse> items = repository.findSlow(limit, filter).stream()
                .map(QueryMetricResponse::from).toList();
        return new SlowQueryListResponse(properties.slowQueryThresholdUs(), items.size(), items);
    }

    @GetMapping("/heatmap")
    public LatencyHeatmapResponse heatmap(@RequestParam(defaultValue = "10") int bucketSeconds,
                                           @RequestParam(defaultValue = "500") int limit) {
        List<QueryMetricRecord> records = repository.findRecent(limit);

        // 실행 시각을 구간별로 묶고 시간순으로 정렬해 차트 데이터로 변환한다.
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
                    long avg = (long) bucketRecords.stream().mapToLong(QueryMetricRecord::durationUs).average().orElse(0);
                    long max = bucketRecords.stream().mapToLong(QueryMetricRecord::durationUs).max().orElse(0);
                    return new LatencyHeatmapResponse.Bucket(e.getKey(), bucketRecords.size(), avg, max);
                })
                .toList();

        return new LatencyHeatmapResponse(bucketSeconds, buckets);
    }
}

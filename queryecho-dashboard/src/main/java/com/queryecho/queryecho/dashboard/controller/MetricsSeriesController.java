package com.queryecho.queryecho.dashboard.controller;

import com.queryecho.queryecho.collector.repository.QueryMetricFilter;
import com.queryecho.queryecho.collector.repository.TxMetricFilter;
import com.queryecho.queryecho.dashboard.dto.QuerySeriesResponse;
import com.queryecho.queryecho.dashboard.dto.TransactionSeriesResponse;
import com.queryecho.queryecho.dashboard.service.MetricsSeriesService;
import java.time.Instant;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/metrics/series")
public class MetricsSeriesController {

    private final MetricsSeriesService service;

    public MetricsSeriesController(MetricsSeriesService service) {
        this.service = service;
    }

    @GetMapping("/queries")
    public QuerySeriesResponse queries(
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false) String environment,
            @RequestParam(required = false) String appName,
            @RequestParam(required = false) String instanceId,
            @RequestParam(required = false) String datasourceName,
            @RequestParam(required = false) Boolean succeeded) {
        MetricTimeRange range = MetricTimeRange.resolve(from, to);
        return service.querySeries(new QueryMetricFilter(range.from(), range.to(), environment,
                appName, instanceId, datasourceName, succeeded));
    }

    @GetMapping("/transactions")
    public TransactionSeriesResponse transactions(
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false) String environment,
            @RequestParam(required = false) String appName,
            @RequestParam(required = false) String instanceId) {
        MetricTimeRange range = MetricTimeRange.resolve(from, to);
        return service.transactionSeries(new TxMetricFilter(range.from(), range.to(), environment,
                appName, instanceId, null));
    }
}

package com.queryecho.queryecho.dashboard.controller;

import com.queryecho.queryecho.collector.repository.DbServerQueryMetricRepository;
import com.queryecho.queryecho.dashboard.dto.DbServerQueryMetricResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/metrics/server-queries")
public class DbServerQueryMetricsController {

    private final DbServerQueryMetricRepository repository;

    public DbServerQueryMetricsController(DbServerQueryMetricRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<DbServerQueryMetricResponse> recent(@RequestParam(defaultValue = "100") int limit) {
        return repository.findRecent(limit).stream().map(DbServerQueryMetricResponse::from).toList();
    }
}

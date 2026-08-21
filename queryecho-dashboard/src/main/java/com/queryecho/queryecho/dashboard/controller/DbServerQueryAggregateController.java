package com.queryecho.queryecho.dashboard.controller;

import com.queryecho.queryecho.collector.repository.DbServerQueryAggregateRepository;
import com.queryecho.queryecho.dashboard.dto.DbServerQueryAggregateResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/metrics/server-query-aggregates")
public class DbServerQueryAggregateController {

    private final DbServerQueryAggregateRepository repository;

    public DbServerQueryAggregateController(DbServerQueryAggregateRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<DbServerQueryAggregateResponse> recent(@RequestParam(defaultValue = "100") int limit) {
        return repository.findRecent(limit).stream().map(DbServerQueryAggregateResponse::from).toList();
    }
}

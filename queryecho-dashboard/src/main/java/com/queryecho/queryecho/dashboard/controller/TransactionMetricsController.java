package com.queryecho.queryecho.dashboard.controller;

import com.queryecho.queryecho.collector.repository.TxMetricRepository;
import com.queryecho.queryecho.dashboard.dto.TxMetricResponse;
import com.queryecho.queryecho.dashboard.dto.TxStatsResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/metrics/transactions")
public class TransactionMetricsController {

    private final TxMetricRepository repository;

    public TransactionMetricsController(TxMetricRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<TxMetricResponse> recent(@RequestParam(defaultValue = "100") int limit) {
        return repository.findRecent(limit).stream().map(TxMetricResponse::from).toList();
    }

    @GetMapping("/stats")
    public TxStatsResponse stats() {
        return TxStatsResponse.from(repository.stats());
    }
}

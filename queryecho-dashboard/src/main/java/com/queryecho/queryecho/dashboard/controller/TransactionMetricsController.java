package com.queryecho.queryecho.dashboard.controller;

import com.queryecho.queryecho.collector.repository.TxMetricRepository;
import com.queryecho.queryecho.collector.repository.QueryMetricRepository;
import com.queryecho.queryecho.dashboard.dto.QueryMetricResponse;
import com.queryecho.queryecho.dashboard.dto.TxMetricResponse;
import com.queryecho.queryecho.dashboard.dto.TxStatsResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/metrics/transactions")
public class TransactionMetricsController {

    private final TxMetricRepository repository;
    private final QueryMetricRepository queryRepository;

    public TransactionMetricsController(TxMetricRepository repository,
                                        QueryMetricRepository queryRepository) {
        this.repository = repository;
        this.queryRepository = queryRepository;
    }

    @GetMapping
    public List<TxMetricResponse> recent(@RequestParam(defaultValue = "100") int limit) {
        return repository.findRecent(limit).stream().map(TxMetricResponse::from).toList();
    }

    @GetMapping("/stats")
    public TxStatsResponse stats() {
        return TxStatsResponse.from(repository.stats());
    }

    /** 한 트랜잭션 안에서 실제 실행된 쿼리를 시간순으로 내려주는 디버깅용 API. */
    @GetMapping("/{transactionId}/queries")
    public List<QueryMetricResponse> queries(@PathVariable UUID transactionId) {
        return queryRepository.findByTransactionId(transactionId).stream()
                .map(QueryMetricResponse::from)
                .toList();
    }
}

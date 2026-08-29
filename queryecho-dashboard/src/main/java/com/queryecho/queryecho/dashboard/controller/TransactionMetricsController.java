package com.queryecho.queryecho.dashboard.controller;

import com.queryecho.queryecho.collector.repository.TxMetricRepository;
import com.queryecho.queryecho.collector.repository.QueryMetricRepository;
import com.queryecho.queryecho.collector.repository.TxMetricFilter;
import com.queryecho.queryecho.dashboard.dto.QueryMetricResponse;
import com.queryecho.queryecho.dashboard.dto.TxMetricResponse;
import com.queryecho.queryecho.dashboard.dto.TxStatsResponse;
import java.util.List;
import java.util.UUID;
import java.time.Instant;
import com.queryecho.queryecho.sdk.dto.TxStatus;
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
    public List<TxMetricResponse> recent(
            @RequestParam(defaultValue = "100") int limit,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false) String environment,
            @RequestParam(required = false) String appName,
            @RequestParam(required = false) String instanceId,
            @RequestParam(required = false) TxStatus status) {
        MetricTimeRange range = MetricTimeRange.resolve(from, to);
        return repository.findRecent(limit, new TxMetricFilter(range.from(), range.to(), environment,
                        appName, instanceId, status)).stream()
                .map(TxMetricResponse::from).toList();
    }

    @GetMapping("/stats")
    public TxStatsResponse stats(
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false) String environment,
            @RequestParam(required = false) String appName,
            @RequestParam(required = false) String instanceId) {
        MetricTimeRange range = MetricTimeRange.resolve(from, to);
        return TxStatsResponse.from(repository.stats(new TxMetricFilter(range.from(), range.to(),
                environment, appName, instanceId, null)));
    }

    /** 한 트랜잭션 안에서 실제 실행된 쿼리를 시간순으로 내려주는 디버깅용 API. */
    @GetMapping("/{transactionId}/queries")
    public List<QueryMetricResponse> queries(@PathVariable UUID transactionId) {
        return queryRepository.findByTransactionId(transactionId).stream()
                .map(QueryMetricResponse::from)
                .toList();
    }
}

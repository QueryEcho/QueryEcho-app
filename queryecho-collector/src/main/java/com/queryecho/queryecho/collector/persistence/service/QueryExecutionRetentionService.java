package com.queryecho.queryecho.collector.persistence.service;

import com.queryecho.queryecho.collector.config.QueryEchoCollectorProperties;
import com.queryecho.queryecho.collector.persistence.repository.QueryExecutionJpaRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class QueryExecutionRetentionService {

    private final QueryExecutionJpaRepository repository;
    private final QueryEchoCollectorProperties properties;

    public QueryExecutionRetentionService(QueryExecutionJpaRepository repository,
                                          QueryEchoCollectorProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    @Scheduled(cron = "${queryecho.collector.retention.cron:0 15 3 * * *}")
    @Transactional
    public long deleteExpired() {
        Instant cutoff = Instant.now().minus(
                properties.getRetention().getExecutionDays(), ChronoUnit.DAYS);
        return repository.deleteByExecutedAtBefore(cutoff);
    }
}

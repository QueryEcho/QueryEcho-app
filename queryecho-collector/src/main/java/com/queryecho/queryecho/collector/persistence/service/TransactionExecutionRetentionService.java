package com.queryecho.queryecho.collector.persistence.service;

import com.queryecho.queryecho.collector.config.QueryEchoCollectorProperties;
import com.queryecho.queryecho.collector.persistence.repository.TransactionExecutionJpaRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransactionExecutionRetentionService {

    private final TransactionExecutionJpaRepository repository;
    private final QueryEchoCollectorProperties properties;

    public TransactionExecutionRetentionService(TransactionExecutionJpaRepository repository,
                                                QueryEchoCollectorProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    @Scheduled(cron = "${queryecho.collector.retention.cron:0 15 3 * * *}")
    @Transactional
    public long deleteExpired() {
        Instant cutoff = Instant.now().minus(
                properties.getRetention().getExecutionDays(), ChronoUnit.DAYS);
        return repository.deleteByCompletedAtBefore(cutoff);
    }
}

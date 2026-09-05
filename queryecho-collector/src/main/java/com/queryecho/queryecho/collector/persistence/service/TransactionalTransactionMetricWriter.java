package com.queryecho.queryecho.collector.persistence.service;

import com.queryecho.queryecho.collector.persistence.entity.TransactionExecutionEntity;
import com.queryecho.queryecho.collector.persistence.entity.TransactionPatternEntity;
import com.queryecho.queryecho.collector.persistence.repository.TransactionExecutionJpaRepository;
import com.queryecho.queryecho.collector.persistence.repository.TransactionPatternJpaRepository;
import com.queryecho.core.dto.TxMetricEvent;
import com.queryecho.core.dto.TxStatus;
import com.queryecho.core.util.TransactionFingerprint;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class TransactionalTransactionMetricWriter {

    private final TransactionPatternJpaRepository patternRepository;
    private final TransactionExecutionJpaRepository executionRepository;

    TransactionalTransactionMetricWriter(TransactionPatternJpaRepository patternRepository,
                                         TransactionExecutionJpaRepository executionRepository) {
        this.patternRepository = patternRepository;
        this.executionRepository = executionRepository;
    }

    @Transactional
    public boolean write(TxMetricEvent event) {
        UUID transactionId = event.transactionId() == null ? UUID.randomUUID() : event.transactionId();
        if (executionRepository.existsById(transactionId)) {
            return false;
        }

        String appName = truncate(safe(event.appName(), "unknown-app"), 100);
        String transactionName = truncate(safe(event.transactionName(), "UNKNOWN"), 500);
        Instant completedAt = event.completedAt() == null ? Instant.now() : event.completedAt();
        String fingerprint = TransactionFingerprint.sha256(appName, transactionName);

        TransactionPatternEntity pattern = patternRepository.findByFingerprint(fingerprint)
                .map(existing -> {
                    existing.seenAt(completedAt);
                    return existing;
                })
                .orElseGet(() -> patternRepository.save(new TransactionPatternEntity(
                        fingerprint, appName, transactionName, completedAt)));

        executionRepository.save(new TransactionExecutionEntity(
                transactionId,
                pattern,
                truncate(safe(event.environment(), "default"), 30),
                truncate(safe(event.instanceId(), "unknown-instance"), 200),
                completedAt,
                Math.max(0, event.durationUs()),
                event.status() == null ? TxStatus.UNKNOWN : event.status(),
                truncate(event.threadName(), 200),
                truncate(event.failureType(), 200),
                truncate(event.failureMessage(), 1_000),
                truncate(event.traceId(), 64),
                truncate(event.requestId(), 100),
                truncate(event.httpMethod(), 10),
                truncate(event.httpPath(), 500),
                truncate(event.handlerName(), 500),
                Instant.now()));
        return true;
    }

    private static String safe(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}

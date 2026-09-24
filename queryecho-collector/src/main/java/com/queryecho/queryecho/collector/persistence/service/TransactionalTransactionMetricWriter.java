package com.queryecho.queryecho.collector.persistence.service;

import com.queryecho.core.dto.TxMetricEvent;
import com.queryecho.core.dto.TxStatus;
import com.queryecho.core.util.TransactionFingerprint;
import com.queryecho.queryecho.collector.persistence.entity.TransactionExecutionEntity;
import com.queryecho.queryecho.collector.persistence.entity.TransactionPatternEntity;
import com.queryecho.queryecho.collector.persistence.repository.TransactionExecutionJpaRepository;
import com.queryecho.queryecho.collector.persistence.repository.TransactionPatternJpaRepository;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class TransactionalTransactionMetricWriter {

    private final TransactionPatternJpaRepository patternRepository;
    private final TransactionExecutionJpaRepository executionRepository;
    private final EntityManager entityManager;

    TransactionalTransactionMetricWriter(TransactionPatternJpaRepository patternRepository,
                                         TransactionExecutionJpaRepository executionRepository,
                                         EntityManager entityManager) {
        this.patternRepository = patternRepository;
        this.executionRepository = executionRepository;
        this.entityManager = entityManager;
    }

    @Transactional
    public boolean write(TxMetricEvent event) {
        return writeBatch(List.of(event)).getFirst();
    }

    /** 한 HTTP 배치의 중복·패턴을 일괄 조회하고 실행 엔티티를 JDBC batch 대상으로 등록한다. */
    @Transactional
    public List<Boolean> writeBatch(List<TxMetricEvent> events) {
        if (events.isEmpty()) {
            return List.of();
        }

        List<UUID> transactionIds = events.stream()
                .map(event -> event.transactionId() == null ? UUID.randomUUID() : event.transactionId())
                .toList();
        Set<UUID> knownIds = new HashSet<>(executionRepository.findExistingIds(transactionIds));

        List<String> appNames = events.stream()
                .map(event -> truncate(safe(event.appName(), "unknown-app"), 100))
                .toList();
        List<String> transactionNames = events.stream()
                .map(event -> truncate(safe(event.transactionName(), "UNKNOWN"), 500))
                .toList();
        List<String> fingerprints = new ArrayList<>(events.size());
        for (int index = 0; index < events.size(); index++) {
            fingerprints.add(TransactionFingerprint.sha256(appNames.get(index), transactionNames.get(index)));
        }

        Map<String, TransactionPatternEntity> patterns = new HashMap<>();
        patternRepository.findAllByFingerprintIn(new HashSet<>(fingerprints))
                .forEach(pattern -> patterns.put(pattern.getFingerprint(), pattern));

        List<TransactionPatternEntity> missingPatterns = new ArrayList<>();
        for (int index = 0; index < events.size(); index++) {
            TxMetricEvent event = events.get(index);
            Instant completedAt = completedAt(event);
            String fingerprint = fingerprints.get(index);
            TransactionPatternEntity pattern = patterns.get(fingerprint);
            if (pattern == null) {
                pattern = new TransactionPatternEntity(
                        fingerprint, appNames.get(index), transactionNames.get(index), completedAt);
                patterns.put(fingerprint, pattern);
                missingPatterns.add(pattern);
            } else {
                pattern.seenAt(completedAt);
            }
        }
        if (!missingPatterns.isEmpty()) {
            patternRepository.saveAllAndFlush(missingPatterns);
        }

        List<Boolean> inserted = new ArrayList<>(events.size());
        Instant ingestedAt = Instant.now();
        for (int index = 0; index < events.size(); index++) {
            TxMetricEvent event = events.get(index);
            UUID transactionId = transactionIds.get(index);
            if (!knownIds.add(transactionId)) {
                inserted.add(false);
                continue;
            }

            entityManager.persist(new TransactionExecutionEntity(
                    transactionId, patterns.get(fingerprints.get(index)),
                    truncate(safe(event.environment(), "default"), 30),
                    truncate(safe(event.instanceId(), "unknown-instance"), 200),
                    completedAt(event), Math.max(0, event.durationUs()),
                    event.status() == null ? TxStatus.UNKNOWN : event.status(),
                    truncate(event.threadName(), 200), truncate(event.failureType(), 200),
                    truncate(event.failureMessage(), 1_000), truncate(event.traceId(), 64),
                    truncate(event.requestId(), 100), truncate(event.httpMethod(), 10),
                    truncate(event.httpPath(), 500), truncate(event.handlerName(), 500), ingestedAt));
            inserted.add(true);
        }
        return List.copyOf(inserted);
    }

    private static Instant completedAt(TxMetricEvent event) {
        return event.completedAt() == null ? Instant.now() : event.completedAt();
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

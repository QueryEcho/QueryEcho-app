package com.queryecho.queryecho.collector.persistence.service;

import com.queryecho.core.dto.QueryMetricEvent;
import com.queryecho.core.util.QueryFingerprint;
import com.queryecho.queryecho.collector.persistence.entity.QueryExecutionEntity;
import com.queryecho.queryecho.collector.persistence.entity.QueryPatternEntity;
import com.queryecho.queryecho.collector.persistence.repository.QueryExecutionJpaRepository;
import com.queryecho.queryecho.collector.persistence.repository.QueryPatternJpaRepository;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class TransactionalQueryMetricWriter {

    private final QueryPatternJpaRepository patternRepository;
    private final QueryExecutionJpaRepository executionRepository;
    private final EntityManager entityManager;

    TransactionalQueryMetricWriter(QueryPatternJpaRepository patternRepository,
                                   QueryExecutionJpaRepository executionRepository,
                                   EntityManager entityManager) {
        this.patternRepository = patternRepository;
        this.executionRepository = executionRepository;
        this.entityManager = entityManager;
    }

    @Transactional
    public boolean write(QueryMetricEvent event) {
        return writeBatch(List.of(event)).getFirst();
    }

    /** 한 배치의 중복·패턴을 일괄 조회하고 쿼리 실행 지표를 묶어 저장한다. */
    @Transactional
    public List<Boolean> writeBatch(List<QueryMetricEvent> events) {
        if (events.isEmpty()) {
            return List.of();
        }

        List<UUID> eventIds = events.stream().map(QueryMetricEvent::eventId).toList();
        Set<UUID> knownIds = new HashSet<>(executionRepository.findExistingIds(eventIds));

        List<String> fingerprints = events.stream()
                .map(event -> QueryFingerprint.sha256(event.dbType(), event.normalizedSql()))
                .toList();
        Map<String, QueryPatternEntity> patterns = new HashMap<>();
        patternRepository.findAllByFingerprintIn(new HashSet<>(fingerprints))
                .forEach(pattern -> patterns.put(pattern.getFingerprint(), pattern));

        List<QueryPatternEntity> missingPatterns = new ArrayList<>();
        for (int index = 0; index < events.size(); index++) {
            QueryMetricEvent event = events.get(index);
            String fingerprint = fingerprints.get(index);
            QueryPatternEntity pattern = patterns.get(fingerprint);
            if (pattern == null) {
                pattern = new QueryPatternEntity(
                        fingerprint,
                        safe(event.dbType(), "unknown"),
                        safe(event.normalizedSql(), "UNKNOWN"),
                        statementType(event.normalizedSql()),
                        event.executedAt());
                patterns.put(fingerprint, pattern);
                missingPatterns.add(pattern);
            } else {
                pattern.seenAt(event.executedAt());
            }
        }
        if (!missingPatterns.isEmpty()) {
            patternRepository.saveAllAndFlush(missingPatterns);
        }

        List<Boolean> inserted = new ArrayList<>(events.size());
        Instant ingestedAt = Instant.now();
        for (int index = 0; index < events.size(); index++) {
            QueryMetricEvent event = events.get(index);
            if (!knownIds.add(event.eventId())) {
                inserted.add(false);
                continue;
            }

            entityManager.persist(toExecution(event, patterns.get(fingerprints.get(index)), ingestedAt));
            inserted.add(true);
        }
        return List.copyOf(inserted);
    }

    private static QueryExecutionEntity toExecution(QueryMetricEvent event,
                                                     QueryPatternEntity pattern,
                                                     Instant ingestedAt) {
        Map<String, Object> params = null;
        if (!event.params().isEmpty()) {
            params = new LinkedHashMap<>();
            params.put("policyVersion", 1);
            params.put("values", event.params());
        }

        return new QueryExecutionEntity(
                event.eventId(), event.transactionId(), event.executedAt(), ingestedAt,
                safe(event.environment(), "default"), safe(event.appName(), "unknown-app"),
                safe(event.instanceId(), "unknown-instance"),
                safe(event.datasourceName(), "unknown-datasource"), pattern,
                Math.max(0, event.durationUs()), event.succeeded(), truncate(event.sqlState(), 5),
                truncate(event.threadName(), 200), truncate(event.traceId(), 64),
                truncate(event.requestId(), 100), truncate(event.httpMethod(), 10),
                truncate(event.httpPath(), 500), truncate(event.handlerName(), 500),
                (short) Math.min(Short.MAX_VALUE, Math.max(0, event.paramCount())), params);
    }

    private static String statementType(String sql) {
        if (sql == null || sql.isBlank()) {
            return "OTHER";
        }
        String first = sql.stripLeading().split("\\s+", 2)[0].toUpperCase(Locale.ROOT);
        return switch (first) {
            case "SELECT", "INSERT", "UPDATE", "DELETE", "MERGE", "CALL" -> first;
            case "CREATE", "ALTER", "DROP", "TRUNCATE" -> "DDL";
            default -> "OTHER";
        };
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

package com.queryecho.queryecho.collector.persistence.service;

import com.queryecho.queryecho.collector.persistence.entity.QueryExecutionEntity;
import com.queryecho.queryecho.collector.persistence.entity.QueryPatternEntity;
import com.queryecho.queryecho.collector.persistence.repository.QueryExecutionJpaRepository;
import com.queryecho.queryecho.collector.persistence.repository.QueryPatternJpaRepository;
import com.queryecho.queryecho.sdk.dto.QueryMetricEvent;
import com.queryecho.queryecho.sdk.util.QueryFingerprint;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class TransactionalQueryMetricWriter {

    private final QueryPatternJpaRepository patternRepository;
    private final QueryExecutionJpaRepository executionRepository;

    TransactionalQueryMetricWriter(QueryPatternJpaRepository patternRepository,
                                   QueryExecutionJpaRepository executionRepository) {
        this.patternRepository = patternRepository;
        this.executionRepository = executionRepository;
    }

    @Transactional
    public boolean write(QueryMetricEvent event) {
        if (executionRepository.existsById(event.eventId())) {
            return false;
        }

        String fingerprint = QueryFingerprint.sha256(event.dbType(), event.normalizedSql());
        QueryPatternEntity pattern = patternRepository.findByFingerprint(fingerprint)
                .map(existing -> {
                    existing.seenAt(event.executedAt());
                    return existing;
                })
                .orElseGet(() -> patternRepository.save(new QueryPatternEntity(
                        fingerprint,
                        safe(event.dbType(), "unknown"),
                        safe(event.normalizedSql(), "UNKNOWN"),
                        statementType(event.normalizedSql()),
                        event.executedAt())));

        Map<String, Object> params = null;
        if (!event.params().isEmpty()) {
            params = new LinkedHashMap<>();
            params.put("policyVersion", 1);
            params.put("values", event.params());
        }

        QueryExecutionEntity execution = new QueryExecutionEntity(
                event.eventId(),
                event.transactionId(),
                event.executedAt(),
                Instant.now(),
                safe(event.environment(), "default"),
                safe(event.appName(), "unknown-app"),
                safe(event.instanceId(), "unknown-instance"),
                safe(event.datasourceName(), "unknown-datasource"),
                pattern,
                Math.max(0, event.durationUs()),
                event.succeeded(),
                truncate(event.sqlState(), 5),
                truncate(event.threadName(), 200),
                truncate(event.traceId(), 64),
                truncate(event.requestId(), 100),
                truncate(event.httpMethod(), 10),
                truncate(event.httpPath(), 500),
                truncate(event.handlerName(), 500),
                (short) Math.min(Short.MAX_VALUE, Math.max(0, event.paramCount())),
                params);
        executionRepository.save(execution);
        return true;
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

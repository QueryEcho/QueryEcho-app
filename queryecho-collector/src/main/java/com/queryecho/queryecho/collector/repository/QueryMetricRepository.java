package com.queryecho.queryecho.collector.repository;

import com.queryecho.queryecho.collector.config.QueryEchoCollectorProperties;
import com.queryecho.queryecho.collector.persistence.entity.QueryExecutionEntity;
import com.queryecho.queryecho.collector.persistence.repository.QueryExecutionJpaRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** 기존 대시보드 API 계약을 유지하면서 JPA 실행 저장소를 읽는 조회용 어댑터. */
@Repository
@Transactional(readOnly = true)
public class QueryMetricRepository {

    private final QueryExecutionJpaRepository repository;
    private final QueryEchoCollectorProperties properties;

    public QueryMetricRepository(QueryExecutionJpaRepository repository,
                                 QueryEchoCollectorProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    public List<QueryMetricRecord> findRecent(int limit) {
        return findRecent(limit, new QueryMetricFilter(null, null, null, null, null, null, null));
    }

    public List<QueryMetricRecord> findRecent(int limit, QueryMetricFilter filter) {
        return repository.findAll(specification(filter), page(limit)).stream()
                .map(this::toRecord)
                .toList();
    }

    public List<QueryMetricRecord> findSlow(int limit) {
        return findSlow(limit, new QueryMetricFilter(null, null, null, null, null, null, null));
    }

    public List<QueryMetricRecord> findSlow(int limit, QueryMetricFilter filter) {
        Specification<QueryExecutionEntity> slow = (root, query, builder) ->
                builder.greaterThanOrEqualTo(root.get("durationUs"), properties.slowQueryThresholdUs());
        return repository.findAll(specification(filter).and(slow), page(limit)).stream()
                .map(this::toRecord)
                .toList();
    }

    public List<QueryMetricRecord> findRange(QueryMetricFilter filter) {
        return repository.findAll(specification(filter), Sort.by(Sort.Direction.ASC, "executedAt")).stream()
                .map(this::toRecord)
                .toList();
    }

    /** 트랜잭션 상세 화면에서 JDBC 실행 순서 그대로 쿼리 흐름을 복원한다. */
    public List<QueryMetricRecord> findByTransactionId(UUID transactionId) {
        return repository.findByTransactionIdOrderByExecutedAtAsc(transactionId).stream()
                .map(this::toRecord)
                .toList();
    }

    private QueryMetricRecord toRecord(QueryExecutionEntity entity) {
        return new QueryMetricRecord(
                entity.getEventId(),
                entity.getTransactionId(),
                entity.getEnvironment(),
                entity.getAppName(),
                entity.getInstanceId(),
                entity.getDatasourceName(),
                entity.getPattern().getNormalizedSql(),
                entity.getPattern().getNormalizedSql(),
                capturedValues(entity.getParams()),
                entity.getParamCount(),
                entity.getDurationUs(),
                entity.getExecutedAt(),
                entity.getThreadName(),
                entity.getDurationUs() >= properties.slowQueryThresholdUs(),
                0,
                entity.isSucceeded(),
                entity.getSqlState(),
                entity.getTraceId(),
                entity.getRequestId(),
                entity.getHttpMethod(),
                entity.getHttpPath(),
                entity.getHandlerName());
    }

    @SuppressWarnings("unchecked")
    private List<Object> capturedValues(Map<String, Object> params) {
        if (params == null || !(params.get("values") instanceof List<?> values)) {
            return List.of();
        }
        return (List<Object>) values;
    }

    private PageRequest page(int requestedLimit) {
        int safeLimit = Math.max(1, Math.min(requestedLimit, 2_000));
        return PageRequest.of(0, safeLimit, Sort.by(Sort.Direction.DESC, "executedAt"));
    }

    private Specification<QueryExecutionEntity> specification(QueryMetricFilter filter) {
        Specification<QueryExecutionEntity> result = Specification.unrestricted();
        if (filter.from() != null) {
            result = result.and((root, query, builder) ->
                    builder.greaterThanOrEqualTo(root.get("executedAt"), filter.from()));
        }
        if (filter.to() != null) {
            result = result.and((root, query, builder) ->
                    builder.lessThan(root.get("executedAt"), filter.to()));
        }
        if (hasText(filter.environment())) {
            result = result.and((root, query, builder) ->
                    builder.equal(root.get("environment"), filter.environment()));
        }
        if (hasText(filter.appName())) {
            result = result.and((root, query, builder) ->
                    builder.equal(root.get("appName"), filter.appName()));
        }
        if (hasText(filter.instanceId())) {
            result = result.and((root, query, builder) ->
                    builder.equal(root.get("instanceId"), filter.instanceId()));
        }
        if (hasText(filter.datasourceName())) {
            result = result.and((root, query, builder) ->
                    builder.equal(root.get("datasourceName"), filter.datasourceName()));
        }
        if (filter.succeeded() != null) {
            result = result.and((root, query, builder) ->
                    builder.equal(root.get("succeeded"), filter.succeeded()));
        }
        return result;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}

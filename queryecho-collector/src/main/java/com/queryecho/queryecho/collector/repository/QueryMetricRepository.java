package com.queryecho.queryecho.collector.repository;

import com.queryecho.queryecho.collector.config.QueryEchoCollectorProperties;
import com.queryecho.queryecho.collector.persistence.entity.QueryExecutionEntity;
import com.queryecho.queryecho.collector.persistence.repository.QueryExecutionJpaRepository;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

/** 기존 대시보드 API 계약을 유지하면서 JPA 실행 저장소를 읽는 조회용 어댑터. */
@Repository
public class QueryMetricRepository {

    private final QueryExecutionJpaRepository repository;
    private final QueryEchoCollectorProperties properties;

    public QueryMetricRepository(QueryExecutionJpaRepository repository,
                                 QueryEchoCollectorProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    public List<QueryMetricRecord> findRecent(int limit) {
        return repository.findAllByOrderByExecutedAtDesc(page(limit)).stream()
                .map(this::toRecord)
                .toList();
    }

    public List<QueryMetricRecord> findSlow(int limit) {
        return repository.findByDurationUsGreaterThanEqualOrderByExecutedAtDesc(
                        properties.slowQueryThresholdUs(), page(limit)).stream()
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
                entity.getSqlState());
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
        return PageRequest.of(0, safeLimit);
    }
}

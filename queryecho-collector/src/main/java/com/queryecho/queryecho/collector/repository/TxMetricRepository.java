package com.queryecho.queryecho.collector.repository;

import com.queryecho.queryecho.collector.persistence.entity.TransactionExecutionEntity;
import com.queryecho.queryecho.collector.persistence.repository.TransactionExecutionJpaRepository;
import com.queryecho.core.dto.TxStatus;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** 기존 Dashboard API 계약을 유지하면서 PostgreSQL 트랜잭션 실행 테이블을 읽는다. */
@Repository
@Transactional(readOnly = true)
public class TxMetricRepository {

    private final TransactionExecutionJpaRepository repository;

    public TxMetricRepository(TransactionExecutionJpaRepository repository) {
        this.repository = repository;
    }

    public List<TxMetricRecord> findRecent(int requestedLimit) {
        return findRecent(requestedLimit, new TxMetricFilter(null, null, null, null, null, null));
    }

    public List<TxMetricRecord> findRecent(int requestedLimit, TxMetricFilter filter) {
        int limit = Math.max(1, Math.min(requestedLimit, 2_000));
        return repository.findAll(specification(filter),
                        PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "completedAt"))).stream()
                .map(this::toRecord)
                .toList();
    }

    public Stats stats() {
        return stats(new TxMetricFilter(null, null, null, null, null, null));
    }

    public Stats stats(TxMetricFilter filter) {
        List<TransactionExecutionEntity> executions = repository.findAll(specification(filter));
        long commit = executions.stream().filter(e -> e.getStatus() == TxStatus.COMMIT).count();
        long rollback = executions.stream().filter(e -> e.getStatus() == TxStatus.ROLLBACK).count();
        long unknown = executions.stream().filter(e -> e.getStatus() == TxStatus.UNKNOWN).count();
        long total = commit + rollback + unknown;
        double rollbackRate = total == 0 ? 0 : (double) rollback / total;
        double average = executions.stream().mapToLong(TransactionExecutionEntity::getDurationUs)
                .average().orElse(0);
        return new Stats(total, commit, rollback, unknown, rollbackRate,
                average);
    }

    public List<TxMetricRecord> findRange(TxMetricFilter filter) {
        return repository.findAll(specification(filter), Sort.by(Sort.Direction.ASC, "completedAt")).stream()
                .map(this::toRecord)
                .toList();
    }

    private TxMetricRecord toRecord(TransactionExecutionEntity entity) {
        return new TxMetricRecord(
                entity.getTransactionId(),
                entity.getPattern().getAppName(),
                entity.getEnvironment(),
                entity.getInstanceId(),
                entity.getPattern().getTransactionName(),
                entity.getDurationUs(),
                entity.getStatus(),
                entity.getCompletedAt(),
                entity.getThreadName(),
                entity.getFailureType(),
                entity.getFailureMessage(),
                entity.getTraceId(),
                entity.getRequestId(),
                entity.getHttpMethod(),
                entity.getHttpPath(),
                entity.getHandlerName());
    }

    private Specification<TransactionExecutionEntity> specification(TxMetricFilter filter) {
        Specification<TransactionExecutionEntity> result = Specification.unrestricted();
        if (filter.from() != null) {
            result = result.and((root, query, builder) ->
                    builder.greaterThanOrEqualTo(root.get("completedAt"), filter.from()));
        }
        if (filter.to() != null) {
            result = result.and((root, query, builder) ->
                    builder.lessThan(root.get("completedAt"), filter.to()));
        }
        if (hasText(filter.environment())) {
            result = result.and((root, query, builder) ->
                    builder.equal(root.get("environment"), filter.environment()));
        }
        if (hasText(filter.appName())) {
            result = result.and((root, query, builder) ->
                    builder.equal(root.get("pattern").get("appName"), filter.appName()));
        }
        if (hasText(filter.instanceId())) {
            result = result.and((root, query, builder) ->
                    builder.equal(root.get("instanceId"), filter.instanceId()));
        }
        if (filter.status() != null) {
            result = result.and((root, query, builder) ->
                    builder.equal(root.get("status"), filter.status()));
        }
        return result;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    public record Stats(long total, long commitCount, long rollbackCount, long unknownCount,
                        double rollbackRate, double avgDurationUs) {
    }
}

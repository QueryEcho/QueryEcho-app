package com.queryecho.queryecho.collector.repository;

import com.queryecho.queryecho.collector.persistence.entity.TransactionExecutionEntity;
import com.queryecho.queryecho.collector.persistence.repository.TransactionExecutionJpaRepository;
import com.queryecho.queryecho.sdk.dto.TxStatus;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

/** 기존 Dashboard API 계약을 유지하면서 PostgreSQL 트랜잭션 실행 테이블을 읽는다. */
@Repository
public class TxMetricRepository {

    private final TransactionExecutionJpaRepository repository;

    public TxMetricRepository(TransactionExecutionJpaRepository repository) {
        this.repository = repository;
    }

    public List<TxMetricRecord> findRecent(int requestedLimit) {
        int limit = Math.max(1, Math.min(requestedLimit, 2_000));
        return repository.findAllByOrderByCompletedAtDesc(PageRequest.of(0, limit)).stream()
                .map(this::toRecord)
                .toList();
    }

    public Stats stats() {
        long commit = repository.countByStatus(TxStatus.COMMIT);
        long rollback = repository.countByStatus(TxStatus.ROLLBACK);
        long unknown = repository.countByStatus(TxStatus.UNKNOWN);
        long total = commit + rollback + unknown;
        double rollbackRate = total == 0 ? 0 : (double) rollback / total;
        Double average = repository.averageDurationUs();
        return new Stats(total, commit, rollback, unknown, rollbackRate,
                average == null ? 0 : average);
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
                entity.getRequestId());
    }

    public record Stats(long total, long commitCount, long rollbackCount, long unknownCount,
                        double rollbackRate, double avgDurationUs) {
    }
}

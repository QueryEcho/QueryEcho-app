package com.queryecho.queryecho.collector.repository;

import com.queryecho.queryecho.collector.persistence.entity.DbServerQueryExecutionEntity;
import com.queryecho.queryecho.collector.persistence.repository.DbServerQueryExecutionJpaRepository;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

@Repository
public class DbServerQueryMetricRepository {

    private final DbServerQueryExecutionJpaRepository repository;

    public DbServerQueryMetricRepository(DbServerQueryExecutionJpaRepository repository) {
        this.repository = repository;
    }

    public List<DbServerQueryMetricRecord> findRecent(int requestedLimit) {
        int limit = Math.max(1, Math.min(requestedLimit, 2_000));
        return repository.findAllByOrderByObservedAtDesc(PageRequest.of(0, limit)).stream()
                .map(this::toRecord)
                .toList();
    }

    private DbServerQueryMetricRecord toRecord(DbServerQueryExecutionEntity entity) {
        return new DbServerQueryMetricRecord(
                entity.getId(),
                entity.getSourceType(),
                entity.getDbInstanceId(),
                entity.getDbType(),
                entity.getSchemaName(),
                entity.getDbUser(),
                entity.getClientHost(),
                entity.getClientProgram(),
                entity.getConnectionId(),
                entity.getFingerprint(),
                entity.getNormalizedSql(),
                entity.getStatementType(),
                entity.getDurationUs(),
                entity.getLockTimeUs(),
                entity.getRowsAffected(),
                entity.getRowsSent(),
                entity.getRowsExamined(),
                entity.isSucceeded(),
                entity.getErrorCode(),
                entity.getSqlState(),
                entity.getErrorMessage(),
                entity.getObservedAt());
    }
}

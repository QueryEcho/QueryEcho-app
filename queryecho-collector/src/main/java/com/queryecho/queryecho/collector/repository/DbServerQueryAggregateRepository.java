package com.queryecho.queryecho.collector.repository;

import com.queryecho.queryecho.collector.persistence.entity.DbServerQueryAggregateEntity;
import com.queryecho.queryecho.collector.persistence.repository.DbServerQueryAggregateJpaRepository;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

@Repository
public class DbServerQueryAggregateRepository {

    private final DbServerQueryAggregateJpaRepository repository;

    public DbServerQueryAggregateRepository(DbServerQueryAggregateJpaRepository repository) {
        this.repository = repository;
    }

    public List<DbServerQueryAggregateRecord> findRecent(int requestedLimit) {
        int limit = Math.max(1, Math.min(requestedLimit, 2_000));
        return repository.findAllByOrderByObservedAtDesc(PageRequest.of(0, limit)).stream()
                .map(this::toRecord)
                .toList();
    }

    private DbServerQueryAggregateRecord toRecord(DbServerQueryAggregateEntity entity) {
        return new DbServerQueryAggregateRecord(
                entity.getId(), entity.getSourceType(), entity.getSampleType(), entity.getDbInstanceId(),
                entity.getDbType(), entity.getSchemaName(), entity.getDbUser(), entity.getFingerprint(),
                entity.getNormalizedSql(), entity.getStatementType(), entity.getExecutionCount(),
                entity.getTotalDurationUs(), entity.getRowsProcessed(), entity.getObservedAt());
    }
}

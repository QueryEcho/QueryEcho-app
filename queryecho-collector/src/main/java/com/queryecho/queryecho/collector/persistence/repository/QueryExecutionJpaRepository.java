package com.queryecho.queryecho.collector.persistence.repository;

import com.queryecho.queryecho.collector.persistence.entity.QueryExecutionEntity;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface QueryExecutionJpaRepository
        extends JpaRepository<QueryExecutionEntity, UUID>, JpaSpecificationExecutor<QueryExecutionEntity> {

    @EntityGraph(attributePaths = "pattern")
    List<QueryExecutionEntity> findAllByOrderByExecutedAtDesc(Pageable pageable);

    @EntityGraph(attributePaths = "pattern")
    List<QueryExecutionEntity> findByDurationUsGreaterThanEqualOrderByExecutedAtDesc(
            long durationUs, Pageable pageable);

    @EntityGraph(attributePaths = "pattern")
    List<QueryExecutionEntity> findByTransactionIdOrderByExecutedAtAsc(UUID transactionId);

    @EntityGraph(attributePaths = "pattern")
    List<QueryExecutionEntity> findByExecutedAtGreaterThanEqualAndExecutedAtLessThan(
            Instant fromInclusive, Instant toExclusive);

    long deleteByExecutedAtBefore(Instant cutoff);
}

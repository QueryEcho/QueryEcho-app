package com.queryecho.queryecho.collector.persistence.repository;

import com.queryecho.queryecho.collector.persistence.entity.TransactionExecutionEntity;
import com.queryecho.queryecho.sdk.dto.TxStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface TransactionExecutionJpaRepository
        extends JpaRepository<TransactionExecutionEntity, UUID> {

    @EntityGraph(attributePaths = "pattern")
    List<TransactionExecutionEntity> findAllByOrderByCompletedAtDesc(Pageable pageable);

    @EntityGraph(attributePaths = "pattern")
    List<TransactionExecutionEntity> findByCompletedAtGreaterThanEqualAndCompletedAtLessThan(
            Instant fromInclusive, Instant toExclusive);

    long countByStatus(TxStatus status);

    @Query("select avg(t.durationUs) from TransactionExecutionEntity t")
    Double averageDurationUs();

    long deleteByCompletedAtBefore(Instant cutoff);
}

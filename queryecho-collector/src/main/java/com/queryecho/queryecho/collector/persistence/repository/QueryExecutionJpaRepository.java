package com.queryecho.queryecho.collector.persistence.repository;

import com.queryecho.queryecho.collector.persistence.entity.QueryExecutionEntity;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QueryExecutionJpaRepository
        extends JpaRepository<QueryExecutionEntity, UUID>, JpaSpecificationExecutor<QueryExecutionEntity> {

    @Query("select q.eventId from QueryExecutionEntity q where q.eventId in :ids")
    Set<UUID> findExistingIds(@Param("ids") Collection<UUID> ids);

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

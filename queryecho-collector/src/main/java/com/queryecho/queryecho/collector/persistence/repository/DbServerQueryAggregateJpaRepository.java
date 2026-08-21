package com.queryecho.queryecho.collector.persistence.repository;

import com.queryecho.queryecho.collector.persistence.entity.DbServerQueryAggregateEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DbServerQueryAggregateJpaRepository extends JpaRepository<DbServerQueryAggregateEntity, UUID> {
    List<DbServerQueryAggregateEntity> findAllByOrderByObservedAtDesc(Pageable pageable);
}

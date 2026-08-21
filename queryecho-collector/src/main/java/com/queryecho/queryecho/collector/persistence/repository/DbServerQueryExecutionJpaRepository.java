package com.queryecho.queryecho.collector.persistence.repository;

import com.queryecho.queryecho.collector.persistence.entity.DbServerQueryExecutionEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DbServerQueryExecutionJpaRepository
        extends JpaRepository<DbServerQueryExecutionEntity, UUID> {

    List<DbServerQueryExecutionEntity> findAllByOrderByObservedAtDesc(Pageable pageable);
}

package com.queryecho.queryecho.collector.persistence.repository;

import com.queryecho.queryecho.collector.persistence.entity.QueryRollup1mEntity;
import com.queryecho.queryecho.collector.persistence.entity.QueryRollup1mId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QueryRollup1mJpaRepository
        extends JpaRepository<QueryRollup1mEntity, QueryRollup1mId> {
}

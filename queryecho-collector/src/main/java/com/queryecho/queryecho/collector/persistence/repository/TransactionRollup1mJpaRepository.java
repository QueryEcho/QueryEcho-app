package com.queryecho.queryecho.collector.persistence.repository;

import com.queryecho.queryecho.collector.persistence.entity.TransactionRollup1mEntity;
import com.queryecho.queryecho.collector.persistence.entity.TransactionRollup1mId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransactionRollup1mJpaRepository
        extends JpaRepository<TransactionRollup1mEntity, TransactionRollup1mId> {
}

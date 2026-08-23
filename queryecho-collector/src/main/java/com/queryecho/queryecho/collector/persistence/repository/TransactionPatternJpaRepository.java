package com.queryecho.queryecho.collector.persistence.repository;

import com.queryecho.queryecho.collector.persistence.entity.TransactionPatternEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransactionPatternJpaRepository
        extends JpaRepository<TransactionPatternEntity, Long> {
    Optional<TransactionPatternEntity> findByFingerprint(String fingerprint);
}

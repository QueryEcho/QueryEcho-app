package com.queryecho.queryecho.collector.persistence.repository;

import com.queryecho.queryecho.collector.persistence.entity.TransactionPatternEntity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransactionPatternJpaRepository
        extends JpaRepository<TransactionPatternEntity, Long> {
    Optional<TransactionPatternEntity> findByFingerprint(String fingerprint);

    List<TransactionPatternEntity> findAllByFingerprintIn(Collection<String> fingerprints);
}

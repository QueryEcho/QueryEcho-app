package com.queryecho.queryecho.collector.persistence.repository;

import com.queryecho.queryecho.collector.persistence.entity.QueryPatternEntity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QueryPatternJpaRepository extends JpaRepository<QueryPatternEntity, Long> {
    Optional<QueryPatternEntity> findByFingerprint(String fingerprint);

    List<QueryPatternEntity> findAllByFingerprintIn(Collection<String> fingerprints);
}

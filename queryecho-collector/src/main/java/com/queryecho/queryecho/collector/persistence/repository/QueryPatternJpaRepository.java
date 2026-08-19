package com.queryecho.queryecho.collector.persistence.repository;

import com.queryecho.queryecho.collector.persistence.entity.QueryPatternEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QueryPatternJpaRepository extends JpaRepository<QueryPatternEntity, Long> {
    Optional<QueryPatternEntity> findByFingerprint(String fingerprint);
}

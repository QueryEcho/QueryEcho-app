package com.queryecho.queryecho.collector.dbserver;

import com.queryecho.queryecho.collector.persistence.entity.DbServerQueryExecutionEntity;
import com.queryecho.queryecho.collector.persistence.repository.DbServerQueryExecutionJpaRepository;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** performance_schema 순환 버퍼를 반복 조회해도 같은 이벤트는 한 번만 저장한다. */
@Service
public class DbServerQueryPersistenceService {

    private final DbServerQueryExecutionJpaRepository repository;

    public DbServerQueryPersistenceService(DbServerQueryExecutionJpaRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public int saveNew(List<DbServerQuerySample> samples) {
        if (samples.isEmpty()) {
            return 0;
        }

        List<UUID> ids = samples.stream().map(this::idOf).toList();
        Set<UUID> existing = new HashSet<>();
        repository.findAllById(ids).forEach(entity -> existing.add(entity.getId()));

        Instant ingestedAt = Instant.now();
        List<DbServerQueryExecutionEntity> added = new ArrayList<>();
        for (DbServerQuerySample sample : samples) {
            UUID id = idOf(sample);
            if (existing.add(id)) {
                added.add(new DbServerQueryExecutionEntity(id, sample, ingestedAt));
            }
        }
        repository.saveAll(added);
        return added.size();
    }

    UUID idOf(DbServerQuerySample sample) {
        return UUID.nameUUIDFromBytes(sample.sourceEventKey().getBytes(StandardCharsets.UTF_8));
    }
}

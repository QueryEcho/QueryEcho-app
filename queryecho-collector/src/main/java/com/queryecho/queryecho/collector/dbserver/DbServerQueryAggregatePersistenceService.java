package com.queryecho.queryecho.collector.dbserver;

import com.queryecho.queryecho.collector.persistence.entity.DbServerQueryAggregateEntity;
import com.queryecho.queryecho.collector.persistence.repository.DbServerQueryAggregateJpaRepository;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DbServerQueryAggregatePersistenceService {

    private final DbServerQueryAggregateJpaRepository repository;

    public DbServerQueryAggregatePersistenceService(DbServerQueryAggregateJpaRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public boolean save(DbServerQueryAggregateSample sample) {
        UUID id = UUID.nameUUIDFromBytes(sample.sourceEventKey().getBytes(StandardCharsets.UTF_8));
        if (repository.existsById(id)) {
            return false;
        }
        repository.save(new DbServerQueryAggregateEntity(id, sample, Instant.now()));
        return true;
    }
}

package com.queryecho.queryecho.collector.dbserver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.queryecho.queryecho.collector.persistence.entity.DbServerQueryExecutionEntity;
import com.queryecho.queryecho.collector.persistence.repository.DbServerQueryExecutionJpaRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class DbServerQueryPersistenceServiceTest {

    @Test
    void duplicatePerformanceSchemaEventIsStoredOnlyOnce() {
        DbServerQueryExecutionJpaRepository repository = mock(DbServerQueryExecutionJpaRepository.class);
        when(repository.findAllById(any())).thenReturn(List.of());
        DbServerQueryPersistenceService service = new DbServerQueryPersistenceService(repository);

        DbServerQuerySample sample = sample("mysql-local-01:42:7");
        int saved = service.saveNew(List.of(sample, sample));

        assertThat(saved).isEqualTo(1);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<DbServerQueryExecutionEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(repository).saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(1);
        assertThat(captor.getValue().getFirst().getSourceType()).isEqualTo("DB_SERVER");
    }

    @Test
    void alreadyPersistedEventIsIgnored() {
        DbServerQueryExecutionJpaRepository repository = mock(DbServerQueryExecutionJpaRepository.class);
        DbServerQueryPersistenceService service = new DbServerQueryPersistenceService(repository);
        DbServerQuerySample sample = sample("mysql-local-01:42:8");
        DbServerQueryExecutionEntity existing = new DbServerQueryExecutionEntity(
                service.idOf(sample), sample, Instant.now());
        when(repository.findAllById(any())).thenReturn(List.of(existing));

        assertThat(service.saveNew(List.of(sample))).isZero();
    }

    private DbServerQuerySample sample(String key) {
        return new DbServerQuerySample(
                key, "mysql-local-01", "mysql", "querytest", "querytest",
                "localhost", "DBeaver", 10L, 42L, 7L, "fingerprint",
                "INSERT INTO customers (name) VALUES (?)", "INSERT",
                2_000, 0, 1, 0, 0, true, null, null, null, Instant.now());
    }
}

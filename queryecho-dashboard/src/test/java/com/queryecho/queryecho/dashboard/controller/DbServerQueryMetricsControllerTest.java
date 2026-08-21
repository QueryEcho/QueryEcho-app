package com.queryecho.queryecho.dashboard.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.queryecho.queryecho.collector.repository.DbServerQueryMetricRecord;
import com.queryecho.queryecho.collector.repository.DbServerQueryMetricRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DbServerQueryMetricsControllerTest {

    @Test
    void returnsDbServerSourceAndClientContext() {
        DbServerQueryMetricRepository repository = mock(DbServerQueryMetricRepository.class);
        when(repository.findRecent(10)).thenReturn(List.of(new DbServerQueryMetricRecord(
                UUID.randomUUID(), "DB_SERVER", "mysql-local-01", "mysql", "querytest",
                "querytest", "localhost", "DBeaver", 11L, "fingerprint",
                "SELECT * FROM customers WHERE id = ?", "SELECT", 1_200, 0,
                0, 1, 1, true, null, null, null, Instant.now())));

        var response = new DbServerQueryMetricsController(repository).recent(10);

        assertThat(response).hasSize(1);
        assertThat(response.getFirst().sourceType()).isEqualTo("DB_SERVER");
        assertThat(response.getFirst().clientProgram()).isEqualTo("DBeaver");
        assertThat(response.getFirst().normalizedSql()).doesNotContain("1");
    }
}

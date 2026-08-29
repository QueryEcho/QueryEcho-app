package com.queryecho.queryecho.dashboard.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.queryecho.queryecho.collector.repository.QueryMetricRecord;
import com.queryecho.queryecho.collector.repository.QueryMetricRepository;
import com.queryecho.queryecho.collector.repository.TxMetricRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TransactionMetricsControllerTest {

    @Test
    void returnsQueriesBelongingToTransactionInRepositoryOrder() {
        TxMetricRepository txRepository = mock(TxMetricRepository.class);
        QueryMetricRepository queryRepository = mock(QueryMetricRepository.class);
        UUID transactionId = UUID.randomUUID();
        QueryMetricRecord query = new QueryMetricRecord(
                UUID.randomUUID(), transactionId, "local", "orders", "instance-01", "main",
                "select * from orders where id = ?", "select * from orders where id = ?",
                List.of(), 1, 12_000, Instant.now(), "http-1", false, 0, true, null,
                null, "request-01", "GET", "/api/orders/{id}", "OrderController#get");
        when(queryRepository.findByTransactionId(transactionId)).thenReturn(List.of(query));

        var response = new TransactionMetricsController(txRepository, queryRepository).queries(transactionId);

        assertThat(response).singleElement().satisfies(item -> {
            assertThat(item.transactionId()).isEqualTo(transactionId);
            assertThat(item.normalizedSql()).contains("orders");
            assertThat(item.httpPath()).isEqualTo("/api/orders/{id}");
        });
    }
}

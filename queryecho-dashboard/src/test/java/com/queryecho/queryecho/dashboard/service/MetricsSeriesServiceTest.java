package com.queryecho.queryecho.dashboard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.queryecho.queryecho.collector.persistence.repository.QueryRollup1mJpaRepository;
import com.queryecho.queryecho.collector.persistence.repository.TransactionRollup1mJpaRepository;
import com.queryecho.queryecho.collector.repository.QueryMetricFilter;
import com.queryecho.queryecho.collector.repository.QueryMetricRepository;
import com.queryecho.queryecho.collector.repository.TxMetricRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class MetricsSeriesServiceTest {

    @Test
    void readsCompletedMinutesFromQueryRollup() {
        QueryRollup1mJpaRepository queryRollups = mock(QueryRollup1mJpaRepository.class);
        TransactionRollup1mJpaRepository transactionRollups = mock(TransactionRollup1mJpaRepository.class);
        QueryMetricRepository queries = mock(QueryMetricRepository.class);
        TxMetricRepository transactions = mock(TxMetricRepository.class);
        QueryRollup1mJpaRepository.QuerySeriesRow row = mock(
                QueryRollup1mJpaRepository.QuerySeriesRow.class);
        Instant from = Instant.parse("2026-08-29T05:00:00Z");
        Instant to = Instant.parse("2026-08-29T05:03:00Z");

        when(row.getBucketStart()).thenReturn(from);
        when(row.getExecutionCount()).thenReturn(12L);
        when(row.getErrorCount()).thenReturn(2L);
        when(row.getTotalDurationUs()).thenReturn(120_000L);
        when(row.getMinDurationUs()).thenReturn(1_000L);
        when(row.getMaxDurationUs()).thenReturn(50_000L);
        when(queryRollups.findSeries(from, to, "test", "orders-api", null, null))
                .thenReturn(List.of(row));

        MetricsSeriesService service = new MetricsSeriesService(
                queryRollups, transactionRollups, queries, transactions);
        var response = service.querySeries(new QueryMetricFilter(
                from, to, "test", "orders-api", null, null, null));

        assertThat(response.buckets()).singleElement().satisfies(bucket -> {
            assertThat(bucket.executionCount()).isEqualTo(12);
            assertThat(bucket.errorCount()).isEqualTo(2);
            assertThat(bucket.avgDurationUs()).isEqualTo(10_000);
        });
    }
}

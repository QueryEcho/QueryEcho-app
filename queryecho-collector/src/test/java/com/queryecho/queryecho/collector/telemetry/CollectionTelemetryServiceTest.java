package com.queryecho.queryecho.collector.telemetry;

import static org.assertj.core.api.Assertions.assertThat;

import com.queryecho.core.dto.QueryMetricEvent;
import com.queryecho.core.dto.SdkHealthReport;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CollectionTelemetryServiceTest {

    @Test
    void combinesSdkDropAndCollectorPersistenceCountersByInstance() {
        CollectionTelemetryService service = new CollectionTelemetryService();
        Instant now = Instant.now();
        QueryMetricEvent event = new QueryMetricEvent(
                UUID.randomUUID(), null, "orders-api", "test", "instance-01", "main",
                "postgresql", "select 1", "select ?", List.of(), 0,
                1_000, now, "test-thread", true, null,
                null, "request-01", "GET", "/api/orders/{id}", "OrderController#get");

        service.recordSdkHealth(new SdkHealthReport(
                "orders-api", "test", "instance-01", now,
                100, 99, 98, 1, 0, 1, 2, 100, now));
        service.recordAccepted(event);
        service.recordPersisted(event, true);

        var snapshot = service.snapshot("test", "orders-api");

        assertThat(snapshot.sdkCapturedTotal()).isEqualTo(100);
        assertThat(snapshot.sdkDroppedTotal()).isEqualTo(2);
        assertThat(snapshot.collectorPersistedTotal()).isEqualTo(1);
        assertThat(snapshot.instances()).singleElement().satisfies(instance -> {
            assertThat(instance.status()).isEqualTo("DEGRADED");
            assertThat(instance.queueSize()).isEqualTo(2);
        });
    }
}

package com.queryecho.queryecho.collector.telemetry;

import static org.assertj.core.api.Assertions.assertThat;

import com.queryecho.core.dto.QueryMetricEvent;
import com.queryecho.core.dto.SdkHealthReport;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
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
        service.recordReceived(event);
        service.recordAccepted(event);
        service.recordPersisted(event, true);
        service.recordQueryPersistenceDuration(TimeUnit.MILLISECONDS.toNanos(12));

        var snapshot = service.snapshot("test", "orders-api");

        assertThat(snapshot.sdkCapturedTotal()).isEqualTo(100);
        assertThat(snapshot.sdkDroppedTotal()).isEqualTo(2);
        assertThat(snapshot.collectorReceivedTotal()).isEqualTo(1);
        assertThat(snapshot.collectorPersistedTotal()).isEqualTo(1);
        assertThat(snapshot.collectorInFlight()).isZero();
        assertThat(snapshot.persistence().query().count()).isEqualTo(1);
        assertThat(snapshot.persistence().query().averageMs()).isEqualTo(12.0);
        assertThat(snapshot.instances()).singleElement().satisfies(instance -> {
            assertThat(instance.status()).isEqualTo("DEGRADED");
            assertThat(instance.queueSize()).isEqualTo(2);
        });
    }

    @Test
    void summarizesQueueWaitAndPersistenceLatencyWithoutKeepingRawSamples() {
        CollectionTelemetryService service = new CollectionTelemetryService();

        service.recordQueryPersistenceDuration(TimeUnit.MILLISECONDS.toNanos(1));
        service.recordQueryPersistenceDuration(TimeUnit.MILLISECONDS.toNanos(20));
        service.recordQueryPersistenceDuration(TimeUnit.MILLISECONDS.toNanos(200));
        service.recordTransactionPersistenceDuration(TimeUnit.MILLISECONDS.toNanos(50));

        var snapshot = service.snapshot(null, null);

        assertThat(snapshot.persistence().query().count()).isEqualTo(3);
        assertThat(snapshot.persistence().query().averageMs()).isEqualTo(73.67);
        assertThat(snapshot.persistence().query().p50Ms()).isEqualTo(25.0);
        assertThat(snapshot.persistence().query().p95Ms()).isEqualTo(250.0);
        assertThat(snapshot.persistence().query().maxMs()).isEqualTo(200.0);
        assertThat(snapshot.persistence().transaction().averageMs()).isEqualTo(50.0);
        assertThat(snapshot.queueWait().count()).isZero();
    }
}

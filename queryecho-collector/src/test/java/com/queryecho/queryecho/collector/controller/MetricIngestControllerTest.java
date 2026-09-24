package com.queryecho.queryecho.collector.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.queryecho.core.dto.QueryMetricEvent;
import com.queryecho.queryecho.collector.config.QueryEchoCollectorProperties;
import com.queryecho.queryecho.collector.event.QueryMetricBatch;
import com.queryecho.queryecho.collector.telemetry.CollectionTelemetryService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.core.task.TaskRejectedException;

class MetricIngestControllerTest {

    @Test
    void rejectsWholeBatchWhenExecutorCannotAcceptBatchTask() {
        AtomicInteger submissions = new AtomicInteger();
        ApplicationEventPublisher publisher = event -> {
            if (submissions.getAndIncrement() == 0) {
                throw new TaskRejectedException("queue full");
            }
        };
        CollectionTelemetryService telemetry = new CollectionTelemetryService();
        MetricIngestController controller = new MetricIngestController(
                publisher, new QueryEchoCollectorProperties(), telemetry);

        var response = controller.ingestQueries(null, List.of(event(), event(), event()));
        var snapshot = telemetry.snapshot(null, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().accepted()).isZero();
        assertThat(snapshot.collectorReceivedTotal()).isEqualTo(3);
        assertThat(snapshot.collectorAcceptedTotal()).isZero();
        assertThat(snapshot.collectorRejectedTotal()).isEqualTo(3);
        assertThat(snapshot.collectorInFlight()).isZero();
    }

    @Test
    void publishesOneAsyncTaskForAcceptedHttpBatch() {
        AtomicInteger submissions = new AtomicInteger();
        ApplicationEventPublisher publisher = event -> {
            assertThat(event).isInstanceOf(QueryMetricBatch.class);
            assertThat(((QueryMetricBatch) event).events()).hasSize(3);
            submissions.incrementAndGet();
        };
        CollectionTelemetryService telemetry = new CollectionTelemetryService();
        MetricIngestController controller = new MetricIngestController(
                publisher, new QueryEchoCollectorProperties(), telemetry);

        var response = controller.ingestQueries(null, List.of(event(), event(), event()));
        var snapshot = telemetry.snapshot(null, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().accepted()).isEqualTo(3);
        assertThat(submissions).hasValue(1);
        assertThat(snapshot.collectorReceivedTotal()).isEqualTo(3);
        assertThat(snapshot.collectorAcceptedTotal()).isEqualTo(3);
        assertThat(snapshot.collectorRejectedTotal()).isZero();
        assertThat(snapshot.collectorInFlight()).isEqualTo(3);
    }

    private static QueryMetricEvent event() {
        return new QueryMetricEvent(
                UUID.randomUUID(), null, "orders-api", "test", "instance-01", "main",
                "postgresql", "select 1", "select ?", List.of(), 0,
                1_000, Instant.now(), "test-thread", true, null,
                null, "request-01", "GET", "/api/orders/{id}", "OrderController#get");
    }
}

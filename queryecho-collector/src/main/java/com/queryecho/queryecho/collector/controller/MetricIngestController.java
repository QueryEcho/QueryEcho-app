package com.queryecho.queryecho.collector.controller;

import com.queryecho.queryecho.collector.dto.IngestResponse;
import com.queryecho.queryecho.collector.config.QueryEchoCollectorProperties;
import com.queryecho.core.dto.QueryMetricEvent;
import com.queryecho.core.dto.SdkHealthReport;
import com.queryecho.core.dto.TxMetricEvent;
import com.queryecho.queryecho.collector.event.QueryMetricBatch;
import com.queryecho.queryecho.collector.event.TxMetricBatch;
import com.queryecho.queryecho.collector.telemetry.CollectionTelemetryService;
import java.util.List;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 원격 SDK의 지표 배치를 검증하고 비동기 저장 처리에 전달하는 수집 API. */
@RestController
@RequestMapping("/api/v1/ingest")
public class MetricIngestController {

    private static final Logger log = LoggerFactory.getLogger(MetricIngestController.class);

    /** 수집 서버의 메모리를 보호하기 위한 요청당 최대 이벤트 수. */
    private static final int MAX_EVENTS_PER_REQUEST = 5_000;

    private final ApplicationEventPublisher applicationEventPublisher;
    private final QueryEchoCollectorProperties properties;
    private final CollectionTelemetryService telemetry;

    public MetricIngestController(
            ApplicationEventPublisher applicationEventPublisher,
            QueryEchoCollectorProperties properties,
            CollectionTelemetryService telemetry) {
        this.applicationEventPublisher = applicationEventPublisher;
        this.properties = properties;
        this.telemetry = telemetry;
    }

    @PostMapping("/queries")
    public ResponseEntity<IngestResponse> ingestQueries(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody List<QueryMetricEvent> events) {
        if (!isAuthorized(authorization)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new IngestResponse(0));
        }
        return accept(events, "queries", new QueryMetricBatch(events));
    }

    @PostMapping("/transactions")
    public ResponseEntity<IngestResponse> ingestTransactions(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody List<TxMetricEvent> events) {
        if (!isAuthorized(authorization)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new IngestResponse(0));
        }
        return accept(events, "transactions", new TxMetricBatch(events));
    }

    @PostMapping("/sdk-health")
    public ResponseEntity<IngestResponse> ingestSdkHealth(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody SdkHealthReport report) {
        if (!isAuthorized(authorization)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new IngestResponse(0));
        }
        telemetry.recordSdkHealth(report);
        return ResponseEntity.accepted().body(new IngestResponse(1));
    }

    private boolean isAuthorized(String authorization) {
        String expectedKey = properties.getIngestApiKey();
        if (expectedKey == null || expectedKey.isBlank()) {
            return true;
        }
        String expected = "Bearer " + expectedKey;
        if (authorization == null) {
            return false;
        }
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                authorization.getBytes(StandardCharsets.UTF_8));
    }

    private ResponseEntity<IngestResponse> accept(List<?> events, String kind, Object batchEvent) {
        if (events == null || events.isEmpty()) {
            return ResponseEntity.ok(new IngestResponse(0));
        }

        events.forEach(telemetry::recordReceived);

        // 부분 저장을 막기 위해 제한을 초과한 배치 전체를 거절한다.
        if (events.size() > MAX_EVENTS_PER_REQUEST) {
            events.forEach(telemetry::recordRejected);
            log.warn("[QueryEcho] Rejected oversized {} ingest request: {} events (max {})",
                    kind, events.size(), MAX_EVENTS_PER_REQUEST);
            return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                    .body(new IngestResponse(0));
        }

        try {
            // HTTP 배치를 하나의 비동기 작업과 데이터베이스 트랜잭션으로 처리한다.
            applicationEventPublisher.publishEvent(batchEvent);
            events.forEach(telemetry::recordAccepted);
        } catch (RuntimeException ex) {
            events.forEach(telemetry::recordRejected);
            log.warn("[QueryEcho] Collector async queue rejected a {} batch ({} events)",
                    kind, events.size(), ex);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(new IngestResponse(0));
        }

        log.debug("[QueryEcho] Ingested {} {} events", events.size(), kind);

        // 저장 완료가 아닌 비동기 처리 접수를 응답한다.
        return ResponseEntity.accepted().body(new IngestResponse(events.size()));
    }
}

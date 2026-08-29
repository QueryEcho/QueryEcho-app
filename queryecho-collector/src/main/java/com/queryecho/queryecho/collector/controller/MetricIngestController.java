package com.queryecho.queryecho.collector.controller;

import com.queryecho.queryecho.collector.dto.IngestResponse;
import com.queryecho.queryecho.collector.config.QueryEchoCollectorProperties;
import com.queryecho.queryecho.sdk.dto.QueryMetricEvent;
import com.queryecho.queryecho.sdk.dto.SdkHealthReport;
import com.queryecho.queryecho.sdk.dto.TxMetricEvent;
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

/**
 * 원격 SDK({@code queryecho.sdk.transport=HTTP})가 배치로 보내오는 지표를 받는 수신 API.
 * 이 컨트롤러가 생기면서 collector가 비로소 "라이브러리"가 아니라 "서버"가 된다.
 *
 * ── 핵심 설계: 받은 이벤트를 그대로 기존 리스너에게 다시 흘려보낸다 ──
 * 여기서 슬로우쿼리 판정이나 N+1 분석을 새로 구현하지 않는다. 받은 이벤트를
 * ApplicationEventPublisher로 다시 발행하기만 하면, 이미 존재하는
 * {@link com.queryecho.queryecho.collector.service.QueryMetricListener}와
 * {@link com.queryecho.queryecho.collector.service.TxMetricListener}가
 * 그대로 받아서 처리한다.
 *
 * 왜 이 방식인가?
 *  - LOCAL 모드(같은 JVM)와 HTTP 모드(원격)가 "이벤트가 발행된 이후"로는 완전히 동일한
 *    코드 경로를 타게 된다. 분석 로직이 두 벌로 갈라지지 않으므로, 어느 모드로 수집했든
 *    슬로우쿼리 판정 결과가 반드시 같다는 것이 구조적으로 보장된다.
 *  - 즉 이 컨트롤러가 바꾸는 것은 "이벤트가 어디서 들어오는가(입구)"뿐이고,
 *    "이벤트를 어떻게 해석하는가"는 전혀 건드리지 않는다.
 *
 * 왜 MetricEventPublisher가 아니라 ApplicationEventPublisher를 직접 주입받는가?
 *  - MetricEventPublisher는 설정에 따라 HTTP 전송 구현체일 수도 있는 추상화다. 만약 이 서버의
 *    transport 설정이 실수로 HTTP로 되어 있으면, 받은 이벤트를 다시 바깥으로 전송해버려
 *    이벤트가 서버 사이를 무한히 돌 수 있다(전송 루프).
 *  - 이 컨트롤러가 원하는 동작은 "내 프로세스 안의 리스너에게 전달"로 명확하게 하나뿐이므로,
 *    모호함이 없는 ApplicationEventPublisher를 직접 쓴다.
 */
@RestController
@RequestMapping("/api/v1/ingest")
public class MetricIngestController {

    private static final Logger log = LoggerFactory.getLogger(MetricIngestController.class);

    /**
     * 한 요청에 허용하는 최대 이벤트 수.
     * SDK 기본 배치 크기(200)보다 넉넉히 잡되, 비정상적으로 거대한 요청 하나가
     * 수집 서버의 메모리를 통째로 잡아먹는 상황은 막는다.
     */
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
        return accept(events, "queries");
    }

    @PostMapping("/transactions")
    public ResponseEntity<IngestResponse> ingestTransactions(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody List<TxMetricEvent> events) {
        if (!isAuthorized(authorization)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new IngestResponse(0));
        }
        return accept(events, "transactions");
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

    private ResponseEntity<IngestResponse> accept(List<?> events, String kind) {
        if (events == null || events.isEmpty()) {
            return ResponseEntity.ok(new IngestResponse(0));
        }

        // 크기 초과 시 "앞에서 N개만 처리하고 나머지는 조용히 버리기"를 하지 않는다.
        // 그렇게 하면 보내는 쪽은 성공(2xx)으로 알고 넘어가는데 실제로는 데이터가 사라져서,
        // 나중에 "지표가 왜 비어 있지?"를 추적할 방법이 없어진다.
        // 명시적으로 거절하면 SDK가 실패로 인식해 drop 로그를 남기므로 원인 추적이 가능하다.
        if (events.size() > MAX_EVENTS_PER_REQUEST) {
            log.warn("[QueryEcho] Rejected oversized {} ingest request: {} events (max {})",
                    kind, events.size(), MAX_EVENTS_PER_REQUEST);
            return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                    .body(new IngestResponse(0));
        }

        int accepted = 0;
        for (Object event : events) {
            try {
                applicationEventPublisher.publishEvent(event);
                telemetry.recordAccepted(event);
                accepted++;
            } catch (RuntimeException ex) {
                telemetry.recordRejected(event);
                log.warn("[QueryEcho] Collector async queue rejected a {} event", kind, ex);
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .body(new IngestResponse(accepted));
            }
        }

        log.debug("[QueryEcho] Ingested {} {} events", events.size(), kind);

        // 200 OK가 아니라 202 Accepted를 쓴 이유:
        //  - 리스너가 @Async라서 이 응답을 돌려주는 시점에는 아직 분석/저장이 끝나지 않았다.
        //    "처리를 완료했다"가 아니라 "처리하기로 접수했다"가 정확한 의미다.
        //  - SDK는 2xx면 성공으로 간주하므로 202도 정상 처리된다.
        return ResponseEntity.accepted().body(new IngestResponse(events.size()));
    }
}

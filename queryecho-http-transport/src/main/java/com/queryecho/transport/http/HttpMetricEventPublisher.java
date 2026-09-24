package com.queryecho.transport.http;
import com.queryecho.core.publisher.MetricEventPublisher;

import com.queryecho.core.config.SdkOptions;
import com.queryecho.core.dto.QueryMetricEvent;
import com.queryecho.core.dto.SdkHealthReport;
import com.queryecho.core.dto.TxMetricEvent;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;




/** 이벤트를 메모리 큐에 적재하고 원격 Collector로 비동기 배치 전송한다. */
public class HttpMetricEventPublisher implements MetricEventPublisher, AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(HttpMetricEventPublisher.class);

    /** 한 번의 전송 주기에서 처리할 최대 배치 수. */
    private static final int MAX_BATCHES_PER_FLUSH = 10;

    private final BlockingQueue<Object> queue;
    private final ScheduledExecutorService scheduler;
    private final HttpClient httpClient;
    private final EventEncoder encoder;
    private final URI queriesEndpoint;
    private final URI transactionsEndpoint;
    private final URI healthEndpoint;
    private final int batchSize;
    private final Duration requestTimeout;
    private final String apiKey;
    private final String appName;
    private final String environment;
    private final String instanceId;
    private final int queueCapacity;
    private final long healthReportIntervalNanos;
    private final AtomicLong nextHealthReportNanos;
    private final AtomicLong capturedTotal = new AtomicLong();
    private final AtomicLong enqueuedTotal = new AtomicLong();
    private final AtomicLong sentTotal = new AtomicLong();
    private final AtomicLong droppedBufferTotal = new AtomicLong();
    private final AtomicLong droppedSerializationTotal = new AtomicLong();
    private final AtomicLong droppedTransportTotal = new AtomicLong();
    private final AtomicReference<Instant> lastSuccessfulSendAt = new AtomicReference<>();
    private final AtomicLong droppedEvents = new AtomicLong();

    public HttpMetricEventPublisher(SdkOptions properties) {
        this(properties, new JsonEventEncoder());
    }

    public HttpMetricEventPublisher(SdkOptions properties, EventEncoder encoder) {
        this.encoder = java.util.Objects.requireNonNull(encoder, "encoder");
        SdkOptions.Buffer buffer = properties.getBuffer();
        if (buffer.getCapacity() < 1 || buffer.getBatchSize() < 1 || buffer.getBatchSize() > 5000
                || buffer.getFlushIntervalMs() < 1 || buffer.getRequestTimeoutMs() < 1) {
            throw new IllegalArgumentException("Buffer capacity, interval and timeout must be positive; batch size must be 1..5000");
        }
        this.queue = new ArrayBlockingQueue<>(buffer.getCapacity());
        this.batchSize = buffer.getBatchSize();
        this.requestTimeout = Duration.ofMillis(buffer.getRequestTimeoutMs());
        this.apiKey = properties.getApiKey();
        this.appName = properties.getAppName();
        this.environment = properties.getEnvironment();
        this.instanceId = properties.getInstanceId();
        this.queueCapacity = buffer.getCapacity();
        this.healthReportIntervalNanos = TimeUnit.MILLISECONDS.toNanos(
                Math.max(1_000, buffer.getHealthReportIntervalMs()));
        this.nextHealthReportNanos = new AtomicLong(System.nanoTime() + healthReportIntervalNanos);

        String baseUrl = stripTrailingSlash(properties.getCollectorUrl());
        this.queriesEndpoint = URI.create(baseUrl + "/api/v1/ingest/queries");
        this.transactionsEndpoint = URI.create(baseUrl + "/api/v1/ingest/transactions");
        this.healthEndpoint = URI.create(baseUrl + "/api/v1/ingest/sdk-health");

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(this.requestTimeout)
                .build();

        // 데몬 스레드로 실행해 SDK가 애플리케이션 종료를 지연시키지 않도록 한다.
        this.scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "queryecho-sdk-sender");
            thread.setDaemon(true);
            return thread;
        });

        // 이전 전송 완료 후 대기해 지연 상황의 연속 전송을 방지한다.
        this.scheduler.scheduleWithFixedDelay(
                this::flushQuietly,
                buffer.getFlushIntervalMs(),
                buffer.getFlushIntervalMs(),
                TimeUnit.MILLISECONDS);

        log.info("[QueryEcho] SDK HTTP transport enabled -> {} (batchSize={}, flushInterval={}ms, capacity={})",
                baseUrl, batchSize, buffer.getFlushIntervalMs(), buffer.getCapacity());
    }

    @Override
    public void publish(Object event) {
        capturedTotal.incrementAndGet();
        // 큐가 가득 차면 대기하지 않고 이벤트를 버려 애플리케이션 스레드를 보호한다.
        if (!queue.offer(event)) {
            droppedBufferTotal.incrementAndGet();
            droppedEvents.incrementAndGet();
        } else {
            enqueuedTotal.incrementAndGet();
        }
    }

    /** 일시적 오류가 다음 예약 전송을 중단하지 않도록 예외를 내부에서 처리한다. */
    private void flushQuietly() {
        try {
            flush();
        } catch (Throwable ex) {
            log.warn("[QueryEcho] Unexpected error while flushing metrics", ex);
        }
    }

    private void flush() {
        for (int i = 0; i < MAX_BATCHES_PER_FLUSH; i++) {
            List<Object> batch = new ArrayList<>(batchSize);
            queue.drainTo(batch, batchSize);
            if (batch.isEmpty()) {
                break;
            }
            sendBatch(batch);
        }
        reportDroppedIfAny();
        sendHealthIfDue();
    }

    /** 혼합 배치를 쿼리와 트랜잭션 이벤트로 나눠 각 수집 주소로 전송한다. */
    private void sendBatch(List<Object> batch) {
        List<QueryMetricEvent> queries = new ArrayList<>();
        List<TxMetricEvent> transactions = new ArrayList<>();

        for (Object event : batch) {
            if (event instanceof QueryMetricEvent queryEvent) {
                // 전송 전에 허용 목록과 길이 제한으로 매개변수를 정제한다.
                queries.add(queryEvent);
            } else if (event instanceof TxMetricEvent txEvent) {
                transactions.add(txEvent);
            }
        }

        if (!queries.isEmpty()) {
            send(queriesEndpoint, queries);
        }
        if (!transactions.isEmpty()) {
            send(transactionsEndpoint, transactions);
        }
    }

    private void send(URI endpoint, List<?> payload) {
        String json;
        try {
            json = encoder.encode(payload);
        } catch (Exception ex) {
            // 직렬화 실패는 재시도해도 결과가 같으므로 곧바로 버린다.
            droppedSerializationTotal.addAndGet(payload.size());
            droppedEvents.addAndGet(payload.size());
            log.warn("[QueryEcho] Failed to serialize {} metric events, dropping them", payload.size(), ex);
            return;
        }

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(endpoint)
                .header("Content-Type", "application/json; charset=utf-8")
                .timeout(requestTimeout)
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8));
        if (apiKey != null && !apiKey.isBlank()) {
            requestBuilder.header("Authorization", "Bearer " + apiKey);
        }
        HttpRequest request = requestBuilder.build();

        try {
            // 응답 본문은 버리고 상태 코드만 확인한다.
            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() / 100 != 2) {
                droppedTransportTotal.addAndGet(payload.size());
                droppedEvents.addAndGet(payload.size());
                log.warn("[QueryEcho] Collector responded {} for {} events, dropping them",
                        response.statusCode(), payload.size());
            } else {
                sentTotal.addAndGet(payload.size());
                lastSuccessfulSendAt.set(Instant.now());
            }
        } catch (IOException ex) {
            droppedTransportTotal.addAndGet(payload.size());
            droppedEvents.addAndGet(payload.size());
            log.warn("[QueryEcho] Failed to send {} events to {}: {}", payload.size(), endpoint, ex.toString());
        } catch (InterruptedException ex) {
            // 인터럽트를 삼키면 종료 신호가 사라지므로 반드시 플래그를 복원한다.
            Thread.currentThread().interrupt();
            droppedTransportTotal.addAndGet(payload.size());
            droppedEvents.addAndGet(payload.size());
        }
    }

    private void sendHealthIfDue() {
        long now = System.nanoTime();
        long next = nextHealthReportNanos.get();
        if (now < next || !nextHealthReportNanos.compareAndSet(next, now + healthReportIntervalNanos)) {
            return;
        }
        sendHealthReport();
    }

    private void sendHealthReport() {
        SdkHealthReport report = new SdkHealthReport(
                appName,
                environment,
                instanceId,
                Instant.now(),
                capturedTotal.get(),
                enqueuedTotal.get(),
                sentTotal.get(),
                droppedBufferTotal.get(),
                droppedSerializationTotal.get(),
                droppedTransportTotal.get(),
                queue.size(),
                queueCapacity,
                lastSuccessfulSendAt.get());

        try {
            String json = encoder.encode(report);
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(healthEndpoint)
                    .header("Content-Type", "application/json; charset=utf-8")
                    .timeout(requestTimeout)
                    .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8));
            if (apiKey != null && !apiKey.isBlank()) {
                requestBuilder.header("Authorization", "Bearer " + apiKey);
            }
            HttpResponse<Void> response = httpClient.send(
                    requestBuilder.build(), HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() / 100 == 2) {
                lastSuccessfulSendAt.set(Instant.now());
            } else {
                log.debug("[QueryEcho] SDK health report was rejected with status {}", response.statusCode());
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        } catch (Exception ex) {
            log.debug("[QueryEcho] Failed to send SDK health report: {}", ex.toString());
        }
    }

    /** 이번 전송 주기에 버린 이벤트 수를 기록하고 주기별 카운터를 초기화한다. */
    private void reportDroppedIfAny() {
        long dropped = droppedEvents.getAndSet(0);
        if (dropped > 0) {
            log.warn("[QueryEcho] Dropped {} metric events (buffer full or send failed)", dropped);
        }
    }

    /** 종료 전에 큐에 남은 이벤트를 마지막으로 전송한다. */
    @Override
    public void close() {
        scheduler.shutdown();
        try {
            // 진행 중인 전송을 제한 시간까지만 기다려 종료 지연을 방지한다.
            if (!scheduler.awaitTermination(requestTimeout.toMillis(), TimeUnit.MILLISECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            scheduler.shutdownNow();
        }
        flushQuietly();
        sendHealthReport();
        log.info("[QueryEcho] SDK HTTP transport stopped");
    }

    private static String stripTrailingSlash(String url) {
        // 기본 주소 끝의 슬래시를 제거해 수집 경로가 중복되지 않게 한다.
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}

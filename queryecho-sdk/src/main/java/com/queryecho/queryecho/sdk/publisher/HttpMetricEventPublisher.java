package com.queryecho.queryecho.sdk.publisher;

import com.queryecho.queryecho.sdk.config.QueryEchoSdkProperties;
import com.queryecho.queryecho.sdk.dto.QueryMetricEvent;
import com.queryecho.queryecho.sdk.dto.TxMetricEvent;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * 타깃 애플리케이션이 별도 프로세스일 때 쓰는 전송 구현체.
 * 관측한 이벤트를 메모리 큐에 모았다가, 백그라운드 스레드가 주기적으로 배치 단위로
 * Collector 서버에 HTTP POST 한다.
 *
 * ── 설계에서 가장 중요한 원칙: "모니터링이 서비스를 죽이면 안 된다" ──
 * 이 클래스의 거의 모든 결정은 이 한 문장에서 나왔다.
 *
 * 1) publish()는 큐에 넣기만 하고 즉시 리턴한다 (논블로킹).
 *    - 실제 HTTP 전송은 전용 백그라운드 스레드가 담당한다. 쿼리를 실행 중인 요청 스레드가
 *      네트워크 왕복을 기다리는 일은 절대 없어야 하기 때문이다.
 *    - 큐 삽입도 put()이 아니라 offer()를 쓴다. put()은 큐가 가득 차면 호출 스레드를
 *      블로킹하는데, 그러면 Collector가 느려질 때 타깃 애플리케이션까지 같이 멈춘다.
 *
 * 2) 큐가 가득 차면 이벤트를 "버린다" (drop).
 *    - 무한 큐를 쓰면 Collector 장애 시 큐가 끝없이 커져 타깃 앱이 OOM으로 죽는다.
 *      지표 몇 개를 잃는 것과 서비스가 죽는 것 중에는 당연히 전자가 낫다.
 *    - 다만 조용히 버리면 "왜 지표가 비었지?"를 디버깅할 수 없으므로 버린 개수를 세어
 *      로그로 알린다.
 *
 * 3) 전송 실패는 재시도하지 않고 버린다.
 *    - 재시도 큐를 두면 장애 상황에서 메모리와 네트워크 부하가 함께 증폭된다(재시도 폭풍).
 *      지표는 "최신 상태를 보는 것"이 목적이라 과거 데이터를 완벽히 복구할 이유가 적다.
 *
 * 4) 배치로 묶어 보낸다.
 *    - 쿼리 1건마다 HTTP 요청 1건이면 모니터링이 만드는 네트워크 요청 수가 원래 애플리케이션의
 *      쿼리 수와 같아진다. 1초에 한 번 수백 건을 묶어 보내면 요청 수가 수백 분의 1이 된다.
 *
 * HTTP 클라이언트로 JDK 내장 {@link HttpClient}를 쓴 이유:
 *  - SDK는 다른 서비스에 얹히는 라이브러리라 의존성이 가벼울수록 좋다. RestTemplate/WebClient를
 *    쓰면 spring-web 전체를 끌어와야 하는데, JDK 11+ 표준 HttpClient로 충분한 기능이다.
 */
public class HttpMetricEventPublisher implements MetricEventPublisher, AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(HttpMetricEventPublisher.class);

    /**
     * 한 번의 flush 주기에서 최대 몇 개의 배치까지 보낼지.
     * 큐에 밀린 이벤트가 아주 많을 때 전송 스레드가 한 주기에서 빠져나오지 못하는 것을 막는다.
     * (전송이 밀리는 상황이면 어차피 큐 상한에 걸려 drop으로 자연히 정리된다.)
     */
    private static final int MAX_BATCHES_PER_FLUSH = 10;

    private final BlockingQueue<Object> queue;
    private final ScheduledExecutorService scheduler;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final URI queriesEndpoint;
    private final URI transactionsEndpoint;
    private final int batchSize;
    private final Duration requestTimeout;
    private final AtomicLong droppedEvents = new AtomicLong();

    public HttpMetricEventPublisher(QueryEchoSdkProperties properties) {
        QueryEchoSdkProperties.Buffer buffer = properties.getBuffer();
        this.queue = new ArrayBlockingQueue<>(buffer.getCapacity());
        this.batchSize = buffer.getBatchSize();
        this.requestTimeout = Duration.ofMillis(buffer.getRequestTimeoutMs());

        String baseUrl = stripTrailingSlash(properties.getCollectorUrl());
        this.queriesEndpoint = URI.create(baseUrl + "/api/v1/ingest/queries");
        this.transactionsEndpoint = URI.create(baseUrl + "/api/v1/ingest/transactions");

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(this.requestTimeout)
                .build();

        // 왜 Spring이 만들어주는 ObjectMapper 빈을 주입받지 않고 직접 만드는가?
        //  - 타깃 애플리케이션이 자기 목적에 맞게 ObjectMapper를 커스터마이징해 놨을 수 있는데
        //    (예: snake_case 전략, null 필드 제외), 그걸 그대로 쓰면 우리가 Collector와 약속한
        //    JSON 형식이 그 앱의 설정에 따라 제멋대로 바뀐다. SDK가 만들어내는 전송 포맷은
        //    타깃 앱 설정과 무관하게 항상 동일해야 하므로 전용 인스턴스를 따로 만든다.
        this.objectMapper = JsonMapper.builder()
                // Instant를 "1717..." 같은 숫자 타임스탬프가 아니라 ISO-8601 문자열로 보낸다.
                // 사람이 로그로 눈으로 확인하기 쉽고, 타임존 해석 실수도 줄어든다.
                // (Jackson 3의 기본값도 이미 ISO-8601이지만, 타깃 앱이 전역 기본값을 바꿔놨을
                //  가능성까지 감안해 전송 포맷을 여기서 못박아둔다.)
                .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();

        // 왜 데몬 스레드인가?
        //  - 타깃 애플리케이션이 종료될 때 이 스레드 때문에 JVM이 안 꺼지는 일이 없어야 한다.
        //    모니터링 스레드가 애플리케이션의 생명주기를 붙잡는 것은 있을 수 없는 일이다.
        this.scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "queryecho-sdk-sender");
            thread.setDaemon(true);
            return thread;
        });

        // 왜 scheduleAtFixedRate가 아니라 scheduleWithFixedDelay인가?
        //  - FixedRate는 이전 작업이 늦어지면 다음 작업을 곧바로 이어서 실행한다. 전송이 느린
        //    상황에서 전송 작업이 쉴 틈 없이 몰아치게 된다. FixedDelay는 "이전 작업이 끝난 뒤"
        //    일정 시간을 쉬므로, 느려질 때 자연스럽게 부하가 완화된다.
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
        // offer()는 큐가 가득 차 있으면 곧바로 false를 리턴한다(블로킹하지 않는다).
        // 이 한 줄이 "모니터링이 애플리케이션을 멈추지 않는다"는 보장의 핵심이다.
        if (!queue.offer(event)) {
            droppedEvents.incrementAndGet();
        }
    }

    /**
     * 스케줄러에 등록되는 진입점.
     *
     * 왜 Throwable까지 통째로 잡는가?
     *  - ScheduledExecutorService는 반복 작업이 예외를 던지면 그 작업을 조용히 취소해버린다.
     *    한 번의 일시적 오류로 이후 모든 전송이 영구히 멈추는 최악의 상황을 막으려면,
     *    이 메서드 밖으로는 어떤 예외도 새어나가면 안 된다.
     */
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
    }

    /**
     * 한 배치 안에는 쿼리 이벤트와 트랜잭션 이벤트가 섞여 있으므로, 종류별로 나눠서
     * 각각의 엔드포인트로 보낸다. (Collector 쪽에서 타입을 판별하는 로직을 두지 않기 위해
     * 엔드포인트 자체를 분리했다 - 서버가 받은 JSON을 그대로 해당 타입으로 역직렬화하면 된다.)
     */
    private void sendBatch(List<Object> batch) {
        List<QueryMetricEvent> queries = new ArrayList<>();
        List<TxMetricEvent> transactions = new ArrayList<>();

        for (Object event : batch) {
            if (event instanceof QueryMetricEvent queryEvent) {
                // 파라미터 허용 목록/길이 제한은 전송 구현체 앞의
                // SanitizingMetricEventPublisher가 이미 적용했다.
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
            json = objectMapper.writeValueAsString(payload);
        } catch (Exception ex) {
            // 직렬화 실패는 재시도해도 결과가 같으므로 곧바로 버린다.
            droppedEvents.addAndGet(payload.size());
            log.warn("[QueryEcho] Failed to serialize {} metric events, dropping them", payload.size(), ex);
            return;
        }

        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .header("Content-Type", "application/json; charset=utf-8")
                .timeout(requestTimeout)
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();

        try {
            // BodyHandlers.discarding(): 응답 본문은 필요 없다. 상태 코드만 보면 되므로
            // 본문을 메모리에 읽어들이지 않아 불필요한 할당을 피한다.
            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() / 100 != 2) {
                droppedEvents.addAndGet(payload.size());
                log.warn("[QueryEcho] Collector responded {} for {} events, dropping them",
                        response.statusCode(), payload.size());
            }
        } catch (IOException ex) {
            droppedEvents.addAndGet(payload.size());
            log.warn("[QueryEcho] Failed to send {} events to {}: {}", payload.size(), endpoint, ex.toString());
        } catch (InterruptedException ex) {
            // 인터럽트를 삼키면 종료 신호가 사라지므로 반드시 플래그를 복원한다.
            Thread.currentThread().interrupt();
            droppedEvents.addAndGet(payload.size());
        }
    }

    /**
     * 버린 이벤트 수를 로그로 알리고 카운터를 0으로 되돌린다.
     * getAndSet(0)을 쓰는 이유는, "지금까지 총 몇 개"가 아니라 "이번 주기에 몇 개"를 보여줘야
     * 문제가 지금 진행 중인지 과거에 한 번 있었던 일인지 구분할 수 있기 때문이다.
     */
    private void reportDroppedIfAny() {
        long dropped = droppedEvents.getAndSet(0);
        if (dropped > 0) {
            log.warn("[QueryEcho] Dropped {} metric events (buffer full or send failed)", dropped);
        }
    }

    /**
     * 애플리케이션 종료 시 호출된다(Spring이 빈 소멸 시점에 실행).
     * 큐에 남아있는 이벤트를 마지막으로 한 번 밀어 넣어, 종료 직전 지표가 통째로
     * 사라지는 일을 줄인다.
     */
    @Override
    public void close() {
        scheduler.shutdown();
        try {
            // 진행 중이던 전송이 끝날 시간을 잠깐 준다. 무한정 기다리면 애플리케이션 종료가
            // 모니터링 때문에 지연되므로 상한을 둔다.
            if (!scheduler.awaitTermination(requestTimeout.toMillis(), TimeUnit.MILLISECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            scheduler.shutdownNow();
        }
        flushQuietly();
        log.info("[QueryEcho] SDK HTTP transport stopped");
    }

    private static String stripTrailingSlash(String url) {
        // "http://localhost:8080/" 처럼 끝에 슬래시가 붙어도 "//api/v1/..."이 되지 않도록 정리한다.
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}

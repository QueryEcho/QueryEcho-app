package com.queryecho.queryecho.collector.service;

import com.queryecho.queryecho.collector.config.QueryEchoCollectorProperties;
import com.queryecho.queryecho.collector.repository.QueryMetricRecord;
import com.queryecho.queryecho.collector.repository.QueryMetricRepository;
import com.queryecho.queryecho.sdk.dto.QueryMetricEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * SDK가 발행한 원시 {@link QueryMetricEvent}를 받아 슬로우 쿼리 판정 + N+1 판정을 거쳐
 * 저장소에 쌓는 collector의 진입점.
 *
 * 왜 @Async인가?
 *  - 이 리스너는 실제 애플리케이션 스레드(HTTP 요청을 처리 중인 스레드)와 같은 스레드에서
 *    이벤트를 받는다(Spring 이벤트는 기본적으로 동기 호출). 만약 여기서 동기적으로
 *    분석/저장을 수행하면 "모니터링 오버헤드가 실제 요청 지연시간에 그대로 더해지는" 문제가
 *    생긴다. @Async로 별도 스레드 풀({@link com.queryecho.queryecho.collector.config.AsyncConfig})에
 *    위임하면 원래 요청은 쿼리가 끝나는 즉시 진행되고, 지표 가공은 백그라운드에서 처리된다.
 *  - 대가로 "쿼리 실행 시각"과 "지표가 저장소에 반영되는 시각" 사이에 약간의 지연이 생기지만,
 *    모니터링 도구 특성상 최신성보다 애플리케이션 성능에 대한 낮은 침습성이 더 중요하다고 판단했다.
 */
@Component
public class QueryMetricListener {

    private static final Logger log = LoggerFactory.getLogger(QueryMetricListener.class);

    private final QueryEchoCollectorProperties properties;
    private final RepeatedQueryDetectionService repeatedQueryDetectionService;
    private final QueryMetricRepository repository;

    public QueryMetricListener(QueryEchoCollectorProperties properties,
                                RepeatedQueryDetectionService repeatedQueryDetectionService,
                                QueryMetricRepository repository) {
        this.properties = properties;
        this.repeatedQueryDetectionService = repeatedQueryDetectionService;
        this.repository = repository;
    }

    @Async
    @EventListener
    public void onQueryMetric(QueryMetricEvent event) {
        boolean slow = event.durationMs() >= properties.getSlowQueryThresholdMs();
        int repeatCount = repeatedQueryDetectionService.recordAndCount(event);

        if (slow) {
            log.warn("[QueryEcho] Slow query ({}ms >= {}ms threshold): {}",
                    event.durationMs(), properties.getSlowQueryThresholdMs(), event.normalizedSql());
        }
        if (repeatCount >= properties.getNPlusOne().getThreshold()) {
            log.warn("[QueryEcho] Possible N+1 pattern: '{}' executed {} times within {}ms on thread [{}]",
                    event.normalizedSql(), repeatCount, properties.getNPlusOne().getWindowMs(), event.threadName());
        }

        repository.save(new QueryMetricRecord(
                event.sql(),
                event.normalizedSql(),
                event.params(),
                event.durationMs(),
                event.executedAt(),
                event.threadName(),
                slow,
                repeatCount));
    }
}

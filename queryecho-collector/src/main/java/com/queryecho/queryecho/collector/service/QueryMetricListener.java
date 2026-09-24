package com.queryecho.queryecho.collector.service;

import com.queryecho.queryecho.collector.config.QueryEchoCollectorProperties;
import com.queryecho.queryecho.collector.event.QueryMetricBatch;
import com.queryecho.queryecho.collector.persistence.service.QueryMetricPersistenceService;
import com.queryecho.core.dto.QueryMetricEvent;
import com.queryecho.queryecho.collector.telemetry.CollectionTelemetryService;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/** 쿼리 지표를 비동기로 분석하고 배치 단위로 저장한다. */
@Component
public class QueryMetricListener {

    private static final Logger log = LoggerFactory.getLogger(QueryMetricListener.class);

    private final QueryEchoCollectorProperties properties;
    private final RepeatedQueryDetectionService repeatedQueryDetectionService;
    private final QueryMetricPersistenceService persistenceService;
    private final CollectionTelemetryService telemetry;

    public QueryMetricListener(QueryEchoCollectorProperties properties,
                                RepeatedQueryDetectionService repeatedQueryDetectionService,
                                QueryMetricPersistenceService persistenceService,
                                CollectionTelemetryService telemetry) {
        this.properties = properties;
        this.repeatedQueryDetectionService = repeatedQueryDetectionService;
        this.persistenceService = persistenceService;
        this.telemetry = telemetry;
    }

    @Async
    @EventListener
    public void onQueryMetric(QueryMetricEvent event) {
        analyze(event);

        long persistenceStartedAt = System.nanoTime();
        try {
            telemetry.recordPersisted(event, persistenceService.save(event));
        } catch (RuntimeException ex) {
            telemetry.recordPersistenceFailure(event);
            throw ex;
        } finally {
            telemetry.recordQueryPersistenceDuration(System.nanoTime() - persistenceStartedAt);
        }
    }

    @Async
    @EventListener
    public void onQueryMetricBatch(QueryMetricBatch batch) {
        List<QueryMetricEvent> events = batch.events();
        events.forEach(this::analyze);

        long persistenceStartedAt = System.nanoTime();
        try {
            List<Boolean> inserted = persistenceService.saveBatch(events);
            for (int index = 0; index < events.size(); index++) {
                telemetry.recordPersisted(events.get(index), inserted.get(index));
            }
        } catch (RuntimeException ex) {
            events.forEach(telemetry::recordPersistenceFailure);
            throw ex;
        } finally {
            long duration = System.nanoTime() - persistenceStartedAt;
            events.forEach(ignored -> telemetry.recordQueryPersistenceDuration(duration));
        }
    }

    private void analyze(QueryMetricEvent event) {
        // 이벤트는 us, 설정은 ms 단위이므로 임계값을 us로 환산해 같은 단위끼리 비교한다.
        boolean slow = event.durationUs() >= properties.slowQueryThresholdUs();
        int repeatCount = repeatedQueryDetectionService.recordAndCount(event);

        if (slow) {
            log.warn("[QueryEcho] Slow query ({} >= {} threshold): {}",
                    DurationFormat.toMillisText(event.durationUs()),
                    DurationFormat.toMillisText(properties.slowQueryThresholdUs()),
                    event.normalizedSql());
        }
        if (repeatCount >= properties.getNPlusOne().getThreshold()) {
            log.warn("[QueryEcho] Possible N+1 pattern: '{}' executed {} times within {}ms on thread [{}]",
                    event.normalizedSql(), repeatCount, properties.getNPlusOne().getWindowMs(), event.threadName());
        }
    }
}

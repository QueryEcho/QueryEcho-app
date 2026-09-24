package com.queryecho.queryecho.collector.service;

import com.queryecho.queryecho.collector.persistence.service.TransactionMetricPersistenceService;
import com.queryecho.core.dto.TxMetricEvent;
import com.queryecho.core.dto.TxStatus;
import com.queryecho.queryecho.collector.event.TxMetricBatch;
import com.queryecho.queryecho.collector.telemetry.CollectionTelemetryService;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/** 트랜잭션 지표를 비동기로 기록하고 배치 단위로 저장한다. */
@Component
public class TxMetricListener {

    private static final Logger log = LoggerFactory.getLogger(TxMetricListener.class);

    private final TransactionMetricPersistenceService persistenceService;
    private final CollectionTelemetryService telemetry;

    public TxMetricListener(TransactionMetricPersistenceService persistenceService,
                            CollectionTelemetryService telemetry) {
        this.persistenceService = persistenceService;
        this.telemetry = telemetry;
    }

    @Async
    @EventListener
    public void onTxMetric(TxMetricEvent event) {
        logRollback(event);
        long persistenceStartedAt = System.nanoTime();
        try {
            telemetry.recordPersisted(event, persistenceService.save(event));
        } catch (RuntimeException ex) {
            telemetry.recordPersistenceFailure(event);
            throw ex;
        } finally {
            telemetry.recordTransactionPersistenceDuration(System.nanoTime() - persistenceStartedAt);
        }
    }

    @Async
    @EventListener
    public void onTxMetricBatch(TxMetricBatch batch) {
        List<TxMetricEvent> events = batch.events();
        events.forEach(this::logRollback);

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
            events.forEach(ignored -> telemetry.recordTransactionPersistenceDuration(duration));
        }
    }

    private void logRollback(TxMetricEvent event) {
        if (event.status() == TxStatus.ROLLBACK) {
            log.warn("[QueryEcho] Transaction rolled back ({}): {} - {}",
                    DurationFormat.toMillisText(event.durationUs()), event.transactionName(),
                    event.failureType());
        }
    }
}

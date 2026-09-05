package com.queryecho.queryecho.collector.service;

import com.queryecho.queryecho.collector.persistence.service.TransactionMetricPersistenceService;
import com.queryecho.core.dto.TxMetricEvent;
import com.queryecho.core.dto.TxStatus;
import com.queryecho.queryecho.collector.telemetry.CollectionTelemetryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * SDK가 발행한 {@link TxMetricEvent}를 저장소에 쌓는다.
 * @Async를 쓰는 이유는 {@link QueryMetricListener}와 동일 (요청 스레드 비침습).
 */
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
        if (event.status() == TxStatus.ROLLBACK) {
            log.warn("[QueryEcho] Transaction rolled back ({}): {} - {}",
                    DurationFormat.toMillisText(event.durationUs()), event.transactionName(),
                    event.failureType());
        }
        try {
            telemetry.recordPersisted(event, persistenceService.save(event));
        } catch (RuntimeException ex) {
            telemetry.recordPersistenceFailure(event);
            throw ex;
        }
    }
}

package com.queryecho.sdk;

import com.queryecho.core.config.SdkOptions;
import com.queryecho.core.dto.TxMetricEvent;
import com.queryecho.core.dto.TxStatus;
import com.queryecho.core.publisher.MetricEventPublisher;
import com.queryecho.jdbc.HttpRequestContext;
import com.queryecho.jdbc.TransactionContext;
import java.time.Instant;
import java.util.UUID;

/** 같은 스레드에서 사용한다. DB 제어가 아닌 측정 범위이며 미완료 close는 UNKNOWN으로 남긴다. */
public final class QueryEchoTransaction implements AutoCloseable {
    private final UUID id = UUID.randomUUID();
    private final Thread owner = Thread.currentThread();
    private final long started = System.nanoTime();
    private final SdkOptions config;
    private final MetricEventPublisher publisher;
    private final String name;
    private final HttpRequestContext.Snapshot request = HttpRequestContext.current();
    private boolean completed;

    QueryEchoTransaction(SdkOptions config, MetricEventPublisher publisher, String name) {
        this.config = config;
        this.publisher = publisher;
        this.name = name;
        TransactionContext.push(id);
    }

    public UUID transactionId() { return id; }
    public void committed() { complete(TxStatus.COMMIT, null); }
    public void rolledBack(Throwable failure) { complete(TxStatus.ROLLBACK, failure); }

    private void complete(TxStatus status, Throwable failure) {
        if (Thread.currentThread() != owner) throw new IllegalStateException("Complete transaction on its owning thread");
        if (completed) return;
        completed = true;
        try {
            publisher.publish(new TxMetricEvent(id, config.getAppName(), config.getEnvironment(), config.getInstanceId(),
                    name, Math.max(0, (System.nanoTime() - started) / 1000), status, Instant.now(), owner.getName(),
                    failure == null ? null : failure.getClass().getName(), failure == null ? null : failure.getMessage(),
                    request == null ? null : request.traceId(), request == null ? null : request.requestId(),
                    request == null ? null : request.httpMethod(), request == null ? null : request.httpPath(),
                    request == null ? null : request.handlerName()));
        } finally { TransactionContext.remove(id); }
    }

    @Override public void close() { complete(TxStatus.UNKNOWN, null); }
}

package com.queryecho.queryecho.collector.persistence.service;

import com.queryecho.core.dto.TxMetricEvent;
import com.queryecho.core.util.TransactionFingerprint;
import java.util.List;
import org.springframework.stereotype.Service;

/** 동일 패턴 최초 저장 경쟁을 고정 크기 striped lock으로 직렬화한다. */
@Service
public class TransactionMetricPersistenceService {

    private static final int LOCK_COUNT = 256;

    private final TransactionalTransactionMetricWriter writer;
    private final Object[] locks = new Object[LOCK_COUNT];
    private final Object batchWriteLock = new Object();

    public TransactionMetricPersistenceService(TransactionalTransactionMetricWriter writer) {
        this.writer = writer;
        for (int i = 0; i < locks.length; i++) {
            locks[i] = new Object();
        }
    }

    public boolean save(TxMetricEvent event) {
        String appName = safe(event.appName(), "unknown-app");
        String transactionName = safe(event.transactionName(), "UNKNOWN");
        String fingerprint = TransactionFingerprint.sha256(appName, transactionName);
        Object lock = locks[(fingerprint.hashCode() & Integer.MAX_VALUE) % locks.length];
        synchronized (lock) {
            return writer.write(event);
        }
    }

    /** 같은 HTTP 배치의 조회·저장·커밋을 한 트랜잭션으로 묶는다. */
    public List<Boolean> saveBatch(List<TxMetricEvent> events) {
        synchronized (batchWriteLock) {
            return writer.writeBatch(events);
        }
    }

    private static String safe(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}

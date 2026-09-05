package com.queryecho.queryecho.collector.persistence.service;

import com.queryecho.core.dto.QueryMetricEvent;
import com.queryecho.core.util.QueryFingerprint;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Service;

/** 같은 fingerprint의 최초 저장 경쟁을 직렬화하고 실제 DB 트랜잭션에 위임한다. */
@Service
public class QueryMetricPersistenceService {

    private final TransactionalQueryMetricWriter writer;
    private final ConcurrentMap<String, Object> patternLocks = new ConcurrentHashMap<>();

    public QueryMetricPersistenceService(TransactionalQueryMetricWriter writer) {
        this.writer = writer;
    }

    public boolean save(QueryMetricEvent event) {
        String fingerprint = QueryFingerprint.sha256(event.dbType(), event.normalizedSql());
        Object lock = patternLocks.computeIfAbsent(fingerprint, ignored -> new Object());
        synchronized (lock) {
            return writer.write(event);
        }
    }
}

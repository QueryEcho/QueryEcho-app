package com.queryecho.queryecho.collector.service;

import com.queryecho.queryecho.collector.config.QueryEchoCollectorProperties;
import com.queryecho.core.dto.QueryMetricEvent;
import java.time.Instant;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import org.springframework.stereotype.Service;

/** 같은 스레드에서 반복된 정규화 SQL을 시간 구간별로 탐지한다. */
@Service
public class RepeatedQueryDetectionService {

    private final QueryEchoCollectorProperties properties;

    // 실행 시각을 큐에 저장해 시간 구간 밖의 기록을 순서대로 제거한다.
    private final Map<Key, Deque<Instant>> recentExecutions = new ConcurrentHashMap<>();

    public RepeatedQueryDetectionService(QueryEchoCollectorProperties properties) {
        this.properties = properties;
    }

    /** 쿼리를 기록하고 현재 시간 구간의 동일 패턴 실행 횟수를 반환한다. */
    public int recordAndCount(QueryMetricEvent event) {
        Key key = new Key(event.threadName(), event.normalizedSql());
        Deque<Instant> timestamps = recentExecutions.computeIfAbsent(key, k -> new ConcurrentLinkedDeque<>());
        timestamps.addLast(event.executedAt());

        Instant windowStart = event.executedAt().minusMillis(properties.getNPlusOne().getWindowMs());
        while (!timestamps.isEmpty() && timestamps.peekFirst().isBefore(windowStart)) {
            timestamps.pollFirst();
        }
        return timestamps.size();
    }

    private record Key(String threadName, String normalizedSql) {
    }
}

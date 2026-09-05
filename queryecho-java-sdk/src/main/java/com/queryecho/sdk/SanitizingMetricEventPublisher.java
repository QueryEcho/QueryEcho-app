package com.queryecho.sdk;
import com.queryecho.core.publisher.MetricEventPublisher;

import com.queryecho.core.config.SdkOptions;
import com.queryecho.core.dto.QueryMetricEvent;
import com.queryecho.core.dto.TxMetricEvent;
import com.queryecho.core.util.QueryFingerprint;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** 민감한 바인딩 값이 SDK 프로세스 밖으로 나가기 전에 허용 목록만 남긴다. */
public final class SanitizingMetricEventPublisher implements MetricEventPublisher, AutoCloseable {

    private final MetricEventPublisher delegate;
    private final SdkOptions properties;

    public SanitizingMetricEventPublisher(MetricEventPublisher delegate, SdkOptions properties) {
        this.delegate = delegate;
        this.properties = properties;
    }

    @Override
    public void publish(Object event) {
        if (event instanceof QueryMetricEvent queryEvent) {
            delegate.publish(sanitize(queryEvent));
            return;
        }
        if (event instanceof TxMetricEvent txEvent) {
            delegate.publish(sanitize(txEvent));
            return;
        }
        delegate.publish(event);
    }

    private QueryMetricEvent sanitize(QueryMetricEvent event) {
        List<Object> captured = captureAllowed(event);
        return new QueryMetricEvent(
                event.eventId(),
                event.transactionId(),
                event.appName(),
                event.environment(),
                event.instanceId(),
                event.datasourceName(),
                event.dbType(),
                // 리터럴이 SQL 문자열에 직접 박힌 경우도 있으므로 Collector에는 정규화 SQL만 보낸다.
                event.normalizedSql(),
                event.normalizedSql(),
                captured,
                event.paramCount(),
                event.durationUs(),
                event.executedAt(),
                event.threadName(),
                event.succeeded(),
                event.sqlState(),
                truncate(event.traceId(), 64),
                truncate(event.requestId(), 100),
                truncate(event.httpMethod(), 10),
                truncate(event.httpPath(), 500),
                truncate(event.handlerName(), 500));
    }

    private TxMetricEvent sanitize(TxMetricEvent event) {
        SdkOptions.Transaction transaction = properties.getTransaction();
        String failureMessage = null;
        if (transaction.isFailureMessageEnabled()) {
            failureMessage = truncate(event.failureMessage(), transaction.getFailureMessageMaxLength());
        }
        return new TxMetricEvent(
                event.transactionId(),
                event.appName(),
                event.environment(),
                event.instanceId(),
                truncate(event.transactionName(), 500),
                event.durationUs(),
                event.status(),
                event.completedAt(),
                event.threadName(),
                truncate(event.failureType(), 200),
                failureMessage,
                truncate(event.traceId(), 64),
                truncate(event.requestId(), 100),
                truncate(event.httpMethod(), 10),
                truncate(event.httpPath(), 500),
                truncate(event.handlerName(), 500));
    }

    private List<Object> captureAllowed(QueryMetricEvent event) {
        SdkOptions.Params params = properties.getParams();
        if (!params.isEnabled() || event.params().isEmpty()) {
            return List.of();
        }

        String fingerprint = QueryFingerprint.sha256(event.dbType(), event.normalizedSql());
        Set<Integer> allowed = params.getRules().stream()
                .filter(rule -> fingerprint.equalsIgnoreCase(rule.getFingerprint()))
                .flatMap(rule -> rule.getAllowedIndexes().stream())
                .filter(index -> index != null && index > 0)
                .collect(Collectors.toSet());

        if (allowed.isEmpty()) {
            return List.of();
        }

        List<Object> result = new ArrayList<>();
        for (int zeroBased = 0; zeroBased < event.params().size(); zeroBased++) {
            int jdbcIndex = zeroBased + 1;
            if (!allowed.contains(jdbcIndex)) {
                continue;
            }
            Object value = event.params().get(zeroBased);
            Map<String, Object> captured = new LinkedHashMap<>();
            captured.put("index", jdbcIndex);
            captured.put("type", value == null ? "NULL" : value.getClass().getSimpleName());
            captured.put("mode", "PLAIN");
            captured.put("value", safeValue(value, params.getMaxTextLength()));
            result.add(captured);
        }
        return List.copyOf(result);
    }

    private Object safeValue(Object value, int maxLength) {
        if (value == null || value instanceof Number || value instanceof Boolean) {
            return value;
        }
        String text = String.valueOf(value);
        int limit = Math.max(0, maxLength);
        return text.length() <= limit ? text : text.substring(0, limit);
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String singleLine = value.replace('\r', ' ').replace('\n', ' ');
        int limit = Math.max(0, maxLength);
        return singleLine.length() <= limit ? singleLine : singleLine.substring(0, limit);
    }

    @Override
    public void close() throws Exception {
        if (delegate instanceof AutoCloseable closeable) {
            closeable.close();
        }
    }
}

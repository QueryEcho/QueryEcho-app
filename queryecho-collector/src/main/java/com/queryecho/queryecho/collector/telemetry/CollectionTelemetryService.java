package com.queryecho.queryecho.collector.telemetry;

import com.queryecho.queryecho.sdk.dto.QueryMetricEvent;
import com.queryecho.queryecho.sdk.dto.SdkHealthReport;
import com.queryecho.queryecho.sdk.dto.TxMetricEvent;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Service;

/** SDK에서 DB 저장까지 각 단계의 성공·실패·유실 수를 인스턴스별로 추적한다. */
@Service
public class CollectionTelemetryService {

    private static final Duration STALE_AFTER = Duration.ofSeconds(30);

    private final ConcurrentMap<InstanceKey, CollectorCounters> collectorCounters = new ConcurrentHashMap<>();
    private final ConcurrentMap<InstanceKey, SdkHealthReport> sdkReports = new ConcurrentHashMap<>();

    public void recordAccepted(Object event) {
        countersFor(event).accepted.incrementAndGet();
    }

    public void recordRejected(Object event) {
        countersFor(event).rejected.incrementAndGet();
    }

    public void recordPersisted(Object event, boolean inserted) {
        CollectorCounters counters = countersFor(event);
        if (inserted) {
            counters.persisted.incrementAndGet();
        } else {
            counters.duplicate.incrementAndGet();
        }
    }

    public void recordPersistenceFailure(Object event) {
        countersFor(event).persistenceFailed.incrementAndGet();
    }

    public void recordSdkHealth(SdkHealthReport report) {
        sdkReports.put(key(report.appName(), report.environment(), report.instanceId()), report);
    }

    public CollectionHealthSnapshot snapshot(String environment, String appName) {
        Set<InstanceKey> keys = new HashSet<>(collectorCounters.keySet());
        keys.addAll(sdkReports.keySet());

        List<InstanceHealth> instances = new ArrayList<>();
        Instant now = Instant.now();
        for (InstanceKey key : keys) {
            if (!matches(environment, key.environment()) || !matches(appName, key.appName())) {
                continue;
            }
            CollectorCounters counters = collectorCounters.getOrDefault(key, new CollectorCounters());
            SdkHealthReport report = sdkReports.get(key);
            long dropped = report == null ? 0 : report.droppedTotal();
            String status;
            if (report == null) {
                status = "NO_SDK_REPORT";
            } else if (Duration.between(report.reportedAt(), now).compareTo(STALE_AFTER) > 0) {
                status = "DISCONNECTED";
            } else if (dropped > 0 || counters.rejected.get() > 0 || counters.persistenceFailed.get() > 0) {
                status = "DEGRADED";
            } else {
                status = "HEALTHY";
            }

            instances.add(new InstanceHealth(
                    key.appName(), key.environment(), key.instanceId(), status,
                    report == null ? null : report.reportedAt(),
                    report == null ? 0 : report.capturedTotal(),
                    report == null ? 0 : report.enqueuedTotal(),
                    report == null ? 0 : report.sentTotal(),
                    dropped,
                    report == null ? 0 : report.droppedBufferTotal(),
                    report == null ? 0 : report.droppedSerializationTotal(),
                    report == null ? 0 : report.droppedTransportTotal(),
                    report == null ? 0 : report.queueSize(),
                    report == null ? 0 : report.queueCapacity(),
                    counters.accepted.get(), counters.persisted.get(), counters.duplicate.get(),
                    counters.rejected.get(), counters.persistenceFailed.get()));
        }
        instances.sort(Comparator.comparing(InstanceHealth::appName)
                .thenComparing(InstanceHealth::instanceId));

        return new CollectionHealthSnapshot(
                now,
                instances.stream().mapToLong(InstanceHealth::capturedTotal).sum(),
                instances.stream().mapToLong(InstanceHealth::sentTotal).sum(),
                instances.stream().mapToLong(InstanceHealth::droppedTotal).sum(),
                instances.stream().mapToLong(InstanceHealth::collectorAcceptedTotal).sum(),
                instances.stream().mapToLong(InstanceHealth::collectorPersistedTotal).sum(),
                instances.stream().mapToLong(InstanceHealth::collectorRejectedTotal).sum(),
                instances.stream().mapToLong(InstanceHealth::collectorPersistenceFailedTotal).sum(),
                List.copyOf(instances));
    }

    private CollectorCounters countersFor(Object event) {
        InstanceKey key;
        if (event instanceof QueryMetricEvent query) {
            key = key(query.appName(), query.environment(), query.instanceId());
        } else if (event instanceof TxMetricEvent transaction) {
            key = key(transaction.appName(), transaction.environment(), transaction.instanceId());
        } else {
            key = key("unknown-app", "default", "unknown-instance");
        }
        return collectorCounters.computeIfAbsent(key, ignored -> new CollectorCounters());
    }

    private static InstanceKey key(String appName, String environment, String instanceId) {
        return new InstanceKey(safe(appName, "unknown-app"), safe(environment, "default"),
                safe(instanceId, "unknown-instance"));
    }

    private static boolean matches(String requested, String actual) {
        return requested == null || requested.isBlank() || requested.equals(actual);
    }

    private static String safe(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private record InstanceKey(String appName, String environment, String instanceId) {
    }

    private static final class CollectorCounters {
        private final AtomicLong accepted = new AtomicLong();
        private final AtomicLong persisted = new AtomicLong();
        private final AtomicLong duplicate = new AtomicLong();
        private final AtomicLong rejected = new AtomicLong();
        private final AtomicLong persistenceFailed = new AtomicLong();
    }

    public record CollectionHealthSnapshot(
            Instant observedAt,
            long sdkCapturedTotal,
            long sdkSentTotal,
            long sdkDroppedTotal,
            long collectorAcceptedTotal,
            long collectorPersistedTotal,
            long collectorRejectedTotal,
            long collectorPersistenceFailedTotal,
            List<InstanceHealth> instances
    ) {
    }

    public record InstanceHealth(
            String appName,
            String environment,
            String instanceId,
            String status,
            Instant lastReportedAt,
            long capturedTotal,
            long enqueuedTotal,
            long sentTotal,
            long droppedTotal,
            long droppedBufferTotal,
            long droppedSerializationTotal,
            long droppedTransportTotal,
            int queueSize,
            int queueCapacity,
            long collectorAcceptedTotal,
            long collectorPersistedTotal,
            long collectorDuplicateTotal,
            long collectorRejectedTotal,
            long collectorPersistenceFailedTotal
    ) {
    }
}

package com.queryecho.queryecho.collector.telemetry;

import com.queryecho.core.dto.QueryMetricEvent;
import com.queryecho.core.dto.SdkHealthReport;
import com.queryecho.core.dto.TxMetricEvent;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;
import java.util.concurrent.atomic.LongAdder;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

/** SDK에서 DB 저장까지 각 단계의 성공·실패·유실 수를 인스턴스별로 추적한다. */
@Service
public class CollectionTelemetryService {

    private static final Duration STALE_AFTER = Duration.ofSeconds(30);

    private final ConcurrentMap<InstanceKey, CollectorCounters> collectorCounters = new ConcurrentHashMap<>();
    private final ConcurrentMap<InstanceKey, SdkHealthReport> sdkReports = new ConcurrentHashMap<>();
    private final LatencyRecorder queueWaitLatency = new LatencyRecorder();
    private final LatencyRecorder queryPersistenceLatency = new LatencyRecorder();
    private final LatencyRecorder transactionPersistenceLatency = new LatencyRecorder();
    private volatile ThreadPoolTaskExecutor executor;

    public void bindExecutor(ThreadPoolTaskExecutor executor) {
        this.executor = executor;
    }

    public Runnable decorateAsyncTask(Runnable task) {
        long submittedAt = System.nanoTime();
        return () -> {
            queueWaitLatency.record(System.nanoTime() - submittedAt);
            task.run();
        };
    }

    public void recordReceived(Object event) {
        countersFor(event).received.incrementAndGet();
    }

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

    public void recordQueryPersistenceDuration(long durationNanos) {
        queryPersistenceLatency.record(durationNanos);
    }

    public void recordTransactionPersistenceDuration(long durationNanos) {
        transactionPersistenceLatency.record(durationNanos);
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
                    counters.received.get(), counters.accepted.get(), counters.persisted.get(),
                    counters.duplicate.get(), counters.rejected.get(), counters.persistenceFailed.get(),
                    inFlight(counters)));
        }
        instances.sort(Comparator.comparing(InstanceHealth::appName)
                .thenComparing(InstanceHealth::instanceId));

        return new CollectionHealthSnapshot(
                now,
                instances.stream().mapToLong(InstanceHealth::capturedTotal).sum(),
                instances.stream().mapToLong(InstanceHealth::sentTotal).sum(),
                instances.stream().mapToLong(InstanceHealth::droppedTotal).sum(),
                instances.stream().mapToLong(InstanceHealth::collectorReceivedTotal).sum(),
                instances.stream().mapToLong(InstanceHealth::collectorAcceptedTotal).sum(),
                instances.stream().mapToLong(InstanceHealth::collectorPersistedTotal).sum(),
                instances.stream().mapToLong(InstanceHealth::collectorRejectedTotal).sum(),
                instances.stream().mapToLong(InstanceHealth::collectorPersistenceFailedTotal).sum(),
                instances.stream().mapToLong(InstanceHealth::collectorInFlight).sum(),
                executorSnapshot(),
                queueWaitLatency.snapshot(),
                new PersistenceLatencySnapshot(
                        queryPersistenceLatency.snapshot(),
                        transactionPersistenceLatency.snapshot()),
                List.copyOf(instances));
    }

    private ExecutorSnapshot executorSnapshot() {
        ThreadPoolTaskExecutor current = executor;
        if (current == null || current.getThreadPoolExecutor() == null) {
            return new ExecutorSnapshot(0, 0, 0.0, 0, 0, 0, 0);
        }
        var threadPool = current.getThreadPoolExecutor();
        int queueSize = threadPool.getQueue().size();
        int remaining = threadPool.getQueue().remainingCapacity();
        int capacity = queueSize + remaining;
        double utilization = capacity == 0 ? 0.0 : queueSize * 100.0 / capacity;
        return new ExecutorSnapshot(
                queueSize,
                capacity,
                round(utilization),
                threadPool.getActiveCount(),
                threadPool.getPoolSize(),
                threadPool.getMaximumPoolSize(),
                threadPool.getCompletedTaskCount());
    }

    private static long inFlight(CollectorCounters counters) {
        return Math.max(0, counters.accepted.get()
                - counters.persisted.get()
                - counters.duplicate.get()
                - counters.persistenceFailed.get());
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
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
        private final AtomicLong received = new AtomicLong();
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
            long collectorReceivedTotal,
            long collectorAcceptedTotal,
            long collectorPersistedTotal,
            long collectorRejectedTotal,
            long collectorPersistenceFailedTotal,
            long collectorInFlight,
            ExecutorSnapshot executor,
            LatencySnapshot queueWait,
            PersistenceLatencySnapshot persistence,
            List<InstanceHealth> instances
    ) {
    }

    public record ExecutorSnapshot(
            int queueSize,
            int queueCapacity,
            double queueUtilizationPercent,
            int activeWorkers,
            int currentWorkers,
            int maxWorkers,
            long completedTasks
    ) {
    }

    public record LatencySnapshot(
            long count,
            double averageMs,
            double p50Ms,
            double p95Ms,
            double p99Ms,
            double maxMs
    ) {
    }

    public record PersistenceLatencySnapshot(
            LatencySnapshot query,
            LatencySnapshot transaction
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
            long collectorReceivedTotal,
            long collectorAcceptedTotal,
            long collectorPersistedTotal,
            long collectorDuplicateTotal,
            long collectorRejectedTotal,
            long collectorPersistenceFailedTotal,
            long collectorInFlight
    ) {
    }


    private static final class LatencyRecorder {

        private static final long[] UPPER_BOUNDS_NANOS = {
                TimeUnit.MILLISECONDS.toNanos(1),
                TimeUnit.MILLISECONDS.toNanos(2),
                TimeUnit.MILLISECONDS.toNanos(5),
                TimeUnit.MILLISECONDS.toNanos(10),
                TimeUnit.MILLISECONDS.toNanos(25),
                TimeUnit.MILLISECONDS.toNanos(50),
                TimeUnit.MILLISECONDS.toNanos(100),
                TimeUnit.MILLISECONDS.toNanos(250),
                TimeUnit.MILLISECONDS.toNanos(500),
                TimeUnit.SECONDS.toNanos(1),
                TimeUnit.MILLISECONDS.toNanos(2500),
                TimeUnit.SECONDS.toNanos(5),
                TimeUnit.SECONDS.toNanos(10),
                Long.MAX_VALUE
        };

        private final LongAdder count = new LongAdder();
        private final LongAdder totalNanos = new LongAdder();
        private final AtomicLong maxNanos = new AtomicLong();
        private final AtomicLongArray buckets = new AtomicLongArray(UPPER_BOUNDS_NANOS.length);

        void record(long durationNanos) {
            long safeDuration = Math.max(0, durationNanos);
            count.increment();
            totalNanos.add(safeDuration);
            maxNanos.accumulateAndGet(safeDuration, Math::max);
            for (int index = 0; index < UPPER_BOUNDS_NANOS.length; index++) {
                if (safeDuration <= UPPER_BOUNDS_NANOS[index]) {
                    buckets.incrementAndGet(index);
                    return;
                }
            }
        }

        LatencySnapshot snapshot() {
            long sampleCount = count.sum();
            if (sampleCount == 0) {
                return new LatencySnapshot(0, 0.0, 0.0, 0.0, 0.0, 0.0);
            }
            return new LatencySnapshot(
                    sampleCount,
                    round(nanosToMillis(totalNanos.sum()) / sampleCount),
                    percentile(sampleCount, 0.50),
                    percentile(sampleCount, 0.95),
                    percentile(sampleCount, 0.99),
                    round(nanosToMillis(maxNanos.get())));
        }

        private double percentile(long sampleCount, double percentile) {
            long target = Math.max(1, (long) Math.ceil(sampleCount * percentile));
            long cumulative = 0;
            for (int index = 0; index < UPPER_BOUNDS_NANOS.length; index++) {
                cumulative += buckets.get(index);
                if (cumulative >= target) {
                    long upperBound = UPPER_BOUNDS_NANOS[index];
                    return upperBound == Long.MAX_VALUE
                            ? round(nanosToMillis(maxNanos.get()))
                            : round(nanosToMillis(upperBound));
                }
            }
            return round(nanosToMillis(maxNanos.get()));
        }

        private static double nanosToMillis(long nanos) {
            return nanos / 1_000_000.0;
        }
    }
}

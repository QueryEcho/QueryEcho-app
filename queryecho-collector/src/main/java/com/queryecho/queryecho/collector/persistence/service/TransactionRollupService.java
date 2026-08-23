package com.queryecho.queryecho.collector.persistence.service;

import com.queryecho.queryecho.collector.config.QueryEchoCollectorProperties;
import com.queryecho.queryecho.collector.persistence.entity.TransactionExecutionEntity;
import com.queryecho.queryecho.collector.persistence.entity.TransactionRollup1mEntity;
import com.queryecho.queryecho.collector.persistence.entity.TransactionRollup1mId;
import com.queryecho.queryecho.collector.persistence.repository.TransactionExecutionJpaRepository;
import com.queryecho.queryecho.collector.persistence.repository.TransactionRollup1mJpaRepository;
import com.queryecho.queryecho.sdk.dto.TxStatus;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 완료된 최근 트랜잭션을 환경·패턴별 1분 통계로 재집계한다. */
@Service
public class TransactionRollupService {

    public static final short HISTOGRAM_VERSION = 1;

    private final TransactionExecutionJpaRepository executionRepository;
    private final TransactionRollup1mJpaRepository rollupRepository;
    private final QueryEchoCollectorProperties properties;

    public TransactionRollupService(TransactionExecutionJpaRepository executionRepository,
                                    TransactionRollup1mJpaRepository rollupRepository,
                                    QueryEchoCollectorProperties properties) {
        this.executionRepository = executionRepository;
        this.rollupRepository = rollupRepository;
        this.properties = properties;
    }

    @Scheduled(
            fixedDelayString = "${queryecho.collector.rollup.interval-ms:60000}",
            initialDelayString = "${queryecho.collector.rollup.interval-ms:60000}")
    public void rebuildRecentCompletedMinutes() {
        Instant toExclusive = Instant.now().truncatedTo(ChronoUnit.MINUTES);
        Instant fromInclusive = toExclusive.minus(
                properties.getRollup().getLookbackMinutes(), ChronoUnit.MINUTES);
        rebuild(fromInclusive, toExclusive);
    }

    @Transactional
    public int rebuild(Instant fromInclusive, Instant toExclusive) {
        List<TransactionExecutionEntity> executions = executionRepository
                .findByCompletedAtGreaterThanEqualAndCompletedAtLessThan(fromInclusive, toExclusive);

        Map<RollupKey, Accumulator> grouped = new LinkedHashMap<>();
        for (TransactionExecutionEntity execution : executions) {
            RollupKey key = new RollupKey(
                    execution.getCompletedAt().truncatedTo(ChronoUnit.MINUTES),
                    execution.getEnvironment(),
                    execution.getPattern().getId());
            grouped.computeIfAbsent(key, ignored -> new Accumulator()).add(execution);
        }

        Instant updatedAt = Instant.now();
        List<TransactionRollup1mEntity> rollups = new ArrayList<>(grouped.size());
        grouped.forEach((key, value) -> rollups.add(value.toEntity(key, updatedAt)));
        rollupRepository.saveAll(rollups);
        return rollups.size();
    }

    private record RollupKey(Instant bucketStart, String environment, Long patternId) {
    }

    private static final class Accumulator {
        private long count;
        private long commits;
        private long rollbacks;
        private long unknowns;
        private long totalUs;
        private long minUs = Long.MAX_VALUE;
        private long maxUs;
        private final Map<String, Long> buckets = emptyBuckets();

        void add(TransactionExecutionEntity execution) {
            long duration = execution.getDurationUs();
            count++;
            if (execution.getStatus() == TxStatus.COMMIT) {
                commits++;
            } else if (execution.getStatus() == TxStatus.ROLLBACK) {
                rollbacks++;
            } else {
                unknowns++;
            }
            totalUs += duration;
            minUs = Math.min(minUs, duration);
            maxUs = Math.max(maxUs, duration);
            buckets.compute(bucketName(duration), (ignored, current) -> current + 1);
        }

        TransactionRollup1mEntity toEntity(RollupKey key, Instant updatedAt) {
            return new TransactionRollup1mEntity(
                    new TransactionRollup1mId(key.bucketStart(), key.environment(), key.patternId()),
                    count, commits, rollbacks, unknowns, totalUs, minUs, maxUs,
                    Map.copyOf(buckets), HISTOGRAM_VERSION, updatedAt);
        }

        private static Map<String, Long> emptyBuckets() {
            Map<String, Long> result = new LinkedHashMap<>();
            result.put("us_0_1000", 0L);
            result.put("us_1000_5000", 0L);
            result.put("us_5000_10000", 0L);
            result.put("us_10000_50000", 0L);
            result.put("us_50000_100000", 0L);
            result.put("us_100000_500000", 0L);
            result.put("us_gte_500000", 0L);
            return result;
        }

        private static String bucketName(long durationUs) {
            if (durationUs < 1_000) return "us_0_1000";
            if (durationUs < 5_000) return "us_1000_5000";
            if (durationUs < 10_000) return "us_5000_10000";
            if (durationUs < 50_000) return "us_10000_50000";
            if (durationUs < 100_000) return "us_50000_100000";
            if (durationUs < 500_000) return "us_100000_500000";
            return "us_gte_500000";
        }
    }
}

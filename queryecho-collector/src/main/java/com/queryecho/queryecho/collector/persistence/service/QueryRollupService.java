package com.queryecho.queryecho.collector.persistence.service;

import com.queryecho.queryecho.collector.config.QueryEchoCollectorProperties;
import com.queryecho.queryecho.collector.persistence.entity.QueryExecutionEntity;
import com.queryecho.queryecho.collector.persistence.entity.QueryRollup1mEntity;
import com.queryecho.queryecho.collector.persistence.entity.QueryRollup1mId;
import com.queryecho.queryecho.collector.persistence.repository.QueryExecutionJpaRepository;
import com.queryecho.queryecho.collector.persistence.repository.QueryRollup1mJpaRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 완료된 최근 1분 구간들을 반복 재집계하여 늦게 도착한 이벤트를 보정한다. */
@Service
public class QueryRollupService {

    public static final short HISTOGRAM_VERSION = 1;

    private final QueryExecutionJpaRepository executionRepository;
    private final QueryRollup1mJpaRepository rollupRepository;
    private final QueryEchoCollectorProperties properties;

    public QueryRollupService(QueryExecutionJpaRepository executionRepository,
                              QueryRollup1mJpaRepository rollupRepository,
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
        List<QueryExecutionEntity> executions = executionRepository
                .findByExecutedAtGreaterThanEqualAndExecutedAtLessThan(fromInclusive, toExclusive);

        Map<RollupKey, Accumulator> grouped = new LinkedHashMap<>();
        for (QueryExecutionEntity execution : executions) {
            RollupKey key = new RollupKey(
                    execution.getExecutedAt().truncatedTo(ChronoUnit.MINUTES),
                    execution.getEnvironment(),
                    execution.getAppName(),
                    execution.getInstanceId(),
                    execution.getDatasourceName(),
                    execution.getPattern().getId());
            grouped.computeIfAbsent(key, ignored -> new Accumulator()).add(execution);
        }

        Instant updatedAt = Instant.now();
        List<QueryRollup1mEntity> rollups = new ArrayList<>(grouped.size());
        grouped.forEach((key, value) -> rollups.add(value.toEntity(key, updatedAt)));
        rollupRepository.saveAll(rollups);
        return rollups.size();
    }

    private record RollupKey(
            Instant bucketStart,
            String environment,
            String appName,
            String instanceId,
            String datasourceName,
            Long patternId
    ) {
    }

    private static final class Accumulator {
        private long count;
        private long errors;
        private long totalUs;
        private long minUs = Long.MAX_VALUE;
        private long maxUs;
        private final Map<String, Long> buckets = emptyBuckets();

        void add(QueryExecutionEntity execution) {
            long duration = execution.getDurationUs();
            count++;
            if (!execution.isSucceeded()) {
                errors++;
            }
            totalUs += duration;
            minUs = Math.min(minUs, duration);
            maxUs = Math.max(maxUs, duration);
            buckets.compute(bucketName(duration), (ignored, current) -> current + 1);
        }

        QueryRollup1mEntity toEntity(RollupKey key, Instant updatedAt) {
            QueryRollup1mId id = new QueryRollup1mId(
                    key.bucketStart(), key.environment(), key.appName(), key.instanceId(),
                    key.datasourceName(), key.patternId());
            return new QueryRollup1mEntity(
                    id, count, errors, totalUs, minUs, maxUs,
                    Map.copyOf(buckets), HISTOGRAM_VERSION, updatedAt);
        }

        private static Map<String, Long> emptyBuckets() {
            Map<String, Long> result = new LinkedHashMap<>();
            result.put("us_0_1000", 0L);
            result.put("us_1000_5000", 0L);
            result.put("us_5000_10000", 0L);
            result.put("us_10000_50000", 0L);
            result.put("us_50000_100000", 0L);
            result.put("us_100000_250000", 0L);
            result.put("us_250000_500000", 0L);
            result.put("us_500000_1000000", 0L);
            result.put("us_1000000_2500000", 0L);
            result.put("us_2500000_5000000", 0L);
            result.put("us_gte_5000000", 0L);
            return result;
        }

        private static String bucketName(long durationUs) {
            if (durationUs < 1_000) return "us_0_1000";
            if (durationUs < 5_000) return "us_1000_5000";
            if (durationUs < 10_000) return "us_5000_10000";
            if (durationUs < 50_000) return "us_10000_50000";
            if (durationUs < 100_000) return "us_50000_100000";
            if (durationUs < 250_000) return "us_100000_250000";
            if (durationUs < 500_000) return "us_250000_500000";
            if (durationUs < 1_000_000) return "us_500000_1000000";
            if (durationUs < 2_500_000) return "us_1000000_2500000";
            if (durationUs < 5_000_000) return "us_2500000_5000000";
            return "us_gte_5000000";
        }
    }
}

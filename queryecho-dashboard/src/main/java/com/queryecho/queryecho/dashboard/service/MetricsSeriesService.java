package com.queryecho.queryecho.dashboard.service;

import com.queryecho.queryecho.collector.persistence.repository.QueryRollup1mJpaRepository;
import com.queryecho.queryecho.collector.persistence.repository.TransactionRollup1mJpaRepository;
import com.queryecho.queryecho.collector.repository.QueryMetricFilter;
import com.queryecho.queryecho.collector.repository.QueryMetricRecord;
import com.queryecho.queryecho.collector.repository.QueryMetricRepository;
import com.queryecho.queryecho.collector.repository.TxMetricFilter;
import com.queryecho.queryecho.collector.repository.TxMetricRecord;
import com.queryecho.queryecho.collector.repository.TxMetricRepository;
import com.queryecho.queryecho.dashboard.dto.QuerySeriesResponse;
import com.queryecho.queryecho.dashboard.dto.TransactionSeriesResponse;
import com.queryecho.queryecho.sdk.dto.TxStatus;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.stereotype.Service;

/** 완료된 분은 롤업, 양 끝의 미완료 분은 원본 실행을 사용해 시계열을 만든다. */
@Service
public class MetricsSeriesService {

    private final QueryRollup1mJpaRepository queryRollups;
    private final TransactionRollup1mJpaRepository transactionRollups;
    private final QueryMetricRepository queries;
    private final TxMetricRepository transactions;

    public MetricsSeriesService(QueryRollup1mJpaRepository queryRollups,
                                TransactionRollup1mJpaRepository transactionRollups,
                                QueryMetricRepository queries,
                                TxMetricRepository transactions) {
        this.queryRollups = queryRollups;
        this.transactionRollups = transactionRollups;
        this.queries = queries;
        this.transactions = transactions;
    }

    public QuerySeriesResponse querySeries(QueryMetricFilter filter) {
        Map<Instant, QueryBucket> buckets = new TreeMap<>();
        // 성공/실패별 롤업은 아직 분리 저장하지 않으므로 이 필터가 있으면 원본만 조회한다.
        if (filter.succeeded() != null) {
            addRawQueries(buckets, filter, filter.from(), filter.to());
            return queryResponse(buckets);
        }
        Window window = Window.of(filter.from(), filter.to());
        if (window.hasFullMinutes()) {
            queryRollups.findSeries(window.fullFrom(), window.fullTo(), blankToNull(filter.environment()),
                            blankToNull(filter.appName()), blankToNull(filter.instanceId()),
                            blankToNull(filter.datasourceName()))
                    .forEach(row -> buckets.put(row.getBucketStart(), new QueryBucket(
                            row.getExecutionCount(), row.getErrorCount(), row.getTotalDurationUs(),
                            row.getMinDurationUs(), row.getMaxDurationUs())));
            addRawQueries(buckets, filter, filter.from(), window.fullFrom());
            addRawQueries(buckets, filter, window.fullTo(), filter.to());
        } else {
            addRawQueries(buckets, filter, filter.from(), filter.to());
        }
        return queryResponse(buckets);
    }

    public TransactionSeriesResponse transactionSeries(TxMetricFilter filter) {
        Map<Instant, TxBucket> buckets = new TreeMap<>();
        // 현재 트랜잭션 롤업에는 instance_id/status 차원이 없어서 해당 필터는 원본으로 계산한다.
        if (blankToNull(filter.instanceId()) != null || filter.status() != null) {
            addRawTransactions(buckets, filter, filter.from(), filter.to());
            return transactionResponse(buckets);
        }
        Window window = Window.of(filter.from(), filter.to());
        if (window.hasFullMinutes()) {
            transactionRollups.findSeries(window.fullFrom(), window.fullTo(),
                            blankToNull(filter.environment()), blankToNull(filter.appName()))
                    .forEach(row -> buckets.put(row.getBucketStart(), new TxBucket(
                            row.getTransactionCount(), row.getCommitCount(), row.getRollbackCount(),
                            row.getUnknownCount(), row.getTotalDurationUs(), row.getMinDurationUs(),
                            row.getMaxDurationUs())));
            addRawTransactions(buckets, filter, filter.from(), window.fullFrom());
            addRawTransactions(buckets, filter, window.fullTo(), filter.to());
        } else {
            addRawTransactions(buckets, filter, filter.from(), filter.to());
        }
        return transactionResponse(buckets);
    }

    private QuerySeriesResponse queryResponse(Map<Instant, QueryBucket> buckets) {
        return new QuerySeriesResponse(60, buckets.entrySet().stream()
                .map(entry -> entry.getValue().response(entry.getKey())).toList());
    }

    private TransactionSeriesResponse transactionResponse(Map<Instant, TxBucket> buckets) {
        return new TransactionSeriesResponse(60, buckets.entrySet().stream()
                .map(entry -> entry.getValue().response(entry.getKey())).toList());
    }

    private void addRawQueries(Map<Instant, QueryBucket> buckets, QueryMetricFilter base,
                               Instant from, Instant to) {
        if (!from.isBefore(to)) return;
        QueryMetricFilter range = new QueryMetricFilter(from, to, base.environment(), base.appName(),
                base.instanceId(), base.datasourceName(), base.succeeded());
        for (QueryMetricRecord query : queries.findRange(range)) {
            buckets.computeIfAbsent(minute(query.executedAt()), ignored -> new QueryBucket())
                    .add(query);
        }
    }

    private void addRawTransactions(Map<Instant, TxBucket> buckets, TxMetricFilter base,
                                    Instant from, Instant to) {
        if (!from.isBefore(to)) return;
        TxMetricFilter range = new TxMetricFilter(from, to, base.environment(), base.appName(),
                base.instanceId(), base.status());
        for (TxMetricRecord transaction : transactions.findRange(range)) {
            buckets.computeIfAbsent(minute(transaction.executedAt()), ignored -> new TxBucket())
                    .add(transaction);
        }
    }

    private static Instant minute(Instant instant) {
        return instant.truncatedTo(ChronoUnit.MINUTES);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private record Window(Instant fullFrom, Instant fullTo) {
        static Window of(Instant from, Instant to) {
            Instant floorFrom = minute(from);
            Instant firstFull = floorFrom.equals(from) ? from : floorFrom.plus(1, ChronoUnit.MINUTES);
            Instant lastFullExclusive = minute(to);
            return new Window(firstFull, lastFullExclusive);
        }

        boolean hasFullMinutes() {
            return fullFrom.isBefore(fullTo);
        }
    }

    private static final class QueryBucket {
        private long count;
        private long errors;
        private long total;
        private long min = Long.MAX_VALUE;
        private long max;

        QueryBucket() {
        }

        QueryBucket(long count, long errors, long total, long min, long max) {
            this.count = count;
            this.errors = errors;
            this.total = total;
            this.min = min;
            this.max = max;
        }

        void add(QueryMetricRecord query) {
            count++;
            if (!query.succeeded()) errors++;
            total += query.durationUs();
            min = Math.min(min, query.durationUs());
            max = Math.max(max, query.durationUs());
        }

        QuerySeriesResponse.Bucket response(Instant start) {
            return new QuerySeriesResponse.Bucket(start, count, errors, total,
                    count == 0 ? 0 : total / count, count == 0 ? 0 : min, max);
        }
    }

    private static final class TxBucket {
        private long count;
        private long commits;
        private long rollbacks;
        private long unknowns;
        private long total;
        private long min = Long.MAX_VALUE;
        private long max;

        TxBucket() {
        }

        TxBucket(long count, long commits, long rollbacks, long unknowns,
                 long total, long min, long max) {
            this.count = count;
            this.commits = commits;
            this.rollbacks = rollbacks;
            this.unknowns = unknowns;
            this.total = total;
            this.min = min;
            this.max = max;
        }

        void add(TxMetricRecord transaction) {
            count++;
            if (transaction.status() == TxStatus.COMMIT) commits++;
            else if (transaction.status() == TxStatus.ROLLBACK) rollbacks++;
            else unknowns++;
            total += transaction.durationUs();
            min = Math.min(min, transaction.durationUs());
            max = Math.max(max, transaction.durationUs());
        }

        TransactionSeriesResponse.Bucket response(Instant start) {
            return new TransactionSeriesResponse.Bucket(start, count, commits, rollbacks, unknowns,
                    total, count == 0 ? 0 : total / count, count == 0 ? 0 : min, max);
        }
    }
}

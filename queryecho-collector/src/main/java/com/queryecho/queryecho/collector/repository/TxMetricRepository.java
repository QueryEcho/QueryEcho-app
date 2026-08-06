package com.queryecho.queryecho.collector.repository;

import com.queryecho.queryecho.sdk.dto.TxStatus;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import org.springframework.stereotype.Repository;

/**
 * 트랜잭션 지표 저장소. 설계 근거는 {@link QueryMetricRepository}와 동일
 * (인메모리 링버퍼, observer effect 회피).
 */
@Repository
public class TxMetricRepository {

    private static final int MAX_SIZE = 2000;

    private final Deque<TxMetricRecord> records = new ConcurrentLinkedDeque<>();

    public void save(TxMetricRecord record) {
        records.addLast(record);
        if (records.size() > MAX_SIZE) {
            records.pollFirst();
        }
    }

    public List<TxMetricRecord> findRecent(int limit) {
        List<TxMetricRecord> result = new ArrayList<>(Math.min(limit, records.size()));
        var it = records.descendingIterator();
        while (it.hasNext() && result.size() < limit) {
            result.add(it.next());
        }
        return result;
    }

    /**
     * 롤백율 계산용 - 현재 버퍼에 남아있는 범위 내에서의 커밋/롤백 집계.
     * 왜 별도 카운터 필드를 유지하지 않고 매번 순회해서 계산하는가?
     *  - 프로토타입 규모(최대 2000건)에서는 O(n) 순회 비용이 무시할 수준이고,
     *    누적 카운터를 따로 두면 "링버퍼에서 밀려난 오래된 레코드"의 카운트를 어떻게
     *    빼줄지에 대한 별도 로직이 필요해져 복잡도만 커진다. 데이터가 늘어나 실제로
     *    문제가 되면 그때 집계 전용 카운터로 옮기면 된다.
     */
    public Stats stats() {
        long commit = 0;
        long rollback = 0;
        long totalDurationMs = 0;
        for (TxMetricRecord record : records) {
            totalDurationMs += record.durationMs();
            if (record.status() == TxStatus.COMMIT) {
                commit++;
            } else {
                rollback++;
            }
        }
        long total = commit + rollback;
        double avgDurationMs = total == 0 ? 0 : (double) totalDurationMs / total;
        double rollbackRate = total == 0 ? 0 : (double) rollback / total;
        return new Stats(total, commit, rollback, rollbackRate, avgDurationMs);
    }

    public record Stats(long total, long commitCount, long rollbackCount, double rollbackRate, double avgDurationMs) {
    }
}

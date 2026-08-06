package com.queryecho.queryecho.collector.repository;

import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import org.springframework.stereotype.Repository;

/**
 * 쿼리 지표 저장소 - 프로토타입 단계에서는 순수 인메모리 링버퍼로 구현한다.
 *
 * 왜 진짜 DB(H2/MySQL)에 저장하지 않았는가?
 *  - 이 프로젝트가 관찰하는 대상 자체가 DB이기 때문에, 지표 저장소를 같은 DB에 두면
 *    "지표를 쓰는 INSERT 쿼리"가 다시 QueryEcho에 잡혀서 자기 자신을 계측하는
 *    순환 참조(observer effect)가 생긴다. 프로토타입에서는 이 문제를 피하려고
 *    완전히 별도의 인메모리 구조를 쓰고, README에 "다음 단계: 별도 저장소/전용 DataSource
 *    분리"로 남겨둔다.
 *  - 인메모리이므로 재시작하면 데이터가 사라지는 것은 프로토타입의 알려진 한계다.
 *
 * 왜 ConcurrentLinkedDeque + 수동 크기 제한인가 (Caffeine 캐시 등 대신)?
 *  - 외부 의존성을 추가하지 않고 JDK 표준 컬렉션만으로 "동시 쓰기 가능한 링버퍼"를
 *    충분히 구현할 수 있는 단순한 요구사항이기 때문. 최신 N개만 유지하면 되므로
 *    복잡한 캐시 정책(만료, 가중치 등)은 필요 없다.
 */
@Repository
public class QueryMetricRepository {

    private static final int MAX_SIZE = 2000;

    private final Deque<QueryMetricRecord> records = new ConcurrentLinkedDeque<>();

    public void save(QueryMetricRecord record) {
        records.addLast(record);
        // 매 저장마다 하나씩만 초과분을 걷기
        if (records.size() > MAX_SIZE) {
            records.pollFirst();
        }
    }

    public List<QueryMetricRecord> findRecent(int limit) {
        return latestFirst(records, limit, r -> true);
    }

    public List<QueryMetricRecord> findSlow(int limit) {
        return latestFirst(records, limit, QueryMetricRecord::slow);
    }

    private List<QueryMetricRecord> latestFirst(Deque<QueryMetricRecord> source, int limit,
                                                  java.util.function.Predicate<QueryMetricRecord> filter) {
        List<QueryMetricRecord> result = new ArrayList<>(Math.min(limit, source.size()));
        // 대시보드 최신것 부터 순회
        var it = source.descendingIterator();
        while (it.hasNext() && result.size() < limit) {
            QueryMetricRecord candidate = it.next();
            if (filter.test(candidate)) {
                result.add(candidate);
            }
        }
        return result;
    }
}

package com.queryecho.queryecho.collector.service;

import com.queryecho.queryecho.collector.config.QueryEchoCollectorProperties;
import com.queryecho.core.dto.QueryMetricEvent;
import java.time.Instant;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import org.springframework.stereotype.Service;

/**
 * N+1 / 반복 쿼리 탐지.
 *
 * 왜 "요청(request) 단위"가 아니라 "스레드 + 정규화된 SQL + 시간 윈도우" 조합으로 판단하는가?
 *  - 진짜 요청 경계(HTTP 요청 ID 등)를 추적하려면 서블릿 필터/인터셉터를 추가로 심어야 하는데,
 *    스프링 MVC 기본 설정에서는 요청 하나가 스레드 하나를 점유하는 thread-per-request 모델을
 *    쓰는 경우가 대부분이다(WebFlux/가상스레드 등 예외는 README 한계 항목에 기록).
 *    이 전제를 이용하면 "같은 스레드에서, 짧은 시간 안에, 모양이 같은 쿼리가 여러 번 나갔다"는
 *    조건만으로 별도 요청 트레이싱 인프라 없이도 N+1의 전형적인 패턴(반복문 안에서 개별 SELECT)을
 *    충분히 근사해서 잡아낼 수 있다. 정확도보다 "설정 없이 바로 동작"을 프로토타입 우선순위로 삼았다.
 *
 * 왜 정규화된 SQL(normalizedSql)로 그룹핑하는가?
 *  - N+1의 특징은 "SQL 모양은 같고 바인딩 값(id 등)만 다른 쿼리가 반복"되는 것이다.
 *    원본 SQL 문자열 그대로 그룹핑하면 리터럴이 그대로 박혀 있는 쿼리는 매번 다른 키로
 *    인식되어 패턴을 놓친다. SqlNormalizer로 리터럴을 지운 값을 키로 써야
 *    "WHERE id = 1"과 "WHERE id = 2"를 같은 패턴으로 묶을 수 있다.
 */
@Service
public class RepeatedQueryDetectionService {

    private final QueryEchoCollectorProperties properties;

    // 왜 Map<Key, Deque<Instant>>인가?
    //  - 슬라이딩 윈도우(최근 N ms)를 구현하는 가장 단순한 방법은 실행 시각들을 순서대로
    //    쌓아두고, 윈도우 밖으로 벗어난 오래된 시각을 앞에서부터 잘라내는 것이다.
    //    Deque는 양쪽 끝 추가/제거가 O(1)이라 이 용도에 적합하다.
    private final Map<Key, Deque<Instant>> recentExecutions = new ConcurrentHashMap<>();

    public RepeatedQueryDetectionService(QueryEchoCollectorProperties properties) {
        this.properties = properties;
    }

    /**
     * 이번 쿼리를 기록하고, 현재 윈도우 안에서 같은 패턴이 몇 번 실행됐는지 반환한다.
     *
     * 알려진 한계 (README에도 기록): recentExecutions 맵의 키는 스레드가 재사용되는 한
     * 계속 남아있어 장시간 운영 시 메모리가 서서히 늘어날 수 있다. 프로토타입에서는
     * 주기적 정리(cleanup) 스케줄러를 아직 두지 않았다.
     */
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

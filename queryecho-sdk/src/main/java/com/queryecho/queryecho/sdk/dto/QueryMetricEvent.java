package com.queryecho.queryecho.sdk.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * JDBC 인터셉터가 Statement/PreparedStatement 실행 1건마다 만들어내는 원시 신호.
 * 내부 이벤트 버스(ApplicationEventPublisher)에 발행되어 collector가 분석한다.
 *
 * 왜 record인가?
 *  - 이 객체는 "특정 시점에 관측된 사실"을 표현하는 불변 값 객체다. 생성 이후 어떤 필드도
 *    바뀔 이유가 없고(가변 상태를 두면 비동기 리스너 여러 개가 동시에 읽을 때 동기화 이슈가 생길 수 있다),
 *    record를 쓰면 equals/hashCode/toString/불변성을 별도 코드 없이 얻을 수 있어 테스트 작성도 쉬워진다.
 *
 * 왜 sdk.dto와 collector.repository에 비슷한 필드를 가진 타입이 따로 있는가?
 *  - 이 타입은 "SDK가 관측한 그대로"의 사실이고, collector.repository.QueryMetricRecord는
 *    거기에 slow/repeatCount 같은 "판단"을 더한 결과물이다. 관측(sdk)과 판단(collector)의
 *    책임을 타입 레벨에서도 분리해 둔 이유는 QueryMetricRecord 쪽 근거 주석에 자세히 적어두었다.
 */
public record QueryMetricEvent(
        UUID eventId, // 재전송되어도 Collector에서 중복 저장되지 않게 하는 실행 고유 ID
        UUID transactionId, // 트랜잭션 안에서 실행됐다면 TxMetricEvent.transactionId, 아니면 null
        String appName,
        String environment,
        String instanceId,
        String datasourceName,
        String dbType,
        String sql, // 원본 sql
        String normalizedSql, // 리터럴 치환한 sql
        List<Object> params, // 허용 정책을 통과한 값만 포함. 기본값은 빈 목록
        int paramCount, // 값을 수집하지 않아도 전체 바인딩 파라미터 개수는 남긴다.
        long durationUs, // 쿼리 실행 시간(마이크로초). ms로 저장하면 1ms 미만 쿼리가 전부 0이 되어
                         // 평균/백분위 집계가 무의미해지므로 원시 값은 마이크로초로 유지한다.
        Instant executedAt, // 실행 시각
        String threadName, // 실행 스레드 이름
        boolean succeeded,
        String sqlState
) {
}

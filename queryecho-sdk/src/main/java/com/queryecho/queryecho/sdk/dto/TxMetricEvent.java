package com.queryecho.queryecho.sdk.dto;

import java.time.Instant;

/**
 * @Transactional 메서드 1회 호출(정확히는 물리 트랜잭션 1개)에 대해, 트랜잭션이 실제로
 * 시작된 시점부터 커밋/롤백될 때까지의 결과를 담는 원시 신호.
 *
 * failureReason을 TxStatus와 별개 필드로 둔 이유:
 *  - 커밋 성공 시에는 항상 null이고, 롤백된 경우에만 값이 채워진다. status만으로는
 *    "왜" 롤백됐는지 알 수 없어 "롤백율 및 실패 원인 분석" 요구사항을 만족할 수 없으므로,
 *    예외 클래스명 + 메시지를 별도로 실어 대시보드에서 바로 원인을 확인할 수 있게 했다.
 */
public record TxMetricEvent(
        String transactionName, // 트랜잭션 네임(어느 메서드)
        long durationUs, // 트랜잭션 실행 시간(마이크로초). 단위 근거는 QueryMetricEvent.durationUs 참고.
        TxStatus status, // 상태( COMMIT/ROLLBACK)
        Instant executedAt, // 실행시간
        String threadName, // 실행 스레드
        String failureReason // ROLLBACK -> 예외 메세지 OR NULL
) {
}

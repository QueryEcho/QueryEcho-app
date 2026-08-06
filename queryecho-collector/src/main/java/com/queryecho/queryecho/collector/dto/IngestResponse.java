package com.queryecho.queryecho.collector.dto;

/**
 * 수신 API의 응답. 몇 건을 받아들였는지만 알려준다.
 *
 * 왜 굳이 개수를 돌려주는가?
 *  - SDK 쪽은 전송 실패를 상태 코드로만 판단하지만, 사람이 curl로 직접 수신 API를 찔러볼 때
 *    "요청은 200인데 실제로 처리된 게 0건"인 상황(예: JSON 배열이 비어 있음)을 즉시 구분할 수
 *    있어야 디버깅이 쉽다.
 */
public record IngestResponse(int accepted) {
}

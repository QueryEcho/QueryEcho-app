package com.queryecho.queryecho.collector.service;

/**
 * 마이크로초로 수집된 소요 시간을 로그에 사람이 읽는 단위로 찍기 위한 헬퍼.
 *
 * 왜 저장은 us인데 로그는 ms인가?
 *  - 저장 값을 ms로 반올림해버리면 1ms 미만 구간이 전부 0으로 뭉개져 집계가 죽는다(그래서 us).
 *    반면 로그는 사람이 "얼마나 느린가"를 즉시 판단하는 용도라 익숙한 ms가 읽기 좋다.
 *    소수점 두 자리를 남기므로 1ms 미만도 0이 아니라 0.42ms처럼 그대로 보인다.
 */
final class DurationFormat {

    private DurationFormat() {
    }

    static String toMillisText(long durationUs) {
        return String.format("%.2fms", durationUs / 1000.0);
    }
}

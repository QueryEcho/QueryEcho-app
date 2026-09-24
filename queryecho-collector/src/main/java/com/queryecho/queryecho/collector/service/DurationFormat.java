package com.queryecho.queryecho.collector.service;

/** 마이크로초 단위의 소요 시간을 읽기 쉬운 밀리초 문자열로 변환한다. */
final class DurationFormat {

    private DurationFormat() {
    }

    static String toMillisText(long durationUs) {
        return String.format("%.2fms", durationUs / 1000.0);
    }
}

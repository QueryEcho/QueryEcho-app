package com.queryecho.queryecho.collector.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** {@code queryecho.collector.*}에 연결되는 수집·분석 설정. */
@Data
@ConfigurationProperties(prefix = "queryecho.collector")
public class QueryEchoCollectorProperties {

    /** 원격 수집 요청 인증 키. 비어 있으면 인증하지 않는다. */
    private String ingestApiKey = "";

    /** 느린 쿼리로 분류할 실행시간 기준값. */
    private long slowQueryThresholdMs = 500;

    /** 밀리초 설정값을 수집 지표와 같은 마이크로초 단위로 변환한다. */
    public long slowQueryThresholdUs() {
        return slowQueryThresholdMs * 1_000;
    }

    private final NPlusOne nPlusOne = new NPlusOne();
    private final Rollup rollup = new Rollup();
    private final Retention retention = new Retention();

    @Data
    public static class NPlusOne {
        /** 반복 쿼리를 같은 패턴으로 묶어 세는 시간 구간. */
        private long windowMs = 1000;

        /** 윈도우 안에서 같은 모양의 쿼리가 이 횟수 이상 반복되면 N+1 의심으로 표시한다. */
        private int threshold = 5;
    }

    @Data
    public static class Rollup {
        /** 늦게 도착한 이벤트를 보정하기 위해 매번 다시 계산할 최근 구간. */
        private long lookbackMinutes = 10;
        private long intervalMs = 60_000;
    }

    @Data
    public static class Retention {
        private int executionDays = 7;
    }
}

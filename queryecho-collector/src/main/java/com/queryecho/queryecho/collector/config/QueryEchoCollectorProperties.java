package com.queryecho.queryecho.collector.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * collector가 원시 이벤트를 판단(슬로우쿼리/N+1 여부)할 때 쓰는 기준값.
 * {@code queryecho.collector.*} 접두어에 바인딩된다.
 *
 * 분리 이유는 {@link com.queryecho.queryecho.sdk.config.QueryEchoSdkProperties}의 클래스
 * 주석 참고 - "SDK를 켤지"와 "판단 기준을 어디로 잡을지"는 서로 다른 모듈의 책임이다.
 */
@Data
@ConfigurationProperties(prefix = "queryecho.collector")
public class QueryEchoCollectorProperties {

    /** 원격 SDK 수집 요청에 요구할 Bearer 키. 비어 있으면 로컬 개발 호환을 위해 인증하지 않는다. */
    private String ingestApiKey = "";

    /** 이 값(ms) 이상 걸린 쿼리는 슬로우 쿼리로 분류된다. */
    private long slowQueryThresholdMs = 500;

    /**
     * 지표는 마이크로초 단위로 수집되므로 비교 시점에는 임계값도 같은 단위여야 한다.
     * 설정 자체를 us로 노출하지 않은 이유: "300ms 넘으면 느린 쿼리"가 사람이 임계값을
     * 정할 때 쓰는 자연스러운 단위이고, 300000us라고 쓰게 만들면 자릿수 실수만 늘어난다.
     *
     * getXxx가 아닌 이름을 쓴 이유는 이 값이 설정 바인딩 대상이 아니라 파생 값이기 때문이다.
     * (@ConfigurationProperties가 setter 없는 getter를 바인딩 후보로 보지 않게 한다.)
     */
    public long slowQueryThresholdUs() {
        return slowQueryThresholdMs * 1_000;
    }

    private final NPlusOne nPlusOne = new NPlusOne();
    private final Rollup rollup = new Rollup();
    private final Retention retention = new Retention();

    @Data
    public static class NPlusOne {
        /**
         * 반복 쿼리를 같은 패턴으로 묶어 세는 슬라이딩 윈도우 길이(ms).
         * 너무 짧으면 정상적인 순차 처리도 N+1로 오탐하고, 너무 길면 서로 무관한 두 요청의
         * 쿼리가 우연히 같은 윈도우에 섞여 오탐/누락이 생길 수 있어 설정으로 노출했다.
         */
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

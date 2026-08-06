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

    /** 이 값(ms) 이상 걸린 쿼리는 슬로우 쿼리로 분류된다. */
    private long slowQueryThresholdMs = 500;

    private final NPlusOne nPlusOne = new NPlusOne();

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
}

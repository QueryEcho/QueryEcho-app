package com.queryecho.queryecho.sdk.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * SDK 모듈 자체의 동작을 조정하는 설정. {@code queryecho.sdk.*} 접두어에 바인딩된다.
 *
 * 왜 예전의 QueryEchoProperties 하나에서 분리했는가?
 *  - 원래는 "SDK를 켤지 말지"(enabled)와 "슬로우쿼리/N+1 판단 기준"(threshold)이 한 클래스에
 *    같이 있었는데, 이 둘은 실제로는 서로 다른 모듈의 관심사다. enabled는 "이 애플리케이션에
 *    QueryEcho SDK를 심을지 말지"를 결정하는 SDK 자신의 스위치이고, 슬로우쿼리 임계값은
 *    "수집된 원시 데이터를 어떤 기준으로 판단할지"를 결정하는 collector(분석 서버)의 몫이다.
 *  - queryecho-sdk 모듈은 앞으로 이 애플리케이션과 별개인 다른 서비스에도 의존성으로 추가될
 *    라이브러리이므로, collector 전용 설정(임계값 등)까지 이 모듈에 같이 들고 있으면
 *    "SDK를 쓰는 모든 서비스가 collector의 판단 기준까지 알아야 하는" 불필요한 결합이 생긴다.
 *    두 설정 클래스를 모듈 경계에 맞춰 쪼개두면, 나중에 SDK와 collector가 완전히 다른
 *    레포/배포 단위로 갈라져도 서로의 설정 클래스를 몰라도 된다.
 */
@Data
@ConfigurationProperties(prefix = "queryecho.sdk")
public class QueryEchoSdkProperties {

    /**
     * 전체 켜고 끄는 스위치. false면 DataSource 프록시 자체를 설치하지 않는다.
     * "계측 오버헤드 없이 순수 성능을 재고 싶다"는 요구를 코드 변경 없이 만족시키기 위한 옵션.
     */
    private boolean enabled = true;

    /**
     * 수집한 이벤트를 어디로 내보낼지 결정한다.
     *
     * 왜 두 가지 모드를 두는가?
     *  - LOCAL: SDK와 collector가 같은 JVM 안에 있는 경우(지금의 queryecho-app 데모).
     *    네트워크를 타지 않으므로 SDK 계측 로직 자체를 검증하기에 가장 단순하다.
     *  - HTTP: 타깃 애플리케이션이 완전히 별도 프로세스인 실제 사용 형태. SDK만 의존성으로
     *    추가한 앱이 원격 Collector 서버로 지표를 보낸다.
     *  기본값을 LOCAL로 둔 이유는, 설정을 아무것도 안 한 상태에서 "네트워크 연결 실패 로그가
     *  쏟아지는" 상황을 피하기 위해서다. 원격 전송은 명시적으로 켜야 한다.
     */
    private Transport transport = Transport.LOCAL;

    /** transport=HTTP일 때 지표를 보낼 Collector 서버의 베이스 URL. */
    private String collectorUrl = "http://localhost:8080";

    private final Buffer buffer = new Buffer();

    public enum Transport {
        LOCAL,
        HTTP
    }

    /**
     * HTTP 전송 시의 버퍼링/배치 설정.
     *
     * 왜 버퍼가 필요한가?
     *  - 쿼리 1건마다 HTTP 요청을 하나씩 보내면, 모니터링 때문에 발생하는 네트워크 왕복이
     *    원래 애플리케이션의 쿼리 수만큼 늘어난다. 이건 "관찰 도구가 관찰 대상보다 더 큰 부하를
     *    만드는" 최악의 상황이다. 이벤트를 메모리 큐에 모았다가 주기적으로 한 번에 보내면
     *    요청 수를 수백 분의 1로 줄일 수 있다.
     */
    @Data
    public static class Buffer {

        /**
         * 메모리 큐에 담아둘 수 있는 최대 이벤트 수. 이 수를 넘어가면 새 이벤트는 버려진다.
         *
         * 왜 무한 큐가 아니라 상한을 두는가?
         *  - Collector 서버가 죽었거나 느릴 때 큐가 무한정 늘어나면 결국 타깃 애플리케이션이
         *    OutOfMemoryError로 죽는다. 모니터링 실패가 서비스 장애로 번지는 건 절대 안 되므로,
         *    "넘치면 지표를 버린다"를 명시적인 정책으로 삼았다(fail-safe).
         */
        private int capacity = 10_000;

        /** HTTP 요청 한 번에 실어 보낼 최대 이벤트 수. */
        private int batchSize = 200;

        /** 큐를 비워 전송하는 주기(ms). */
        private long flushIntervalMs = 1000;

        /**
         * 전송 요청 하나의 타임아웃(ms).
         * Collector가 응답하지 않을 때 전송 스레드가 무한정 붙잡히지 않도록 반드시 필요하다.
         */
        private long requestTimeoutMs = 3000;
    }
}

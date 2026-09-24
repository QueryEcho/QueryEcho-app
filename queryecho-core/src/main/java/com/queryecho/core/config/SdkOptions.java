package com.queryecho.core.config;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;


/** {@code queryecho.sdk.*}에 바인딩되는 SDK 실행 및 전송 설정. */
@Data

public class SdkOptions {

    /** SDK 계측 활성화 여부. 비활성화하면 DataSource 프록시를 설치하지 않는다. */
    private boolean enabled = true;

    /** Collector에서 지표의 출처를 구분하기 위한 논리적 애플리케이션 이름. */
    private String appName = "unknown-app";

    /** local/dev/staging/prod 같은 실행 환경. */
    private String environment = "default";

    /** 서버, 컨테이너 또는 Pod를 구분하는 안정적인 인스턴스 식별자. */
    private String instanceId = defaultInstanceId();

    /** mysql/postgresql/h2처럼 fingerprint를 DB 종류별로 분리하기 위한 값. */
    private String dbType = "unknown";

    /** Collector 저장용 DataSource처럼 관찰하면 안 되는 빈 이름. */
    private List<String> excludedDataSourceBeans = new ArrayList<>();

    /** 같은 JVM에 Collector가 있을 때 자기 저장 트랜잭션을 다시 계측하지 않을 패키지 접두어. */
    private List<String> excludedTransactionPackages = new ArrayList<>();

    /** 바인딩 파라미터 수집은 기본적으로 꺼져 있고, fingerprint+index 허용 목록만 지원한다. */
    private final Params params = new Params();

    /** 롤백 예외 메시지는 민감정보가 포함될 수 있어 기본적으로 Collector에 보내지 않는다. */
    private final Transaction transaction = new Transaction();

    /** 이벤트 전달 방식. LOCAL은 동일 JVM, HTTP는 원격 Collector로 전달한다. */
    private Transport transport = Transport.LOCAL;

    /** transport=HTTP일 때 지표를 보낼 Collector 서버의 베이스 URL. */
    private String collectorUrl = "http://localhost:8080";

    /** Collector 수집 API의 Bearer 인증 키. 비어 있으면 인증 헤더를 보내지 않는다. */
    private String apiKey = "";

    private final Buffer buffer = new Buffer();

    public enum Transport {
        LOCAL,
        HTTP
    }

    /** HTTP 비동기 배치 전송을 위한 버퍼 설정. */
    @Data
    public static class Buffer {

        /** 메모리 큐 최대 크기. 초과한 신규 이벤트는 애플리케이션 보호를 위해 버린다. */
        private int capacity = 10_000;

        /** HTTP 요청 한 번에 실어 보낼 최대 이벤트 수. */
        private int batchSize = 200;

        /** 큐를 비워 전송하는 주기(ms). */
        private long flushIntervalMs = 1000;

        /** SDK 큐·전송·유실 누적 상태를 Collector에 보고하는 주기(ms). */
        private long healthReportIntervalMs = 10_000;

        /** Collector HTTP 요청 타임아웃(ms). */
        private long requestTimeoutMs = 3000;
    }

    @Data
    public static class Params {
        private boolean enabled = false;
        private int maxTextLength = 100;
        private List<ParamRule> rules = new ArrayList<>();
    }

    @Data
    public static class Transaction {
        private boolean failureMessageEnabled = false;
        private int failureMessageMaxLength = 1_000;
    }

    @Data
    public static class ParamRule {
        /** SHA-256(dbType + ':' + normalizedSql). */
        private String fingerprint;
        /** JDBC의 1부터 시작하는 파라미터 인덱스. */
        private List<Integer> allowedIndexes = new ArrayList<>();
    }

    private static String defaultInstanceId() {
        String hostname = System.getenv("HOSTNAME");
        if (hostname == null || hostname.isBlank()) {
            hostname = System.getenv("COMPUTERNAME");
        }
        return hostname == null || hostname.isBlank() ? "unknown-instance" : hostname;
    }
}

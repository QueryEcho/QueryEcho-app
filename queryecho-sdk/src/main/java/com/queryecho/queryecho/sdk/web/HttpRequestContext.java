package com.queryecho.queryecho.sdk.web;

/** 현재 HTTP 요청과 그 안에서 실행되는 JDBC/트랜잭션 지표를 연결하는 스레드 컨텍스트. */
public final class HttpRequestContext {

    private static final ThreadLocal<Snapshot> CURRENT = new ThreadLocal<>();

    private HttpRequestContext() {
    }

    public static void set(Snapshot snapshot) {
        CURRENT.set(snapshot);
    }

    public static Snapshot current() {
        return CURRENT.get();
    }

    public static void clear() {
        CURRENT.remove();
    }

    public record Snapshot(
            String traceId,
            String requestId,
            String httpMethod,
            String httpPath,
            String handlerName
    ) {
    }
}

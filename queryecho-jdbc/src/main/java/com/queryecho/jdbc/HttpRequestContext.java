package com.queryecho.jdbc;

/** 선택적인 웹 계층이 JDBC/트랜잭션 이벤트에 전달하는 요청 컨텍스트. */
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

    public record Snapshot(String traceId, String requestId, String httpMethod, String httpPath, String handlerName) {
    }
}

package com.queryecho.queryecho.sdk.util;

/** 애플리케이션과 전체 메서드 시그니처를 트랜잭션 패턴 식별자로 바꾼다. */
public final class TransactionFingerprint {

    private TransactionFingerprint() {
    }

    public static String sha256(String appName, String transactionName) {
        return QueryFingerprint.sha256(appName, transactionName);
    }
}

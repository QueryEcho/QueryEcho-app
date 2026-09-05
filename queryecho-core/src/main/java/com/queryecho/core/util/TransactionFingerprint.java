package com.queryecho.core.util;

public final class TransactionFingerprint {
    private TransactionFingerprint() {
    }

    public static String sha256(String appName, String transactionName) {
        return QueryFingerprint.sha256(appName, transactionName);
    }
}

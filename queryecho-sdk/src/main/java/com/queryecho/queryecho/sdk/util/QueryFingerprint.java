package com.queryecho.queryecho.sdk.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** 정규화 SQL을 저장/집계용 고정 길이 식별자로 바꾼다. */
public final class QueryFingerprint {

    private QueryFingerprint() {
    }

    public static String sha256(String dbType, String normalizedSql) {
        String source = safe(dbType) + ":" + safe(normalizedSql);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(source.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    private static String safe(String value) {
        return value == null ? "unknown" : value;
    }
}

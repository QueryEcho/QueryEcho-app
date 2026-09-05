package com.queryecho.core.util;

import java.util.regex.Pattern;

/** SQL 리터럴과 공백 차이를 제거해 집계 가능한 형태로 정규화한다. */
public final class SqlNormalizer {
    private static final Pattern STRING_LITERAL = Pattern.compile("'(?:[^']|'')*'");
    private static final Pattern NUMBER_LITERAL = Pattern.compile("\\b\\d+\\b");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern IN_LIST = Pattern.compile("(?i)\\bIN\\s*\\(\\s*\\?(\\s*,\\s*\\?)*\\s*\\)");

    private SqlNormalizer() {
    }

    public static String normalize(String sql) {
        if (sql == null || sql.isBlank()) {
            return sql;
        }
        String normalized = STRING_LITERAL.matcher(sql.trim()).replaceAll("?");
        normalized = NUMBER_LITERAL.matcher(normalized).replaceAll("?");
        normalized = IN_LIST.matcher(normalized).replaceAll("IN (?)");
        return WHITESPACE.matcher(normalized).replaceAll(" ").trim();
    }
}

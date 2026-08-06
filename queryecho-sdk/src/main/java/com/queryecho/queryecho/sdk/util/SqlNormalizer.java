package com.queryecho.queryecho.sdk.util;

import java.util.regex.Pattern;

/**
 * 원본 SQL에서 리터럴 값(문자열, 숫자, IN 목록 길이)을 제거해 "모양(shape)"만 남긴다.
 * 예: {@code WHERE id = 42} 와 {@code WHERE id = 7} 은 둘 다 {@code WHERE id = ?}로 정규화된다.
 *
 * 왜 필요한가?
 *  - 슬로우 쿼리 집계나 N+1 탐지는 "같은 쿼리가 반복됐는가"를 판단해야 하는데,
 *    바인딩 값이 다르면(리터럴이든 파라미터든) 원본 SQL 문자열은 매번 달라 보일 수 있다.
 *    리터럴을 마스킹해서 그룹핑 키로 쓰면 이런 값 차이를 무시하고 "진짜 같은 쿼리 패턴"만
 *    비교할 수 있다. 그라파나/APM 도구들이 흔히 쓰는 "쿼리 지문(query fingerprint)" 기법과 동일한 발상이다.
 *  - 또한 바인딩 값을 그대로 로그에 남기면 개인정보(이메일, 이름 등)가 노출될 위험이 있는데,
 *    정규화된 SQL은 값이 전부 '?'로 치환되어 있어 대시보드 목록/집계 뷰에서 안전하게 노출할 수 있다
 *    (원본 값은 params 필드에 별도로 담아, 상세 조회 시에만 보여주는 방식으로 분리).
 *
 * 왜 정규식 기반인가 (진짜 SQL 파서/JSqlParser 같은 라이브러리 대신)?
 *  - 목적이 "재실행 가능한 정확한 AST"가 아니라 "그룹핑용 근사 지문"이면 충분하기 때문에,
 *    무거운 파서 의존성을 추가하지 않고 몇 개의 정규식만으로 실용적인 수준의 정확도를 얻는다.
 *    복잡한 서브쿼리/CTE에서 완벽하지 않을 수 있다는 한계는 README에 명시한다.
 */
public final class SqlNormalizer {

    // 'abc' 형태의 문자열 리터럴. SQL 표준의 이스케이프 규칙(작은따옴표 두 번 = '')도 함께 처리한다.
    private static final Pattern STRING_LITERAL = Pattern.compile("'(?:[^']|'')*'");
    // 순수 숫자 리터럴. 단, 컬럼명에 포함된 숫자(예: col1)까지 지우지 않도록 \b(단어 경계)로 제한한다.
    private static final Pattern NUMBER_LITERAL = Pattern.compile("\\b\\d+\\b");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    // IN (?, ?, ?) 처럼 바인딩 파라미터 개수만 다른 IN 목록은, 길이 차이 때문에
    // 서로 다른 패턴으로 취급되지 않도록 "IN (?)" 하나로 뭉갠다.
    private static final Pattern IN_LIST = Pattern.compile("(?i)\\bIN\\s*\\(\\s*\\?(\\s*,\\s*\\?)*\\s*\\)");

    private SqlNormalizer() {
    }

    public static String normalize(String sql) {
        if (sql == null || sql.isBlank()) {
            return sql;
        }
        String normalized = sql.trim();
        // 처리 순서가 중요하다: 문자열 리터럴을 먼저 지워야, 문자열 안에 숫자가 들어있는 경우
        // (예: '2024-01-01') NUMBER_LITERAL 패턴이 문자열 내부까지 잘못 건드리지 않는다.
        normalized = STRING_LITERAL.matcher(normalized).replaceAll("?");
        normalized = NUMBER_LITERAL.matcher(normalized).replaceAll("?");
        normalized = IN_LIST.matcher(normalized).replaceAll("IN (?)");
        normalized = WHITESPACE.matcher(normalized).replaceAll(" ");
        return normalized.trim();
    }
}

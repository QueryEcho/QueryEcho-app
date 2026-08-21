package com.queryecho.queryecho.collector.dbserver;

/**
 * DB 제품별 서버 쿼리 수집 어댑터의 공통 계약.
 * MySQL은 performance_schema, PostgreSQL은 pg_stat_statements처럼 서로 다른
 * 원천을 사용한다. 개별 실행을 제공하는 DB는 {@link DbServerQuerySample}, 누적 통계만
 * 제공하는 DB는 {@link DbServerQueryAggregateSample}로 변환해 공통 저장 계층으로 보낸다.
 */
public interface DbServerQueryCollector {

    /** mysql, postgresql처럼 어댑터가 담당하는 DB 제품 식별자. */
    String dbType();

    /** 한 번 수집한다. 스케줄링/재시도 정책은 각 DB 어댑터가 제품 특성에 맞게 가진다. */
    void collect();
}

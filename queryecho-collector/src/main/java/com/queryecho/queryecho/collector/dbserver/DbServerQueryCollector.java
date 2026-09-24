package com.queryecho.queryecho.collector.dbserver;

/** 데이터베이스별 쿼리 통계를 공통 형식으로 수집하는 규격. */
public interface DbServerQueryCollector {

    /** mysql, postgresql처럼 어댑터가 담당하는 DB 제품 식별자. */
    String dbType();

    /** 한 번 수집한다. 스케줄링/재시도 정책은 각 DB 어댑터가 제품 특성에 맞게 가진다. */
    void collect();
}

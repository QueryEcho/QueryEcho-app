package com.queryecho.queryecho.sdk.interceptor;

/** 한 DataSource에서 발생하는 지표에 공통으로 붙는 출처 정보. */
record QueryMetricSource(
        String appName, // 해당 앱 네임 ex) product-api
        String environment, // 해당 환경 ex)prod, dev, local
        String instanceId, // 인스턴스 아이디
        String datasourceName, // dataSource 이름 구분
        String dbType // DBMS 구분 ex)mysql, postgres 등
) {
}

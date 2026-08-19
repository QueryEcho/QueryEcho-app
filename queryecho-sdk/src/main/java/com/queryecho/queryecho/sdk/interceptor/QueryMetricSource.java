package com.queryecho.queryecho.sdk.interceptor;

/** 한 DataSource에서 발생하는 지표에 공통으로 붙는 출처 정보. */
record QueryMetricSource(
        String appName,
        String environment,
        String instanceId,
        String datasourceName,
        String dbType
) {
}

package com.queryecho.jdbc;

/** 한 DataSource에서 만들어지는 모든 쿼리 이벤트에 붙는 출처 정보. */
public record QueryMetricSource(
        String appName,
        String environment,
        String instanceId,
        String datasourceName,
        String dbType
) {
}

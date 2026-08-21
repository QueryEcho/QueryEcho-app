package com.queryecho.queryecho.collector.dbserver.postgresql;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PostgreSqlServerCollectorProperties.class)
public class PostgreSqlServerCollectorConfiguration {
}

package com.queryecho.queryecho.collector.dbserver.mysql;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(MySqlServerCollectorProperties.class)
public class MySqlServerCollectorConfiguration {
}

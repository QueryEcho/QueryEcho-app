package com.queryecho.queryecho.config;

import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/** Collector 저장 DB와 데모 관찰 대상 DB를 분리한다. */
@Configuration
public class QueryEchoDataSourceConfig {

    @Bean(name = "queryEchoStorageDataSource")
    @Primary
    public DataSource queryEchoStorageDataSource(
            @Value("${spring.datasource.url}") String url,
            @Value("${spring.datasource.driver-class-name}") String driverClassName,
            @Value("${spring.datasource.username}") String username,
            @Value("${spring.datasource.password:}") String password) {
        return dataSource(url, driverClassName, username, password);
    }

    @Bean(name = "demoDataSource")
    @ConditionalOnProperty(prefix = "queryecho.demo", name = "enabled", havingValue = "true", matchIfMissing = true)
    public DataSource demoDataSource(
            @Value("${queryecho.demo.datasource.url}") String url,
            @Value("${queryecho.demo.datasource.driver-class-name}") String driverClassName,
            @Value("${queryecho.demo.datasource.username}") String username,
            @Value("${queryecho.demo.datasource.password:}") String password) {
        return dataSource(url, driverClassName, username, password);
    }

    @Bean
    @ConditionalOnProperty(prefix = "queryecho.demo", name = "enabled", havingValue = "true", matchIfMissing = true)
    public JdbcTemplate demoJdbcTemplate(@Qualifier("demoDataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    private DataSource dataSource(String url, String driverClassName, String username, String password) {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName(driverClassName);
        dataSource.setUrl(url);
        dataSource.setUsername(username);
        dataSource.setPassword(password);
        return dataSource;
    }
}

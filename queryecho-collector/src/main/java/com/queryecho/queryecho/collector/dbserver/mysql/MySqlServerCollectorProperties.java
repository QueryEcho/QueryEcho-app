package com.queryecho.queryecho.collector.dbserver.mysql;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** MySQL performance_schema 기반 DB 서버 쿼리 수집 설정. */
@ConfigurationProperties(prefix = "queryecho.db-collector.mysql")
public class MySqlServerCollectorProperties {

    private boolean enabled;
    private String jdbcUrl = "jdbc:mysql://localhost:3307/performance_schema"
            + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Seoul";
    private String username = "queryecho_monitor";
    private String password = "queryecho_monitor";
    private String instanceId = "mysql-local-01";
    private String schema = "querytest";
    private long pollIntervalMs = 1_000;
    private int batchSize = 2_000;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getJdbcUrl() { return jdbcUrl; }
    public void setJdbcUrl(String jdbcUrl) { this.jdbcUrl = jdbcUrl; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getInstanceId() { return instanceId; }
    public void setInstanceId(String instanceId) { this.instanceId = instanceId; }
    public String getSchema() { return schema; }
    public void setSchema(String schema) { this.schema = schema; }
    public long getPollIntervalMs() { return pollIntervalMs; }
    public void setPollIntervalMs(long pollIntervalMs) { this.pollIntervalMs = pollIntervalMs; }
    public int getBatchSize() { return batchSize; }
    public void setBatchSize(int batchSize) { this.batchSize = batchSize; }
}

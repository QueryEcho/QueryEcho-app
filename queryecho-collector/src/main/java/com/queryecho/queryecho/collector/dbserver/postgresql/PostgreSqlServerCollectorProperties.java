package com.queryecho.queryecho.collector.dbserver.postgresql;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "queryecho.db-collector.postgresql")
public class PostgreSqlServerCollectorProperties {

    private boolean enabled;
    private String jdbcUrl = "jdbc:postgresql://localhost:5434/querytest";
    private String username = "queryecho_monitor";
    private String password = "queryecho_monitor";
    private String instanceId = "postgresql-local-01";
    private String database = "querytest";

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
    public String getDatabase() { return database; }
    public void setDatabase(String database) { this.database = database; }
}

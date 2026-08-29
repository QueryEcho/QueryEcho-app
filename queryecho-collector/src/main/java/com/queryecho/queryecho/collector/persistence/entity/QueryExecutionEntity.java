package com.queryecho.queryecho.collector.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "query_execution")
public class QueryExecutionEntity {

    @Id
    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "transaction_id")
    private UUID transactionId;

    @Column(name = "executed_at", nullable = false)
    private Instant executedAt;

    @Column(name = "ingested_at", nullable = false)
    private Instant ingestedAt;

    @Column(nullable = false, length = 30)
    private String environment;

    @Column(name = "app_name", nullable = false, length = 100)
    private String appName;

    @Column(name = "instance_id", nullable = false, length = 200)
    private String instanceId;

    @Column(name = "datasource_name", nullable = false, length = 100)
    private String datasourceName;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pattern_id", nullable = false)
    private QueryPatternEntity pattern;

    @Column(name = "duration_us", nullable = false)
    private long durationUs;

    @Column(nullable = false)
    private boolean succeeded;

    @Column(name = "sql_state", length = 5)
    private String sqlState;

    @Column(name = "thread_name", length = 200)
    private String threadName;

    @Column(name = "trace_id", length = 64)
    private String traceId;

    @Column(name = "request_id", length = 100)
    private String requestId;

    @Column(name = "http_method", length = 10)
    private String httpMethod;

    @Column(name = "http_path", length = 500)
    private String httpPath;

    @Column(name = "handler_name", length = 500)
    private String handlerName;

    @Column(name = "param_count", nullable = false)
    private short paramCount;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> params;

    protected QueryExecutionEntity() {
    }

    public QueryExecutionEntity(UUID eventId, UUID transactionId, Instant executedAt, Instant ingestedAt,
                                String environment, String appName, String instanceId,
                                String datasourceName, QueryPatternEntity pattern,
                                long durationUs, boolean succeeded, String sqlState,
                                String threadName, String traceId, String requestId,
                                String httpMethod, String httpPath, String handlerName,
                                short paramCount, Map<String, Object> params) {
        this.eventId = eventId;
        this.transactionId = transactionId;
        this.executedAt = executedAt;
        this.ingestedAt = ingestedAt;
        this.environment = environment;
        this.appName = appName;
        this.instanceId = instanceId;
        this.datasourceName = datasourceName;
        this.pattern = pattern;
        this.durationUs = durationUs;
        this.succeeded = succeeded;
        this.sqlState = sqlState;
        this.threadName = threadName;
        this.traceId = traceId;
        this.requestId = requestId;
        this.httpMethod = httpMethod;
        this.httpPath = httpPath;
        this.handlerName = handlerName;
        this.paramCount = paramCount;
        this.params = params;
    }

    public UUID getEventId() { return eventId; }
    public UUID getTransactionId() { return transactionId; }
    public Instant getExecutedAt() { return executedAt; }
    public Instant getIngestedAt() { return ingestedAt; }
    public String getEnvironment() { return environment; }
    public String getAppName() { return appName; }
    public String getInstanceId() { return instanceId; }
    public String getDatasourceName() { return datasourceName; }
    public QueryPatternEntity getPattern() { return pattern; }
    public long getDurationUs() { return durationUs; }
    public boolean isSucceeded() { return succeeded; }
    public String getSqlState() { return sqlState; }
    public String getThreadName() { return threadName; }
    public String getTraceId() { return traceId; }
    public String getRequestId() { return requestId; }
    public String getHttpMethod() { return httpMethod; }
    public String getHttpPath() { return httpPath; }
    public String getHandlerName() { return handlerName; }
    public short getParamCount() { return paramCount; }
    public Map<String, Object> getParams() { return params; }
}

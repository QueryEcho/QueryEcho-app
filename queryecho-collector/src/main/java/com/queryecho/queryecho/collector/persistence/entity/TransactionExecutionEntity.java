package com.queryecho.queryecho.collector.persistence.entity;

import com.queryecho.queryecho.sdk.dto.TxStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "transaction_execution")
public class TransactionExecutionEntity {

    @Id
    @Column(name = "transaction_id", nullable = false)
    private UUID transactionId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pattern_id", nullable = false)
    private TransactionPatternEntity pattern;

    @Column(nullable = false, length = 30)
    private String environment;

    @Column(name = "instance_id", nullable = false, length = 200)
    private String instanceId;

    @Column(name = "completed_at", nullable = false)
    private Instant completedAt;

    @Column(name = "duration_us", nullable = false)
    private long durationUs;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TxStatus status;

    @Column(name = "thread_name", length = 200)
    private String threadName;

    @Column(name = "failure_type", length = 200)
    private String failureType;

    @Column(name = "failure_message", length = 1000)
    private String failureMessage;

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

    @Column(name = "ingested_at", nullable = false)
    private Instant ingestedAt;

    protected TransactionExecutionEntity() {
    }

    public TransactionExecutionEntity(UUID transactionId, TransactionPatternEntity pattern,
                                      String environment, String instanceId, Instant completedAt,
                                      long durationUs, TxStatus status, String threadName,
                                      String failureType, String failureMessage, String traceId,
                                      String requestId, String httpMethod, String httpPath,
                                      String handlerName, Instant ingestedAt) {
        this.transactionId = transactionId;
        this.pattern = pattern;
        this.environment = environment;
        this.instanceId = instanceId;
        this.completedAt = completedAt;
        this.durationUs = durationUs;
        this.status = status;
        this.threadName = threadName;
        this.failureType = failureType;
        this.failureMessage = failureMessage;
        this.traceId = traceId;
        this.requestId = requestId;
        this.httpMethod = httpMethod;
        this.httpPath = httpPath;
        this.handlerName = handlerName;
        this.ingestedAt = ingestedAt;
    }

    public UUID getTransactionId() { return transactionId; }
    public TransactionPatternEntity getPattern() { return pattern; }
    public String getEnvironment() { return environment; }
    public String getInstanceId() { return instanceId; }
    public Instant getCompletedAt() { return completedAt; }
    public long getDurationUs() { return durationUs; }
    public TxStatus getStatus() { return status; }
    public String getThreadName() { return threadName; }
    public String getFailureType() { return failureType; }
    public String getFailureMessage() { return failureMessage; }
    public String getTraceId() { return traceId; }
    public String getRequestId() { return requestId; }
    public String getHttpMethod() { return httpMethod; }
    public String getHttpPath() { return httpPath; }
    public String getHandlerName() { return handlerName; }
    public Instant getIngestedAt() { return ingestedAt; }
}

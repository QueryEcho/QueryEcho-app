package com.queryecho.queryecho.collector.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "transaction_pattern")
public class TransactionPatternEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String fingerprint;

    @Column(name = "app_name", nullable = false, length = 100)
    private String appName;

    @Column(name = "transaction_name", nullable = false, length = 500)
    private String transactionName;

    @Column(name = "first_seen_at", nullable = false)
    private Instant firstSeenAt;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    protected TransactionPatternEntity() {
    }

    public TransactionPatternEntity(String fingerprint, String appName,
                                    String transactionName, Instant seenAt) {
        this.fingerprint = fingerprint;
        this.appName = appName;
        this.transactionName = transactionName;
        this.firstSeenAt = seenAt;
        this.lastSeenAt = seenAt;
    }

    public void seenAt(Instant seenAt) {
        if (seenAt.isAfter(lastSeenAt)) {
            lastSeenAt = seenAt;
        }
    }

    public Long getId() { return id; }
    public String getFingerprint() { return fingerprint; }
    public String getAppName() { return appName; }
    public String getTransactionName() { return transactionName; }
    public Instant getFirstSeenAt() { return firstSeenAt; }
    public Instant getLastSeenAt() { return lastSeenAt; }
}

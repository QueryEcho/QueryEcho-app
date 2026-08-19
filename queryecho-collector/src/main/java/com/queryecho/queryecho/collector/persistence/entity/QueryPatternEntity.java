package com.queryecho.queryecho.collector.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "query_pattern")
public class QueryPatternEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String fingerprint;

    @Column(name = "db_type", nullable = false, length = 30)
    private String dbType;

    @Column(name = "normalized_sql", nullable = false, columnDefinition = "text")
    private String normalizedSql;

    @Column(name = "statement_type", nullable = false, length = 16)
    private String statementType;

    @Column(name = "first_seen_at", nullable = false)
    private Instant firstSeenAt;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    protected QueryPatternEntity() {
    }

    public QueryPatternEntity(String fingerprint, String dbType, String normalizedSql,
                              String statementType, Instant seenAt) {
        this.fingerprint = fingerprint;
        this.dbType = dbType;
        this.normalizedSql = normalizedSql;
        this.statementType = statementType;
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
    public String getDbType() { return dbType; }
    public String getNormalizedSql() { return normalizedSql; }
    public String getStatementType() { return statementType; }
    public Instant getFirstSeenAt() { return firstSeenAt; }
    public Instant getLastSeenAt() { return lastSeenAt; }
}

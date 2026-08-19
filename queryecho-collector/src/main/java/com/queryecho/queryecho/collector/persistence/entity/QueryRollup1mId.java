package com.queryecho.queryecho.collector.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

@Embeddable
public class QueryRollup1mId implements Serializable {

    @Column(name = "bucket_start", nullable = false)
    private Instant bucketStart;
    @Column(nullable = false, length = 30)
    private String environment;
    @Column(name = "app_name", nullable = false, length = 100)
    private String appName;
    @Column(name = "instance_id", nullable = false, length = 200)
    private String instanceId;
    @Column(name = "datasource_name", nullable = false, length = 100)
    private String datasourceName;
    @Column(name = "pattern_id", nullable = false)
    private Long patternId;

    protected QueryRollup1mId() {
    }

    public QueryRollup1mId(Instant bucketStart, String environment, String appName,
                           String instanceId, String datasourceName, Long patternId) {
        this.bucketStart = bucketStart;
        this.environment = environment;
        this.appName = appName;
        this.instanceId = instanceId;
        this.datasourceName = datasourceName;
        this.patternId = patternId;
    }

    public Instant getBucketStart() { return bucketStart; }
    public String getEnvironment() { return environment; }
    public String getAppName() { return appName; }
    public String getInstanceId() { return instanceId; }
    public String getDatasourceName() { return datasourceName; }
    public Long getPatternId() { return patternId; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof QueryRollup1mId that)) return false;
        return Objects.equals(bucketStart, that.bucketStart)
                && Objects.equals(environment, that.environment)
                && Objects.equals(appName, that.appName)
                && Objects.equals(instanceId, that.instanceId)
                && Objects.equals(datasourceName, that.datasourceName)
                && Objects.equals(patternId, that.patternId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(bucketStart, environment, appName, instanceId, datasourceName, patternId);
    }
}

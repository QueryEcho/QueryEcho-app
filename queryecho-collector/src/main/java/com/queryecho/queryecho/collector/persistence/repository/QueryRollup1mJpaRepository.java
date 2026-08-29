package com.queryecho.queryecho.collector.persistence.repository;

import com.queryecho.queryecho.collector.persistence.entity.QueryRollup1mEntity;
import com.queryecho.queryecho.collector.persistence.entity.QueryRollup1mId;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QueryRollup1mJpaRepository
        extends JpaRepository<QueryRollup1mEntity, QueryRollup1mId> {

    @Query("""
            select r.id.bucketStart as bucketStart,
                   sum(r.execCount) as executionCount,
                   sum(r.errorCount) as errorCount,
                   sum(r.totalUs) as totalDurationUs,
                   min(r.minUs) as minDurationUs,
                   max(r.maxUs) as maxDurationUs
              from QueryRollup1mEntity r
             where r.id.bucketStart >= :from and r.id.bucketStart < :to
               and (:environment is null or r.id.environment = :environment)
               and (:appName is null or r.id.appName = :appName)
               and (:instanceId is null or r.id.instanceId = :instanceId)
               and (:datasourceName is null or r.id.datasourceName = :datasourceName)
             group by r.id.bucketStart
             order by r.id.bucketStart
            """)
    List<QuerySeriesRow> findSeries(
            @Param("from") Instant from,
            @Param("to") Instant to,
            @Param("environment") String environment,
            @Param("appName") String appName,
            @Param("instanceId") String instanceId,
            @Param("datasourceName") String datasourceName);

    interface QuerySeriesRow {
        Instant getBucketStart();
        long getExecutionCount();
        long getErrorCount();
        long getTotalDurationUs();
        long getMinDurationUs();
        long getMaxDurationUs();
    }
}

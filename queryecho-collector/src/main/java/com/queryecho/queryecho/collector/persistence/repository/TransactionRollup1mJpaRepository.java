package com.queryecho.queryecho.collector.persistence.repository;

import com.queryecho.queryecho.collector.persistence.entity.TransactionRollup1mEntity;
import com.queryecho.queryecho.collector.persistence.entity.TransactionRollup1mId;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TransactionRollup1mJpaRepository
        extends JpaRepository<TransactionRollup1mEntity, TransactionRollup1mId> {

    @Query("""
            select r.id.bucketStart as bucketStart,
                   sum(r.txCount) as transactionCount,
                   sum(r.commitCount) as commitCount,
                   sum(r.rollbackCount) as rollbackCount,
                   sum(r.unknownCount) as unknownCount,
                   sum(r.totalUs) as totalDurationUs,
                   min(r.minUs) as minDurationUs,
                   max(r.maxUs) as maxDurationUs
              from TransactionRollup1mEntity r, TransactionPatternEntity p
             where r.id.patternId = p.id
               and r.id.bucketStart >= :from and r.id.bucketStart < :to
               and (:environment is null or r.id.environment = :environment)
               and (:appName is null or p.appName = :appName)
             group by r.id.bucketStart
             order by r.id.bucketStart
            """)
    List<TransactionSeriesRow> findSeries(
            @Param("from") Instant from,
            @Param("to") Instant to,
            @Param("environment") String environment,
            @Param("appName") String appName);

    interface TransactionSeriesRow {
        Instant getBucketStart();
        long getTransactionCount();
        long getCommitCount();
        long getRollbackCount();
        long getUnknownCount();
        long getTotalDurationUs();
        long getMinDurationUs();
        long getMaxDurationUs();
    }
}

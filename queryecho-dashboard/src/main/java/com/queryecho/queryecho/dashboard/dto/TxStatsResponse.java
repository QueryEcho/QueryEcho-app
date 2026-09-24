package com.queryecho.queryecho.dashboard.dto;

import com.queryecho.queryecho.collector.repository.TxMetricRepository;

/** 커밋·롤백 건수와 비율을 제공하는 트랜잭션 요약 응답. */
public record TxStatsResponse(long total, long commitCount, long rollbackCount, long unknownCount,
                              double rollbackRate, double avgDurationUs) {

    public static TxStatsResponse from(TxMetricRepository.Stats stats) {
        return new TxStatsResponse(
                stats.total(),
                stats.commitCount(),
                stats.rollbackCount(),
                stats.unknownCount(),
                stats.rollbackRate(),
                stats.avgDurationUs());
    }
}

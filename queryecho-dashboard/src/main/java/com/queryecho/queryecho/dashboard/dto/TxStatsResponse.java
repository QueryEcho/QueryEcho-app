package com.queryecho.queryecho.dashboard.dto;

import com.queryecho.queryecho.collector.repository.TxMetricRepository;

/**
 * "정상 커밋 비율 대비 롤백 발생 비율" 요구사항에 대응하는 요약 응답.
 * 개별 트랜잭션 목록(TxMetricResponse)과 별도 엔드포인트로 분리한 이유는,
 * 대시보드 상단 요약 카드(rollback rate %)는 매번 전체 목록을 내려받아
 * 클라이언트에서 재계산할 필요 없이 가벼운 집계 하나만 조회하면 되도록 하기 위함이다.
 */
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

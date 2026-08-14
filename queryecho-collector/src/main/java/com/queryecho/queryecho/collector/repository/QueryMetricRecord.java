package com.queryecho.queryecho.collector.repository;

import java.time.Instant;
import java.util.List;

/**
 * 저장소에 실제로 쌓이는 "분석이 끝난" 쿼리 지표.
 *
 * sdk.dto.QueryMetricEvent와 굳이 필드가 비슷한데도 별도 타입으로 분리한 이유:
 *  - QueryMetricEvent는 SDK가 "관측한 그대로"의 원시 신호(raw signal)이고,
 *    QueryMetricRecord는 collector가 slow/repeatedCount 같은 "판단 결과"를 덧붙인
 *    가공된 결과물이다. 두 계층의 책임(관측 vs 판단)을 타입으로도 분리해두면,
 *    나중에 collector의 판단 로직(임계값, N+1 알고리즘 등)이 바뀌어도 sdk 쪽 이벤트
 *    스키마는 건드릴 필요가 없다.
 */
public record QueryMetricRecord(
        String sql,
        String normalizedSql,
        List<Object> params,
        long durationUs,
        Instant executedAt,
        String threadName,
        boolean slow,
        int recentRepeatCount
) {
}

package com.queryecho.queryecho.dashboard.dto;

import com.queryecho.queryecho.collector.repository.QueryMetricRecord;
import java.time.Instant;
import java.util.List;

/**
 * API 응답 전용 DTO. collector.repository.QueryMetricRecord를 그대로 노출하지 않고
 * 한 겹 더 감싸는 이유:
 *  - 내부 저장 모델(Record)과 외부 API 계약(Response)을 분리해두면, 나중에 저장 방식이
 *    바뀌거나(예: DB 테이블 컬럼 추가) 필드를 내부적으로 리팩터링해도 프론트엔드/외부
 *    소비자가 보는 JSON 스키마는 우리가 명시적으로 바꾸기 전까지 안정적으로 유지된다.
 */
public record QueryMetricResponse(
        String sql,
        String normalizedSql,
        List<Object> params,
        long durationUs,
        Instant executedAt,
        String threadName,
        boolean slow,
        int recentRepeatCount
) {
    public static QueryMetricResponse from(QueryMetricRecord record) {
        return new QueryMetricResponse(
                record.sql(),
                record.normalizedSql(),
                record.params(),
                record.durationUs(),
                record.executedAt(),
                record.threadName(),
                record.slow(),
                record.recentRepeatCount());
    }
}

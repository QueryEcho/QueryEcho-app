package com.queryecho.queryecho.dashboard.dto;

import java.time.Instant;
import java.util.List;

/**
 * 시간 구간(bucket)별 지연시간 분포 - 그라파나의 히트맵 패널과 같은 용도.
 *
 * 왜 서버에서 버킷팅까지 미리 해서 내려주는가 (원본 리스트를 주고 프론트에서 집계하지 않고)?
 *  - 프로토타입 저장소가 최대 2000건이라 지금 당장은 클라이언트 집계도 가능하지만,
 *    실제 서비스 규모(수만~수십만 건)를 가정하면 "집계는 서버가, 렌더링은 클라이언트가"
 *    책임을 나누는 편이 네트워크 페이로드와 클라이언트 연산량 모두에 유리하다.
 *    지금 인터페이스를 이 형태로 잡아두면 나중에 저장소가 실제 DB로 바뀌어
 *    집계 로직을 SQL GROUP BY로 옮기더라도 API 계약은 그대로 유지된다.
 */
public record LatencyHeatmapResponse(int bucketSeconds, List<Bucket> buckets) {

    public record Bucket(Instant bucketStart, int count, long avgDurationMs, long maxDurationMs) {
    }
}

package com.queryecho.queryecho.collector.event;

import com.queryecho.core.dto.QueryMetricEvent;
import java.util.List;

/** HTTP로 한 번에 전달된 쿼리 이벤트를 하나의 비동기·DB 작업 단위로 유지한다. */
public record QueryMetricBatch(List<QueryMetricEvent> events) {

    public QueryMetricBatch {
        events = List.copyOf(events);
    }
}

package com.queryecho.queryecho.collector.event;

import com.queryecho.core.dto.TxMetricEvent;
import java.util.List;

/** HTTP로 한 번에 전달된 트랜잭션 이벤트를 하나의 비동기·DB 작업 단위로 유지한다. */
public record TxMetricBatch(List<TxMetricEvent> events) {

    public TxMetricBatch {
        events = List.copyOf(events);
    }
}

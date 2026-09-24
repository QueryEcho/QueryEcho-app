package com.queryecho.queryecho.collector.dto;

/** 수집 API가 접수한 이벤트 수를 반환한다. */
public record IngestResponse(int accepted) {
}

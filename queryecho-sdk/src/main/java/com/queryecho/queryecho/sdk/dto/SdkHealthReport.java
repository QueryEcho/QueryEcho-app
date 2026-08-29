package com.queryecho.queryecho.sdk.dto;

import java.time.Instant;

/** SDK 전송 파이프라인의 누적 상태를 Collector에 주기적으로 알리는 보고서. */
public record SdkHealthReport(
        String appName,
        String environment,
        String instanceId,
        Instant reportedAt,
        long capturedTotal,
        long enqueuedTotal,
        long sentTotal,
        long droppedBufferTotal,
        long droppedSerializationTotal,
        long droppedTransportTotal,
        int queueSize,
        int queueCapacity,
        Instant lastSuccessfulSendAt
) {
    public long droppedTotal() {
        return droppedBufferTotal + droppedSerializationTotal + droppedTransportTotal;
    }
}

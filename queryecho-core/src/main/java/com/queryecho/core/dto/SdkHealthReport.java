package com.queryecho.core.dto;

import java.time.Instant;

/** SDK 전송 파이프라인이 Collector에 주기적으로 알리는 누적 상태. */
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

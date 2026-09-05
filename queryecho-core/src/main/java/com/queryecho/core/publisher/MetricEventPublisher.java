package com.queryecho.core.publisher;

/** 계측 코드와 전송 방식을 분리하는 비동기 발행 경계. */
@FunctionalInterface
public interface MetricEventPublisher {
    void publish(Object event);
}

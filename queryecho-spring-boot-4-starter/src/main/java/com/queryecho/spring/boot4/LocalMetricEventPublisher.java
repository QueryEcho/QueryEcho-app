package com.queryecho.spring.boot4;
import com.queryecho.core.publisher.MetricEventPublisher;
import org.springframework.context.ApplicationEventPublisher;

/** 같은 프로세스의 수집기로 이벤트를 전달하는 로컬 전송 기능. */
public class LocalMetricEventPublisher implements MetricEventPublisher {

    private final ApplicationEventPublisher applicationEventPublisher;

    public LocalMetricEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @Override
    public void publish(Object event) {
        applicationEventPublisher.publishEvent(event);
    }
}

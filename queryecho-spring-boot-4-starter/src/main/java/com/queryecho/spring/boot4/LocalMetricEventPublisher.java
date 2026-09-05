package com.queryecho.spring.boot4;
import com.queryecho.core.publisher.MetricEventPublisher;
import com.queryecho.transport.http.HttpMetricEventPublisher;

import org.springframework.context.ApplicationEventPublisher;

/**
 * SDK와 collector가 같은 JVM 안에 있을 때 쓰는 전송 구현체.
 * Spring의 ApplicationEventPublisher로 이벤트를 그대로 흘려보내면,
 * 같은 프로세스 안의 {@code @EventListener}가 받아서 처리한다.
 *
 * 이 방식의 성격:
 *  - publishEvent()는 동기 호출이다. 즉 리스너가 같은 스레드에서 바로 실행된다.
 *    그래서 collector 쪽 리스너에 @Async를 붙여 별도 스레드 풀로 넘기는 것이 전제되어 있다.
 *  - 네트워크를 타지 않으므로 유실이 없고 지연도 사실상 0이다. SDK 계측 로직 자체를
 *    검증하는 용도(queryecho-app 데모)에 적합하다.
 *  - 반대로 "타깃 애플리케이션은 별도 프로세스"라는 실제 사용 형태는 표현하지 못한다.
 *    그 경우는 {@link HttpMetricEventPublisher}를 쓴다.
 */
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

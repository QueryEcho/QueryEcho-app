package com.queryecho.queryecho.sdk.config;

import com.queryecho.queryecho.sdk.publisher.HttpMetricEventPublisher;
import com.queryecho.queryecho.sdk.publisher.LocalMetricEventPublisher;
import com.queryecho.queryecho.sdk.publisher.MetricEventPublisher;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * {@code queryecho.sdk.transport} 설정값에 따라 전송 구현체를 하나 골라 빈으로 등록한다.
 *
 * 왜 @ConditionalOnProperty 두 개가 아니라 if 분기 하나로 처리했는가?
 *  - 조건부 빈 두 개로 나누면 "설정값이 예상 밖일 때 빈이 아예 하나도 안 만들어져서
 *    애플리케이션이 알 수 없는 이유로 실패"하는 경우가 생긴다. 여기서는 명시적으로
 *    HTTP가 아니면 LOCAL로 떨어지게 해서, 어떤 설정이 들어와도 반드시 전송 구현체가
 *    하나는 존재하도록 보장한다.
 *
 * 소멸(close) 처리에 대해:
 *  - @Bean의 destroyMethod 기본값은 "추론(inferred)"이라, 빈이 AutoCloseable을 구현하고
 *    있으면 Spring이 종료 시점에 close()를 자동으로 호출해준다. HttpMetricEventPublisher는
 *    AutoCloseable이라 백그라운드 스레드 정리가 자동으로 이뤄지고,
 *    LocalMetricEventPublisher는 해당되지 않아 그냥 넘어간다. 그래서 여기에 destroyMethod를
 *    직접 명시하지 않았다(명시하면 close()가 없는 LOCAL 쪽에서 오히려 오류가 난다).
 */
@Configuration
public class MetricEventPublisherConfig {

    @Bean
    public MetricEventPublisher metricEventPublisher(QueryEchoSdkProperties properties,
                                                      ApplicationEventPublisher applicationEventPublisher) {
        if (properties.getTransport() == QueryEchoSdkProperties.Transport.HTTP) {
            return new HttpMetricEventPublisher(properties);
        }
        return new LocalMetricEventPublisher(applicationEventPublisher);
    }
}

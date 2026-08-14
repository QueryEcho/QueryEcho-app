package com.queryecho.queryecho.sdk.interceptor;

import com.queryecho.queryecho.sdk.publisher.MetricEventPublisher;
import javax.sql.DataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.Ordered;

/**
 * 컨테이너가 만든 DataSource 빈을 자동으로 {@link QueryEchoDataSourceProxy}로 감싼다.
 *
 * 왜 BeanPostProcessor를 선택했는가 (직접 @Bean으로 감싸는 대신)?
 *  - 사용자가 "이 DataSource를 감싸주세요"라고 별도로 설정하지 않아도, Spring Boot가
 *    자동 구성한 DataSource(H2, HikariCP 등 무엇이든)를 애플리케이션 코드/설정 변경 없이
 *    투명하게 계측하는 것이 SDK의 핵심 목표다. BeanPostProcessor는 컨테이너에 등록된
 *    "모든" DataSource 타입 빈을 초기화 시점에 가로채므로, 구체적인 DataSource 구현체를
 *    알 필요 없이 자동 계측(auto-instrumentation)을 구현할 수 있다.
 *
 * 왜 postProcessAfterInitialization인가 (BeforeInitialization이 아니라)?
 *  - HikariDataSource 같은 구현체는 초기화(@PostConstruct, afterPropertiesSet 등)가
 *    끝난 뒤에야 커넥션 풀이 실제로 준비된다. 초기화가 완전히 끝난 "이후"의 결과물을
 *    감싸야 안전하게 프록시를 씌울 수 있다.
 *
 * 이 클래스에는 @Component가 없다. 등록은
 * {@link com.queryecho.queryecho.sdk.config.QueryEchoSdkAutoConfiguration}이 전담한다
 * (등록 경로가 둘이면 스캔 범위에 들어오는 앱에서 빈이 두 번 만들어진다).
 * queryecho.sdk.enabled=false 처리도 자동 구성의 @ConditionalOnProperty가 담당하므로,
 * 여기까지 왔다는 것은 이미 "켜진" 상태라는 뜻이다.
 */
public class QueryEchoDataSourceBeanPostProcessor implements BeanPostProcessor, Ordered {

    // ObjectProvider로 지연 조회하는 이유는 자동 구성 클래스의 @Bean 메서드 주석 참고
    // (BeanPostProcessor는 아주 이른 시점에 만들어지므로, 여기서 publisher를 즉시
    //  주입받으면 publisher와 그 의존성들이 너무 일찍 생성된다).
    private final ObjectProvider<MetricEventPublisher> publisherProvider;

    public QueryEchoDataSourceBeanPostProcessor(ObjectProvider<MetricEventPublisher> publisherProvider) {
        this.publisherProvider = publisherProvider;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        // 이미 감싼 인스턴스를 다시 감싸는 것을 방지 (멀티 DataSource 빈 재등록 등 방어)
        if (bean instanceof DataSource dataSource && !(bean instanceof QueryEchoDataSourceProxy)) {
            return new QueryEchoDataSourceProxy(dataSource, publisherProvider.getObject());
        }
        return bean;
    }

    @Override
    public int getOrder() {
        // 다른 BeanPostProcessor가 DataSource를 추가로 감싸는 경우와 순서 충돌을 피하기 위해
        // 가장 마지막(LOWEST_PRECEDENCE)에 적용되도록 한다 - 즉 "최종적으로 완성된" DataSource를 감싼다.
        return Ordered.LOWEST_PRECEDENCE;
    }
}

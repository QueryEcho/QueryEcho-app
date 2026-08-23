package com.queryecho.queryecho.sdk.config;

import com.queryecho.queryecho.sdk.interceptor.QueryEchoDataSourceBeanPostProcessor;
import com.queryecho.queryecho.sdk.publisher.HttpMetricEventPublisher;
import com.queryecho.queryecho.sdk.publisher.LocalMetricEventPublisher;
import com.queryecho.queryecho.sdk.publisher.MetricEventPublisher;
import com.queryecho.queryecho.sdk.publisher.SanitizingMetricEventPublisher;
import com.queryecho.queryecho.sdk.transaction.TransactionMetricsAspect;
import javax.sql.DataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * QueryEcho SDK를 "의존성만 추가하면 켜지는" 라이브러리로 만들어 주는 자동 구성.
 * SDK의 빈들은 {@code com.queryecho.queryecho.sdk} 패키지에 있다. 그런데 이 SDK를 갖다 쓰는
 * 애플리케이션의 {@code @SpringBootApplication}은 자기 자신의 기준 패키지(예: com.example.app)만
 * 컴포넌트 스캔한다. 즉 jar를 의존성에 추가해도 스캔 범위 밖이라 SDK 빈이 단 하나도 등록되지
 * 않고, 아무 오류 없이 조용히 아무것도 수집되지 않는다.
 *
 * Spring Boot의 자동 구성은 컴포넌트 스캔과 완전히 별개의 경로다. 클래스패스에
 * {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}가
 * 있으면, 스캔 범위와 무관하게 여기 적힌 설정 클래스가 로드된다. 그래서 이 파일과 이 클래스가
 * "타깃 애플리케이션 코드를 한 줄도 수정하지 않는다"는 SDK의 핵심 약속을 실제로 지켜준다.
 *
 * ── 왜 SDK 클래스들에서 @Component / @Configuration을 걷어냈는가 ──
 * 등록 경로가 둘(컴포넌트 스캔 + 자동 구성)이 되면, 스캔 범위에 들어오는 애플리케이션에서는
 * 같은 빈이 두 번 등록된다. 특히 {@link TransactionMetricsAspect}가 두 개 생기면 어드바이저도
 * 둘이 되어 트랜잭션이 중복 계측된다. 등록 경로를 이 클래스 하나로 단일화해서 그 위험을 없앴다.
 */
@AutoConfiguration(beforeName = "org.springframework.boot.transaction.autoconfigure.TransactionAutoConfiguration")
@ConditionalOnClass(DataSource.class)
@ConditionalOnProperty(prefix = "queryecho.sdk", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(QueryEchoSdkProperties.class)
@EnableTransactionManagement(order = 100)
public class QueryEchoSdkAutoConfiguration {

    //전송 설정에 따라 Local 또는 Http를 선택한다.

    @Bean
    public MetricEventPublisher metricEventPublisher(QueryEchoSdkProperties properties,
                                                      ApplicationEventPublisher applicationEventPublisher) {
        // 그 앞을 보안 처리 Publisher로 한 번 더 감싼다.
        MetricEventPublisher transport = properties.getTransport() == QueryEchoSdkProperties.Transport.HTTP
                ? new HttpMetricEventPublisher(properties)
                : new LocalMetricEventPublisher(applicationEventPublisher);
        return new SanitizingMetricEventPublisher(transport, properties);
    }

    /**
     * DataSource를 자동으로 감싸는 BeanPostProcessor.(프록시로 바꿔주는)
     */
    @Bean
    public static QueryEchoDataSourceBeanPostProcessor queryEchoDataSourceBeanPostProcessor(
            ObjectProvider<MetricEventPublisher> publisherProvider,
            QueryEchoSdkProperties properties) {
        return new QueryEchoDataSourceBeanPostProcessor(publisherProvider, properties);
    }

    // 트랜잭션의 실행시간과 Commit-Rollback를 측정한다
    @Bean
    public TransactionMetricsAspect transactionMetricsAspect(MetricEventPublisher publisher,
                                                              QueryEchoSdkProperties properties) {
        return new TransactionMetricsAspect(publisher, properties);
    }
}

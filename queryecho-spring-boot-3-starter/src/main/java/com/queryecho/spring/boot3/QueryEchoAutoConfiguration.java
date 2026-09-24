package com.queryecho.spring.boot3;

import com.queryecho.spring.boot3.QueryEchoDataSourceBeanPostProcessor;
import com.queryecho.transport.http.HttpMetricEventPublisher;
import com.queryecho.spring.boot3.LocalMetricEventPublisher;
import com.queryecho.core.publisher.MetricEventPublisher;
import com.queryecho.sdk.SanitizingMetricEventPublisher;
import com.queryecho.spring.boot3.TransactionMetricsAspect;
import javax.sql.DataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/** 의존성 추가만으로 QueryEcho 계측 기능을 등록하는 자동 구성. */
@AutoConfiguration(beforeName = "org.springframework.boot.autoconfigure.transaction.TransactionAutoConfiguration")
@ConditionalOnClass(DataSource.class)
@ConditionalOnProperty(prefix = "queryecho.sdk", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(QueryEchoProperties.class)
@EnableTransactionManagement(order = 100)
public class QueryEchoAutoConfiguration {

    @Bean
    public MetricEventPublisher metricEventPublisher(QueryEchoProperties properties,
                                                      ApplicationEventPublisher applicationEventPublisher) {
        // 설정에 따라 로컬 또는 HTTP 전송을 선택하고 민감정보 필터를 적용한다.
        MetricEventPublisher transport = properties.getTransport() == QueryEchoProperties.Transport.HTTP
                ? new HttpMetricEventPublisher(properties)
                : new LocalMetricEventPublisher(applicationEventPublisher);
        return new SanitizingMetricEventPublisher(transport, properties);
    }

    /** 데이터소스 빈을 QueryEcho 계측 프록시로 자동 변환한다. */
    @Bean
    public static QueryEchoDataSourceBeanPostProcessor queryEchoDataSourceBeanPostProcessor(
            ObjectProvider<MetricEventPublisher> publisherProvider,
            QueryEchoProperties properties) {
        return new QueryEchoDataSourceBeanPostProcessor(publisherProvider, properties);
    }

    // 트랜잭션 실행시간과 커밋·롤백 결과를 수집한다.
    @Bean
    public TransactionMetricsAspect transactionMetricsAspect(MetricEventPublisher publisher,
                                                              QueryEchoProperties properties) {
        return new TransactionMetricsAspect(publisher, properties);
    }
}

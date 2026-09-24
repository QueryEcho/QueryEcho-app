package com.queryecho.spring.boot4;
import com.queryecho.jdbc.QueryEchoDataSourceProxy;
import com.queryecho.jdbc.QueryMetricSource;

import com.queryecho.core.publisher.MetricEventPublisher;
import com.queryecho.spring.boot4.QueryEchoProperties;
import javax.sql.DataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.Ordered;

/** 초기화된 데이터소스 빈을 QueryEcho 계측 프록시로 자동 변환한다. */
public class QueryEchoDataSourceBeanPostProcessor implements BeanPostProcessor, Ordered {

    // 게시자를 지연 조회해 빈의 조기 생성을 방지한다.
    private final ObjectProvider<MetricEventPublisher> publisherProvider;
    private final QueryEchoProperties properties;

    public QueryEchoDataSourceBeanPostProcessor(ObjectProvider<MetricEventPublisher> publisherProvider,
                                                QueryEchoProperties properties) {
        this.publisherProvider = publisherProvider;
        this.properties = properties;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        // 중복 계측과 제외 대상으로 지정한 데이터소스를 건너뛴다.
        if (bean instanceof DataSource dataSource && !(bean instanceof QueryEchoDataSourceProxy)
                && !properties.getExcludedDataSourceBeans().contains(beanName)) {
            QueryMetricSource source = new QueryMetricSource(
                    properties.getAppName(),
                    properties.getEnvironment(),
                    properties.getInstanceId(),
                    beanName,
                    properties.getDbType());
            return new QueryEchoDataSourceProxy(dataSource, publisherProvider.getObject(), source);
        }
        return bean;
    }

    @Override
    public int getOrder() {
        // 다른 후처리가 끝난 최종 데이터소스에 계측 프록시를 적용한다.
        return Ordered.LOWEST_PRECEDENCE;
    }
}

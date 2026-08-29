package com.queryecho.queryecho.sdk.config;

import com.queryecho.queryecho.sdk.web.QueryEchoHttpRequestInterceptor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Servlet 기반 Spring MVC 애플리케이션에서만 API 요청 컨텍스트 수집을 활성화한다. */
@AutoConfiguration(after = QueryEchoSdkAutoConfiguration.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(HandlerInterceptor.class)
public class QueryEchoWebMvcAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public QueryEchoHttpRequestInterceptor queryEchoHttpRequestInterceptor() {
        return new QueryEchoHttpRequestInterceptor();
    }

    @Bean
    public WebMvcConfigurer queryEchoWebMvcConfigurer(QueryEchoHttpRequestInterceptor interceptor) {
        return new WebMvcConfigurer() {
            @Override
            public void addInterceptors(InterceptorRegistry registry) {
                registry.addInterceptor(interceptor);
            }
        };
    }
}

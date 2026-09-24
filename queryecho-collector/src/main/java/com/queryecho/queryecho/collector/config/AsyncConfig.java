package com.queryecho.queryecho.collector.config;

import com.queryecho.queryecho.collector.telemetry.CollectionTelemetryService;
import java.util.concurrent.Executor;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/** 수집 이벤트 저장을 담당하는 제한형 비동기 작업 풀. */
@Configuration
@EnableAsync
@EnableScheduling
public class AsyncConfig implements AsyncConfigurer {

    private final CollectionTelemetryService telemetry;

    public AsyncConfig(CollectionTelemetryService telemetry) {
        this.telemetry = telemetry;
    }

    @Bean(name = "collectorTaskExecutor")
    public ThreadPoolTaskExecutor collectorTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // 작은 제한형 큐로 작업자 확장을 빠르게 유도하고 과도한 적재를 방지한다.
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("queryecho-collector-");
        executor.setTaskDecorator(telemetry::decorateAsyncTask);
        executor.initialize();
        telemetry.bindExecutor(executor);
        return executor;
    }

    @Override
    public Executor getAsyncExecutor() {
        return collectorTaskExecutor();
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        // 호출자에게 전달되지 않는 비동기 처리 오류를 로그로 남긴다.
        return (ex, method, params) ->
                org.slf4j.LoggerFactory.getLogger(AsyncConfig.class)
                        .error("[QueryEcho] Uncaught async error in {}", method, ex);
    }
}

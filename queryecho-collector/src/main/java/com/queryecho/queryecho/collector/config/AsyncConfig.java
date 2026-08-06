package com.queryecho.queryecho.collector.config;

import java.util.concurrent.Executor;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * collector 계층의 이벤트 리스너({@code @Async @EventListener})가 사용할 전용 스레드 풀.
 *
 * 왜 전용 Executor를 따로 두는가 (Spring 기본 SimpleAsyncTaskExecutor를 쓰지 않고)?
 *  - 기본 실행기는 호출마다 새 스레드를 무제한으로 만들기 때문에, 트래픽이 몰리면
 *    "지표를 수집하려고 만든 스레드"가 무한정 늘어나 오히려 애플리케이션 자체의
 *    스레드/메모리 자원을 위협할 수 있다. corePoolSize/maxPoolSize/queueCapacity를
 *    명시적으로 제한한 ThreadPoolTaskExecutor를 씀으로써, 모니터링 기능이
 *    "관찰 대상인 애플리케이션보다 더 큰 부하를 유발하는" 역설을 방지한다.
 *  - 이 풀을 애플리케이션의 다른 @Async 작업과 분리해서 이름(queryecho-collector-)까지
 *    따로 붙인 이유는, 스레드 덤프나 로그에서 "이건 QueryEcho가 만든 스레드"임을
 *    한눈에 구분하기 위함이다 (운영 중 문제 발생 시 원인 추적 용이).
 */
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

    @Override
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(1000);
        executor.setThreadNamePrefix("queryecho-collector-");
        executor.initialize();
        return executor;
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        // @Async 메서드는 리턴 타입이 void면 예외가 호출자에게 전파되지 않고 그냥 사라진다.
        // 지표 수집 로직에서 예외가 나도 애플리케이션에는 영향을 주면 안 되지만(모니터링 부작용 방지),
        // 그렇다고 조용히 삼켜버리면 "왜 슬로우 쿼리가 하나도 안 잡히지?" 같은 상황을 디버깅할 수 없다.
        // 그래서 최소한 로그로는 남기도록 핸들러를 등록한다.
        return (ex, method, params) ->
                org.slf4j.LoggerFactory.getLogger(AsyncConfig.class)
                        .error("[QueryEcho] Uncaught async error in {}", method, ex);
    }
}

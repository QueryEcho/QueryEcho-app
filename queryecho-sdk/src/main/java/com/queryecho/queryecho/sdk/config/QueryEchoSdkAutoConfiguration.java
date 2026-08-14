package com.queryecho.queryecho.sdk.config;

import com.queryecho.queryecho.sdk.interceptor.QueryEchoDataSourceBeanPostProcessor;
import com.queryecho.queryecho.sdk.publisher.HttpMetricEventPublisher;
import com.queryecho.queryecho.sdk.publisher.LocalMetricEventPublisher;
import com.queryecho.queryecho.sdk.publisher.MetricEventPublisher;
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
 *
 * ── 왜 이 클래스가 반드시 필요한가 ──
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
 *
 * ── 트랜잭션 어드바이저 순서에 대한 주의 ──
 * {@code @EnableTransactionManagement(order = 100)}을 여기서 선언한다.
 * {@link TransactionMetricsAspect}는 트랜잭션이 이미 시작된 "안쪽"에서 실행되어야 커밋/롤백
 * 콜백을 등록할 수 있는데, Spring Boot 기본값은 트랜잭션 어드바이저를 가장 안쪽
 * (Ordered.LOWEST_PRECEDENCE)에 두기 때문에 그대로면 우리 Aspect가 바깥이 되어버린다.
 * LOWEST_PRECEDENCE보다 더 안쪽 값은 존재하지 않으므로, 어드바이저를 바깥으로 끌어내는 것
 * 외에는 방법이 없다.
 *
 * 따라서 타깃 애플리케이션이 자체적으로 {@code @EnableTransactionManagement}를 선언하면서
 * order를 200 이상으로 지정하면 순서가 뒤집혀 트랜잭션 지표가 수집되지 않는다.
 * 이 경우를 조용히 넘기지 않도록 {@link TransactionMetricsAspect}가 최초 1회 경고 로그를 남긴다.
 */
@AutoConfiguration(beforeName = "org.springframework.boot.transaction.autoconfigure.TransactionAutoConfiguration")
@ConditionalOnClass(DataSource.class)
@ConditionalOnProperty(prefix = "queryecho.sdk", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(QueryEchoSdkProperties.class)
@EnableTransactionManagement(order = 100)
public class QueryEchoSdkAutoConfiguration {

    /**
     * 전송 구현체를 설정값에 따라 하나만 고른다.
     *
     * 왜 @ConditionalOnProperty 두 개로 나누지 않고 if 분기 하나로 처리했는가?
     *  - 조건부 빈 두 개로 나누면 설정값이 예상 밖일 때 빈이 아예 하나도 만들어지지 않아
     *    애플리케이션이 알 수 없는 이유로 실패한다. 여기서는 HTTP가 아니면 LOCAL로 떨어지게 해서
     *    어떤 값이 들어와도 전송 구현체가 반드시 하나는 존재하도록 보장한다.
     *
     * destroyMethod를 명시하지 않은 이유:
     *  - @Bean의 기본값이 "추론"이라, AutoCloseable을 구현한 HttpMetricEventPublisher는
     *    종료 시 close()가 자동 호출되어 백그라운드 스레드가 정리되고,
     *    LocalMetricEventPublisher는 해당되지 않아 그냥 넘어간다.
     *    (명시하면 close()가 없는 LOCAL 쪽에서 오히려 오류가 난다.)
     */
    @Bean
    public MetricEventPublisher metricEventPublisher(QueryEchoSdkProperties properties,
                                                      ApplicationEventPublisher applicationEventPublisher) {
        if (properties.getTransport() == QueryEchoSdkProperties.Transport.HTTP) {
            return new HttpMetricEventPublisher(properties);
        }
        return new LocalMetricEventPublisher(applicationEventPublisher);
    }

    /**
     * DataSource를 자동으로 감싸는 BeanPostProcessor.
     *
     * 왜 static 메서드인가?
     *  - BeanPostProcessor는 컨테이너 초기화 아주 이른 시점에 만들어져야 한다. static이 아니면
     *    이 빈을 만들기 위해 설정 클래스 자체를 먼저 인스턴스화해야 하고, 그 과정에서 설정
     *    클래스가 의존하는 빈들까지 너무 일찍 생성되어 "이 빈은 모든 BeanPostProcessor의 처리
     *    대상이 되지 못했다"는 경고가 뜬다. static으로 두면 설정 클래스 인스턴스 없이 등록된다.
     *
     * 왜 MetricEventPublisher를 ObjectProvider로 받는가?
     *  - 같은 이유로, 여기서 publisher를 직접 주입받으면 컨테이너가 뜨자마자 publisher가
     *    만들어진다(HTTP 모드면 백그라운드 스레드까지 그때 뜬다). ObjectProvider로 감싸두면
     *    실제로 DataSource를 감쌀 때 처음 조회되므로 초기화 순서 부담이 줄어든다.
     */
    @Bean
    public static QueryEchoDataSourceBeanPostProcessor queryEchoDataSourceBeanPostProcessor(
            ObjectProvider<MetricEventPublisher> publisherProvider) {
        return new QueryEchoDataSourceBeanPostProcessor(publisherProvider);
    }

    @Bean
    public TransactionMetricsAspect transactionMetricsAspect(MetricEventPublisher publisher) {
        return new TransactionMetricsAspect(publisher);
    }
}

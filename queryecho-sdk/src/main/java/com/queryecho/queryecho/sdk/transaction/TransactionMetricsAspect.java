package com.queryecho.queryecho.sdk.transaction;

import com.queryecho.queryecho.sdk.dto.TxMetricEvent;
import com.queryecho.queryecho.sdk.dto.TxStatus;
import com.queryecho.queryecho.sdk.publisher.MetricEventPublisher;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * @Transactional 메서드의 시작~커밋/롤백까지 소요 시간을 측정하는 AOP Aspect.
 *
 * 왜 AOP로 접근했는가 (PlatformTransactionManager를 직접 감싸는 대신)?
 *  - PlatformTransactionManager를 감싸는 방식은 프로그래밍적 트랜잭션(TransactionTemplate)까지
 *    커버할 수 있어 더 일반적이지만, 구현이 훨씬 복잡하고 Spring Data JPA가 내부적으로
 *    만드는 트랜잭션 매니저 빈 교체 문제가 얽힌다. 반면 이 프로젝트에서 다루는 트랜잭션은
 *    대부분 @Transactional 애노테이션 기반이므로, 프로토타입 단계에서는 AOP가
 *    훨씬 적은 코드로 "트랜잭션 경계 = 메서드 경계"를 그대로 재사용할 수 있어 선택했다.
 *
 * 왜 TransactionSynchronizationManager.registerSynchronization을 쓰는가
 *   (그냥 메서드 실행 시간을 재는 게 아니라)?
 *  - @Transactional(propagation = REQUIRED)가 중첩 호출되면 실제 커밋/롤백은
 *    "가장 바깥쪽" 메서드가 끝날 때 딱 한 번 일어난다. 각 메서드 리턴 시점을 그냥 재면
 *    중첩 트랜잭션마다 중복으로 이벤트가 발생하거나, 커밋 전에 종료 시각을 측정하는 오류가 생긴다.
 *    TransactionSynchronization.afterCompletion(status)는 스프링이 실제로 커밋/롤백을
 *    끝낸 "직후"에 정확히 한 번 호출해주므로, 실제 물리 트랜잭션의 결과와 정확히 일치한다.
 *
 * 왜 ThreadLocal로 "이미 등록했는지" 추적하는가?
 *  - 중첩 호출(A가 B를 호출, 둘 다 @Transactional REQUIRED)에서 Aspect는 두 번 실행되지만
 *    물리 트랜잭션은 하나뿐이다. 가장 바깥쪽 호출에서만 measuring/synchronization을
 *    등록해야 "트랜잭션 1개 = 이벤트 1개"가 보장된다.
 *
 * 이 Aspect가 트랜잭션 어드바이저보다 안쪽에서 실행되어야 하는 이유는
 * {@link com.queryecho.queryecho.sdk.config.QueryEchoSdkAutoConfiguration}의 주석 참고.
 * (@Component가 없는 이유도 같은 곳에 적어두었다 - 등록은 자동 구성이 전담한다.)
 */
@Aspect
@Order(200)
public class TransactionMetricsAspect {

    private static final Logger log = LoggerFactory.getLogger(TransactionMetricsAspect.class);

    private static final ThreadLocal<Boolean> ALREADY_TRACKING = ThreadLocal.withInitial(() -> false);

    /** 순서가 뒤집힌 환경에서 매 호출마다 경고가 쏟아지지 않도록 최초 1회만 남긴다. */
    private static final AtomicBoolean ORDER_WARNING_LOGGED = new AtomicBoolean();

    private final MetricEventPublisher publisher;

    public TransactionMetricsAspect(MetricEventPublisher publisher) {
        this.publisher = publisher;
    }

    @Around("@annotation(org.springframework.transaction.annotation.Transactional) "
            + "|| @within(org.springframework.transaction.annotation.Transactional)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        // 트랜잭션 어드바이저가 이미 물리 트랜잭션을 시작해준 뒤에만 여기 도달한다(order 설정 덕분).
        // 그런데 타깃 애플리케이션이 자체 @EnableTransactionManagement로 order를 200 이상으로
        // 잡아버리면 순서가 뒤집혀 여기서 동기화가 비활성 상태가 된다. 그냥 건너뛰면 트랜잭션
        // 지표가 "이유 없이 하나도 안 쌓이는" 상태가 되어 원인을 찾기 어려우므로, 최초 1회
        // 경고를 남겨 진단 가능하게 만든다.
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            if (ORDER_WARNING_LOGGED.compareAndSet(false, true)) {
                log.warn("[QueryEcho] {} 호출 시점에 활성 트랜잭션이 없어 트랜잭션 지표를 수집하지 못했습니다. "
                                + "타깃 애플리케이션이 @EnableTransactionManagement(order >= 200)으로 "
                                + "QueryEcho의 order=100 설정을 덮어썼는지 확인하세요.",
                        joinPoint.getSignature().toShortString());
            }
            return joinPoint.proceed();
        }

        // 중첩 호출(REQUIRED 전파)에서는 물리 트랜잭션이 하나뿐이므로 가장 바깥에서만 계측한다.
        if (ALREADY_TRACKING.get()) {
            return joinPoint.proceed();
        }

        ALREADY_TRACKING.set(true);
        long start = System.nanoTime();
        String transactionName = joinPoint.getSignature().toShortString();
        // 롤백을 유발한 예외 메시지는 afterCompletion(status) 콜백에서는 알 수 없으므로,
        // try/catch에서 잡은 예외를 AtomicReference에 담아 클로저로 넘겨준다.
        AtomicReference<String> failureReason = new AtomicReference<>();

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                ALREADY_TRACKING.remove();
                // 마이크로초로 기록하는 이유는 QueryMetricEvent.durationUs 주석 참고.
                long durationUs = (System.nanoTime() - start) / 1_000;
                TxStatus txStatus = status == TransactionSynchronization.STATUS_COMMITTED
                        ? TxStatus.COMMIT
                        : TxStatus.ROLLBACK;
                publisher.publish(new TxMetricEvent(
                        transactionName,
                        durationUs,
                        txStatus,
                        Instant.now(),
                        Thread.currentThread().getName(),
                        failureReason.get()));
            }
        });

        try {
            return joinPoint.proceed();
        } catch (Throwable ex) {
            failureReason.set(ex.getClass().getSimpleName() + ": " + ex.getMessage());
            throw ex;
        }
    }
}

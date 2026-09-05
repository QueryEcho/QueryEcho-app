package com.queryecho.spring.boot3;
import com.queryecho.jdbc.TransactionContext;

import com.queryecho.spring.boot3.QueryEchoProperties;
import com.queryecho.core.dto.TxMetricEvent;
import com.queryecho.core.dto.TxStatus;
import com.queryecho.core.publisher.MetricEventPublisher;
import com.queryecho.jdbc.HttpRequestContext;
import java.time.Instant;
import java.util.UUID;
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

/** {@code @Transactional} 메서드가 참여한 실제 물리 트랜잭션의 완료 결과를 수집한다. */
@Aspect
@Order(200)
public class TransactionMetricsAspect {

    private static final Logger log = LoggerFactory.getLogger(TransactionMetricsAspect.class);
    private static final AtomicBoolean ORDER_WARNING_LOGGED = new AtomicBoolean();

    private final MetricEventPublisher publisher;
    private final QueryEchoProperties properties;

    public TransactionMetricsAspect(MetricEventPublisher publisher, QueryEchoProperties properties) {
        this.publisher = publisher;
        this.properties = properties;
    }

    @Around("@annotation(org.springframework.transaction.annotation.Transactional) "
            + "|| @within(org.springframework.transaction.annotation.Transactional)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        if (isExcluded(joinPoint)) {
            return joinPoint.proceed();
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            if (ORDER_WARNING_LOGGED.compareAndSet(false, true)) {
                log.warn("[QueryEcho] {} 호출 시점에 활성 트랜잭션이 없어 트랜잭션 지표를 수집하지 "
                                + "못했습니다. 트랜잭션 어드바이저 순서를 확인하세요.",
                        joinPoint.getSignature().toShortString());
            }
            return joinPoint.proceed();
        }

        // REQUIRED 중첩 호출에서는 현재 물리 트랜잭션의 synchronization 목록에 이미
        // QueryEcho marker가 있다. REQUIRES_NEW는 바깥 synchronization을 suspend하므로
        // 새 목록에는 marker가 없고 별도 transactionId를 생성하게 된다.
        boolean alreadyTracking = TransactionSynchronizationManager.getSynchronizations().stream()
                .anyMatch(QueryEchoTransactionSynchronization.class::isInstance);
        if (alreadyTracking) {
            return joinPoint.proceed();
        }

        UUID transactionId = UUID.randomUUID();
        long startNanos = System.nanoTime();
        String transactionName = joinPoint.getSignature().toLongString();
        AtomicReference<Failure> failure = new AtomicReference<>();
        HttpRequestContext.Snapshot request = HttpRequestContext.current();

        TransactionContext.push(transactionId);
        try {
            TransactionSynchronizationManager.registerSynchronization(
                    new QueryEchoTransactionSynchronization(
                            transactionId, transactionName, startNanos, failure, request));
        } catch (RuntimeException ex) {
            TransactionContext.remove(transactionId);
            throw ex;
        }

        try {
            return joinPoint.proceed();
        } catch (Throwable ex) {
            failure.set(new Failure(ex.getClass().getName(), ex.getMessage()));
            throw ex;
        }
    }

    private boolean isExcluded(ProceedingJoinPoint joinPoint) {
        String className = joinPoint.getTarget() == null
                ? joinPoint.getSignature().getDeclaringTypeName()
                : joinPoint.getTarget().getClass().getName();
        return properties.getExcludedTransactionPackages().stream()
                .filter(prefix -> prefix != null && !prefix.isBlank())
                .anyMatch(className::startsWith);
    }

    private final class QueryEchoTransactionSynchronization implements TransactionSynchronization {

        private final UUID transactionId;
        private final String transactionName;
        private final long startNanos;
        private final AtomicReference<Failure> failure;
        private final HttpRequestContext.Snapshot request;

        private QueryEchoTransactionSynchronization(UUID transactionId,
                                                    String transactionName,
                                                    long startNanos,
                                                    AtomicReference<Failure> failure,
                                                    HttpRequestContext.Snapshot request) {
            this.transactionId = transactionId;
            this.transactionName = transactionName;
            this.startNanos = startNanos;
            this.failure = failure;
            this.request = request;
        }

        @Override
        public void afterCompletion(int completionStatus) {
            try {
                TxStatus status = switch (completionStatus) {
                    case TransactionSynchronization.STATUS_COMMITTED -> TxStatus.COMMIT;
                    case TransactionSynchronization.STATUS_ROLLED_BACK -> TxStatus.ROLLBACK;
                    default -> TxStatus.UNKNOWN;
                };
                Failure cause = failure.get();
                publisher.publish(new TxMetricEvent(
                        transactionId,
                        properties.getAppName(),
                        properties.getEnvironment(),
                        properties.getInstanceId(),
                        transactionName,
                        Math.max(0, (System.nanoTime() - startNanos) / 1_000),
                        status,
                        Instant.now(),
                        Thread.currentThread().getName(),
                        cause == null ? null : cause.type(),
                        cause == null ? null : cause.message(),
                        request == null ? null : request.traceId(),
                        request == null ? null : request.requestId(),
                        request == null ? null : request.httpMethod(),
                        request == null ? null : request.httpPath(),
                        request == null ? null : request.handlerName()));
            } finally {
                TransactionContext.remove(transactionId);
            }
        }
    }

    private record Failure(String type, String message) {
    }
}

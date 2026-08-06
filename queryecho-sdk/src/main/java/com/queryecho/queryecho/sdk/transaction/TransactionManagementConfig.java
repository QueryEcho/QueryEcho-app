package com.queryecho.queryecho.sdk.transaction;

import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * @Transactional 프록시 어드바이저의 순서(order)를 명시적으로 고정하기 위한 설정.
 *
 * 왜 이게 필요한가?
 *  - Spring AOP는 order 값이 "작을수록" 바깥쪽(먼저 진입 / 나중에 빠져나감)에서 감싼다.
 *    Spring Boot가 자동 구성하는 트랜잭션 어드바이저는 기본값으로 Ordered.LOWEST_PRECEDENCE
 *    (가장 안쪽)를 쓰는데, 우리 {@link TransactionMetricsAspect}도 우리가 별도로 order를
 *    지정하지 않으면 마찬가지로 LOWEST_PRECEDENCE가 되어 두 어드바이스의 안/바깥 순서가
 *    보장되지 않는다.
 *  - TransactionMetricsAspect는 "트랜잭션이 실제로 시작된 이후"에 실행되어야
 *    TransactionSynchronizationManager.isSynchronizationActive()가 true를 반환하고,
 *    커밋/롤백 콜백을 정확히 등록할 수 있다. 즉 우리 Aspect가 트랜잭션 어드바이저보다
 *    "더 안쪽(order 값이 더 큼)"에 있어야 한다.
 *  - 그래서 여기서 트랜잭션 어드바이저의 order를 100으로 고정하고,
 *    TransactionMetricsAspect에는 @Order(200)을 붙여 확실히 그 안쪽에서 실행되게 한다.
 *    (이 @Configuration이 존재하면 Spring Boot의 자동 트랜잭션 관리 설정은 스스로 비활성화된다 -
 *    TransactionAutoConfiguration이 "이미 트랜잭션 관리가 활성화되어 있으면" 개입하지 않기 때문.)
 */
@Configuration
@EnableTransactionManagement(order = 100)
public class TransactionManagementConfig {
}

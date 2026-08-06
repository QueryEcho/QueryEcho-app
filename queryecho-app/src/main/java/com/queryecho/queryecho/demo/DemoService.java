package com.queryecho.queryecho.demo;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * QueryEcho 자체 기능이 아니라, 프로토타입이 실제로 잡아내는 모습을 눈으로 확인하기 위한
 * "부하 발생기"다. com.queryecho.queryecho.sdk/collector/dashboard 세 계층은 이 패키지가
 * 없어도 완전히 동작하며, 운영 배포 시에는 demo 패키지 전체를 삭제하면 된다
 * (README의 "다음 단계"에도 명시).
 *
 * 왜 H2를 감시 대상 DB로 같이 쓰는가?
 *  - 외부 DB 설치 없이 `./gradlew bootRun` 한 번으로 슬로우 쿼리/N+1/롤백 시나리오를
 *    전부 재현할 수 있어야 이 프로토타입을 처음 받는 사람이 5분 안에 "동작하는 걸" 볼 수 있다.
 */
@Service
public class DemoService {

    private final JdbcTemplate jdbcTemplate;

    public DemoService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public void seed() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS demo_item (
                    id INT PRIMARY KEY,
                    name VARCHAR(100)
                )
                """);
        for (int i = 1; i <= 20; i++) {
            jdbcTemplate.update(
                    "MERGE INTO demo_item (id, name) KEY (id) VALUES (?, ?)",
                    i, "item-" + i);
        }
    }

    @Transactional
    public void runFastQuery() {
        jdbcTemplate.queryForObject("SELECT COUNT(*) FROM demo_item", Integer.class);
    }

    /**
     * 왜 Thread.sleep으로 흉내내지 않고 진짜로 무거운 쿼리를 실행하는가?
     *  - Thread.sleep()은 애플리케이션 스레드를 멈출 뿐 실제 JDBC 실행 시간이 아니므로,
     *    "인터셉터가 정말 DB 왕복 시간을 재고 있다"는 걸 증명하지 못한다.
     *    H2의 SYSTEM_RANGE 테이블 함수로 대량의 행을 실제로 스캔시켜 진짜 느린 쿼리를 만든다.
     */
    @Transactional
    public void runSlowQuery() {
        jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM SYSTEM_RANGE(1, 20000000) t1, SYSTEM_RANGE(1, 5) t2",
                Long.class);
    }

    /**
     * 반복문 안에서 개별 SELECT를 날려 전형적인 N+1 패턴을 재현한다.
     */
    @Transactional
    public void runNPlusOneQueries() {
        for (int i = 1; i <= 10; i++) {
            jdbcTemplate.queryForObject(
                    "SELECT name FROM demo_item WHERE id = ?", String.class, i);
        }
    }

    /**
     * 트랜잭션 롤백 시나리오 - insert 후 강제로 예외를 던져
     * TransactionMetricsAspect가 ROLLBACK + failureReason을 기록하는지 확인한다.
     */
    @Transactional
    public void runFailingTransaction() {
        jdbcTemplate.update("MERGE INTO demo_item (id, name) KEY (id) VALUES (?, ?)", 999, "will-be-rolled-back");
        throw new IllegalStateException("의도적으로 발생시킨 실패 - 롤백 모니터링 데모용");
    }
}

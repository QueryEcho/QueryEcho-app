package com.queryecho.queryecho.demo;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 쿼리 지연, 반복 조회, 롤백 상황을 재현하는 데모 기능. */
@Service
@ConditionalOnProperty(prefix = "queryecho.demo", name = "enabled", havingValue = "true", matchIfMissing = true)
public class DemoService {

    private final JdbcTemplate jdbcTemplate;

    public DemoService(@Qualifier("demoJdbcTemplate") JdbcTemplate jdbcTemplate) {
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

    /** 대량 행 조회로 느린 JDBC 실행을 재현한다. */
    @Transactional
    public void runSlowQuery() {
        jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM SYSTEM_RANGE(1, 20000000) t1, SYSTEM_RANGE(1, 5) t2",
                Long.class);
    }

    /** 반복 개별 조회로 N+1 패턴을 재현한다. */
    @Transactional
    public void runNPlusOneQueries() {
        for (int i = 1; i <= 10; i++) {
            jdbcTemplate.queryForObject(
                    "SELECT name FROM demo_item WHERE id = ?", String.class, i);
        }
    }

    /** 저장 후 예외를 발생시켜 트랜잭션 롤백 수집을 검증한다. */
    @Transactional
    public void runFailingTransaction() {
        jdbcTemplate.update("MERGE INTO demo_item (id, name) KEY (id) VALUES (?, ?)", 999, "will-be-rolled-back");
        throw new IllegalStateException("의도적으로 발생시킨 실패 - 롤백 모니터링 데모용");
    }
}

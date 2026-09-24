package com.queryecho.queryecho.demo;

import org.springframework.http.ResponseEntity;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 쿼리와 트랜잭션 수집 동작을 확인하는 데모 API. */
@RestController
@RequestMapping("/api/v1/demo")
@ConditionalOnProperty(prefix = "queryecho.demo", name = "enabled", havingValue = "true", matchIfMissing = true)
public class DemoController {

    private final DemoService demoService;

    public DemoController(DemoService demoService) {
        this.demoService = demoService;
    }

    @PostMapping("/seed")
    public ResponseEntity<Void> seed() {
        demoService.seed();
        return ResponseEntity.ok().build();
    }

    @PostMapping("/fast")
    public ResponseEntity<Void> fast() {
        demoService.runFastQuery();
        return ResponseEntity.ok().build();
    }

    @PostMapping("/slow")
    public ResponseEntity<Void> slow() {
        demoService.runSlowQuery();
        return ResponseEntity.ok().build();
    }

    @PostMapping("/n-plus-one")
    public ResponseEntity<Void> nPlusOne() {
        demoService.runNPlusOneQueries();
        return ResponseEntity.ok().build();
    }

    @PostMapping("/failing-transaction")
    public ResponseEntity<String> failingTransaction() {
        try {
            demoService.runFailingTransaction();
            return ResponseEntity.ok().build();
        } catch (IllegalStateException ex) {
            // 의도된 롤백 결과를 데모 응답으로 반환한다.
            return ResponseEntity.ok("Rolled back as expected: " + ex.getMessage());
        }
    }
}

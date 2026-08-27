package com.queryecho.queryecho.demo;

import org.springframework.http.ResponseEntity;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 프로토타입 시연용 엔드포인트. /api/v1/metrics/* 로 관측할 트래픽을 만들어낸다.
 * 실제 서비스 코드가 아니므로 dashboard 패키지의 조회 API와 경로 프리픽스를
 * /api/v1/demo 로 명확히 구분해서, "이건 QueryEcho 기능이 아니라 테스트용"임을 드러낸다.
 */
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
            // 데모 목적상 예외를 그대로 500으로 흘려보내지 않고, 의도된 실패임을 응답에 남긴다.
            return ResponseEntity.ok("Rolled back as expected: " + ex.getMessage());
        }
    }
}

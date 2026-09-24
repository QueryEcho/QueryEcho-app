package com.queryecho.queryecho;

import com.queryecho.queryecho.collector.config.QueryEchoCollectorProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/** Collector와 대시보드를 실행하고 SDK 기능은 자동 구성으로 등록한다. */
@SpringBootApplication(scanBasePackages = {
        "com.queryecho.queryecho.collector",
        "com.queryecho.queryecho.dashboard",
        "com.queryecho.queryecho.demo",
        "com.queryecho.queryecho.config"
})
@EnableConfigurationProperties(QueryEchoCollectorProperties.class)
public class QueryEchoApplication {

    public static void main(String[] args) {
        SpringApplication.run(QueryEchoApplication.class, args);
    }

}

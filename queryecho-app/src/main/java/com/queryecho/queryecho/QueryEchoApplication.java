package com.queryecho.queryecho;

import com.queryecho.queryecho.collector.config.QueryEchoCollectorProperties;
import com.queryecho.queryecho.sdk.config.QueryEchoSdkProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({QueryEchoSdkProperties.class, QueryEchoCollectorProperties.class})
public class QueryEchoApplication {

    public static void main(String[] args) {
        SpringApplication.run(QueryEchoApplication.class, args);
    }

}

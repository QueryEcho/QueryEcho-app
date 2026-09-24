package com.queryecho.spring.boot3;
import com.queryecho.core.config.SdkOptions;
import org.springframework.boot.context.properties.ConfigurationProperties;
/** {@code queryecho.sdk.*} 설정을 SDK 공통 옵션에 연결한다. */
@ConfigurationProperties(prefix = "queryecho.sdk")
public class QueryEchoProperties extends SdkOptions {
}

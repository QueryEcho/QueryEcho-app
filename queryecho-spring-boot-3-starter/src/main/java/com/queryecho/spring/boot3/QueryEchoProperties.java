package com.queryecho.spring.boot3;
import com.queryecho.core.config.SdkOptions;
import org.springframework.boot.context.properties.ConfigurationProperties;
/** 기존 queryecho.sdk.* 설정을 공통 Java 설정에 바인딩한다. */
@ConfigurationProperties(prefix = "queryecho.sdk")
public class QueryEchoProperties extends SdkOptions {
}

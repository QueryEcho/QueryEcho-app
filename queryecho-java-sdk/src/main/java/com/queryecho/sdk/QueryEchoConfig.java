package com.queryecho.sdk;

import com.queryecho.core.config.SdkOptions;

/** 순수 Java는 HTTP를 기본값으로 사용한다. Spring 설정은 Starter가 담당한다. */
public final class QueryEchoConfig extends SdkOptions {
    public QueryEchoConfig() {
        setTransport(Transport.HTTP);
    }
}

package com.queryecho.transport.http;

/** HTTP 전송과 JSON 구현을 분리한다. */
@FunctionalInterface
public interface EventEncoder {
    String encode(Object event) throws Exception;
}

package com.queryecho.queryecho.sdk.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;

class QueryEchoHttpRequestInterceptorTest {

    private final QueryEchoHttpRequestInterceptor interceptor = new QueryEchoHttpRequestInterceptor();

    @AfterEach
    void clearContext() {
        HttpRequestContext.clear();
    }

    @Test
    void capturesRouteTemplateAndHandlerForCurrentRequest() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/orders/42");
        request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/api/orders/{id}");
        MockHttpServletResponse response = new MockHttpServletResponse();
        HandlerMethod handler = new HandlerMethod(new TestController(),
                TestController.class.getDeclaredMethod("order"));

        interceptor.preHandle(request, response, handler);

        HttpRequestContext.Snapshot context = HttpRequestContext.current();
        assertThat(context.httpMethod()).isEqualTo("GET");
        assertThat(context.httpPath()).isEqualTo("/api/orders/{id}");
        assertThat(context.handlerName()).endsWith("TestController#order");
        assertThat(context.requestId()).isEqualTo(response.getHeader("X-Request-ID"));
    }

    static class TestController {
        void order() {
        }
    }
}

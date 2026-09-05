package com.queryecho.spring.boot3;
import com.queryecho.jdbc.HttpRequestContext;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;
import static org.assertj.core.api.Assertions.assertThat;

class QueryEchoHttpRequestInterceptorTest {
    @Test void capturesRouteAndClearsRequestContext() throws Exception {
        var request = new MockHttpServletRequest("GET", "/orders/42");
        request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/orders/{id}");
        var response = new MockHttpServletResponse();
        var handler = new HandlerMethod(this, getClass().getDeclaredMethod("endpoint"));
        var interceptor = new QueryEchoHttpRequestInterceptor();
        try {
            interceptor.preHandle(request, response, handler);
            assertThat(HttpRequestContext.current().httpPath()).isEqualTo("/orders/{id}");
            assertThat(HttpRequestContext.current().requestId()).isEqualTo(response.getHeader("X-Request-ID"));
        } finally { interceptor.afterCompletion(request, response, handler, null); }
        assertThat(HttpRequestContext.current()).isNull();
    }
    void endpoint() {}
}

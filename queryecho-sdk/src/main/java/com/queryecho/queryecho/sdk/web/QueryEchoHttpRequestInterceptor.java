package com.queryecho.queryecho.sdk.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.UUID;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.AsyncHandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

/** Spring MVC가 선택한 API 경로와 Controller 메서드를 요청 처리 스레드에 기록한다. */
public final class QueryEchoHttpRequestInterceptor implements AsyncHandlerInterceptor {

    private static final String REQUEST_ID_HEADER = "X-Request-ID";
    private static final String REQUEST_ID_ATTRIBUTE = QueryEchoHttpRequestInterceptor.class.getName() + ".requestId";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String requestId = value(request.getHeader(REQUEST_ID_HEADER));
        if (requestId == null) {
            requestId = value((String) request.getAttribute(REQUEST_ID_ATTRIBUTE));
        }
        if (requestId == null) {
            requestId = UUID.randomUUID().toString();
            request.setAttribute(REQUEST_ID_ATTRIBUTE, requestId);
        }
        response.setHeader(REQUEST_ID_HEADER, requestId);

        String route = value((String) request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE));
        if (route == null) {
            route = request.getRequestURI();
        }
        String handlerName = handler instanceof HandlerMethod method
                ? method.getBeanType().getName() + "#" + method.getMethod().getName()
                : handler.getClass().getName();

        HttpRequestContext.set(new HttpRequestContext.Snapshot(
                traceId(request), requestId, request.getMethod(), route, handlerName));
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        HttpRequestContext.clear();
    }

    @Override
    public void afterConcurrentHandlingStarted(HttpServletRequest request, HttpServletResponse response,
                                               Object handler) {
        HttpRequestContext.clear();
    }

    private static String traceId(HttpServletRequest request) {
        String traceparent = value(request.getHeader("traceparent"));
        if (traceparent != null) {
            String[] parts = traceparent.split("-");
            if (parts.length >= 4 && parts[1].matches("[0-9a-fA-F]{32}")) {
                return parts[1].toLowerCase();
            }
        }
        return value(request.getHeader("X-B3-TraceId"));
    }

    private static String value(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}

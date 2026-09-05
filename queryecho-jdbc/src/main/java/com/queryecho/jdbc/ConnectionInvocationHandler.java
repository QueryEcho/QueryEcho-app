package com.queryecho.jdbc;

import com.queryecho.core.publisher.MetricEventPublisher;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;

final class ConnectionInvocationHandler implements InvocationHandler {
    private final Connection target;
    private final MetricEventPublisher publisher;
    private final QueryMetricSource source;

    ConnectionInvocationHandler(Connection target, MetricEventPublisher publisher, QueryMetricSource source) {
        this.target = target;
        this.publisher = publisher;
        this.source = source;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        String name = method.getName();
        if ("prepareStatement".equals(name) && hasSqlFirstArg(args)) {
            return wrap((PreparedStatement) invokeTarget(method, args), (String) args[0], PreparedStatement.class);
        }
        if ("prepareCall".equals(name) && hasSqlFirstArg(args)) {
            return wrap((CallableStatement) invokeTarget(method, args), (String) args[0], CallableStatement.class);
        }
        if ("createStatement".equals(name)) {
            return wrap((Statement) invokeTarget(method, args), null, Statement.class);
        }
        return invokeTarget(method, args);
    }

    private static boolean hasSqlFirstArg(Object[] args) {
        return args != null && args.length > 0 && args[0] instanceof String;
    }

    private Object wrap(Statement statement, String sql, Class<? extends Statement> type) {
        return Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                new StatementInvocationHandler(statement, sql, publisher, source));
    }

    private Object invokeTarget(Method method, Object[] args) throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException ex) {
            throw ex.getTargetException();
        }
    }
}

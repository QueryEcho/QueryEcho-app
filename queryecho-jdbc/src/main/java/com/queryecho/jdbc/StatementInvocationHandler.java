package com.queryecho.jdbc;

import com.queryecho.core.dto.QueryMetricEvent;
import com.queryecho.core.publisher.MetricEventPublisher;
import com.queryecho.core.util.SqlNormalizer;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

final class StatementInvocationHandler implements InvocationHandler {
    private static final Set<String> EXECUTE_METHODS = Set.of(
            "execute", "executeQuery", "executeUpdate", "executeLargeUpdate", "executeBatch", "executeLargeBatch");

    private final Statement target;
    private final String preparedSql;
    private final MetricEventPublisher publisher;
    private final QueryMetricSource source;
    private final Map<Integer, Object> boundParams = new HashMap<>();

    StatementInvocationHandler(Statement target, String preparedSql, MetricEventPublisher publisher,
                               QueryMetricSource source) {
        this.target = target;
        this.preparedSql = preparedSql;
        this.publisher = publisher;
        this.source = source;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        String name = method.getName();
        if ("clearParameters".equals(name)) {
            boundParams.clear();
        } else if (isParameterSetter(name, args)) {
            boundParams.put((Integer) args[0], "setNull".equals(name) ? null : args[1]);
        }
        if (EXECUTE_METHODS.contains(name)) {
            return timedExecute(method, args, name);
        }
        return invokeTarget(method, args);
    }

    private static boolean isParameterSetter(String name, Object[] args) {
        return name.startsWith("set") && args != null && args.length >= 2 && args[0] instanceof Integer;
    }

    private Object timedExecute(Method method, Object[] args, String methodName) throws Throwable {
        String sql = resolveSql(args, methodName);
        long start = System.nanoTime();
        boolean succeeded = false;
        String sqlState = null;
        try {
            Object result = invokeTarget(method, args);
            succeeded = true;
            return result;
        } catch (Throwable ex) {
            if (ex instanceof SQLException sqlException) {
                sqlState = sqlException.getSQLState();
            }
            throw ex;
        } finally {
            long durationUs = Math.max(0, (System.nanoTime() - start) / 1_000);
            List<Object> params = new ArrayList<>(new TreeMap<>(boundParams).values());
            HttpRequestContext.Snapshot request = HttpRequestContext.current();
            publisher.publish(new QueryMetricEvent(
                    UUID.randomUUID(), TransactionContext.currentId(), source.appName(), source.environment(),
                    source.instanceId(), source.datasourceName(), source.dbType(), sql, SqlNormalizer.normalize(sql),
                    java.util.Collections.unmodifiableList(params), params.size(), durationUs,
                    Instant.now(), Thread.currentThread().getName(), succeeded, sqlState,
                    request == null ? null : request.traceId(), request == null ? null : request.requestId(),
                    request == null ? null : request.httpMethod(), request == null ? null : request.httpPath(),
                    request == null ? null : request.handlerName()));
        }
    }

    private String resolveSql(Object[] args, String methodName) {
        if (preparedSql != null) return preparedSql;
        if (!"executeBatch".equals(methodName) && !"executeLargeBatch".equals(methodName)
                && args != null && args.length > 0 && args[0] instanceof String sql) return sql;
        return "UNKNOWN";
    }

    private Object invokeTarget(Method method, Object[] args) throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException ex) {
            throw ex.getTargetException();
        }
    }
}

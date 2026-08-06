package com.queryecho.queryecho.sdk.interceptor;

import com.queryecho.queryecho.sdk.publisher.MetricEventPublisher;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;

/**
 * Connection 한 개를 감싸는 프록시의 InvocationHandler.
 *
 * 하는 일은 딱 하나: createStatement / prepareStatement / prepareCall 호출을 가로채서,
 * 실제 드라이버가 만들어준 Statement 대신 우리가 만든 {@link StatementInvocationHandler}
 * 프록시를 대신 돌려준다. 그 외 메서드(commit, close, setAutoCommit ...)는 손대지 않고
 * 그대로 원본 Connection에 위임한다.
 *
 * 왜 Connection 단계에서 감싸야 하는가?
 *  - Statement를 만드는 주체가 Connection이기 때문에, "새로 만들어지는 모든 Statement를
 *    자동으로 계측하고 싶다"는 요구를 만족하려면 Statement 생성 지점인 Connection을
 *    먼저 감싸는 수밖에 없다. 이렇게 하면 애플리케이션/ORM(JPA, MyBatis 등) 코드는
 *    전혀 수정하지 않아도 모든 쿼리가 자동으로 계측된다.
 */
class ConnectionInvocationHandler implements InvocationHandler {

    private final Connection target;
    private final MetricEventPublisher publisher;

    ConnectionInvocationHandler(Connection target, MetricEventPublisher publisher) {
        this.target = target;
        this.publisher = publisher;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        String name = method.getName();

        if ("prepareStatement".equals(name) && hasSqlFirstArg(args)) {
            PreparedStatement ps = (PreparedStatement) invokeTarget(method, args);
            return wrapStatement(ps, (String) args[0], PreparedStatement.class);
        }
        if ("prepareCall".equals(name) && hasSqlFirstArg(args)) {
            CallableStatement cs = (CallableStatement) invokeTarget(method, args);
            return wrapStatement(cs, (String) args[0], CallableStatement.class);
        }
        if ("createStatement".equals(name)) {
            Statement st = (Statement) invokeTarget(method, args);
            // plain Statement는 이 시점에 SQL을 모르므로 null을 넘긴다.
            // 실제 SQL은 execute(sql) 호출 시점에 StatementInvocationHandler가 알아낸다.
            return wrapStatement(st, null, Statement.class);
        }

        return invokeTarget(method, args);
    }

    private boolean hasSqlFirstArg(Object[] args) {
        return args != null && args.length > 0 && args[0] instanceof String;
    }

    private Object wrapStatement(Statement statement, String sql, Class<? extends Statement> iface) {
        return Proxy.newProxyInstance(
                iface.getClassLoader(),
                new Class<?>[]{iface},
                new StatementInvocationHandler(statement, sql, publisher));
    }

    private Object invokeTarget(Method method, Object[] args) throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException e) {
            throw e.getTargetException();
        }
    }
}

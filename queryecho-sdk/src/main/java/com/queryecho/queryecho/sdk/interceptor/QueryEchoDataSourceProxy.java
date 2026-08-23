package com.queryecho.queryecho.sdk.interceptor;

import com.queryecho.queryecho.sdk.publisher.MetricEventPublisher;
import java.io.PrintWriter;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.logging.Logger;
import javax.sql.DataSource;

/**
 * 실제 DataSource(Hikari 등)를 그대로 감싸는 데코레이터.
 *
 * 왜 DataSource 자체를 구현(implements)했는가, 상속(extends)하지 않았는가?
 *  - HikariDataSource 같은 실제 구현체는 final이거나 생성자가 커넥션 풀 초기화를 전제로 하기 때문에
 *    안전하게 상속할 수 없다. DataSource는 표준 인터페이스이므로 위임(delegate) 패턴으로
 *    구현하면 어떤 커넥션 풀 구현체가 오더라도 동일하게 감쌀 수 있다.
 *
 * getConnection()에서 리턴하는 Connection만 프록시로 감싸고, 나머지 DataSource 메서드
 * (getLoginTimeout 등)는 전부 원본에 그대로 위임한다 - 우리가 관심 있는 건 "이 DataSource로
 * 나가는 쿼리"이지 DataSource 설정 자체가 아니기 때문이다.
 */
public class QueryEchoDataSourceProxy implements DataSource {

    private final DataSource delegate;
    private final MetricEventPublisher publisher;
    private final QueryMetricSource source;

    public QueryEchoDataSourceProxy(DataSource delegate, MetricEventPublisher publisher, QueryMetricSource source) {
        this.delegate = delegate;
        this.publisher = publisher;
        this.source = source;
    }

    @Override
    public Connection getConnection() throws SQLException {
        return wrap(delegate.getConnection());
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return wrap(delegate.getConnection(username, password));
    }

    // 프록시를 만드는 코드
    private Connection wrap(Connection connection) {
        // 커넥션 획득 시간까지 측정하려면 delegate.getConnection() 호출 전후도 별도로 측정해야 한다.
        return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                new ConnectionInvocationHandler(connection, publisher, source));
    }

    @Override
    public PrintWriter getLogWriter() throws SQLException {
        return delegate.getLogWriter();
    }

    @Override
    public void setLogWriter(PrintWriter out) throws SQLException {
        delegate.setLogWriter(out);
    }

    @Override
    public void setLoginTimeout(int seconds) throws SQLException {
        delegate.setLoginTimeout(seconds);
    }

    @Override
    public int getLoginTimeout() throws SQLException {
        return delegate.getLoginTimeout();
    }

    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        return delegate.getParentLogger();
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        // 순수 DataSource 인터페이스로 unwrap을 요청하면 원본을 그대로 내어준다.
        // (Hibernate 등 일부 프레임워크가 구체 타입으로 unwrap을 시도하는 경우 대비)
        if (iface.isInstance(this)) {
            return iface.cast(this);
        }
        return delegate.unwrap(iface);
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        return iface.isInstance(this) || delegate.isWrapperFor(iface);
    }
}

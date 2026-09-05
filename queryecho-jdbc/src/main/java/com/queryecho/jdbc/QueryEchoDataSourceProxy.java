package com.queryecho.jdbc;

import com.queryecho.core.publisher.MetricEventPublisher;
import java.io.PrintWriter;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Objects;
import java.util.logging.Logger;
import javax.sql.DataSource;

/** 어떤 JDBC 풀에도 적용할 수 있도록 표준 DataSource를 위임 방식으로 감싼다. */
public final class QueryEchoDataSourceProxy implements DataSource {
    private final DataSource delegate;
    private final MetricEventPublisher publisher;
    private final QueryMetricSource source;

    public QueryEchoDataSourceProxy(DataSource delegate, MetricEventPublisher publisher, QueryMetricSource source) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.publisher = Objects.requireNonNull(publisher, "publisher");
        this.source = Objects.requireNonNull(source, "source");
    }

    @Override
    public Connection getConnection() throws SQLException {
        return wrap(delegate.getConnection());
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return wrap(delegate.getConnection(username, password));
    }

    private Connection wrap(Connection connection) {
        return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                new ConnectionInvocationHandler(connection, publisher, source));
    }

    @Override public PrintWriter getLogWriter() throws SQLException { return delegate.getLogWriter(); }
    @Override public void setLogWriter(PrintWriter out) throws SQLException { delegate.setLogWriter(out); }
    @Override public void setLoginTimeout(int seconds) throws SQLException { delegate.setLoginTimeout(seconds); }
    @Override public int getLoginTimeout() throws SQLException { return delegate.getLoginTimeout(); }
    @Override public Logger getParentLogger() throws SQLFeatureNotSupportedException { return delegate.getParentLogger(); }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        return iface.isInstance(this) ? iface.cast(this) : delegate.unwrap(iface);
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        return iface.isInstance(this) || delegate.isWrapperFor(iface);
    }
}

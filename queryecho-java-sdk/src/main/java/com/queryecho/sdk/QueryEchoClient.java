package com.queryecho.sdk;

import com.queryecho.core.config.SdkOptions;
import com.queryecho.core.publisher.MetricEventPublisher;
import com.queryecho.jdbc.QueryEchoDataSourceProxy;
import com.queryecho.jdbc.QueryMetricSource;
import com.queryecho.transport.http.HttpMetricEventPublisher;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.sql.DataSource;

/** JVM당 한 개를 생성하고 애플리케이션 종료 시 닫는다. 대상 DataSource는 소유하지 않는다. */
public final class QueryEchoClient implements AutoCloseable {
    private final SdkOptions config;
    private final SanitizingMetricEventPublisher publisher;
    private final AtomicBoolean closed = new AtomicBoolean();

    public QueryEchoClient(QueryEchoConfig config) {
        this(config, config.isEnabled() ? new HttpMetricEventPublisher(config) : event -> {});
    }

    /** 테스트 또는 사용자 전송 구현 주입. client.close()는 전송 구현도 닫는다. */
    public QueryEchoClient(SdkOptions config, MetricEventPublisher transport) {
        this.config = Objects.requireNonNull(config, "config");
        this.publisher = new SanitizingMetricEventPublisher(Objects.requireNonNull(transport, "transport"), config);
    }

    public DataSource wrap(DataSource dataSource, String datasourceName) {
        ensureOpen();
        if (!config.isEnabled() || dataSource instanceof QueryEchoDataSourceProxy) return dataSource;
        return new QueryEchoDataSourceProxy(dataSource, publisher, new QueryMetricSource(
                config.getAppName(), config.getEnvironment(), config.getInstanceId(), datasourceName, config.getDbType()));
    }

    /** 관측 범위만 연다. 실제 Connection.commit/rollback은 호출자가 수행해야 한다. */
    public QueryEchoTransaction beginTransaction(String name) {
        ensureOpen();
        return new QueryEchoTransaction(config, config.isEnabled() ? publisher : event -> {}, name);
    }

    private void ensureOpen() {
        if (closed.get()) throw new IllegalStateException("QueryEchoClient is closed");
    }

    @Override
    public void close() throws Exception {
        if (closed.compareAndSet(false, true)) publisher.close();
    }
}

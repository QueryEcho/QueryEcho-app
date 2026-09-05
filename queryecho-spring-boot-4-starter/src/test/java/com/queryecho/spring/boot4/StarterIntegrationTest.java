package com.queryecho.spring.boot4;

import com.queryecho.core.dto.QueryMetricEvent;
import com.queryecho.core.dto.TxMetricEvent;
import com.queryecho.core.dto.TxStatus;
import com.queryecho.jdbc.QueryEchoDataSourceProxy;
import com.queryecho.jdbc.TransactionContext;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration;
import static org.assertj.core.api.Assertions.assertThat;

class StarterIntegrationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(org.springframework.boot.autoconfigure.aop.AopAutoConfiguration.class, QueryEchoAutoConfiguration.class,
                    DataSourceAutoConfiguration.class, DataSourceTransactionManagerAutoConfiguration.class))
            .withUserConfiguration(Fixture.class)
            .withPropertyValues("spring.datasource.url=jdbc:h2:mem:boot4;DB_CLOSE_DELAY=-1",
                    "queryecho.sdk.app-name=compat-test", "queryecho.sdk.transport=LOCAL");

    @Test void wrapsDataSourceAndLinksCommittedAndRolledBackQueries() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(DataSource.class)).isInstanceOf(QueryEchoDataSourceProxy.class);
            Service service = context.getBean(Service.class);
            Events events = context.getBean(Events.class);
            service.success();
            assertThat(events.transactions).hasSize(1);
            TxMetricEvent committed = events.transactions.get(0);
            assertThat(committed.status()).isEqualTo(TxStatus.COMMIT);
            assertThat(events.queries.get(0).transactionId()).isEqualTo(committed.transactionId());
            assertThat(events.queries.get(0).params()).isEmpty();
            assertThat(TransactionContext.currentId()).isNull();
            try { service.failure(); } catch (IllegalStateException expected) {}
            assertThat(events.transactions).hasSize(2);
            assertThat(events.transactions.get(1).status()).isEqualTo(TxStatus.ROLLBACK);
            assertThat(events.transactions.get(1).failureMessage()).isNull();
            assertThat(TransactionContext.currentId()).isNull();
        });
    }

    @Test void requiresNewGetsItsOwnIdAndRestoresOuterContext() {
        runner.run(context -> {
            context.getBean(Service.class).nested();
            Events events = context.getBean(Events.class);
            assertThat(events.transactions).hasSize(2);
            assertThat(events.queries).hasSize(3);
            assertThat(events.queries.get(0).transactionId()).isEqualTo(events.queries.get(2).transactionId());
            assertThat(events.queries.get(1).transactionId()).isNotEqualTo(events.queries.get(0).transactionId());
            assertThat(TransactionContext.currentId()).isNull();
        });
    }

    @Test void disablingSdkLeavesDataSourceUntouched() {
        runner.withPropertyValues("queryecho.sdk.enabled=false").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(DataSource.class)).isNotInstanceOf(QueryEchoDataSourceProxy.class);
            context.getBean(Service.class).success();
            assertThat(context.getBean(Events.class).queries).isEmpty();
        });
    }

    @Configuration(proxyBeanMethods = false)
    static class Fixture {
        @Bean Service service(DataSource ds, Inner inner) { return new Service(ds, inner); }
        @Bean Inner inner(DataSource ds) { return new Inner(ds); }
        @Bean Events events() { return new Events(); }
    }
    static class Events {
        final List<QueryMetricEvent> queries = new ArrayList<>();
        final List<TxMetricEvent> transactions = new ArrayList<>();
        @EventListener public void query(QueryMetricEvent event) { queries.add(event); }
        @EventListener public void transaction(TxMetricEvent event) { transactions.add(event); }
    }
    static void execute(DataSource ds) {
        Connection c = org.springframework.jdbc.datasource.DataSourceUtils.getConnection(ds);
        try (var statement = c.prepareStatement("select ?")) {
            statement.setString(1, "secret");
            statement.executeQuery().close();
        } catch (SQLException ex) { throw new IllegalStateException(ex); }
        finally { org.springframework.jdbc.datasource.DataSourceUtils.releaseConnection(c, ds); }
    }
    static class Service {
        private final DataSource ds;
        private final Inner inner;
        Service(DataSource ds, Inner inner) { this.ds = ds; this.inner = inner; }
        @Transactional public void success() { execute(ds); }
        @Transactional public void failure() { execute(ds); throw new IllegalStateException("private-message"); }
        @Transactional public void nested() { execute(ds); inner.executeNew(); execute(ds); }
    }
    static class Inner {
        private final DataSource ds;
        Inner(DataSource ds) { this.ds = ds; }
        @Transactional(propagation = Propagation.REQUIRES_NEW) public void executeNew() { execute(ds); }
    }
}

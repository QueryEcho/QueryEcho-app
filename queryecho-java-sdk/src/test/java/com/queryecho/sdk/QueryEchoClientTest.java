package com.queryecho.sdk;

import com.queryecho.core.dto.QueryMetricEvent;
import com.queryecho.core.dto.TxMetricEvent;
import com.queryecho.core.dto.TxStatus;
import com.queryecho.core.util.QueryFingerprint;
import com.queryecho.jdbc.TransactionContext;
import java.util.ArrayList;
import java.util.List;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class QueryEchoClientTest {
    @Test void plainJavaCollectsQueriesAndExplicitTransactionWithoutSpring() throws Exception {
        JdbcDataSource raw = new JdbcDataSource();
        raw.setURL("jdbc:h2:mem:plain");
        QueryEchoConfig config = new QueryEchoConfig();
        config.setAppName("plain-java");
        List<Object> events = new ArrayList<>();
        try (QueryEchoClient client = new QueryEchoClient(config, events::add);
             var connection = client.wrap(raw, "main").getConnection()) {
            connection.setAutoCommit(false);
            try (QueryEchoTransaction tx = client.beginTransaction("order");
                 var statement = connection.prepareStatement("select ?")) {
                statement.setString(1, null);
                statement.executeQuery().close();
                connection.commit();
                tx.committed();
            }
        }
        assertEquals(2, events.size());
        QueryMetricEvent query = (QueryMetricEvent) events.get(0);
        TxMetricEvent tx = (TxMetricEvent) events.get(1);
        assertEquals(tx.transactionId(), query.transactionId());
        assertEquals(TxStatus.COMMIT, tx.status());
        assertTrue(query.params().isEmpty());
        assertEquals(1, query.paramCount());
        assertNull(TransactionContext.currentId());
    }

    @Test void failureMessageIsOffAndUnfinishedScopeIsUnknown() throws Exception {
        List<Object> events = new ArrayList<>();
        try (QueryEchoClient client = new QueryEchoClient(new QueryEchoConfig(), events::add)) {
            try (var tx = client.beginTransaction("rollback")) {
                tx.rolledBack(new IllegalStateException("secret"));
            }
            try (var tx = client.beginTransaction("unfinished")) { }
        }
        assertNull(((TxMetricEvent) events.get(0)).failureMessage());
        assertEquals(TxStatus.UNKNOWN, ((TxMetricEvent) events.get(1)).status());
        assertNull(TransactionContext.currentId());
    }

    @Test void onlyAllowlistedParameterIsExported() throws Exception {
        JdbcDataSource raw = new JdbcDataSource(); raw.setURL("jdbc:h2:mem:params");
        QueryEchoConfig config = new QueryEchoConfig(); config.setDbType("h2");
        config.getParams().setEnabled(true);
        var rule = new com.queryecho.core.config.SdkOptions.ParamRule();
        rule.setFingerprint(QueryFingerprint.sha256("h2", "select ?, ?"));
        rule.setAllowedIndexes(List.of(2)); config.getParams().getRules().add(rule);
        List<Object> events = new ArrayList<>();
        try (var client = new QueryEchoClient(config, events::add);
             var connection = client.wrap(raw, "main").getConnection();
             var statement = connection.prepareStatement("select ?, ?")) {
            statement.setString(1, "secret"); statement.setInt(2, 42);
            statement.executeQuery().close();
        }
        var query = (QueryMetricEvent) events.get(0);
        assertEquals(1, query.params().size());
        assertFalse(query.params().toString().contains("secret"));
        assertTrue(query.params().toString().contains("42"));
    }
}

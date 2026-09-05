package com.queryecho.transport.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.queryecho.core.config.SdkOptions;
import com.queryecho.core.dto.QueryMetricEvent;
import com.queryecho.core.dto.TxMetricEvent;
import com.queryecho.core.dto.TxStatus;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HttpTransportTest {
    @Test void preservesJsonContractAndEscapesText() throws Exception {
        var event = transaction("quote\" slash\\ newline\n 한국어 😀");
        var json = new ObjectMapper().readTree(new JsonEventEncoder().encode(List.of(event)));
        assertEquals(event.transactionName(), json.get(0).get("transactionName").asText());
        assertEquals("COMMIT", json.get(0).get("status").asText());
        assertEquals(event.completedAt().toString(), json.get(0).get("completedAt").asText());
        assertTrue(json.get(0).get("failureMessage").isNull());
    }

    @Test void sendsSeparateBatchesAndBearerHeaderToRealHttpServer() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var bodies = new ConcurrentHashMap<String, String>();
        var auth = new ConcurrentHashMap<String, String>();
        CountDownLatch received = new CountDownLatch(2);
        server.createContext("/api/v1/ingest", exchange -> {
            String path = exchange.getRequestURI().getPath();
            bodies.put(path, new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            auth.put(path, exchange.getRequestHeaders().getFirst("Authorization"));
            exchange.sendResponseHeaders(202, -1); exchange.close();
            if (!path.endsWith("sdk-health")) received.countDown();
        });
        server.start();
        SdkOptions options = new SdkOptions();
        options.setCollectorUrl("http://127.0.0.1:" + server.getAddress().getPort());
        options.setApiKey("test-key"); options.getBuffer().setFlushIntervalMs(20);
        try (var publisher = new HttpMetricEventPublisher(options)) {
            publisher.publish(transaction("order"));
            publisher.publish(new QueryMetricEvent(UUID.randomUUID(), null, "test", "test", "one", "main", "h2",
                    "select ?", "select ?", List.of(), 1, 42, Instant.now(), "test", true, null,
                    null, null, null, null, null));
            assertTrue(received.await(5, TimeUnit.SECONDS));
            assertEquals("Bearer test-key", auth.get("/api/v1/ingest/queries"));
            assertEquals(1, new ObjectMapper().readTree(bodies.get("/api/v1/ingest/queries")).size());
        } finally { server.stop(0); }
    }

    private TxMetricEvent transaction(String name) {
        return new TxMetricEvent(UUID.randomUUID(), "test", "test", "one", name, 123, TxStatus.COMMIT,
                Instant.parse("2026-09-05T12:00:00Z"), "test", null, null, null, null, null, null, null);
    }
}

package com.queryecho.queryecho;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import tools.jackson.databind.ObjectMapper;
import com.queryecho.core.dto.QueryMetricEvent;
import com.queryecho.queryecho.collector.persistence.repository.QueryExecutionJpaRepository;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:queryecho-context;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "queryecho.demo.enabled=false",
        "queryecho.sdk.enabled=false"
})
@AutoConfigureMockMvc
class QueryEchoApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private QueryExecutionJpaRepository queryExecutionRepository;

    @Test
    void contextLoads() {
    }

    @Test
    void exposesFilteredRollupSeries() throws Exception {
        mockMvc.perform(get("/api/v1/metrics/series/queries")
                        .param("from", "2026-08-29T00:00:00Z")
                        .param("to", "2026-08-29T01:00:00Z")
                        .param("environment", "test")
                        .param("appName", "sample-app"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bucketSeconds").value(60))
                .andExpect(jsonPath("$.buckets").isArray());
    }

    @Test
    void exposesCollectionHealth() throws Exception {
        mockMvc.perform(get("/api/v1/metrics/collection-health")
                        .param("environment", "test")
                        .param("appName", "sample-app"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.collectorReceivedTotal").isNumber())
                .andExpect(jsonPath("$.collectorAcceptedTotal").isNumber())
                .andExpect(jsonPath("$.collectorInFlight").isNumber())
                .andExpect(jsonPath("$.executor.queueCapacity").value(100))
                .andExpect(jsonPath("$.executor.maxWorkers").value(8))
                .andExpect(jsonPath("$.queueWait.count").isNumber())
                .andExpect(jsonPath("$.persistence.query.count").isNumber())
                .andExpect(jsonPath("$.persistence.transaction.count").isNumber())
                .andExpect(jsonPath("$.instances").isArray());
    }

    @Test
    void persistsHttpQueryBatchThroughSingleBatchEvent() throws Exception {
        List<QueryMetricEvent> events = List.of(queryEvent(), queryEvent(), queryEvent());
        Set<UUID> eventIds = Set.of(
                events.get(0).eventId(), events.get(1).eventId(), events.get(2).eventId());

        mockMvc.perform(post("/api/v1/ingest/queries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(events)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.accepted").value(3));

        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
        while (queryExecutionRepository.findExistingIds(eventIds).size() < eventIds.size()
                && System.nanoTime() < deadline) {
            Thread.sleep(25);
        }

        org.assertj.core.api.Assertions.assertThat(queryExecutionRepository.findExistingIds(eventIds))
                .containsExactlyInAnyOrderElementsOf(eventIds);
    }

    private static QueryMetricEvent queryEvent() {
        return new QueryMetricEvent(
                UUID.randomUUID(), null, "batch-test-app", "test", "batch-instance", "main",
                "postgresql", "select 1", "select ?", List.of(), 0,
                1_000, Instant.now(), "test-thread", true, null,
                null, "request-batch", "GET", "/batch", "BatchController#get");
    }

}

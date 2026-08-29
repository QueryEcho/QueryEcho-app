package com.queryecho.queryecho;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
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
                .andExpect(jsonPath("$.collectorAcceptedTotal").value(0))
                .andExpect(jsonPath("$.instances").isArray());
    }

}

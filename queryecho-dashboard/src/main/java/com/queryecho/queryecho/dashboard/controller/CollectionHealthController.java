package com.queryecho.queryecho.dashboard.controller;

import com.queryecho.queryecho.collector.telemetry.CollectionTelemetryService;
import com.queryecho.queryecho.collector.telemetry.CollectionTelemetryService.CollectionHealthSnapshot;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/metrics/collection-health")
public class CollectionHealthController {

    private final CollectionTelemetryService telemetry;

    public CollectionHealthController(CollectionTelemetryService telemetry) {
        this.telemetry = telemetry;
    }

    @GetMapping
    public CollectionHealthSnapshot health(
            @RequestParam(required = false) String environment,
            @RequestParam(required = false) String appName) {
        return telemetry.snapshot(environment, appName);
    }
}

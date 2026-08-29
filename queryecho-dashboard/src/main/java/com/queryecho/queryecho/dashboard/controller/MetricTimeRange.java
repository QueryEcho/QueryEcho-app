package com.queryecho.queryecho.dashboard.controller;

import java.time.Duration;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

record MetricTimeRange(Instant from, Instant to) {

    private static final Duration DEFAULT_RANGE = Duration.ofHours(1);
    private static final Duration MAX_RANGE = Duration.ofDays(31);

    static MetricTimeRange resolve(Instant requestedFrom, Instant requestedTo) {
        Instant to = requestedTo == null ? Instant.now() : requestedTo;
        Instant from = requestedFrom == null ? to.minus(DEFAULT_RANGE) : requestedFrom;
        if (!from.isBefore(to)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "from must be earlier than to");
        }
        if (Duration.between(from, to).compareTo(MAX_RANGE) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "time range must not exceed 31 days");
        }
        return new MetricTimeRange(from, to);
    }
}

CREATE TABLE transaction_pattern (
    id BIGSERIAL PRIMARY KEY,
    fingerprint VARCHAR(64) NOT NULL,
    app_name VARCHAR(100) NOT NULL,
    transaction_name VARCHAR(500) NOT NULL,
    first_seen_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_seen_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_transaction_pattern_fingerprint UNIQUE (fingerprint),
    CONSTRAINT uk_transaction_pattern_app_name UNIQUE (app_name, transaction_name),
    CONSTRAINT ck_transaction_pattern_seen_time CHECK (last_seen_at >= first_seen_at)
);

CREATE TABLE transaction_execution (
    transaction_id UUID PRIMARY KEY,
    pattern_id BIGINT NOT NULL,
    environment VARCHAR(30) NOT NULL,
    instance_id VARCHAR(200) NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    duration_us BIGINT NOT NULL,
    status VARCHAR(10) NOT NULL,
    thread_name VARCHAR(200),
    failure_type VARCHAR(200),
    failure_message VARCHAR(1000),
    trace_id VARCHAR(64),
    request_id VARCHAR(100),
    ingested_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_transaction_execution_pattern
        FOREIGN KEY (pattern_id) REFERENCES transaction_pattern(id),
    CONSTRAINT ck_transaction_execution_duration CHECK (duration_us >= 0),
    CONSTRAINT ck_transaction_execution_status CHECK (status IN ('COMMIT', 'ROLLBACK', 'UNKNOWN'))
);

CREATE INDEX idx_transaction_execution_pattern_time
    ON transaction_execution (environment, pattern_id, completed_at DESC);
CREATE INDEX idx_transaction_execution_instance_time
    ON transaction_execution (environment, instance_id, completed_at DESC);
CREATE INDEX idx_transaction_execution_rollback_time
    ON transaction_execution (completed_at DESC)
    WHERE status = 'ROLLBACK';
CREATE INDEX idx_transaction_execution_trace
    ON transaction_execution (trace_id)
    WHERE trace_id IS NOT NULL;
CREATE INDEX idx_transaction_execution_retention
    ON transaction_execution (completed_at);

CREATE TABLE transaction_rollup_1m (
    bucket_start TIMESTAMP WITH TIME ZONE NOT NULL,
    environment VARCHAR(30) NOT NULL,
    pattern_id BIGINT NOT NULL,
    tx_count BIGINT NOT NULL,
    commit_count BIGINT NOT NULL,
    rollback_count BIGINT NOT NULL,
    unknown_count BIGINT NOT NULL,
    total_us BIGINT NOT NULL,
    min_us BIGINT NOT NULL,
    max_us BIGINT NOT NULL,
    bucket_counts JSONB NOT NULL,
    histogram_version SMALLINT NOT NULL DEFAULT 1,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_transaction_rollup_1m PRIMARY KEY (bucket_start, environment, pattern_id),
    CONSTRAINT fk_transaction_rollup_pattern
        FOREIGN KEY (pattern_id) REFERENCES transaction_pattern(id),
    CONSTRAINT ck_transaction_rollup_count CHECK (
        tx_count > 0
        AND commit_count >= 0
        AND rollback_count >= 0
        AND unknown_count >= 0
        AND commit_count + rollback_count + unknown_count = tx_count
    ),
    CONSTRAINT ck_transaction_rollup_duration CHECK (
        total_us >= 0 AND min_us >= 0 AND max_us >= min_us
    ),
    CONSTRAINT ck_transaction_rollup_histogram_version CHECK (histogram_version > 0)
);

CREATE INDEX idx_transaction_rollup_pattern_time
    ON transaction_rollup_1m (pattern_id, environment, bucket_start DESC);
CREATE INDEX idx_transaction_rollup_environment_time
    ON transaction_rollup_1m (environment, bucket_start DESC);

-- Query events normally arrive before the transaction-completion event, so this
-- correlation column intentionally has no foreign key to transaction_execution.
ALTER TABLE query_execution
    ADD COLUMN transaction_id UUID;

CREATE INDEX idx_query_execution_transaction_time
    ON query_execution (transaction_id, executed_at)
    WHERE transaction_id IS NOT NULL;

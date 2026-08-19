CREATE TABLE query_pattern (
    id BIGSERIAL PRIMARY KEY,
    fingerprint VARCHAR(64) NOT NULL,
    db_type VARCHAR(30) NOT NULL,
    normalized_sql TEXT NOT NULL,
    statement_type VARCHAR(16) NOT NULL,
    first_seen_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_seen_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_query_pattern_fingerprint UNIQUE (fingerprint),
    CONSTRAINT ck_query_pattern_seen_time CHECK (last_seen_at >= first_seen_at)
);

CREATE TABLE query_execution (
    event_id UUID PRIMARY KEY,
    executed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    ingested_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    environment VARCHAR(30) NOT NULL,
    app_name VARCHAR(100) NOT NULL,
    instance_id VARCHAR(200) NOT NULL,
    datasource_name VARCHAR(100) NOT NULL,
    pattern_id BIGINT NOT NULL,
    duration_us BIGINT NOT NULL,
    succeeded BOOLEAN NOT NULL,
    sql_state VARCHAR(5),
    thread_name VARCHAR(200),
    param_count SMALLINT NOT NULL DEFAULT 0,
    params JSONB,
    CONSTRAINT fk_query_execution_pattern FOREIGN KEY (pattern_id) REFERENCES query_pattern(id),
    CONSTRAINT ck_query_execution_duration CHECK (duration_us >= 0),
    CONSTRAINT ck_query_execution_param_count CHECK (param_count >= 0)
);

CREATE INDEX idx_query_execution_instance_time
    ON query_execution (environment, app_name, instance_id, datasource_name, executed_at DESC);
CREATE INDEX idx_query_execution_pattern_time
    ON query_execution (pattern_id, executed_at DESC);
CREATE INDEX idx_query_execution_failed_time
    ON query_execution (environment, app_name, instance_id, datasource_name, executed_at DESC);
CREATE INDEX idx_query_execution_retention ON query_execution (executed_at);

CREATE TABLE query_rollup_1m (
    bucket_start TIMESTAMP WITH TIME ZONE NOT NULL,
    environment VARCHAR(30) NOT NULL,
    app_name VARCHAR(100) NOT NULL,
    instance_id VARCHAR(200) NOT NULL,
    datasource_name VARCHAR(100) NOT NULL,
    pattern_id BIGINT NOT NULL,
    exec_count BIGINT NOT NULL,
    error_count BIGINT NOT NULL,
    total_us BIGINT NOT NULL,
    min_us BIGINT NOT NULL,
    max_us BIGINT NOT NULL,
    bucket_counts JSONB NOT NULL,
    histogram_version SMALLINT NOT NULL DEFAULT 1,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_query_rollup_1m PRIMARY KEY (
        bucket_start, environment, app_name, instance_id, datasource_name, pattern_id
    ),
    CONSTRAINT fk_query_rollup_pattern FOREIGN KEY (pattern_id) REFERENCES query_pattern(id),
    CONSTRAINT ck_query_rollup_exec_count CHECK (exec_count > 0),
    CONSTRAINT ck_query_rollup_error_count CHECK (error_count >= 0 AND error_count <= exec_count),
    CONSTRAINT ck_query_rollup_duration CHECK (total_us >= 0 AND min_us >= 0 AND max_us >= min_us),
    CONSTRAINT ck_query_rollup_histogram_version CHECK (histogram_version > 0)
);

CREATE INDEX idx_query_rollup_instance_pattern_time
    ON query_rollup_1m (
        environment, app_name, instance_id, datasource_name, pattern_id, bucket_start DESC
    );
CREATE INDEX idx_query_rollup_instance_time
    ON query_rollup_1m (
        environment, app_name, instance_id, datasource_name, bucket_start DESC
    );
CREATE INDEX idx_query_rollup_pattern_time
    ON query_rollup_1m (pattern_id, bucket_start DESC);

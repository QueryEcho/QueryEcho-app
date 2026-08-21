CREATE TABLE db_server_query_aggregate (
    id UUID PRIMARY KEY,
    source_event_key VARCHAR(240) NOT NULL,
    source_type VARCHAR(20) NOT NULL DEFAULT 'DB_SERVER',
    sample_type VARCHAR(20) NOT NULL DEFAULT 'AGGREGATE_DELTA',
    db_instance_id VARCHAR(100) NOT NULL,
    db_type VARCHAR(30) NOT NULL,
    schema_name VARCHAR(100),
    db_user VARCHAR(100),
    fingerprint VARCHAR(64) NOT NULL,
    normalized_sql TEXT NOT NULL,
    statement_type VARCHAR(32) NOT NULL,
    execution_count BIGINT NOT NULL,
    total_duration_us BIGINT NOT NULL,
    rows_processed BIGINT NOT NULL,
    observed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    ingested_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_db_server_query_aggregate_source UNIQUE (source_event_key),
    CONSTRAINT ck_db_server_query_aggregate_source CHECK (source_type = 'DB_SERVER'),
    CONSTRAINT ck_db_server_query_aggregate_sample CHECK (sample_type = 'AGGREGATE_DELTA'),
    CONSTRAINT ck_db_server_query_aggregate_values CHECK (
        execution_count > 0 AND total_duration_us >= 0 AND rows_processed >= 0
    )
);

CREATE INDEX idx_db_server_query_aggregate_instance_time
    ON db_server_query_aggregate (db_instance_id, observed_at DESC);

CREATE INDEX idx_db_server_query_aggregate_fingerprint_time
    ON db_server_query_aggregate (fingerprint, observed_at DESC);

CREATE TABLE db_server_query_execution (
    id UUID PRIMARY KEY,
    source_event_key VARCHAR(240) NOT NULL,
    source_type VARCHAR(20) NOT NULL DEFAULT 'DB_SERVER',
    db_instance_id VARCHAR(100) NOT NULL,
    db_type VARCHAR(30) NOT NULL,
    schema_name VARCHAR(100),
    db_user VARCHAR(100),
    client_host VARCHAR(255),
    client_program VARCHAR(200),
    connection_id BIGINT,
    thread_id BIGINT NOT NULL,
    source_event_id BIGINT NOT NULL,
    fingerprint VARCHAR(64) NOT NULL,
    normalized_sql TEXT NOT NULL,
    statement_type VARCHAR(32) NOT NULL,
    duration_us BIGINT NOT NULL,
    lock_time_us BIGINT NOT NULL DEFAULT 0,
    rows_affected BIGINT NOT NULL DEFAULT 0,
    rows_sent BIGINT NOT NULL DEFAULT 0,
    rows_examined BIGINT NOT NULL DEFAULT 0,
    succeeded BOOLEAN NOT NULL,
    error_code INTEGER,
    sql_state VARCHAR(5),
    error_message VARCHAR(1000),
    observed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    ingested_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_db_server_query_source_event UNIQUE (source_event_key),
    CONSTRAINT ck_db_server_query_source_type CHECK (source_type = 'DB_SERVER'),
    CONSTRAINT ck_db_server_query_duration CHECK (duration_us >= 0 AND lock_time_us >= 0),
    CONSTRAINT ck_db_server_query_rows CHECK (
        rows_affected >= 0 AND rows_sent >= 0 AND rows_examined >= 0
    )
);

CREATE INDEX idx_db_server_query_instance_time
    ON db_server_query_execution (db_instance_id, observed_at DESC);

CREATE INDEX idx_db_server_query_fingerprint_time
    ON db_server_query_execution (fingerprint, observed_at DESC);

CREATE INDEX idx_db_server_query_failed_time
    ON db_server_query_execution (succeeded, observed_at DESC);

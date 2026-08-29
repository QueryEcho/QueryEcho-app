ALTER TABLE query_execution
    ADD COLUMN trace_id VARCHAR(64),
    ADD COLUMN request_id VARCHAR(100),
    ADD COLUMN http_method VARCHAR(10),
    ADD COLUMN http_path VARCHAR(500),
    ADD COLUMN handler_name VARCHAR(500);

CREATE INDEX idx_query_execution_request_time
    ON query_execution (request_id, executed_at)
    WHERE request_id IS NOT NULL;

CREATE INDEX idx_query_execution_http_path_time
    ON query_execution (http_method, http_path, executed_at DESC)
    WHERE http_path IS NOT NULL;

ALTER TABLE transaction_execution
    ADD COLUMN http_method VARCHAR(10),
    ADD COLUMN http_path VARCHAR(500),
    ADD COLUMN handler_name VARCHAR(500);

CREATE INDEX idx_transaction_execution_request
    ON transaction_execution (request_id, completed_at DESC)
    WHERE request_id IS NOT NULL;

CREATE INDEX idx_transaction_execution_http_path_time
    ON transaction_execution (http_method, http_path, completed_at DESC)
    WHERE http_path IS NOT NULL;

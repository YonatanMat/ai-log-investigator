CREATE TABLE IF NOT EXISTS investigations (
    id CHAR(36) PRIMARY KEY,
    created_ms BIGINT NOT NULL,
    total_entries INT NOT NULL,
    analysis_json MEDIUMTEXT NOT NULL,
    top_message TEXT,
    top_count INT,
    explanation TEXT,
    INDEX investigations_recent (created_ms, id)
);

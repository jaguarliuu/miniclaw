CREATE TABLE IF NOT EXISTS uploaded_files (
    id            VARCHAR(36) PRIMARY KEY,
    session_id    VARCHAR(36) NOT NULL REFERENCES sessions(id) ON DELETE CASCADE,
    filename      VARCHAR(512) NOT NULL,
    mime_type     VARCHAR(128) NOT NULL,
    size_bytes    BIGINT NOT NULL,
    sha256        VARCHAR(64) NOT NULL,
    storage_path  VARCHAR(1024) NOT NULL,
    title         VARCHAR(512),
    page_count    INT,
    status        VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    error_message TEXT,
    created_at    TEXT NOT NULL DEFAULT (datetime('now')),
    parsed_at     TEXT
);

CREATE INDEX IF NOT EXISTS idx_files_session ON uploaded_files(session_id);
CREATE INDEX IF NOT EXISTS idx_files_sha256 ON uploaded_files(sha256);

CREATE TABLE IF NOT EXISTS file_chunks (
    id              VARCHAR(36) PRIMARY KEY,
    file_id         VARCHAR(36) NOT NULL REFERENCES uploaded_files(id) ON DELETE CASCADE,
    chunk_index     INT NOT NULL,
    page_number     INT,
    text_content    TEXT NOT NULL,
    char_offset     INT NOT NULL DEFAULT 0,
    tokens_estimate INT NOT NULL DEFAULT 0,
    created_at      TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE INDEX IF NOT EXISTS idx_chunks_file ON file_chunks(file_id);

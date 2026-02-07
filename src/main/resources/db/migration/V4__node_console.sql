CREATE TABLE IF NOT EXISTS nodes (
    id                    VARCHAR(36) PRIMARY KEY,
    alias                 VARCHAR(100) NOT NULL UNIQUE,
    display_name          VARCHAR(255),
    connector_type        VARCHAR(20) NOT NULL,
    host                  VARCHAR(255),
    port                  INTEGER,
    username              VARCHAR(100),
    auth_type             VARCHAR(20),
    encrypted_credential  TEXT NOT NULL,
    credential_iv         VARCHAR(44) NOT NULL,
    tags                  TEXT,
    safety_policy         VARCHAR(20) NOT NULL DEFAULT 'strict',
    last_tested_at        TIMESTAMP,
    last_test_success     BOOLEAN,
    created_at            TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_nodes_alias ON nodes(alias);
CREATE INDEX IF NOT EXISTS idx_nodes_connector_type ON nodes(connector_type);

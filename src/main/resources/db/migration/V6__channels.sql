CREATE TABLE IF NOT EXISTS channels (
    id                    VARCHAR(36) PRIMARY KEY,
    name                  VARCHAR(100) NOT NULL UNIQUE,
    type                  VARCHAR(20) NOT NULL,
    enabled               BOOLEAN NOT NULL DEFAULT TRUE,
    config                TEXT NOT NULL,
    encrypted_credential  TEXT,
    credential_iv         VARCHAR(44),
    last_tested_at        TIMESTAMP,
    last_test_success     BOOLEAN,
    created_at            TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_channels_type ON channels(type);
CREATE INDEX IF NOT EXISTS idx_channels_name ON channels(name);

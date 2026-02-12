-- Soul 配置表
CREATE TABLE soul_config (
    id SERIAL PRIMARY KEY,
    agent_name VARCHAR(100),
    personality VARCHAR(2000),
    traits TEXT,
    response_style VARCHAR(50),
    detail_level VARCHAR(50),
    expertise TEXT,
    forbidden_topics TEXT,
    custom_prompt TEXT,
    enabled BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_soul_config_enabled ON soul_config(enabled, updated_at DESC);

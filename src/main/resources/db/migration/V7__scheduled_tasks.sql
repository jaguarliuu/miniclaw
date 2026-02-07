CREATE TABLE IF NOT EXISTS scheduled_tasks (
    id              VARCHAR(36) PRIMARY KEY,
    name            VARCHAR(200) NOT NULL,
    cron_expr       VARCHAR(100) NOT NULL,
    prompt          TEXT NOT NULL,
    channel_id      VARCHAR(36) NOT NULL,
    channel_type    VARCHAR(20) NOT NULL,
    email_to        VARCHAR(500),
    email_cc        VARCHAR(500),
    enabled         BOOLEAN NOT NULL DEFAULT TRUE,
    last_run_at     TIMESTAMP,
    last_run_success BOOLEAN,
    last_run_error  TEXT,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_scheduled_tasks_enabled ON scheduled_tasks(enabled);

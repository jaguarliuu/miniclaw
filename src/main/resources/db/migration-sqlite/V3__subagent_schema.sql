-- V3__subagent_schema.sql
-- SubAgent 系统：父子会话/运行关系 + announce outbox（SQLite 版）

-- sessions 扩展：SQLite 不支持 ADD COLUMN IF NOT EXISTS，
-- 但 Flyway 保证只执行一次，直接用 ALTER TABLE
ALTER TABLE sessions ADD COLUMN agent_id VARCHAR(100);
ALTER TABLE sessions ADD COLUMN session_kind VARCHAR(20) NOT NULL DEFAULT 'main';
ALTER TABLE sessions ADD COLUMN session_key TEXT;
ALTER TABLE sessions ADD COLUMN parent_session_id VARCHAR(36) REFERENCES sessions(id) ON DELETE SET NULL;
ALTER TABLE sessions ADD COLUMN created_by_run_id VARCHAR(36) REFERENCES runs(id) ON DELETE SET NULL;

CREATE INDEX idx_sessions_agent_id ON sessions(agent_id);
CREATE INDEX idx_sessions_session_kind ON sessions(session_kind);
CREATE INDEX idx_sessions_parent_session_id ON sessions(parent_session_id);
-- SQLite 不支持 partial unique index，使用普通 unique index
CREATE UNIQUE INDEX idx_sessions_session_key_unique ON sessions(session_key);

-- runs 扩展
ALTER TABLE runs ADD COLUMN agent_id VARCHAR(100);
ALTER TABLE runs ADD COLUMN run_kind VARCHAR(20) NOT NULL DEFAULT 'main';
ALTER TABLE runs ADD COLUMN lane VARCHAR(20) NOT NULL DEFAULT 'main';
ALTER TABLE runs ADD COLUMN parent_run_id VARCHAR(36) REFERENCES runs(id) ON DELETE SET NULL;
ALTER TABLE runs ADD COLUMN requester_session_id VARCHAR(36) REFERENCES sessions(id) ON DELETE SET NULL;
ALTER TABLE runs ADD COLUMN deliver BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX idx_runs_agent_id ON runs(agent_id);
CREATE INDEX idx_runs_run_kind ON runs(run_kind);
CREATE INDEX idx_runs_lane ON runs(lane);
CREATE INDEX idx_runs_parent_run_id ON runs(parent_run_id);
CREATE INDEX idx_runs_requester_session_id ON runs(requester_session_id);

-- subagent 回传 outbox
CREATE TABLE subagent_outbox (
    id                 VARCHAR(36) PRIMARY KEY,
    parent_run_id      VARCHAR(36) NOT NULL REFERENCES runs(id) ON DELETE CASCADE,
    parent_session_id  VARCHAR(36) NOT NULL REFERENCES sessions(id) ON DELETE CASCADE,
    sub_run_id         VARCHAR(36) NOT NULL REFERENCES runs(id) ON DELETE CASCADE,
    sub_session_id     VARCHAR(36) NOT NULL REFERENCES sessions(id) ON DELETE CASCADE,
    event_type         VARCHAR(50) NOT NULL DEFAULT 'subagent.announced',
    payload            TEXT NOT NULL,
    status             VARCHAR(20) NOT NULL DEFAULT 'pending',
    retry_count        INT NOT NULL DEFAULT 0,
    next_retry_at      TIMESTAMP,
    delivered_at       TIMESTAMP,
    last_error         TEXT,
    created_at         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_subagent_outbox_status ON subagent_outbox(status);
CREATE INDEX idx_subagent_outbox_next_retry_at ON subagent_outbox(next_retry_at);
CREATE INDEX idx_subagent_outbox_parent_run_id ON subagent_outbox(parent_run_id);
CREATE INDEX idx_subagent_outbox_sub_run_id ON subagent_outbox(sub_run_id);

ALTER TABLE sessions RENAME COLUMN user_id TO owner_id;

ALTER TABLE sessions
    ALTER COLUMN owner_id DROP NOT NULL,
    ALTER COLUMN status SET DEFAULT 'IDLE';

UPDATE sessions
SET status = 'IDLE'
WHERE status = 'ACTIVE';

ALTER TABLE sessions
    ADD COLUMN updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN closed_at TIMESTAMP NULL;

DROP INDEX IF EXISTS idx_sessions_user_id;

CREATE INDEX idx_sessions_owner_id ON sessions(owner_id);
CREATE INDEX idx_sessions_status ON sessions(status);

ALTER TABLE sessions
ADD COLUMN title VARCHAR(255),
ADD COLUMN status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE';

CREATE INDEX idx_sessions_user_id ON sessions(user_id);


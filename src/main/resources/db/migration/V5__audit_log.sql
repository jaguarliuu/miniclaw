CREATE TABLE IF NOT EXISTS node_audit_logs (
    id                VARCHAR(36) PRIMARY KEY,

    -- 事件分类
    event_type        VARCHAR(30) NOT NULL,   -- command.execute | command.reject | node.register | node.remove | node.test

    -- Agent 上下文（命令执行时有值，RPC 操作时为 null）
    run_id            VARCHAR(36),
    session_id        VARCHAR(36),

    -- 节点信息（冗余存储，节点删除后仍可审计）
    node_alias        VARCHAR(100),
    node_id           VARCHAR(36),
    connector_type    VARCHAR(20),

    -- 命令信息
    tool_name         VARCHAR(50),            -- remote_exec | kubectl_exec | null(RPC操作)
    command           TEXT,

    -- 安全分类
    safety_level      VARCHAR(20),            -- read_only | side_effect | destructive
    safety_policy     VARCHAR(20),            -- strict | standard | relaxed

    -- HITL 决策
    hitl_required     BOOLEAN NOT NULL DEFAULT FALSE,
    hitl_decision     VARCHAR(20),            -- approve | reject | null

    -- 执行结果
    result_status     VARCHAR(20) NOT NULL,   -- success | error | rejected | blocked
    result_summary    TEXT,                   -- 截断的输出或错误信息（前 500 字符）
    duration_ms       INTEGER,

    -- 时间
    created_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_audit_node_alias ON node_audit_logs(node_alias);
CREATE INDEX IF NOT EXISTS idx_audit_event_type ON node_audit_logs(event_type);
CREATE INDEX IF NOT EXISTS idx_audit_created_at ON node_audit_logs(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_audit_session_id ON node_audit_logs(session_id);
CREATE INDEX IF NOT EXISTS idx_audit_safety_level ON node_audit_logs(safety_level);
CREATE INDEX IF NOT EXISTS idx_audit_result_status ON node_audit_logs(result_status);

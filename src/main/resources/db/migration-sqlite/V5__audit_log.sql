CREATE TABLE node_audit_logs (
    id                VARCHAR(36) PRIMARY KEY,

    -- 事件分类
    event_type        VARCHAR(30) NOT NULL,

    -- Agent 上下文
    run_id            VARCHAR(36),
    session_id        VARCHAR(36),

    -- 节点信息
    node_alias        VARCHAR(100),
    node_id           VARCHAR(36),
    connector_type    VARCHAR(20),

    -- 命令信息
    tool_name         VARCHAR(50),
    command           TEXT,

    -- 安全分类
    safety_level      VARCHAR(20),
    safety_policy     VARCHAR(20),

    -- HITL 决策
    hitl_required     BOOLEAN NOT NULL DEFAULT FALSE,
    hitl_decision     VARCHAR(20),

    -- 执行结果
    result_status     VARCHAR(20) NOT NULL,
    result_summary    TEXT,
    duration_ms       INTEGER,

    -- 时间
    created_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_audit_node_alias ON node_audit_logs(node_alias);
CREATE INDEX idx_audit_event_type ON node_audit_logs(event_type);
CREATE INDEX idx_audit_created_at ON node_audit_logs(created_at DESC);
CREATE INDEX idx_audit_session_id ON node_audit_logs(session_id);
CREATE INDEX idx_audit_safety_level ON node_audit_logs(safety_level);
CREATE INDEX idx_audit_result_status ON node_audit_logs(result_status);

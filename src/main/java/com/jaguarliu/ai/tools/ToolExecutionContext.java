package com.jaguarliu.ai.tools;

import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * 工具执行上下文（ThreadLocal）
 *
 * 在 ReAct 循环中，每次工具执行前设置上下文，执行后清理。
 * 用于传递运行时信息（如 skill 资源目录、subagent 元数据）到工具实现。
 */
public class ToolExecutionContext {

    private static final ThreadLocal<ToolExecutionContext> CURRENT = new ThreadLocal<>();

    /**
     * 额外允许访问的路径（除了 workspace 之外）
     * 例如 skill 的资源目录
     */
    private final Set<Path> additionalAllowedPaths;

    /**
     * WebSocket 连接 ID（用于事件推送）
     */
    private final String connectionId;

    /**
     * Agent ID（当前运行的 Agent Profile）
     */
    private final String agentId;

    /**
     * 运行类型：main / subagent
     */
    private final String runKind;

    /**
     * 父运行 ID（仅 subagent 有值）
     */
    private final String parentRunId;

    /**
     * 当前运行 ID
     */
    private final String runId;

    /**
     * 当前会话 ID
     */
    private final String sessionId;

    /**
     * 派生深度
     */
    private final int depth;

    private ToolExecutionContext(Set<Path> additionalAllowedPaths,
                                  String connectionId,
                                  String agentId,
                                  String runKind,
                                  String parentRunId,
                                  String runId,
                                  String sessionId,
                                  int depth) {
        this.additionalAllowedPaths = Collections.unmodifiableSet(additionalAllowedPaths);
        this.connectionId = connectionId;
        this.agentId = agentId;
        this.runKind = runKind;
        this.parentRunId = parentRunId;
        this.runId = runId;
        this.sessionId = sessionId;
        this.depth = depth;
    }

    /**
     * 获取当前线程的执行上下文
     */
    public static ToolExecutionContext current() {
        return CURRENT.get();
    }

    /**
     * 设置当前线程的执行上下文
     */
    public static void set(ToolExecutionContext context) {
        CURRENT.set(context);
    }

    /**
     * 清理当前线程的执行上下文
     */
    public static void clear() {
        CURRENT.remove();
    }

    /**
     * 检查路径是否在额外允许列表中
     */
    public boolean isPathAllowed(Path path) {
        Path normalized = path.toAbsolutePath().normalize();
        for (Path allowed : additionalAllowedPaths) {
            if (normalized.startsWith(allowed)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 获取额外允许的路径
     */
    public Set<Path> getAdditionalAllowedPaths() {
        return additionalAllowedPaths;
    }

    /**
     * 获取连接 ID
     */
    public String getConnectionId() {
        return connectionId;
    }

    /**
     * 获取 Agent ID
     */
    public String getAgentId() {
        return agentId;
    }

    /**
     * 获取运行类型
     */
    public String getRunKind() {
        return runKind;
    }

    /**
     * 获取父运行 ID
     */
    public String getParentRunId() {
        return parentRunId;
    }

    /**
     * 获取当前运行 ID
     */
    public String getRunId() {
        return runId;
    }

    /**
     * 获取当前会话 ID
     */
    public String getSessionId() {
        return sessionId;
    }

    /**
     * 获取派生深度
     */
    public int getDepth() {
        return depth;
    }

    /**
     * 判断当前是否为子代理运行
     */
    public boolean isSubagent() {
        return "subagent".equals(runKind);
    }

    /**
     * 判断当前是否为主运行
     */
    public boolean isMain() {
        return "main".equals(runKind);
    }

    /**
     * 构建器
     */
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final Set<Path> paths = new HashSet<>();
        private String connectionId;
        private String agentId = "main";
        private String runKind = "main";
        private String parentRunId;
        private String runId;
        private String sessionId;
        private int depth = 0;

        public Builder addAllowedPath(Path path) {
            if (path != null) {
                paths.add(path.toAbsolutePath().normalize());
            }
            return this;
        }

        public Builder connectionId(String connectionId) {
            this.connectionId = connectionId;
            return this;
        }

        public Builder agentId(String agentId) {
            this.agentId = agentId;
            return this;
        }

        public Builder runKind(String runKind) {
            this.runKind = runKind;
            return this;
        }

        public Builder parentRunId(String parentRunId) {
            this.parentRunId = parentRunId;
            return this;
        }

        public Builder runId(String runId) {
            this.runId = runId;
            return this;
        }

        public Builder sessionId(String sessionId) {
            this.sessionId = sessionId;
            return this;
        }

        public Builder depth(int depth) {
            this.depth = depth;
            return this;
        }

        public ToolExecutionContext build() {
            return new ToolExecutionContext(paths, connectionId, agentId, runKind, parentRunId, runId, sessionId, depth);
        }
    }
}

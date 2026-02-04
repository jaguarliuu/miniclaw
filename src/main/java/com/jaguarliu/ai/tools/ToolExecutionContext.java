package com.jaguarliu.ai.tools;

import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * 工具执行上下文（ThreadLocal）
 *
 * 在 ReAct 循环中，每次工具执行前设置上下文，执行后清理。
 * 用于传递运行时信息（如 skill 资源目录）到工具实现。
 */
public class ToolExecutionContext {

    private static final ThreadLocal<ToolExecutionContext> CURRENT = new ThreadLocal<>();

    /**
     * 额外允许访问的路径（除了 workspace 之外）
     * 例如 skill 的资源目录
     */
    private final Set<Path> additionalAllowedPaths;

    private ToolExecutionContext(Set<Path> additionalAllowedPaths) {
        this.additionalAllowedPaths = Collections.unmodifiableSet(additionalAllowedPaths);
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
     * 构建器
     */
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final Set<Path> paths = new HashSet<>();

        public Builder addAllowedPath(Path path) {
            if (path != null) {
                paths.add(path.toAbsolutePath().normalize());
            }
            return this;
        }

        public ToolExecutionContext build() {
            return new ToolExecutionContext(paths);
        }
    }
}

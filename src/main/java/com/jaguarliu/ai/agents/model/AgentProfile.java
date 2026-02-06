package com.jaguarliu.ai.agents.model;

import lombok.Data;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Agent 身份配置
 * 定义单个 Agent 的工具权限、沙箱级别、工作目录等
 */
@Data
public class AgentProfile {

    /**
     * Agent 唯一标识（如 main, public, researcher）
     */
    private String id;

    /**
     * 沙箱级别：trusted / restricted
     * - trusted: 允许执行 shell、写文件等敏感操作
     * - restricted: 只读访问，禁止危险工具
     */
    private String sandbox = "trusted";

    /**
     * 工具权限配置
     */
    private ToolPermissions tools = new ToolPermissions();

    /**
     * 工作目录（相对于全局 workspace）
     */
    private String workspace = "./workspace";

    /**
     * 认证目录（存放该 Agent 的凭据）
     */
    private String authDir = "./.miniclaw/auth/main";

    /**
     * 是否允许派生子代理
     */
    private boolean canSpawn = true;

    /**
     * 工具权限配置内部类
     */
    @Data
    public static class ToolPermissions {
        /**
         * 允许使用的工具列表（白名单）
         * 为空时表示允许所有未被 deny 的工具
         */
        private List<String> allow = Collections.emptyList();

        /**
         * 禁止使用的工具列表（黑名单）
         * deny 优先级高于 allow
         */
        private List<String> deny = Collections.emptyList();
    }

    /**
     * 判断工具是否被允许
     *
     * @param toolName 工具名称
     * @return true 如果允许使用
     */
    public boolean isToolAllowed(String toolName) {
        // deny 优先
        if (tools.getDeny() != null && tools.getDeny().contains(toolName)) {
            return false;
        }
        // allow 为空表示允许所有
        if (tools.getAllow() == null || tools.getAllow().isEmpty()) {
            return true;
        }
        return tools.getAllow().contains(toolName);
    }

    /**
     * 获取最终允许的工具集合
     *
     * @param availableTools 系统中所有可用的工具名称
     * @return 过滤后的允许工具集合
     */
    public Set<String> resolveAllowedTools(Set<String> availableTools) {
        Set<String> result = new HashSet<>();

        // 如果 allow 为空，从全部可用工具开始
        if (tools.getAllow() == null || tools.getAllow().isEmpty()) {
            result.addAll(availableTools);
        } else {
            // 只取 allow 与 availableTools 的交集
            for (String tool : tools.getAllow()) {
                if (availableTools.contains(tool)) {
                    result.add(tool);
                }
            }
        }

        // 移除 deny 列表中的工具
        if (tools.getDeny() != null) {
            result.removeAll(tools.getDeny());
        }

        return result;
    }

    /**
     * 判断是否为受限沙箱
     */
    public boolean isRestricted() {
        return "restricted".equalsIgnoreCase(sandbox);
    }
}

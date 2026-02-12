package com.jaguarliu.ai.runtime.strategy;

import lombok.Builder;
import lombok.Getter;

import java.util.Set;

/**
 * Agent 策略输出的执行方案
 * 定义 system prompt、工具白名单、步数限制等
 */
@Getter
@Builder
public class AgentExecutionPlan {

    /**
     * 完整的 system prompt
     */
    private final String systemPrompt;

    /**
     * 工具白名单：null 表示允许全部工具，非 null 则只允许指定工具
     */
    private final Set<String> allowedTools;

    /**
     * 排除的 MCP 服务器
     */
    private final Set<String> excludedMcpServers;

    /**
     * 最大步数覆盖：null 表示使用默认值
     */
    private final Integer maxStepsOverride;

    /**
     * 策略名称，用于日志和调试
     */
    private final String strategyName;
}

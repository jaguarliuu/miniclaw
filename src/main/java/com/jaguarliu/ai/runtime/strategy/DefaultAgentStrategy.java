package com.jaguarliu.ai.runtime.strategy;

import com.jaguarliu.ai.runtime.SystemPromptBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 默认 Agent 策略（兜底）
 * 行为与原始流程完全一致：使用标准 system prompt，允许所有工具
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultAgentStrategy implements AgentStrategy {

    private final SystemPromptBuilder systemPromptBuilder;

    @Override
    public boolean supports(AgentContext context) {
        return true; // 兜底，始终匹配
    }

    @Override
    public AgentExecutionPlan prepare(AgentContext context) {
        log.info("Using agent strategy: default (runId={})", context.getRunId());

        String systemPrompt = systemPromptBuilder.build(
                SystemPromptBuilder.PromptMode.FULL,
                null,
                context.getExcludedMcpServers()
        );

        return AgentExecutionPlan.builder()
                .systemPrompt(systemPrompt)
                .allowedTools(null)  // 全部工具
                .excludedMcpServers(context.getExcludedMcpServers())
                .maxStepsOverride(null)  // 使用默认
                .strategyName("default")
                .build();
    }

    @Override
    public int priority() {
        return 0;
    }
}

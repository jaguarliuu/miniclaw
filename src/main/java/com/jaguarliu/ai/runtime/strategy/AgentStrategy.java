package com.jaguarliu.ai.runtime.strategy;

/**
 * Agent 策略接口
 * 根据 AgentContext 判断是否适用，并生成执行方案
 */
public interface AgentStrategy {

    /**
     * 判断当前策略是否适用于给定上下文
     */
    boolean supports(AgentContext context);

    /**
     * 构建执行方案
     */
    AgentExecutionPlan prepare(AgentContext context);

    /**
     * 策略优先级，高优先级先匹配，默认为 0
     */
    int priority();
}

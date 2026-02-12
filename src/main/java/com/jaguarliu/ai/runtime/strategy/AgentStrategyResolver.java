package com.jaguarliu.ai.runtime.strategy;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

/**
 * 策略发现与选择器
 * 自动注入所有 AgentStrategy bean，按优先级降序匹配
 */
@Slf4j
@Component
public class AgentStrategyResolver {

    private final List<AgentStrategy> strategies;

    public AgentStrategyResolver(List<AgentStrategy> strategies) {
        // 按 priority 降序排列
        this.strategies = strategies.stream()
                .sorted(Comparator.comparingInt(AgentStrategy::priority).reversed())
                .toList();

        log.info("Loaded {} agent strategies: {}", this.strategies.size(),
                this.strategies.stream()
                        .map(s -> s.getClass().getSimpleName() + "(priority=" + s.priority() + ")")
                        .toList());
    }

    /**
     * 解析上下文，返回第一个匹配的策略
     */
    public AgentStrategy resolve(AgentContext context) {
        for (AgentStrategy strategy : strategies) {
            if (strategy.supports(context)) {
                log.debug("Resolved strategy: {} for context: dataSourceId={}",
                        strategy.getClass().getSimpleName(), context.getDataSourceId());
                return strategy;
            }
        }
        // 理论上不会走到这里，因为 DefaultAgentStrategy 总是 supports=true
        throw new IllegalStateException("No matching agent strategy found");
    }
}

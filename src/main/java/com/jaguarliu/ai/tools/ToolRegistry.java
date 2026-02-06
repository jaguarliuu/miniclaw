package com.jaguarliu.ai.tools;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 工具注册中心
 * 管理所有可用工具，支持 Spring 自动发现
 *
 * 注意：使用 @PostConstruct 动态发现工具，避免构造函数注入导致的循环依赖。
 * 这样 ToolRegistry 在构造时不依赖任何 Tool，只在初始化完成后才注册工具。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ToolRegistry {

    /**
     * Spring 应用上下文，用于动态发现 Tool Bean
     */
    private final ApplicationContext applicationContext;

    /**
     * 工具映射表：name → Tool
     */
    private final Map<String, Tool> registry = new ConcurrentHashMap<>();

    /**
     * 初始化：动态发现并注册所有 Spring Bean 工具
     *
     * 使用 ApplicationContext.getBeansOfType() 而非构造函数注入，
     * 可以避免与依赖 ToolRegistry 的工具（如 SessionsSpawnTool）形成循环依赖。
     */
    @PostConstruct
    public void init() {
        Map<String, Tool> tools = applicationContext.getBeansOfType(Tool.class);
        for (Tool tool : tools.values()) {
            register(tool);
        }
        log.info("ToolRegistry initialized with {} tools: {}",
                registry.size(),
                registry.keySet());
    }

    /**
     * 注册工具
     */
    public void register(Tool tool) {
        String name = tool.getName();
        if (registry.containsKey(name)) {
            log.warn("Tool already registered, overwriting: {}", name);
        }
        registry.put(name, tool);
        log.debug("Registered tool: {}", name);
    }

    /**
     * 获取工具
     */
    public Optional<Tool> get(String name) {
        return Optional.ofNullable(registry.get(name));
    }

    /**
     * 列出所有工具定义（供 LLM Function Calling 使用）
     */
    public List<ToolDefinition> listDefinitions() {
        return registry.values().stream()
                .map(Tool::getDefinition)
                .toList();
    }

    /**
     * 转换为 OpenAI Function Calling 格式
     */
    public List<Map<String, Object>> toOpenAiTools() {
        return registry.values().stream()
                .map(tool -> tool.getDefinition().toOpenAiFormat())
                .toList();
    }

    /**
     * 转换为 OpenAI Function Calling 格式（过滤版）
     * 只包含指定的工具
     *
     * @param allowedTools 允许的工具名称集合
     * @return 过滤后的工具列表
     */
    public List<Map<String, Object>> toOpenAiTools(Set<String> allowedTools) {
        if (allowedTools == null || allowedTools.isEmpty()) {
            return toOpenAiTools();
        }
        return registry.values().stream()
                .filter(tool -> allowedTools.contains(tool.getName()))
                .map(tool -> tool.getDefinition().toOpenAiFormat())
                .toList();
    }

    /**
     * 检查工具是否存在
     */
    public boolean exists(String name) {
        return registry.containsKey(name);
    }

    /**
     * 获取已注册工具数量
     */
    public int size() {
        return registry.size();
    }
}

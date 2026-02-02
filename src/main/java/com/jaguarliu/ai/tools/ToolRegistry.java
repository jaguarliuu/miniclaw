package com.jaguarliu.ai.tools;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 工具注册中心
 * 管理所有可用工具，支持 Spring 自动发现
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ToolRegistry {

    /**
     * Spring 自动注入所有 Tool 实现
     */
    private final List<Tool> tools;

    /**
     * 工具映射表：name → Tool
     */
    private final Map<String, Tool> registry = new ConcurrentHashMap<>();

    /**
     * 初始化：自动注册所有 Spring Bean 工具
     */
    @PostConstruct
    public void init() {
        for (Tool tool : tools) {
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

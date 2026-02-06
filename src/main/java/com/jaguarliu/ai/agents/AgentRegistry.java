package com.jaguarliu.ai.agents;

import com.jaguarliu.ai.agents.model.AgentProfile;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Agent 注册中心
 * 管理所有 Agent Profile，提供按 agentId 查询能力
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentRegistry {

    private final AgentsProperties agentsProperties;

    /**
     * Agent Profile 映射表：agentId → AgentProfile
     */
    private final Map<String, AgentProfile> registry = new ConcurrentHashMap<>();

    /**
     * 初始化：从配置加载所有 Agent Profile
     */
    @PostConstruct
    public void init() {
        Map<String, AgentProfile> profiles = agentsProperties.getProfiles();

        if (profiles == null || profiles.isEmpty()) {
            // 没有配置时，创建默认的 main profile
            log.info("No agent profiles configured, creating default 'main' profile");
            AgentProfile defaultProfile = createDefaultProfile();
            registry.put("main", defaultProfile);
        } else {
            // 从配置加载
            for (Map.Entry<String, AgentProfile> entry : profiles.entrySet()) {
                String agentId = entry.getKey();
                AgentProfile profile = entry.getValue();
                profile.setId(agentId); // 确保 id 与 key 一致
                registry.put(agentId, profile);
                log.debug("Loaded agent profile: {} (sandbox={}, canSpawn={})",
                        agentId, profile.getSandbox(), profile.isCanSpawn());
            }
        }

        // 验证默认 agent 存在
        String defaultAgent = agentsProperties.getDefaultAgent();
        if (!registry.containsKey(defaultAgent)) {
            log.warn("Default agent '{}' not found in profiles, falling back to first available", defaultAgent);
            if (!registry.isEmpty()) {
                agentsProperties.setDefaultAgent(registry.keySet().iterator().next());
            }
        }

        log.info("AgentRegistry initialized with {} profiles: {}, default={}",
                registry.size(),
                registry.keySet(),
                agentsProperties.getDefaultAgent());
    }

    /**
     * 获取 Agent Profile
     *
     * @param agentId Agent ID
     * @return Optional<AgentProfile>
     */
    public Optional<AgentProfile> get(String agentId) {
        return Optional.ofNullable(registry.get(agentId));
    }

    /**
     * 获取 Agent Profile，不存在时返回默认
     *
     * @param agentId Agent ID，为 null 时返回默认
     * @return AgentProfile
     */
    public AgentProfile getOrDefault(String agentId) {
        if (agentId == null || agentId.isBlank()) {
            agentId = agentsProperties.getDefaultAgent();
        }
        return registry.getOrDefault(agentId, registry.get(agentsProperties.getDefaultAgent()));
    }

    /**
     * 获取默认 Agent Profile
     */
    public AgentProfile getDefault() {
        return getOrDefault(null);
    }

    /**
     * 获取默认 Agent ID
     */
    public String getDefaultAgentId() {
        return agentsProperties.getDefaultAgent();
    }

    /**
     * 检查 Agent 是否存在
     */
    public boolean exists(String agentId) {
        return registry.containsKey(agentId);
    }

    /**
     * 获取所有已注册的 Agent ID
     */
    public Set<String> listAgentIds() {
        return Collections.unmodifiableSet(registry.keySet());
    }

    /**
     * 获取 Lane 配置
     */
    public AgentsProperties.LaneConfig getLaneConfig() {
        return agentsProperties.getLane();
    }

    /**
     * 验证 agentId 是否有效
     * 用于 spawn 时校验目标 agent 是否可用
     *
     * @param agentId 要验证的 Agent ID
     * @return true 如果有效
     */
    public boolean isValidAgentId(String agentId) {
        if (agentId == null || agentId.isBlank()) {
            return true; // 空值会使用默认
        }
        return registry.containsKey(agentId);
    }

    /**
     * 检查指定 agent 是否允许 spawn 子代理
     *
     * @param agentId Agent ID
     * @return true 如果允许 spawn
     */
    public boolean canSpawn(String agentId) {
        AgentProfile profile = getOrDefault(agentId);
        return profile != null && profile.isCanSpawn();
    }

    /**
     * 创建默认 Profile
     */
    private AgentProfile createDefaultProfile() {
        AgentProfile profile = new AgentProfile();
        profile.setId("main");
        profile.setSandbox("trusted");
        profile.setWorkspace("./workspace");
        profile.setAuthDir("./.miniclaw/auth/main");
        profile.setCanSpawn(true);
        // tools 保持默认（允许所有）
        return profile;
    }
}

package com.jaguarliu.ai.agents;

import com.jaguarliu.ai.agents.model.AgentProfile;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

/**
 * Agent 配置属性
 * 支持多个 Agent Profile 配置
 *
 * 配置示例：
 * <pre>
 * agents:
 *   default-agent: main
 *   profiles:
 *     main:
 *       sandbox: trusted
 *       tools:
 *         allow: [read_file, write_file, shell, ...]
 *         deny: []
 *       workspace: ./workspace
 *       auth-dir: ./.miniclaw/auth/main
 *     public:
 *       sandbox: restricted
 *       tools:
 *         allow: [read_file, http_get]
 *         deny: [shell, sessions_spawn]
 *       workspace: ./workspace/public
 * </pre>
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "agents")
public class AgentsProperties {

    /**
     * 默认 Agent ID
     * 当未指定 agentId 时使用此默认值
     */
    private String defaultAgent = "main";

    /**
     * Agent Profile 配置映射
     * Key: agentId, Value: AgentProfile
     */
    private Map<String, AgentProfile> profiles = new HashMap<>();

    /**
     * SubAgent lane 并发配置
     */
    private LaneConfig lane = new LaneConfig();

    /**
     * Lane 并发配置内部类
     */
    @Data
    public static class LaneConfig {
        /**
         * main lane 最大并发数
         */
        private int mainMaxConcurrency = 4;

        /**
         * subagent lane 最大并发数
         */
        private int subagentMaxConcurrency = 8;
    }
}

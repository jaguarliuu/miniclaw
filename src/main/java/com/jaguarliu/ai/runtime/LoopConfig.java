package com.jaguarliu.ai.runtime;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * ReAct 循环配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "agent.loop")
public class LoopConfig {

    /**
     * 最大循环步数（防止无限循环）
     */
    private int maxSteps = 10;

    /**
     * 整个循环的超时时间（秒）
     */
    private long runTimeoutSeconds = 300;

    /**
     * 单步超时时间（秒），包含 LLM 调用 + 工具执行
     */
    private long stepTimeoutSeconds = 120;

    /**
     * 创建一个覆盖了 maxSteps 的新配置实例，其余参数继承自 base
     */
    public static LoopConfig withMaxSteps(int maxSteps, LoopConfig base) {
        LoopConfig config = new LoopConfig();
        config.setMaxSteps(maxSteps);
        config.setRunTimeoutSeconds(base.getRunTimeoutSeconds());
        config.setStepTimeoutSeconds(base.getStepTimeoutSeconds());
        return config;
    }
}

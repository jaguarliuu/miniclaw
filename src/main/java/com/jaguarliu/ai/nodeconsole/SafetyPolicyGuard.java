package com.jaguarliu.ai.nodeconsole;

import org.springframework.stereotype.Component;

/**
 * 安全策略守卫：根据节点策略和命令分类决定执行行为
 */
@Component
public class SafetyPolicyGuard {

    public enum Decision {
        AUTO_EXECUTE,    // 自动执行
        REQUIRE_HITL,    // 需要人工确认
        BLOCK            // 拒绝执行
    }

    /**
     * 根据安全级别和策略做出决策
     *
     * @param safetyLevel 命令安全级别（0=只读, 1=副作用, 2=破坏性）
     * @param safetyPolicy 节点安全策略（strict/standard/relaxed）
     * @return 执行决策
     */
    public Decision decide(int safetyLevel, String safetyPolicy) {
        // Level 2 (DESTRUCTIVE): 永远拒绝
        if (safetyLevel == 2) {
            return Decision.BLOCK;
        }

        // Level 1 (SIDE_EFFECT): 根据策略决定
        if (safetyLevel == 1) {
            if ("relaxed".equals(safetyPolicy)) {
                return Decision.AUTO_EXECUTE;
            }
            return Decision.REQUIRE_HITL;
        }

        // Level 0 (READ_ONLY): 根据策略决定
        if ("strict".equals(safetyPolicy)) {
            return Decision.REQUIRE_HITL;
        }
        return Decision.AUTO_EXECUTE;
    }

    /**
     * 检查命令是否可以执行（不包括 HITL 场景）
     */
    public boolean isAllowed(int safetyLevel, String safetyPolicy) {
        Decision decision = decide(safetyLevel, safetyPolicy);
        return decision != Decision.BLOCK;
    }

    /**
     * 检查是否需要 HITL 确认
     */
    public boolean requiresHitl(int safetyLevel, String safetyPolicy) {
        return decide(safetyLevel, safetyPolicy) == Decision.REQUIRE_HITL;
    }
}

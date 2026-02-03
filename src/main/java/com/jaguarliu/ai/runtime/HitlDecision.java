package com.jaguarliu.ai.runtime;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.Builder;

import java.util.Map;

/**
 * HITL 确认决策
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HitlDecision {

    public enum Action {
        APPROVE,
        REJECT
    }

    /**
     * 决策动作
     */
    private Action action;

    /**
     * 修改后的参数（可选，仅 APPROVE 时有效）
     */
    private Map<String, Object> modifiedArguments;

    public boolean isApproved() {
        return action == Action.APPROVE;
    }

    public static HitlDecision approve() {
        return HitlDecision.builder().action(Action.APPROVE).build();
    }

    public static HitlDecision approve(Map<String, Object> modifiedArgs) {
        return HitlDecision.builder().action(Action.APPROVE).modifiedArguments(modifiedArgs).build();
    }

    public static HitlDecision reject() {
        return HitlDecision.builder().action(Action.REJECT).build();
    }
}

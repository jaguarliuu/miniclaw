package com.jaguarliu.ai.skills.selector;

import lombok.Builder;
import lombok.Data;

/**
 * Skill 选择结果
 */
@Data
@Builder
public class SkillSelection {

    /**
     * 是否选中了 skill
     */
    private boolean selected;

    /**
     * 选中的 skill 名称
     */
    private String skillName;

    /**
     * 用户参数（$ARGUMENTS 的值）
     */
    private String arguments;

    /**
     * 选择来源
     */
    private SelectionSource source;

    /**
     * 原始用户输入（用于 fallback）
     */
    private String originalInput;

    /**
     * 选择来源枚举
     */
    public enum SelectionSource {
        /**
         * 手动触发：/skill-name args
         */
        MANUAL,

        /**
         * LLM 自动选择：[USE_SKILL:xxx]
         */
        AUTO,

        /**
         * 未选择任何 skill
         */
        NONE
    }

    /**
     * 创建"未选择"结果
     */
    public static SkillSelection none(String originalInput) {
        return SkillSelection.builder()
                .selected(false)
                .source(SelectionSource.NONE)
                .originalInput(originalInput)
                .build();
    }

    /**
     * 创建手动选择结果
     */
    public static SkillSelection manual(String skillName, String arguments, String originalInput) {
        return SkillSelection.builder()
                .selected(true)
                .skillName(skillName)
                .arguments(arguments)
                .source(SelectionSource.MANUAL)
                .originalInput(originalInput)
                .build();
    }

    /**
     * 创建自动选择结果
     */
    public static SkillSelection auto(String skillName, String arguments) {
        return SkillSelection.builder()
                .selected(true)
                .skillName(skillName)
                .arguments(arguments)
                .source(SelectionSource.AUTO)
                .build();
    }

    /**
     * 是否为手动触发
     */
    public boolean isManual() {
        return source == SelectionSource.MANUAL;
    }

    /**
     * 是否为自动选择
     */
    public boolean isAuto() {
        return source == SelectionSource.AUTO;
    }
}

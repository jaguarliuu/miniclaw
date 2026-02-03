package com.jaguarliu.ai.skills.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Skill 条目
 * SkillRegistry 中存储的完整对象，包含元数据 + 可用性状态 + 成本信息
 *
 * 生命周期：
 * 1. Discovery 阶段：解析 SKILL.md → 得到 SkillMetadata
 * 2. Gating 阶段：检查 requires → 设置 available / unavailableReason
 * 3. Indexing 阶段：计算 tokenCost → 用于 budget 控制
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SkillEntry {

    /**
     * 元数据（从 YAML frontmatter 解析）
     */
    private SkillMetadata metadata;

    /**
     * 是否可用（gating 结果）
     * true = 满足所有 requires 条件
     */
    private boolean available;

    /**
     * 不可用原因（gating 失败时记录）
     * 示例："Missing env: OPENAI_API_KEY; Missing binary: git"
     */
    private String unavailableReason;

    /**
     * 文件最后修改时间（用于热更新检测）
     */
    private long lastModified;

    /**
     * 预估 token 成本（索引部分）
     * 用于 budget 控制，避免索引占用过多 context window
     */
    private int tokenCost;

    /**
     * 计算索引 token 成本
     * 公式：base_overhead（XML 标签） + name_tokens + description_tokens
     */
    public static int calculateTokenCost(SkillMetadata metadata) {
        int baseCost = 20;
        int nameCost = estimateTokens(metadata.getName());
        int descCost = estimateTokens(metadata.getDescription());
        return baseCost + nameCost + descCost;
    }

    /**
     * 简单 token 估算
     * 中文字符约 2 token/字，英文约 0.3 token/字符
     */
    private static int estimateTokens(String text) {
        if (text == null || text.isEmpty()) return 0;

        int chineseCount = 0;
        int otherCount = 0;
        for (char c : text.toCharArray()) {
            if (Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN) {
                chineseCount++;
            } else {
                otherCount++;
            }
        }
        return (int) (chineseCount * 2 + otherCount * 0.3);
    }
}

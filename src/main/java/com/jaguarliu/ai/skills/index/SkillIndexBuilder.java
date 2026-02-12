package com.jaguarliu.ai.skills.index;

import com.jaguarliu.ai.skills.model.SkillEntry;
import com.jaguarliu.ai.skills.registry.SkillRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Skill 索引构建器
 *
 * 职责：
 * 1. 构建 token-aware 的 XML 索引
 * 2. 支持 budget 控制（超出预算时截断）
 * 3. 生成 Progressive Disclosure 的"索引"阶段内容
 *
 * 输出格式：
 * <skills>
 *   <skill name="code-review">代码审查，检查代码质量和问题</skill>
 *   <skill name="git-commit">生成规范的 Git commit message</skill>
 * </skills>
 */
@Slf4j
@Component
public class SkillIndexBuilder {

    private final SkillRegistry registry;

    @Value("${skills.index-token-budget:2000}")
    private int indexTokenBudget = 2000;  // 默认值，防止 @Value 未生效时为 0

    // 基础开销（XML 结构、说明文字等）
    private static final int BASE_OVERHEAD_TOKENS = 150;

    public SkillIndexBuilder(SkillRegistry registry) {
        this.registry = registry;
    }

    /**
     * 构建 skill 索引（注入 system prompt）
     *
     * @return 格式化的索引字符串，如果没有可用 skill 返回空字符串
     */
    public String buildIndex() {
        List<SkillEntry> available = registry.getAvailable();

        if (available.isEmpty()) {
            return "";
        }

        // 按优先级排序（数字小的优先）
        available = available.stream()
                .sorted(Comparator.comparingInt(e -> e.getMetadata().getPriority()))
                .collect(Collectors.toList());

        // 计算 budget
        int usedTokens = BASE_OVERHEAD_TOKENS;

        StringBuilder skillsXml = new StringBuilder();
        int includedCount = 0;

        for (SkillEntry entry : available) {
            int cost = entry.getTokenCost();
            if (usedTokens + cost > indexTokenBudget) {
                log.debug("Token budget exceeded. Included {} of {} skills.",
                        includedCount, available.size());
                break;
            }

            usedTokens += cost;
            includedCount++;

            skillsXml.append(String.format(
                    "  <skill name=\"%s\">%s</skill>\n",
                    escapeXml(entry.getMetadata().getName()),
                    escapeXml(entry.getMetadata().getDescription())
            ));
        }

        if (includedCount == 0) {
            return "";
        }

        // 构建完整索引
        StringBuilder sb = new StringBuilder();
        sb.append("\n---\n\n");
        sb.append("## Available Skills\n\n");
        sb.append("The following skills are available. To use a skill:\n");
        sb.append("- Manual: User types `/skill-name arguments`\n");
        sb.append("- Auto: Call `use_skill(skill_name=\"...\")` tool when a task matches a skill\n\n");
        sb.append("<skills>\n");
        sb.append(skillsXml);
        sb.append("</skills>\n\n");
        sb.append("Call `use_skill` BEFORE writing code or creating files to load expert instructions.\n");

        log.debug("Built skill index: {} skills, ~{} tokens", includedCount, usedTokens);

        return sb.toString();
    }

    /**
     * 构建紧凑索引（只有 XML 部分，不含说明）
     */
    public String buildCompactIndex() {
        List<SkillEntry> available = registry.getAvailable();

        if (available.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("<skills>\n");

        for (SkillEntry entry : available) {
            sb.append(String.format(
                    "  <skill name=\"%s\">%s</skill>\n",
                    escapeXml(entry.getMetadata().getName()),
                    escapeXml(entry.getMetadata().getDescription())
            ));
        }

        sb.append("</skills>");
        return sb.toString();
    }

    /**
     * 构建 skill 列表（用于 UI 展示或补全）
     */
    public List<SkillSummary> buildSkillList() {
        return registry.getAvailable().stream()
                .sorted(Comparator.comparingInt(e -> e.getMetadata().getPriority()))
                .map(entry -> new SkillSummary(
                        entry.getMetadata().getName(),
                        entry.getMetadata().getDescription(),
                        entry.getTokenCost()
                ))
                .collect(Collectors.toList());
    }

    /**
     * 计算索引的 token 成本
     */
    public int calculateIndexCost() {
        int skillTokens = registry.getAvailable().stream()
                .mapToInt(SkillEntry::getTokenCost)
                .sum();
        return BASE_OVERHEAD_TOKENS + skillTokens;
    }

    /**
     * 获取索引统计信息
     */
    public IndexStats getStats() {
        List<SkillEntry> available = registry.getAvailable();
        int totalCost = calculateIndexCost();
        int includedCount = 0;
        int usedTokens = BASE_OVERHEAD_TOKENS;

        for (SkillEntry entry : available) {
            if (usedTokens + entry.getTokenCost() <= indexTokenBudget) {
                usedTokens += entry.getTokenCost();
                includedCount++;
            } else {
                break;
            }
        }

        return new IndexStats(
                available.size(),
                includedCount,
                totalCost,
                indexTokenBudget,
                totalCost > indexTokenBudget
        );
    }

    /**
     * 设置 token 预算（用于测试）
     */
    public void setIndexTokenBudget(int budget) {
        this.indexTokenBudget = budget;
    }

    /**
     * XML 转义
     */
    private String escapeXml(String text) {
        if (text == null) return "";
        return text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    /**
     * Skill 摘要（用于列表展示）
     */
    public record SkillSummary(
            String name,
            String description,
            int tokenCost
    ) {}

    /**
     * 索引统计信息
     */
    public record IndexStats(
            int totalAvailable,
            int includedInIndex,
            int totalTokenCost,
            int tokenBudget,
            boolean truncated
    ) {}
}

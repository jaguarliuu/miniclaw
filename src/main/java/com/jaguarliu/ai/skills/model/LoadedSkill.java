package com.jaguarliu.ai.skills.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

/**
 * 已加载的 Skill（包含完整正文）
 *
 * 这是 Progressive Disclosure 的"展开"阶段：
 * - 索引阶段只暴露 name/description（轻量）
 * - 激活后才加载完整正文（按需）
 *
 * 每次激活都从文件系统重新读取，确保内容最新。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoadedSkill {

    /**
     * 唯一标识
     */
    private String name;

    /**
     * 描述
     */
    private String description;

    /**
     * 完整正文（Markdown）
     * 包含 SOP 步骤、操作说明、$ARGUMENTS 占位符等
     */
    private String body;

    /**
     * Skill 的基础目录路径
     * 用于访问 skill 相关的资源文件（如 html2pptx.md、scripts/ 等）
     */
    private Path basePath;

    /**
     * 允许的工具白名单
     * 为 null 时不限制
     */
    private Set<String> allowedTools;

    /**
     * 需要确认的工具（覆盖默认 HITL 配置）
     * 为 null 时使用工具默认配置
     */
    private Set<String> confirmBefore;

    /**
     * 运行时注入的环境变量
     * 只在单次 run 生效，run 结束后恢复原环境
     */
    private Map<String, String> runtimeEnv;
}

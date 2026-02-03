package com.jaguarliu.ai.skills.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.nio.file.Path;
import java.util.List;

/**
 * Skill 元数据
 * 从 SKILL.md 的 YAML frontmatter 解析
 *
 * 只包含"索引"需要的轻量信息，不包含正文。
 * 正文在激活时才加载（Progressive Disclosure）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SkillMetadata {

    /**
     * 唯一标识（必需）
     * 同时也是 slash command 的名称：/code-review
     */
    private String name;

    /**
     * 简短描述（必需）
     * 用于 system prompt 索引，让 LLM 知道这个 skill 能做什么
     */
    private String description;

    /**
     * 允许的工具白名单
     * 为 null 时不限制；非空时只允许列表中的工具
     */
    private List<String> allowedTools;

    /**
     * 需要 HITL 确认的工具（覆盖工具默认配置）
     * 为 null 时使用工具默认 HITL 配置
     */
    private List<String> confirmBefore;

    /**
     * 可用性条件
     * 为 null 时无条件可用
     */
    private SkillRequires requires;

    /**
     * 主要环境变量（UI 展示用）
     * 示例：OPENAI_API_KEY
     */
    private String primaryEnv;

    /**
     * 源文件路径（SKILL.md 的绝对路径）
     */
    private Path sourcePath;

    /**
     * 优先级：0=项目级, 1=用户级, 2=内置
     * 数字越小优先级越高，同名 skill 高优先级覆盖低优先级
     */
    private int priority;
}

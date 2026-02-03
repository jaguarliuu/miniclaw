package com.jaguarliu.ai.skills.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Skill 可用性条件
 * 对应 SKILL.md 中的 metadata.miniclaw.requires
 *
 * 在 load-time 做 gating 检查：
 * - 全部条件满足 → skill 可用
 * - 任一条件不满足 → skill 不可用 + 记录原因
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SkillRequires {

    /**
     * 需要的环境变量（全部必须存在）
     * 示例：["OPENAI_API_KEY", "GITHUB_TOKEN"]
     */
    private List<String> env;

    /**
     * 需要的二进制程序（全部必须在 PATH 中）
     * 示例：["git", "node"]
     */
    private List<String> bins;

    /**
     * 需要的二进制程序（任一存在即可）
     * 示例：["npm", "yarn", "pnpm"]
     */
    private List<String> anyBins;

    /**
     * 需要的配置项为 true
     * 示例：["git.enabled", "shell.enabled"]
     */
    private List<String> config;

    /**
     * 支持的操作系统
     * 可选值：darwin / linux / win32
     */
    private List<String> os;
}

package com.jaguarliu.ai.skills.parser;

import com.jaguarliu.ai.skills.model.SkillMetadata;
import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * SKILL.md 解析结果
 *
 * 采用 Result 模式（类似 Rust 的 Result<T, E>），明确区分成功和失败状态。
 *
 * 设计特点：
 * 1. 支持多个错误（验证可能发现多个问题）
 * 2. 包含原始内容（便于调试）
 * 3. 提供便捷的工厂方法
 */
@Data
@Builder
public class SkillParseResult {

    /**
     * 是否解析成功
     */
    private boolean valid;

    /**
     * 解析得到的元数据（成功时有值）
     */
    private SkillMetadata metadata;

    /**
     * 正文内容（frontmatter 之后的部分）
     */
    private String body;

    /**
     * 原始 YAML frontmatter（用于调试）
     */
    private String rawFrontmatter;

    /**
     * 文件最后修改时间
     */
    private long lastModified;

    /**
     * 解析错误列表（失败时有值）
     */
    @Builder.Default
    private List<SkillParseError> errors = new ArrayList<>();

    /**
     * frontmatter 是否为空（只有注释或空白）
     * 类似 gray-matter 的 file.empty 属性
     */
    private boolean emptyFrontmatter;

    // ==================== 工厂方法 ====================

    /**
     * 创建成功结果
     */
    public static SkillParseResult success(SkillMetadata metadata, String body,
                                           String rawFrontmatter, long lastModified) {
        return SkillParseResult.builder()
                .valid(true)
                .metadata(metadata)
                .body(body)
                .rawFrontmatter(rawFrontmatter)
                .lastModified(lastModified)
                .emptyFrontmatter(false)
                .build();
    }

    /**
     * 创建单个错误的失败结果
     */
    public static SkillParseResult failure(SkillParseError error) {
        return SkillParseResult.builder()
                .valid(false)
                .errors(List.of(error))
                .build();
    }

    /**
     * 创建多个错误的失败结果
     */
    public static SkillParseResult failure(List<SkillParseError> errors) {
        return SkillParseResult.builder()
                .valid(false)
                .errors(new ArrayList<>(errors))
                .build();
    }

    /**
     * 创建带部分数据的失败结果（用于验证阶段的错误）
     */
    public static SkillParseResult failureWithContext(List<SkillParseError> errors,
                                                       String rawFrontmatter, String body) {
        return SkillParseResult.builder()
                .valid(false)
                .errors(new ArrayList<>(errors))
                .rawFrontmatter(rawFrontmatter)
                .body(body)
                .build();
    }

    // ==================== 便捷方法 ====================

    /**
     * 是否有错误
     */
    public boolean hasErrors() {
        return errors != null && !errors.isEmpty();
    }

    /**
     * 获取第一个错误（便捷方法）
     */
    public SkillParseError getFirstError() {
        return hasErrors() ? errors.get(0) : null;
    }

    /**
     * 获取格式化的错误消息
     */
    public String getErrorMessage() {
        if (!hasErrors()) {
            return null;
        }
        if (errors.size() == 1) {
            return errors.get(0).format();
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Multiple errors (").append(errors.size()).append("):\n");
        for (int i = 0; i < errors.size(); i++) {
            sb.append("  ").append(i + 1).append(". ").append(errors.get(i).format());
            if (i < errors.size() - 1) {
                sb.append("\n");
            }
        }
        return sb.toString();
    }

    /**
     * 添加错误并返回自身（链式调用）
     */
    public SkillParseResult addError(SkillParseError error) {
        if (this.errors == null) {
            this.errors = new ArrayList<>();
        }
        this.errors.add(error);
        this.valid = false;
        return this;
    }

    /**
     * 检查是否包含特定错误码
     */
    public boolean hasErrorCode(SkillParseError.ErrorCode code) {
        return errors != null && errors.stream()
                .anyMatch(e -> e.getCode() == code);
    }
}

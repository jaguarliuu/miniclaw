package com.jaguarliu.ai.skills.parser;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;

/**
 * Skill 解析错误
 *
 * 提供结构化的错误信息，包含：
 * - 错误码（用于程序化处理）
 * - 错误消息（人类可读）
 * - 位置信息（行号，便于定位）
 *
 * 设计参考：gray-matter 的错误处理模式
 */
@Data
@Builder
@AllArgsConstructor
public class SkillParseError {

    /**
     * 错误码
     */
    private ErrorCode code;

    /**
     * 错误消息
     */
    private String message;

    /**
     * 发生错误的行号（1-based，0 表示未知）
     */
    private int line;

    /**
     * 原始异常（可选）
     */
    private Throwable cause;

    /**
     * 错误码枚举
     * 按类别组织，便于前端展示和国际化
     */
    @Getter
    public enum ErrorCode {
        // 文件级错误 (1xx)
        FILE_NOT_FOUND("E101", "File not found"),
        FILE_READ_ERROR("E102", "Failed to read file"),
        FILE_ENCODING_ERROR("E103", "Invalid file encoding"),

        // Frontmatter 格式错误 (2xx)
        MISSING_FRONTMATTER("E201", "Missing YAML frontmatter"),
        UNCLOSED_FRONTMATTER("E202", "Unclosed frontmatter delimiter"),
        EMPTY_FRONTMATTER("E203", "Empty frontmatter"),

        // YAML 解析错误 (3xx)
        YAML_SYNTAX_ERROR("E301", "YAML syntax error"),
        YAML_MAPPING_ERROR("E302", "Failed to map YAML to object"),

        // Schema 验证错误 (4xx)
        MISSING_REQUIRED_FIELD("E401", "Missing required field"),
        INVALID_FIELD_TYPE("E402", "Invalid field type"),
        INVALID_FIELD_VALUE("E403", "Invalid field value"),
        INVALID_FIELD_FORMAT("E404", "Invalid field format"),

        // 安全错误 (5xx)
        CONTENT_TOO_LARGE("E501", "Content exceeds size limit"),
        UNSAFE_YAML_CONTENT("E502", "Potentially unsafe YAML content");

        private final String code;
        private final String defaultMessage;

        ErrorCode(String code, String defaultMessage) {
            this.code = code;
            this.defaultMessage = defaultMessage;
        }
    }

    /**
     * 创建文件未找到错误
     */
    public static SkillParseError fileNotFound(String path) {
        return SkillParseError.builder()
                .code(ErrorCode.FILE_NOT_FOUND)
                .message("File not found: " + path)
                .line(0)
                .build();
    }

    /**
     * 创建文件读取错误
     */
    public static SkillParseError fileReadError(String path, Throwable cause) {
        return SkillParseError.builder()
                .code(ErrorCode.FILE_READ_ERROR)
                .message("Failed to read file: " + path + " - " + cause.getMessage())
                .line(0)
                .cause(cause)
                .build();
    }

    /**
     * 创建缺失 frontmatter 错误
     */
    public static SkillParseError missingFrontmatter() {
        return SkillParseError.builder()
                .code(ErrorCode.MISSING_FRONTMATTER)
                .message("SKILL.md must start with YAML frontmatter (---)")
                .line(1)
                .build();
    }

    /**
     * 创建未闭合 frontmatter 错误
     */
    public static SkillParseError unclosedFrontmatter(int startLine) {
        return SkillParseError.builder()
                .code(ErrorCode.UNCLOSED_FRONTMATTER)
                .message("Frontmatter opened at line " + startLine + " is not closed with ---")
                .line(startLine)
                .build();
    }

    /**
     * 创建空 frontmatter 错误
     */
    public static SkillParseError emptyFrontmatter() {
        return SkillParseError.builder()
                .code(ErrorCode.EMPTY_FRONTMATTER)
                .message("Frontmatter is empty or contains only whitespace/comments")
                .line(1)
                .build();
    }

    /**
     * 创建 YAML 语法错误
     */
    public static SkillParseError yamlSyntaxError(int line, Throwable cause) {
        String msg = cause.getMessage();
        // 提取更友好的错误信息
        if (msg != null && msg.contains("line ")) {
            // 使用原始消息
        } else {
            msg = "YAML syntax error at line " + line + ": " + msg;
        }
        return SkillParseError.builder()
                .code(ErrorCode.YAML_SYNTAX_ERROR)
                .message(msg)
                .line(line)
                .cause(cause)
                .build();
    }

    /**
     * 创建必填字段缺失错误
     */
    public static SkillParseError missingRequiredField(String fieldName) {
        return SkillParseError.builder()
                .code(ErrorCode.MISSING_REQUIRED_FIELD)
                .message("Missing required field: '" + fieldName + "'")
                .line(0)
                .build();
    }

    /**
     * 创建字段类型错误
     */
    public static SkillParseError invalidFieldType(String fieldName, String expectedType, String actualType) {
        return SkillParseError.builder()
                .code(ErrorCode.INVALID_FIELD_TYPE)
                .message("Field '" + fieldName + "' should be " + expectedType + ", got " + actualType)
                .line(0)
                .build();
    }

    /**
     * 创建字段格式错误
     */
    public static SkillParseError invalidFieldFormat(String fieldName, String pattern, String value) {
        return SkillParseError.builder()
                .code(ErrorCode.INVALID_FIELD_FORMAT)
                .message("Field '" + fieldName + "' value '" + value + "' does not match pattern: " + pattern)
                .line(0)
                .build();
    }

    /**
     * 创建内容过大错误
     */
    public static SkillParseError contentTooLarge(long size, long maxSize) {
        return SkillParseError.builder()
                .code(ErrorCode.CONTENT_TOO_LARGE)
                .message("Content size " + size + " bytes exceeds limit of " + maxSize + " bytes")
                .line(0)
                .build();
    }

    /**
     * 格式化为用户友好的字符串
     */
    public String format() {
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(code.getCode()).append("] ");
        sb.append(message);
        if (line > 0) {
            sb.append(" (line ").append(line).append(")");
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return format();
    }
}

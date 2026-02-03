package com.jaguarliu.ai.skills.parser;

import lombok.Data;

/**
 * Frontmatter 提取器
 *
 * 核心设计原则（来自 gray-matter）：
 * - 不使用正则表达式进行核心解析
 * - 使用状态机逐行处理，更准确地处理边界情况
 * - 支持各种 edge cases（嵌套的 ---、代码块中的 ---）
 *
 * 支持的 frontmatter 格式：
 * ---
 * key: value
 * ---
 * content here
 */
public class SkillFrontmatterExtractor {

    private static final String DELIMITER = "---";
    private static final int MAX_FRONTMATTER_LINES = 1000;  // 防止无限循环

    /**
     * 提取 frontmatter 和 body
     */
    public ExtractionResult extract(String content) {
        if (content == null || content.isEmpty()) {
            return ExtractionResult.error(SkillParseError.missingFrontmatter());
        }

        // 标准化换行符
        String normalized = normalizeLineEndings(content);
        String[] lines = normalized.split("\n", -1);  // -1 保留尾部空行

        if (lines.length == 0) {
            return ExtractionResult.error(SkillParseError.missingFrontmatter());
        }

        // 状态机
        State state = State.BEFORE_OPEN;
        int openDelimiterLine = -1;
        int closeDelimiterLine = -1;
        StringBuilder frontmatterBuilder = new StringBuilder();

        for (int i = 0; i < lines.length && i < MAX_FRONTMATTER_LINES; i++) {
            String line = lines[i];
            String trimmed = line.trim();

            switch (state) {
                case BEFORE_OPEN:
                    // 第一个非空行必须是 ---
                    if (trimmed.isEmpty()) {
                        continue;  // 允许前导空行
                    }
                    if (isDelimiter(trimmed)) {
                        state = State.IN_FRONTMATTER;
                        openDelimiterLine = i + 1;  // 1-based
                    } else {
                        // 第一个非空行不是 ---，没有 frontmatter
                        return ExtractionResult.error(SkillParseError.missingFrontmatter());
                    }
                    break;

                case IN_FRONTMATTER:
                    if (isDelimiter(trimmed)) {
                        // 找到关闭分隔符
                        closeDelimiterLine = i + 1;
                        state = State.AFTER_CLOSE;
                    } else {
                        // 累积 frontmatter 内容
                        if (frontmatterBuilder.length() > 0) {
                            frontmatterBuilder.append("\n");
                        }
                        frontmatterBuilder.append(line);
                    }
                    break;

                case AFTER_CLOSE:
                    // 不需要继续处理，跳出循环
                    break;
            }

            if (state == State.AFTER_CLOSE) {
                break;
            }
        }

        // 检查状态
        if (state == State.BEFORE_OPEN) {
            return ExtractionResult.error(SkillParseError.missingFrontmatter());
        }

        if (state == State.IN_FRONTMATTER) {
            return ExtractionResult.error(SkillParseError.unclosedFrontmatter(openDelimiterLine));
        }

        // 提取 frontmatter 和 body
        String frontmatter = frontmatterBuilder.toString();
        String body = extractBody(lines, closeDelimiterLine);

        // 检查 frontmatter 是否为空
        boolean isEmpty = isEmptyFrontmatter(frontmatter);

        return ExtractionResult.success(frontmatter, body, isEmpty, openDelimiterLine);
    }

    /**
     * 检查是否是分隔符行
     * 只接受纯粹的 --- （可选尾部空白）
     */
    private boolean isDelimiter(String trimmed) {
        // 严格模式：只匹配 ---
        // 不匹配 ---- 或 --- something
        return DELIMITER.equals(trimmed);
    }

    /**
     * 标准化换行符
     */
    private String normalizeLineEndings(String content) {
        return content.replace("\r\n", "\n").replace("\r", "\n");
    }

    /**
     * 提取 body（关闭分隔符之后的内容）
     */
    private String extractBody(String[] lines, int closeDelimiterLine) {
        if (closeDelimiterLine >= lines.length) {
            return "";
        }

        StringBuilder body = new StringBuilder();
        // 跳过关闭分隔符后的第一个空行（如果有）
        int startLine = closeDelimiterLine;  // 0-based index = closeDelimiterLine (1-based) - 1 + 1

        // 找到第一个非空行
        while (startLine < lines.length && lines[startLine].trim().isEmpty()) {
            startLine++;
        }

        for (int i = startLine; i < lines.length; i++) {
            if (body.length() > 0) {
                body.append("\n");
            }
            body.append(lines[i]);
        }

        return body.toString();
    }

    /**
     * 检查 frontmatter 是否为空（只有空白或 YAML 注释）
     */
    private boolean isEmptyFrontmatter(String frontmatter) {
        if (frontmatter == null || frontmatter.isEmpty()) {
            return true;
        }

        // 移除所有注释和空白
        String[] lines = frontmatter.split("\n");
        for (String line : lines) {
            String trimmed = line.trim();
            // 跳过空行和注释行
            if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                return false;
            }
        }
        return true;
    }

    /**
     * 解析状态
     */
    private enum State {
        BEFORE_OPEN,      // 还没找到开始的 ---
        IN_FRONTMATTER,   // 在 frontmatter 内部
        AFTER_CLOSE       // 已经找到结束的 ---
    }

    /**
     * 提取结果
     */
    @Data
    public static class ExtractionResult {
        private boolean success;
        private String frontmatter;
        private String body;
        private boolean emptyFrontmatter;
        private int frontmatterStartLine;  // 1-based
        private SkillParseError error;

        private ExtractionResult() {}

        public static ExtractionResult success(String frontmatter, String body,
                                               boolean isEmpty, int startLine) {
            ExtractionResult result = new ExtractionResult();
            result.success = true;
            result.frontmatter = frontmatter;
            result.body = body;
            result.emptyFrontmatter = isEmpty;
            result.frontmatterStartLine = startLine;
            return result;
        }

        public static ExtractionResult error(SkillParseError error) {
            ExtractionResult result = new ExtractionResult();
            result.success = false;
            result.error = error;
            return result;
        }
    }
}

package com.jaguarliu.ai.skills.parser;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SkillFrontmatterExtractor 单元测试
 *
 * 专门测试状态机实现的 frontmatter 提取逻辑
 */
@DisplayName("SkillFrontmatterExtractor Tests")
class SkillFrontmatterExtractorTest {

    private SkillFrontmatterExtractor extractor;

    @BeforeEach
    void setUp() {
        extractor = new SkillFrontmatterExtractor();
    }

    @Nested
    @DisplayName("成功提取")
    class SuccessfulExtraction {

        @Test
        @DisplayName("标准格式提取")
        void standardFormat() {
            String content = """
                    ---
                    key: value
                    another: data
                    ---

                    Body content here.
                    """;

            var result = extractor.extract(content);

            assertTrue(result.isSuccess());
            assertEquals("key: value\nanother: data", result.getFrontmatter());
            assertTrue(result.getBody().contains("Body content here."));
            assertFalse(result.isEmptyFrontmatter());
        }

        @Test
        @DisplayName("前导空行")
        void leadingEmptyLines() {
            String content = """


                    ---
                    key: value
                    ---
                    Body
                    """;

            var result = extractor.extract(content);

            assertTrue(result.isSuccess());
            assertEquals("key: value", result.getFrontmatter());
        }

        @Test
        @DisplayName("frontmatter 内有空行")
        void emptyLinesInFrontmatter() {
            String content = """
                    ---
                    key: value

                    another: data
                    ---
                    Body
                    """;

            var result = extractor.extract(content);

            assertTrue(result.isSuccess());
            assertTrue(result.getFrontmatter().contains("key: value"));
            assertTrue(result.getFrontmatter().contains("another: data"));
        }

        @Test
        @DisplayName("body 开头有空行（会被去除）")
        void emptyLinesBeforeBody() {
            String content = """
                    ---
                    key: value
                    ---



                    Body starts here.
                    """;

            var result = extractor.extract(content);

            assertTrue(result.isSuccess());
            assertTrue(result.getBody().startsWith("Body starts here."));
        }

        @Test
        @DisplayName("没有 body")
        void noBody() {
            String content = """
                    ---
                    key: value
                    ---
                    """;

            var result = extractor.extract(content);

            assertTrue(result.isSuccess());
            assertEquals("key: value", result.getFrontmatter());
            assertTrue(result.getBody().isEmpty());
        }

        @Test
        @DisplayName("body 中包含 ---")
        void dashesInBody() {
            String content = """
                    ---
                    key: value
                    ---

                    Here is some content.

                    ---

                    This dash line should be in body.

                    ```
                    ---
                    yaml: example
                    ---
                    ```
                    """;

            var result = extractor.extract(content);

            assertTrue(result.isSuccess());
            assertEquals("key: value", result.getFrontmatter());
            assertTrue(result.getBody().contains("This dash line should be in body."));
            assertTrue(result.getBody().contains("yaml: example"));
        }
    }

    @Nested
    @DisplayName("空 frontmatter 检测")
    class EmptyFrontmatterDetection {

        @Test
        @DisplayName("完全空的 frontmatter")
        void completelyEmpty() {
            String content = """
                    ---
                    ---
                    Body
                    """;

            var result = extractor.extract(content);

            assertTrue(result.isSuccess());
            assertTrue(result.isEmptyFrontmatter());
        }

        @Test
        @DisplayName("只有空白的 frontmatter")
        void onlyWhitespace() {
            String content = "---\n   \n\t\n---\nBody";

            var result = extractor.extract(content);

            assertTrue(result.isSuccess());
            assertTrue(result.isEmptyFrontmatter());
        }

        @Test
        @DisplayName("只有注释的 frontmatter")
        void onlyComments() {
            String content = """
                    ---
                    # This is a comment
                    # Another comment
                    ---
                    Body
                    """;

            var result = extractor.extract(content);

            assertTrue(result.isSuccess());
            assertTrue(result.isEmptyFrontmatter());
        }

        @Test
        @DisplayName("注释和实际内容混合")
        void commentsWithContent() {
            String content = """
                    ---
                    # Comment
                    key: value
                    # Another comment
                    ---
                    Body
                    """;

            var result = extractor.extract(content);

            assertTrue(result.isSuccess());
            assertFalse(result.isEmptyFrontmatter());
        }
    }

    @Nested
    @DisplayName("错误情况")
    class ErrorCases {

        @Test
        @DisplayName("没有开始分隔符")
        void noOpeningDelimiter() {
            String content = """
                    key: value
                    ---
                    Body
                    """;

            var result = extractor.extract(content);

            assertFalse(result.isSuccess());
            assertEquals(SkillParseError.ErrorCode.MISSING_FRONTMATTER, result.getError().getCode());
        }

        @Test
        @DisplayName("没有结束分隔符")
        void noClosingDelimiter() {
            String content = """
                    ---
                    key: value
                    Body without closing
                    """;

            var result = extractor.extract(content);

            assertFalse(result.isSuccess());
            assertEquals(SkillParseError.ErrorCode.UNCLOSED_FRONTMATTER, result.getError().getCode());
        }

        @Test
        @DisplayName("空字符串")
        void emptyString() {
            var result = extractor.extract("");

            assertFalse(result.isSuccess());
            assertEquals(SkillParseError.ErrorCode.MISSING_FRONTMATTER, result.getError().getCode());
        }

        @Test
        @DisplayName("null 输入")
        void nullInput() {
            var result = extractor.extract(null);

            assertFalse(result.isSuccess());
            assertEquals(SkillParseError.ErrorCode.MISSING_FRONTMATTER, result.getError().getCode());
        }

        @Test
        @DisplayName("分隔符不是独立行（有额外内容）")
        void delimiterWithExtraContent() {
            String content = """
                    --- start
                    key: value
                    ---
                    Body
                    """;

            var result = extractor.extract(content);

            // 第一行不是纯 ---，所以被视为没有 frontmatter
            assertFalse(result.isSuccess());
            assertEquals(SkillParseError.ErrorCode.MISSING_FRONTMATTER, result.getError().getCode());
        }

        @Test
        @DisplayName("四个横线不被识别为分隔符")
        void fourDashes() {
            String content = """
                    ----
                    key: value
                    ----
                    Body
                    """;

            var result = extractor.extract(content);

            // ---- 不等于 ---，所以没有 frontmatter
            assertFalse(result.isSuccess());
        }
    }

    @Nested
    @DisplayName("换行符处理")
    class LineEndingHandling {

        @Test
        @DisplayName("Unix 换行符 (LF)")
        void unixLineEndings() {
            String content = "---\nkey: value\n---\nBody";

            var result = extractor.extract(content);

            assertTrue(result.isSuccess());
            assertEquals("key: value", result.getFrontmatter());
        }

        @Test
        @DisplayName("Windows 换行符 (CRLF)")
        void windowsLineEndings() {
            String content = "---\r\nkey: value\r\n---\r\nBody";

            var result = extractor.extract(content);

            assertTrue(result.isSuccess());
            assertEquals("key: value", result.getFrontmatter());
        }

        @Test
        @DisplayName("旧 Mac 换行符 (CR)")
        void oldMacLineEndings() {
            String content = "---\rkey: value\r---\rBody";

            var result = extractor.extract(content);

            assertTrue(result.isSuccess());
            assertEquals("key: value", result.getFrontmatter());
        }

        @Test
        @DisplayName("混合换行符")
        void mixedLineEndings() {
            String content = "---\nkey: value\r\nanother: data\r---\nBody";

            var result = extractor.extract(content);

            assertTrue(result.isSuccess());
        }
    }

    @Nested
    @DisplayName("行号追踪")
    class LineNumberTracking {

        @Test
        @DisplayName("记录 frontmatter 开始行号")
        void tracksFrontmatterStartLine() {
            String content = """
                    ---
                    key: value
                    ---
                    Body
                    """;

            var result = extractor.extract(content);

            assertTrue(result.isSuccess());
            assertEquals(1, result.getFrontmatterStartLine());
        }

        @Test
        @DisplayName("有前导空行时正确记录行号")
        void tracksLineWithLeadingEmpty() {
            String content = """


                    ---
                    key: value
                    ---
                    Body
                    """;

            var result = extractor.extract(content);

            assertTrue(result.isSuccess());
            // 第 3 行才是 ---
            assertEquals(3, result.getFrontmatterStartLine());
        }
    }
}

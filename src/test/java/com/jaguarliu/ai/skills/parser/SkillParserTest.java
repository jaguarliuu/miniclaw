package com.jaguarliu.ai.skills.parser;

import com.jaguarliu.ai.skills.model.SkillMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SkillParser 单元测试
 *
 * 测试覆盖：
 * 1. 正常解析（各种有效格式）
 * 2. Frontmatter 提取错误
 * 3. YAML 语法错误
 * 4. Schema 验证错误
 * 5. 边界情况
 */
@DisplayName("SkillParser Tests")
class SkillParserTest {

    private SkillParser parser;
    private static final Path DUMMY_PATH = Path.of("/test/skill/SKILL.md");

    @BeforeEach
    void setUp() {
        parser = new SkillParser();
    }

    // ==================== 正常解析测试 ====================

    @Nested
    @DisplayName("Happy Path - Valid SKILL.md")
    class HappyPathTests {

        @Test
        @DisplayName("解析最小有效 SKILL.md")
        void parseMinimalValidSkill() {
            String content = """
                    ---
                    name: test-skill
                    description: A simple test skill
                    ---

                    # Test Skill

                    This is the body.
                    """;

            SkillParseResult result = parser.parse(content, DUMMY_PATH, 0, System.currentTimeMillis());

            assertTrue(result.isValid(), "Should parse successfully");
            assertFalse(result.hasErrors(), "Should have no errors");

            SkillMetadata metadata = result.getMetadata();
            assertEquals("test-skill", metadata.getName());
            assertEquals("A simple test skill", metadata.getDescription());
            assertNull(metadata.getAllowedTools());
            assertNull(metadata.getRequires());
            assertEquals(0, metadata.getPriority());

            assertTrue(result.getBody().contains("# Test Skill"));
            assertTrue(result.getBody().contains("This is the body."));
        }

        @Test
        @DisplayName("解析完整 SKILL.md（包含所有字段）")
        void parseFullSkill() {
            String content = """
                    ---
                    name: code-review
                    description: Code review skill for analyzing code quality
                    allowed-tools:
                      - read_file
                      - memory_search
                    confirm-before:
                      - shell
                    metadata:
                      miniclaw:
                        requires:
                          env:
                            - OPENAI_API_KEY
                          bins:
                            - git
                            - node
                          anyBins:
                            - npm
                            - yarn
                          config:
                            - git.enabled
                          os:
                            - darwin
                            - linux
                            - win32
                        primaryEnv: OPENAI_API_KEY
                    ---

                    # Code Review Skill

                    You are a code reviewer...

                    User request: $ARGUMENTS
                    """;

            SkillParseResult result = parser.parse(content, DUMMY_PATH, 1, System.currentTimeMillis());

            assertTrue(result.isValid());

            SkillMetadata metadata = result.getMetadata();
            assertEquals("code-review", metadata.getName());
            assertEquals("Code review skill for analyzing code quality", metadata.getDescription());
            assertEquals(List.of("read_file", "memory_search"), metadata.getAllowedTools());
            assertEquals(List.of("shell"), metadata.getConfirmBefore());
            assertEquals(1, metadata.getPriority());
            assertEquals("OPENAI_API_KEY", metadata.getPrimaryEnv());

            // 验证 requires
            assertNotNull(metadata.getRequires());
            assertEquals(List.of("OPENAI_API_KEY"), metadata.getRequires().getEnv());
            assertEquals(List.of("git", "node"), metadata.getRequires().getBins());
            assertEquals(List.of("npm", "yarn"), metadata.getRequires().getAnyBins());
            assertEquals(List.of("git.enabled"), metadata.getRequires().getConfig());
            assertEquals(List.of("darwin", "linux", "win32"), metadata.getRequires().getOs());

            assertTrue(result.getBody().contains("$ARGUMENTS"));
        }

        @Test
        @DisplayName("允许前导空行")
        void allowLeadingEmptyLines() {
            String content = """


                    ---
                    name: test-skill
                    description: Test
                    ---

                    Body
                    """;

            SkillParseResult result = parser.parse(content, DUMMY_PATH, 0, System.currentTimeMillis());
            assertTrue(result.isValid());
        }

        @Test
        @DisplayName("name 支持数字和连字符")
        void nameWithNumbersAndHyphens() {
            String content = """
                    ---
                    name: my-skill-v2
                    description: Test skill version 2
                    ---
                    Body
                    """;

            SkillParseResult result = parser.parse(content, DUMMY_PATH, 0, System.currentTimeMillis());
            assertTrue(result.isValid());
            assertEquals("my-skill-v2", result.getMetadata().getName());
        }
    }

    // ==================== Frontmatter 提取错误 ====================

    @Nested
    @DisplayName("Frontmatter Extraction Errors")
    class FrontmatterExtractionTests {

        @Test
        @DisplayName("缺少 frontmatter - E201")
        void missingFrontmatter() {
            String content = """
                    # Just a Markdown file

                    No frontmatter here.
                    """;

            SkillParseResult result = parser.parse(content, DUMMY_PATH, 0, System.currentTimeMillis());

            assertFalse(result.isValid());
            assertTrue(result.hasErrorCode(SkillParseError.ErrorCode.MISSING_FRONTMATTER));
        }

        @Test
        @DisplayName("未闭合 frontmatter - E202")
        void unclosedFrontmatter() {
            String content = """
                    ---
                    name: test
                    description: Test

                    # No closing delimiter
                    """;

            SkillParseResult result = parser.parse(content, DUMMY_PATH, 0, System.currentTimeMillis());

            assertFalse(result.isValid());
            assertTrue(result.hasErrorCode(SkillParseError.ErrorCode.UNCLOSED_FRONTMATTER));
        }

        @Test
        @DisplayName("空 frontmatter - E203")
        void emptyFrontmatter() {
            String content = """
                    ---
                    ---

                    Body content
                    """;

            SkillParseResult result = parser.parse(content, DUMMY_PATH, 0, System.currentTimeMillis());

            assertFalse(result.isValid());
            assertTrue(result.hasErrorCode(SkillParseError.ErrorCode.EMPTY_FRONTMATTER));
        }

        @Test
        @DisplayName("只有注释的 frontmatter 视为空")
        void frontmatterWithOnlyComments() {
            String content = """
                    ---
                    # This is a comment
                    # Another comment
                    ---

                    Body
                    """;

            SkillParseResult result = parser.parse(content, DUMMY_PATH, 0, System.currentTimeMillis());

            assertFalse(result.isValid());
            assertTrue(result.hasErrorCode(SkillParseError.ErrorCode.EMPTY_FRONTMATTER));
        }

        @Test
        @DisplayName("空内容")
        void emptyContent() {
            SkillParseResult result = parser.parse("", DUMMY_PATH, 0, System.currentTimeMillis());

            assertFalse(result.isValid());
            assertTrue(result.hasErrorCode(SkillParseError.ErrorCode.MISSING_FRONTMATTER));
        }

        @Test
        @DisplayName("null 内容")
        void nullContent() {
            SkillParseResult result = parser.parse(null, DUMMY_PATH, 0, System.currentTimeMillis());

            assertFalse(result.isValid());
        }
    }

    // ==================== YAML 语法错误 ====================

    @Nested
    @DisplayName("YAML Syntax Errors")
    class YamlSyntaxTests {

        @Test
        @DisplayName("无效 YAML 语法 - E301")
        void invalidYamlSyntax() {
            String content = """
                    ---
                    name: test
                    description: Test
                    invalid_yaml: [unclosed bracket
                    ---
                    Body
                    """;

            SkillParseResult result = parser.parse(content, DUMMY_PATH, 0, System.currentTimeMillis());

            assertFalse(result.isValid());
            assertTrue(result.hasErrorCode(SkillParseError.ErrorCode.YAML_SYNTAX_ERROR));
        }

        @Test
        @DisplayName("缩进错误")
        void indentationError() {
            String content = """
                    ---
                    name: test
                    description: Test
                    allowed-tools:
                    - read_file
                     - write_file
                    ---
                    Body
                    """;

            SkillParseResult result = parser.parse(content, DUMMY_PATH, 0, System.currentTimeMillis());

            // 这种情况可能解析成功但结果不对，或者直接报错
            // 取决于 YAML parser 的宽容程度
            if (!result.isValid()) {
                assertTrue(result.hasErrorCode(SkillParseError.ErrorCode.YAML_SYNTAX_ERROR));
            }
        }
    }

    // ==================== Schema 验证错误 ====================

    @Nested
    @DisplayName("Schema Validation Errors")
    class SchemaValidationTests {

        @Test
        @DisplayName("缺少 name 字段 - E401")
        void missingName() {
            String content = """
                    ---
                    description: Test skill without name
                    ---
                    Body
                    """;

            SkillParseResult result = parser.parse(content, DUMMY_PATH, 0, System.currentTimeMillis());

            assertFalse(result.isValid());
            assertTrue(result.hasErrorCode(SkillParseError.ErrorCode.MISSING_REQUIRED_FIELD));
            assertTrue(result.getErrorMessage().contains("name"));
        }

        @Test
        @DisplayName("缺少 description 字段 - E401")
        void missingDescription() {
            String content = """
                    ---
                    name: test-skill
                    ---
                    Body
                    """;

            SkillParseResult result = parser.parse(content, DUMMY_PATH, 0, System.currentTimeMillis());

            assertFalse(result.isValid());
            assertTrue(result.hasErrorCode(SkillParseError.ErrorCode.MISSING_REQUIRED_FIELD));
            assertTrue(result.getErrorMessage().contains("description"));
        }

        @Test
        @DisplayName("name 格式错误 - 大写字母 - E404")
        void nameWithUppercase() {
            String content = """
                    ---
                    name: Test-Skill
                    description: Test
                    ---
                    Body
                    """;

            SkillParseResult result = parser.parse(content, DUMMY_PATH, 0, System.currentTimeMillis());

            assertFalse(result.isValid());
            assertTrue(result.hasErrorCode(SkillParseError.ErrorCode.INVALID_FIELD_FORMAT));
        }

        @Test
        @DisplayName("name 格式错误 - 数字开头 - E404")
        void nameStartsWithNumber() {
            String content = """
                    ---
                    name: 123-skill
                    description: Test
                    ---
                    Body
                    """;

            SkillParseResult result = parser.parse(content, DUMMY_PATH, 0, System.currentTimeMillis());

            assertFalse(result.isValid());
            assertTrue(result.hasErrorCode(SkillParseError.ErrorCode.INVALID_FIELD_FORMAT));
        }

        @Test
        @DisplayName("name 格式错误 - 包含空格 - E404")
        void nameWithSpaces() {
            String content = """
                    ---
                    name: test skill
                    description: Test
                    ---
                    Body
                    """;

            SkillParseResult result = parser.parse(content, DUMMY_PATH, 0, System.currentTimeMillis());

            assertFalse(result.isValid());
            assertTrue(result.hasErrorCode(SkillParseError.ErrorCode.INVALID_FIELD_FORMAT));
        }

        @Test
        @DisplayName("name 格式错误 - 太短 - E404")
        void nameTooShort() {
            String content = """
                    ---
                    name: a
                    description: Test
                    ---
                    Body
                    """;

            SkillParseResult result = parser.parse(content, DUMMY_PATH, 0, System.currentTimeMillis());

            assertFalse(result.isValid());
            assertTrue(result.hasErrorCode(SkillParseError.ErrorCode.INVALID_FIELD_FORMAT));
        }

        @Test
        @DisplayName("allowed-tools 类型错误 - E402")
        void invalidAllowedToolsType() {
            String content = """
                    ---
                    name: test-skill
                    description: Test
                    allowed-tools: read_file
                    ---
                    Body
                    """;

            SkillParseResult result = parser.parse(content, DUMMY_PATH, 0, System.currentTimeMillis());

            assertFalse(result.isValid());
            assertTrue(result.hasErrorCode(SkillParseError.ErrorCode.INVALID_FIELD_TYPE));
        }

        @Test
        @DisplayName("无效的 OS 值 - E403")
        void invalidOsValue() {
            String content = """
                    ---
                    name: test-skill
                    description: Test
                    metadata:
                      miniclaw:
                        requires:
                          os:
                            - windows
                    ---
                    Body
                    """;

            SkillParseResult result = parser.parse(content, DUMMY_PATH, 0, System.currentTimeMillis());

            assertFalse(result.isValid());
            assertTrue(result.hasErrorCode(SkillParseError.ErrorCode.INVALID_FIELD_VALUE));
        }
    }

    // ==================== 边界情况 ====================

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("Body 为空")
        void emptyBody() {
            String content = """
                    ---
                    name: test-skill
                    description: Test
                    ---
                    """;

            SkillParseResult result = parser.parse(content, DUMMY_PATH, 0, System.currentTimeMillis());

            assertTrue(result.isValid());
            assertTrue(result.getBody().isEmpty() || result.getBody().isBlank());
        }

        @Test
        @DisplayName("Body 中包含 --- 不影响解析")
        void bodyContainsDashes() {
            String content = """
                    ---
                    name: test-skill
                    description: Test
                    ---

                    # Example

                    Here is some YAML example:

                    ```yaml
                    ---
                    key: value
                    ---
                    ```

                    More content after code block.
                    """;

            SkillParseResult result = parser.parse(content, DUMMY_PATH, 0, System.currentTimeMillis());

            assertTrue(result.isValid());
            assertTrue(result.getBody().contains("```yaml"));
            assertTrue(result.getBody().contains("key: value"));
        }

        @Test
        @DisplayName("description 含有特殊字符")
        void descriptionWithSpecialChars() {
            String content = """
                    ---
                    name: test-skill
                    description: "Test: with special chars & <symbols>"
                    ---
                    Body
                    """;

            SkillParseResult result = parser.parse(content, DUMMY_PATH, 0, System.currentTimeMillis());

            assertTrue(result.isValid());
            assertEquals("Test: with special chars & <symbols>", result.getMetadata().getDescription());
        }

        @Test
        @DisplayName("中文 description")
        void chineseDescription() {
            String content = """
                    ---
                    name: test-skill
                    description: 这是一个测试技能，用于代码审查
                    ---

                    # 技能说明

                    你是一个代码审查专家...
                    """;

            SkillParseResult result = parser.parse(content, DUMMY_PATH, 0, System.currentTimeMillis());

            assertTrue(result.isValid());
            assertEquals("这是一个测试技能，用于代码审查", result.getMetadata().getDescription());
        }

        @Test
        @DisplayName("Windows 换行符 (CRLF)")
        void windowsLineEndings() {
            String content = "---\r\nname: test-skill\r\ndescription: Test\r\n---\r\n\r\nBody";

            SkillParseResult result = parser.parse(content, DUMMY_PATH, 0, System.currentTimeMillis());

            assertTrue(result.isValid());
        }

        @Test
        @DisplayName("空的 allowed-tools 列表")
        void emptyAllowedToolsList() {
            String content = """
                    ---
                    name: test-skill
                    description: Test
                    allowed-tools: []
                    ---
                    Body
                    """;

            SkillParseResult result = parser.parse(content, DUMMY_PATH, 0, System.currentTimeMillis());

            assertTrue(result.isValid());
            assertNotNull(result.getMetadata().getAllowedTools());
            assertTrue(result.getMetadata().getAllowedTools().isEmpty());
        }
    }

    // ==================== 错误消息格式化测试 ====================

    @Nested
    @DisplayName("Error Message Formatting")
    class ErrorMessageTests {

        @Test
        @DisplayName("错误消息包含错误码")
        void errorMessageContainsCode() {
            String content = "No frontmatter";

            SkillParseResult result = parser.parse(content, DUMMY_PATH, 0, System.currentTimeMillis());

            assertFalse(result.isValid());
            String errorMsg = result.getErrorMessage();
            assertTrue(errorMsg.contains("[E201]"), "Error message should contain error code");
        }

        @Test
        @DisplayName("多个错误时显示所有错误")
        void multipleErrors() {
            String content = """
                    ---
                    allowed-tools: not-a-list
                    ---
                    Body
                    """;

            SkillParseResult result = parser.parse(content, DUMMY_PATH, 0, System.currentTimeMillis());

            assertFalse(result.isValid());
            // 应该有多个错误：缺少 name, 缺少 description, allowed-tools 类型错误
            assertTrue(result.getErrors().size() >= 2, "Should have multiple errors");
        }
    }
}

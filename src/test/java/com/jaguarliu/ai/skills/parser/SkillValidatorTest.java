package com.jaguarliu.ai.skills.parser;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SkillValidator 单元测试
 */
@DisplayName("SkillValidator Tests")
class SkillValidatorTest {

    private SkillValidator validator;

    @BeforeEach
    void setUp() {
        validator = new SkillValidator();
    }

    @Nested
    @DisplayName("必填字段验证")
    class RequiredFieldsValidation {

        @Test
        @DisplayName("完整的有效数据")
        void validData() {
            Map<String, Object> frontmatter = Map.of(
                    "name", "test-skill",
                    "description", "A test skill"
            );

            List<SkillParseError> errors = validator.validate(frontmatter);

            assertTrue(errors.isEmpty());
        }

        @Test
        @DisplayName("缺少 name")
        void missingName() {
            Map<String, Object> frontmatter = Map.of(
                    "description", "A test skill"
            );

            List<SkillParseError> errors = validator.validate(frontmatter);

            assertEquals(1, errors.size());
            assertEquals(SkillParseError.ErrorCode.MISSING_REQUIRED_FIELD, errors.get(0).getCode());
            assertTrue(errors.get(0).getMessage().contains("name"));
        }

        @Test
        @DisplayName("缺少 description")
        void missingDescription() {
            Map<String, Object> frontmatter = Map.of(
                    "name", "test-skill"
            );

            List<SkillParseError> errors = validator.validate(frontmatter);

            assertEquals(1, errors.size());
            assertTrue(errors.get(0).getMessage().contains("description"));
        }

        @Test
        @DisplayName("空 name")
        void emptyName() {
            Map<String, Object> frontmatter = Map.of(
                    "name", "",
                    "description", "Test"
            );

            List<SkillParseError> errors = validator.validate(frontmatter);

            assertFalse(errors.isEmpty());
            assertTrue(errors.stream().anyMatch(e ->
                    e.getCode() == SkillParseError.ErrorCode.MISSING_REQUIRED_FIELD));
        }

        @Test
        @DisplayName("空白 name")
        void blankName() {
            Map<String, Object> frontmatter = Map.of(
                    "name", "   ",
                    "description", "Test"
            );

            List<SkillParseError> errors = validator.validate(frontmatter);

            assertFalse(errors.isEmpty());
        }

        @Test
        @DisplayName("null map")
        void nullMap() {
            List<SkillParseError> errors = validator.validate(null);

            assertFalse(errors.isEmpty());
            assertEquals(SkillParseError.ErrorCode.EMPTY_FRONTMATTER, errors.get(0).getCode());
        }

        @Test
        @DisplayName("空 map")
        void emptyMap() {
            List<SkillParseError> errors = validator.validate(Map.of());

            assertFalse(errors.isEmpty());
            assertEquals(SkillParseError.ErrorCode.EMPTY_FRONTMATTER, errors.get(0).getCode());
        }
    }

    @Nested
    @DisplayName("Name 格式验证")
    class NameFormatValidation {

        @Test
        @DisplayName("有效的 name 格式")
        void validNames() {
            String[] validNames = {
                    "ab",               // 最短
                    "test-skill",
                    "my-skill-v2",
                    "code-review",
                    "a" + "b".repeat(49)  // 50 字符
            };

            for (String name : validNames) {
                Map<String, Object> frontmatter = Map.of(
                        "name", name,
                        "description", "Test"
                );

                List<SkillParseError> errors = validator.validate(frontmatter);
                assertTrue(errors.isEmpty(), "Name '" + name + "' should be valid");
            }
        }

        @Test
        @DisplayName("无效的 name 格式 - 大写字母")
        void uppercaseName() {
            Map<String, Object> frontmatter = Map.of(
                    "name", "Test-Skill",
                    "description", "Test"
            );

            List<SkillParseError> errors = validator.validate(frontmatter);

            assertFalse(errors.isEmpty());
            assertEquals(SkillParseError.ErrorCode.INVALID_FIELD_FORMAT, errors.get(0).getCode());
        }

        @Test
        @DisplayName("无效的 name 格式 - 数字开头")
        void nameStartsWithNumber() {
            Map<String, Object> frontmatter = Map.of(
                    "name", "1-skill",
                    "description", "Test"
            );

            List<SkillParseError> errors = validator.validate(frontmatter);

            assertFalse(errors.isEmpty());
            assertEquals(SkillParseError.ErrorCode.INVALID_FIELD_FORMAT, errors.get(0).getCode());
        }

        @Test
        @DisplayName("无效的 name 格式 - 包含空格")
        void nameWithSpaces() {
            Map<String, Object> frontmatter = Map.of(
                    "name", "test skill",
                    "description", "Test"
            );

            List<SkillParseError> errors = validator.validate(frontmatter);

            assertFalse(errors.isEmpty());
        }

        @Test
        @DisplayName("无效的 name 格式 - 包含下划线")
        void nameWithUnderscore() {
            Map<String, Object> frontmatter = Map.of(
                    "name", "test_skill",
                    "description", "Test"
            );

            List<SkillParseError> errors = validator.validate(frontmatter);

            assertFalse(errors.isEmpty());
        }

        @Test
        @DisplayName("无效的 name 格式 - 太短（1 字符）")
        void nameTooShort() {
            Map<String, Object> frontmatter = Map.of(
                    "name", "a",
                    "description", "Test"
            );

            List<SkillParseError> errors = validator.validate(frontmatter);

            assertFalse(errors.isEmpty());
        }

        @Test
        @DisplayName("无效的 name 格式 - 太长（51 字符）")
        void nameTooLong() {
            Map<String, Object> frontmatter = Map.of(
                    "name", "a" + "b".repeat(50),
                    "description", "Test"
            );

            List<SkillParseError> errors = validator.validate(frontmatter);

            assertFalse(errors.isEmpty());
        }
    }

    @Nested
    @DisplayName("可选字段类型验证")
    class OptionalFieldTypeValidation {

        @Test
        @DisplayName("allowed-tools 应该是列表")
        void allowedToolsShouldBeList() {
            Map<String, Object> frontmatter = Map.of(
                    "name", "test-skill",
                    "description", "Test",
                    "allowed-tools", "read_file"  // 错误：应该是列表
            );

            List<SkillParseError> errors = validator.validate(frontmatter);

            assertFalse(errors.isEmpty());
            assertEquals(SkillParseError.ErrorCode.INVALID_FIELD_TYPE, errors.get(0).getCode());
        }

        @Test
        @DisplayName("allowed-tools 列表元素应该是字符串")
        void allowedToolsElementsShouldBeStrings() {
            Map<String, Object> frontmatter = Map.of(
                    "name", "test-skill",
                    "description", "Test",
                    "allowed-tools", List.of("read_file", 123)  // 123 不是字符串
            );

            List<SkillParseError> errors = validator.validate(frontmatter);

            assertFalse(errors.isEmpty());
            assertTrue(errors.get(0).getMessage().contains("allowed-tools[1]"));
        }

        @Test
        @DisplayName("confirm-before 应该是列表")
        void confirmBeforeShouldBeList() {
            Map<String, Object> frontmatter = Map.of(
                    "name", "test-skill",
                    "description", "Test",
                    "confirm-before", "shell"
            );

            List<SkillParseError> errors = validator.validate(frontmatter);

            assertFalse(errors.isEmpty());
            assertEquals(SkillParseError.ErrorCode.INVALID_FIELD_TYPE, errors.get(0).getCode());
        }
    }

    @Nested
    @DisplayName("Metadata 结构验证")
    class MetadataValidation {

        @Test
        @DisplayName("有效的完整 metadata")
        void validFullMetadata() {
            Map<String, Object> frontmatter = new HashMap<>();
            frontmatter.put("name", "test-skill");
            frontmatter.put("description", "Test");
            frontmatter.put("metadata", Map.of(
                    "miniclaw", Map.of(
                            "requires", Map.of(
                                    "env", List.of("API_KEY"),
                                    "bins", List.of("git"),
                                    "os", List.of("darwin", "linux")
                            ),
                            "primaryEnv", "API_KEY"
                    )
            ));

            List<SkillParseError> errors = validator.validate(frontmatter);

            assertTrue(errors.isEmpty());
        }

        @Test
        @DisplayName("无效的 OS 值")
        void invalidOsValue() {
            Map<String, Object> frontmatter = new HashMap<>();
            frontmatter.put("name", "test-skill");
            frontmatter.put("description", "Test");
            frontmatter.put("metadata", Map.of(
                    "miniclaw", Map.of(
                            "requires", Map.of(
                                    "os", List.of("windows")  // 应该是 win32
                            )
                    )
            ));

            List<SkillParseError> errors = validator.validate(frontmatter);

            assertFalse(errors.isEmpty());
            assertEquals(SkillParseError.ErrorCode.INVALID_FIELD_VALUE, errors.get(0).getCode());
            assertTrue(errors.get(0).getMessage().contains("windows"));
        }

        @Test
        @DisplayName("metadata 不是 Map")
        void metadataNotMap() {
            Map<String, Object> frontmatter = new HashMap<>();
            frontmatter.put("name", "test-skill");
            frontmatter.put("description", "Test");
            frontmatter.put("metadata", "invalid");

            List<SkillParseError> errors = validator.validate(frontmatter);

            assertFalse(errors.isEmpty());
            assertEquals(SkillParseError.ErrorCode.INVALID_FIELD_TYPE, errors.get(0).getCode());
        }

        @Test
        @DisplayName("primaryEnv 不是字符串")
        void primaryEnvNotString() {
            Map<String, Object> frontmatter = new HashMap<>();
            frontmatter.put("name", "test-skill");
            frontmatter.put("description", "Test");
            frontmatter.put("metadata", Map.of(
                    "miniclaw", Map.of(
                            "primaryEnv", 123
                    )
            ));

            List<SkillParseError> errors = validator.validate(frontmatter);

            assertFalse(errors.isEmpty());
            assertTrue(errors.get(0).getMessage().contains("primaryEnv"));
        }
    }

    @Nested
    @DisplayName("Description 长度验证")
    class DescriptionLengthValidation {

        @Test
        @DisplayName("正常长度的 description")
        void normalDescription() {
            Map<String, Object> frontmatter = Map.of(
                    "name", "test-skill",
                    "description", "A".repeat(500)  // 刚好 500
            );

            List<SkillParseError> errors = validator.validate(frontmatter);

            assertTrue(errors.isEmpty());
        }

        @Test
        @DisplayName("超长的 description")
        void tooLongDescription() {
            Map<String, Object> frontmatter = Map.of(
                    "name", "test-skill",
                    "description", "A".repeat(501)  // 超过 500
            );

            List<SkillParseError> errors = validator.validate(frontmatter);

            assertFalse(errors.isEmpty());
            assertEquals(SkillParseError.ErrorCode.INVALID_FIELD_VALUE, errors.get(0).getCode());
        }
    }
}

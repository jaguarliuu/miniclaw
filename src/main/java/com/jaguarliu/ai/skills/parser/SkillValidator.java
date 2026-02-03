package com.jaguarliu.ai.skills.parser;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Skill Frontmatter 验证器
 *
 * 验证从 YAML 解析出的 Map 是否符合 SKILL.md 规范。
 *
 * 验证规则：
 * 1. 必填字段：name, description
 * 2. 字段类型：确保类型正确
 * 3. 字段格式：name 必须符合 slug 格式
 * 4. 可选字段：allowed-tools, confirm-before, metadata
 */
public class SkillValidator {

    /**
     * Skill name 的格式要求：
     * - 只能包含小写字母、数字、连字符
     * - 必须以字母开头
     * - 长度 2-50
     *
     * 示例：code-review, git-commit, my-skill-123
     */
    private static final Pattern NAME_PATTERN = Pattern.compile("^[a-z][a-z0-9-]{1,49}$");

    /**
     * Description 最大长度
     */
    private static final int MAX_DESCRIPTION_LENGTH = 500;

    /**
     * 验证 frontmatter Map
     *
     * @param frontmatter 解析后的 YAML Map
     * @return 验证错误列表（空表示验证通过）
     */
    public List<SkillParseError> validate(Map<String, Object> frontmatter) {
        List<SkillParseError> errors = new ArrayList<>();

        if (frontmatter == null || frontmatter.isEmpty()) {
            errors.add(SkillParseError.emptyFrontmatter());
            return errors;
        }

        // 1. 验证必填字段
        validateRequiredFields(frontmatter, errors);

        // 如果必填字段缺失，后续验证可能会失败，提前返回
        if (!errors.isEmpty()) {
            return errors;
        }

        // 2. 验证 name 格式
        validateNameFormat(frontmatter, errors);

        // 3. 验证 description 长度
        validateDescriptionLength(frontmatter, errors);

        // 4. 验证可选字段类型
        validateOptionalFieldTypes(frontmatter, errors);

        // 5. 验证 metadata 结构（如果存在）
        validateMetadata(frontmatter, errors);

        return errors;
    }

    /**
     * 验证必填字段存在且非空
     */
    private void validateRequiredFields(Map<String, Object> frontmatter, List<SkillParseError> errors) {
        // name
        if (!frontmatter.containsKey("name")) {
            errors.add(SkillParseError.missingRequiredField("name"));
        } else {
            Object name = frontmatter.get("name");
            if (name == null || (name instanceof String && ((String) name).isBlank())) {
                errors.add(SkillParseError.missingRequiredField("name"));
            } else if (!(name instanceof String)) {
                errors.add(SkillParseError.invalidFieldType("name", "string", getTypeName(name)));
            }
        }

        // description
        if (!frontmatter.containsKey("description")) {
            errors.add(SkillParseError.missingRequiredField("description"));
        } else {
            Object desc = frontmatter.get("description");
            if (desc == null || (desc instanceof String && ((String) desc).isBlank())) {
                errors.add(SkillParseError.missingRequiredField("description"));
            } else if (!(desc instanceof String)) {
                errors.add(SkillParseError.invalidFieldType("description", "string", getTypeName(desc)));
            }
        }
    }

    /**
     * 验证 name 格式
     */
    private void validateNameFormat(Map<String, Object> frontmatter, List<SkillParseError> errors) {
        Object nameObj = frontmatter.get("name");
        if (!(nameObj instanceof String)) {
            return;  // 类型错误已在前面报告
        }

        String name = (String) nameObj;
        if (!NAME_PATTERN.matcher(name).matches()) {
            errors.add(SkillParseError.invalidFieldFormat(
                    "name",
                    "lowercase letters, numbers, hyphens; start with letter; 2-50 chars",
                    name
            ));
        }
    }

    /**
     * 验证 description 长度
     */
    private void validateDescriptionLength(Map<String, Object> frontmatter, List<SkillParseError> errors) {
        Object descObj = frontmatter.get("description");
        if (!(descObj instanceof String)) {
            return;
        }

        String desc = (String) descObj;
        if (desc.length() > MAX_DESCRIPTION_LENGTH) {
            errors.add(SkillParseError.builder()
                    .code(SkillParseError.ErrorCode.INVALID_FIELD_VALUE)
                    .message("Field 'description' exceeds maximum length of " + MAX_DESCRIPTION_LENGTH +
                            " characters (got " + desc.length() + ")")
                    .line(0)
                    .build());
        }
    }

    /**
     * 验证可选字段类型
     */
    @SuppressWarnings("unchecked")
    private void validateOptionalFieldTypes(Map<String, Object> frontmatter, List<SkillParseError> errors) {
        // allowed-tools: 应该是 List<String>
        if (frontmatter.containsKey("allowed-tools")) {
            Object value = frontmatter.get("allowed-tools");
            if (value != null && !(value instanceof List)) {
                errors.add(SkillParseError.invalidFieldType("allowed-tools", "list", getTypeName(value)));
            } else if (value instanceof List) {
                List<?> list = (List<?>) value;
                for (int i = 0; i < list.size(); i++) {
                    if (!(list.get(i) instanceof String)) {
                        errors.add(SkillParseError.invalidFieldType(
                                "allowed-tools[" + i + "]", "string", getTypeName(list.get(i))));
                    }
                }
            }
        }

        // confirm-before: 应该是 List<String>
        if (frontmatter.containsKey("confirm-before")) {
            Object value = frontmatter.get("confirm-before");
            if (value != null && !(value instanceof List)) {
                errors.add(SkillParseError.invalidFieldType("confirm-before", "list", getTypeName(value)));
            } else if (value instanceof List) {
                List<?> list = (List<?>) value;
                for (int i = 0; i < list.size(); i++) {
                    if (!(list.get(i) instanceof String)) {
                        errors.add(SkillParseError.invalidFieldType(
                                "confirm-before[" + i + "]", "string", getTypeName(list.get(i))));
                    }
                }
            }
        }
    }

    /**
     * 验证 metadata 结构
     */
    @SuppressWarnings("unchecked")
    private void validateMetadata(Map<String, Object> frontmatter, List<SkillParseError> errors) {
        if (!frontmatter.containsKey("metadata")) {
            return;
        }

        Object metadataObj = frontmatter.get("metadata");
        if (!(metadataObj instanceof Map)) {
            errors.add(SkillParseError.invalidFieldType("metadata", "object", getTypeName(metadataObj)));
            return;
        }

        Map<String, Object> metadata = (Map<String, Object>) metadataObj;

        // 检查 miniclaw 子对象
        if (metadata.containsKey("miniclaw")) {
            Object miniclawObj = metadata.get("miniclaw");
            if (!(miniclawObj instanceof Map)) {
                errors.add(SkillParseError.invalidFieldType("metadata.miniclaw", "object", getTypeName(miniclawObj)));
                return;
            }

            Map<String, Object> miniclaw = (Map<String, Object>) miniclawObj;

            // 检查 requires 子对象
            if (miniclaw.containsKey("requires")) {
                Object requiresObj = miniclaw.get("requires");
                if (!(requiresObj instanceof Map)) {
                    errors.add(SkillParseError.invalidFieldType("metadata.miniclaw.requires", "object", getTypeName(requiresObj)));
                } else {
                    validateRequires((Map<String, Object>) requiresObj, errors);
                }
            }

            // 检查 primaryEnv
            if (miniclaw.containsKey("primaryEnv")) {
                Object primaryEnv = miniclaw.get("primaryEnv");
                if (primaryEnv != null && !(primaryEnv instanceof String)) {
                    errors.add(SkillParseError.invalidFieldType("metadata.miniclaw.primaryEnv", "string", getTypeName(primaryEnv)));
                }
            }
        }
    }

    /**
     * 验证 requires 结构
     */
    private void validateRequires(Map<String, Object> requires, List<SkillParseError> errors) {
        String prefix = "metadata.miniclaw.requires.";

        // 验证各个字段是 List<String> 类型
        String[] listFields = {"env", "bins", "anyBins", "config", "os"};
        for (String field : listFields) {
            if (requires.containsKey(field)) {
                Object value = requires.get(field);
                if (value != null && !(value instanceof List)) {
                    errors.add(SkillParseError.invalidFieldType(prefix + field, "list", getTypeName(value)));
                }
            }
        }

        // 验证 os 字段的值
        if (requires.containsKey("os")) {
            Object osObj = requires.get("os");
            if (osObj instanceof List) {
                List<?> osList = (List<?>) osObj;
                for (Object os : osList) {
                    if (os instanceof String) {
                        String osStr = (String) os;
                        if (!isValidOs(osStr)) {
                            errors.add(SkillParseError.builder()
                                    .code(SkillParseError.ErrorCode.INVALID_FIELD_VALUE)
                                    .message("Invalid OS value '" + osStr + "' in " + prefix + "os. " +
                                            "Valid values: darwin, linux, win32")
                                    .line(0)
                                    .build());
                        }
                    }
                }
            }
        }
    }

    /**
     * 检查是否是有效的 OS 值
     */
    private boolean isValidOs(String os) {
        return "darwin".equals(os) || "linux".equals(os) || "win32".equals(os);
    }

    /**
     * 获取类型名称（用于错误消息）
     */
    private String getTypeName(Object obj) {
        if (obj == null) {
            return "null";
        }
        if (obj instanceof String) {
            return "string";
        }
        if (obj instanceof Number) {
            return "number";
        }
        if (obj instanceof Boolean) {
            return "boolean";
        }
        if (obj instanceof List) {
            return "list";
        }
        if (obj instanceof Map) {
            return "object";
        }
        return obj.getClass().getSimpleName();
    }
}

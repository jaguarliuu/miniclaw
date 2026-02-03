package com.jaguarliu.ai.skills.parser;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.jaguarliu.ai.skills.model.SkillMetadata;
import com.jaguarliu.ai.skills.model.SkillRequires;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * SKILL.md 解析器
 *
 * 企业级设计，参考 gray-matter 和 SnakeYAML 最佳实践：
 * 1. 不使用正则解析核心内容（使用状态机）
 * 2. 结构化错误报告（错误码 + 行号 + 消息）
 * 3. Schema 验证（必填字段 + 类型 + 格式）
 * 4. 安全性考虑（大小限制 + 安全 YAML 解析）
 *
 * 解析流程：
 * 1. 读取文件
 * 2. 提取 frontmatter（状态机）
 * 3. 解析 YAML
 * 4. 验证 Schema
 * 5. 构建 SkillMetadata
 *
 * @see <a href="https://github.com/jonschlinkert/gray-matter">gray-matter</a>
 * @see <a href="https://www.baeldung.com/java-snake-yaml">SnakeYAML Best Practices</a>
 */
@Slf4j
@Component
public class SkillParser {

    /**
     * 最大文件大小：1MB
     */
    private static final long MAX_FILE_SIZE = 1024 * 1024;

    /**
     * Jackson YAML mapper（线程安全）
     */
    private final ObjectMapper yamlMapper;

    /**
     * Frontmatter 提取器
     */
    private final SkillFrontmatterExtractor extractor;

    /**
     * Frontmatter 验证器
     */
    private final SkillValidator validator;

    public SkillParser() {
        // 配置 YAML mapper
        this.yamlMapper = new ObjectMapper(new YAMLFactory());

        // 初始化组件
        this.extractor = new SkillFrontmatterExtractor();
        this.validator = new SkillValidator();
    }

    /**
     * 解析 SKILL.md 文件
     *
     * @param skillPath SKILL.md 文件路径
     * @param priority  优先级（0=项目级, 1=用户级, 2=内置）
     * @return 解析结果
     */
    public SkillParseResult parse(Path skillPath, int priority) {
        log.debug("Parsing skill file: {}", skillPath);

        // 1. 检查文件存在
        if (!Files.exists(skillPath)) {
            return SkillParseResult.failure(SkillParseError.fileNotFound(skillPath.toString()));
        }

        // 2. 检查文件大小
        try {
            long size = Files.size(skillPath);
            if (size > MAX_FILE_SIZE) {
                return SkillParseResult.failure(SkillParseError.contentTooLarge(size, MAX_FILE_SIZE));
            }
        } catch (IOException e) {
            return SkillParseResult.failure(SkillParseError.fileReadError(skillPath.toString(), e));
        }

        // 3. 读取文件内容
        String content;
        long lastModified;
        try {
            content = Files.readString(skillPath, StandardCharsets.UTF_8);
            lastModified = Files.getLastModifiedTime(skillPath).toMillis();
        } catch (IOException e) {
            log.error("Failed to read skill file: {}", skillPath, e);
            return SkillParseResult.failure(SkillParseError.fileReadError(skillPath.toString(), e));
        }

        // 4. 调用内容解析
        return parse(content, skillPath, priority, lastModified);
    }

    /**
     * 解析 SKILL.md 内容（可用于测试）
     *
     * @param content      文件内容
     * @param sourcePath   源文件路径（用于记录）
     * @param priority     优先级
     * @param lastModified 最后修改时间
     * @return 解析结果
     */
    public SkillParseResult parse(String content, Path sourcePath, int priority, long lastModified) {
        // 1. 提取 frontmatter
        SkillFrontmatterExtractor.ExtractionResult extraction = extractor.extract(content);

        if (!extraction.isSuccess()) {
            return SkillParseResult.failure(extraction.getError());
        }

        if (extraction.isEmptyFrontmatter()) {
            return SkillParseResult.failure(SkillParseError.emptyFrontmatter());
        }

        String rawFrontmatter = extraction.getFrontmatter();
        String body = extraction.getBody();

        // 2. 解析 YAML
        Map<String, Object> frontmatterMap;
        try {
            frontmatterMap = parseYaml(rawFrontmatter);
        } catch (JsonProcessingException e) {
            log.warn("YAML parse error in {}: {}", sourcePath, e.getMessage());
            return SkillParseResult.failureWithContext(
                    List.of(SkillParseError.yamlSyntaxError(extraction.getFrontmatterStartLine(), e)),
                    rawFrontmatter,
                    body
            );
        }

        // 3. 验证 Schema
        List<SkillParseError> validationErrors = validator.validate(frontmatterMap);
        if (!validationErrors.isEmpty()) {
            log.warn("Validation errors in {}: {}", sourcePath, validationErrors.size());
            return SkillParseResult.failureWithContext(validationErrors, rawFrontmatter, body);
        }

        // 4. 构建 SkillMetadata
        SkillMetadata metadata = buildMetadata(frontmatterMap, sourcePath, priority);

        log.debug("Successfully parsed skill: {} from {}", metadata.getName(), sourcePath);

        return SkillParseResult.success(metadata, body, rawFrontmatter, lastModified);
    }

    /**
     * 解析 YAML 字符串为 Map
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseYaml(String yaml) throws JsonProcessingException {
        if (yaml == null || yaml.isBlank()) {
            return Map.of();
        }
        Object result = yamlMapper.readValue(yaml, Object.class);
        if (result instanceof Map) {
            return (Map<String, Object>) result;
        }
        return Map.of();
    }

    /**
     * 从解析后的 Map 构建 SkillMetadata
     */
    @SuppressWarnings("unchecked")
    private SkillMetadata buildMetadata(Map<String, Object> frontmatter, Path sourcePath, int priority) {
        String name = ((String) frontmatter.get("name")).trim();
        String description = ((String) frontmatter.get("description")).trim();

        // 提取可选字段
        List<String> allowedTools = (List<String>) frontmatter.get("allowed-tools");
        List<String> confirmBefore = (List<String>) frontmatter.get("confirm-before");

        // 提取 metadata.miniclaw
        SkillRequires requires = null;
        String primaryEnv = null;

        Object metadataObj = frontmatter.get("metadata");
        if (metadataObj instanceof Map) {
            Map<String, Object> metadata = (Map<String, Object>) metadataObj;
            Object miniclawObj = metadata.get("miniclaw");
            if (miniclawObj instanceof Map) {
                Map<String, Object> miniclaw = (Map<String, Object>) miniclawObj;
                requires = buildRequires(miniclaw);
                primaryEnv = (String) miniclaw.get("primaryEnv");
            }
        }

        return SkillMetadata.builder()
                .name(name)
                .description(description)
                .allowedTools(allowedTools)
                .confirmBefore(confirmBefore)
                .requires(requires)
                .primaryEnv(primaryEnv)
                .sourcePath(sourcePath)
                .priority(priority)
                .build();
    }

    /**
     * 构建 SkillRequires
     */
    @SuppressWarnings("unchecked")
    private SkillRequires buildRequires(Map<String, Object> miniclaw) {
        Object requiresObj = miniclaw.get("requires");
        if (!(requiresObj instanceof Map)) {
            return null;
        }

        Map<String, Object> requires = (Map<String, Object>) requiresObj;

        return SkillRequires.builder()
                .env((List<String>) requires.get("env"))
                .bins((List<String>) requires.get("bins"))
                .anyBins((List<String>) requires.get("anyBins"))
                .config((List<String>) requires.get("config"))
                .os((List<String>) requires.get("os"))
                .build();
    }
}

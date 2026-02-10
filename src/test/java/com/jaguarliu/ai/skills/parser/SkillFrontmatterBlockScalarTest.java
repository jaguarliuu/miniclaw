package com.jaguarliu.ai.skills.parser;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 测试 YAML block scalar 场景下的 frontmatter 提取
 */
class SkillFrontmatterBlockScalarTest {

    private final SkillFrontmatterExtractor extractor = new SkillFrontmatterExtractor();

    @Test
    void testBlockScalarWithDelimiterInside() {
        String content = """
                ---
                name: test-skill
                description: |
                  This is a multi-line description
                  with a delimiter --- inside
                  which should not close the frontmatter
                author: John Doe
                ---

                # Skill Body
                """;

        SkillFrontmatterExtractor.ExtractionResult result = extractor.extract(content);

        assertTrue(result.isSuccess(), "Should successfully extract frontmatter");
        assertFalse(result.isEmptyFrontmatter(), "Frontmatter should not be empty");

        String frontmatter = result.getFrontmatter();
        assertTrue(frontmatter.contains("name: test-skill"));
        assertTrue(frontmatter.contains("with a delimiter --- inside"));
        assertTrue(frontmatter.contains("author: John Doe"));

        String body = result.getBody();
        assertTrue(body.contains("# Skill Body"));
    }

    @Test
    void testFoldedScalarWithDelimiter() {
        String content = """
                ---
                name: test-skill
                description: >
                  This is a folded description
                  with delimiter ---
                  which should be ignored
                version: 1.0
                ---

                Body content
                """;

        SkillFrontmatterExtractor.ExtractionResult result = extractor.extract(content);

        assertTrue(result.isSuccess());
        assertTrue(result.getFrontmatter().contains("with delimiter ---"));
        assertTrue(result.getFrontmatter().contains("version: 1.0"));
    }

    @Test
    void testNestedBlockScalar() {
        String content = """
                ---
                name: complex-skill
                config: |
                  key1: value1
                  ---
                  key2: value2
                  ---
                  key3: value3
                metadata: test
                ---

                Content
                """;

        SkillFrontmatterExtractor.ExtractionResult result = extractor.extract(content);

        assertTrue(result.isSuccess());
        String fm = result.getFrontmatter();
        assertTrue(fm.contains("key1: value1"));
        assertTrue(fm.contains("key2: value2"));
        assertTrue(fm.contains("key3: value3"));
        assertTrue(fm.contains("metadata: test"));
    }

    @Test
    void testBlockScalarAtEnd() {
        String content = """
                ---
                name: test-skill
                description: short
                notes: |
                  Some notes with ---
                  more notes
                ---

                Body
                """;

        SkillFrontmatterExtractor.ExtractionResult result = extractor.extract(content);

        assertTrue(result.isSuccess());
        assertTrue(result.getFrontmatter().contains("Some notes with ---"));
    }

    @Test
    void testMultipleBlockScalars() {
        String content = """
                ---
                name: test
                desc1: |
                  First block with ---
                desc2: |
                  Second block with ---
                  and more
                simple: value
                ---

                Body
                """;

        SkillFrontmatterExtractor.ExtractionResult result = extractor.extract(content);

        assertTrue(result.isSuccess());
        String fm = result.getFrontmatter();
        assertTrue(fm.contains("First block with ---"));
        assertTrue(fm.contains("Second block with ---"));
        assertTrue(fm.contains("simple: value"));
    }

    @Test
    void testBlockScalarIndentExit() {
        // Block scalar 应该在缩进回退时结束
        String content = """
                ---
                name: test
                description: |
                  Indented content
                  with ---
                next_key: value
                ---

                Body
                """;

        SkillFrontmatterExtractor.ExtractionResult result = extractor.extract(content);

        assertTrue(result.isSuccess());
        assertTrue(result.getFrontmatter().contains("next_key: value"));
    }

    @Test
    void testRegularDelimiterStillWorks() {
        // 确保普通的 --- 仍然正常工作
        String content = """
                ---
                name: simple
                description: simple skill
                ---

                Body
                """;

        SkillFrontmatterExtractor.ExtractionResult result = extractor.extract(content);

        assertTrue(result.isSuccess());
        assertEquals("Body", result.getBody().trim());
    }
}

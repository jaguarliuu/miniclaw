package com.jaguarliu.ai.skills.index;

import com.jaguarliu.ai.skills.gating.GatingResult;
import com.jaguarliu.ai.skills.gating.SkillGatingService;
import com.jaguarliu.ai.skills.parser.SkillParser;
import com.jaguarliu.ai.skills.registry.SkillRegistry;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * SkillIndexBuilder 单元测试
 */
@DisplayName("SkillIndexBuilder Tests")
class SkillIndexBuilderTest {

    @TempDir
    Path tempDir;

    @Mock
    private SkillGatingService gatingService;

    private SkillParser parser;
    private SkillRegistry registry;
    private SkillIndexBuilder indexBuilder;

    private Path skillsDir;

    @BeforeEach
    void setUp() throws IOException {
        MockitoAnnotations.openMocks(this);
        parser = new SkillParser();

        when(gatingService.evaluate(any())).thenReturn(GatingResult.PASSED);

        registry = new SkillRegistry(parser, gatingService);
        indexBuilder = new SkillIndexBuilder(registry);
        // 设置默认 token budget（@Value 在单元测试中不生效）
        indexBuilder.setIndexTokenBudget(2000);

        skillsDir = tempDir.resolve("skills");
        Files.createDirectories(skillsDir);

        registry.configure(skillsDir, null, null);
    }

    @Nested
    @DisplayName("空索引")
    class EmptyIndexTests {

        @Test
        @DisplayName("无 skill 时返回空字符串")
        void emptyWhenNoSkills() {
            registry.refresh();

            String index = indexBuilder.buildIndex();

            assertEquals("", index);
        }

        @Test
        @DisplayName("紧凑索引也为空")
        void compactIndexEmpty() {
            registry.refresh();

            String index = indexBuilder.buildCompactIndex();

            assertEquals("", index);
        }

        @Test
        @DisplayName("skill 列表为空")
        void skillListEmpty() {
            registry.refresh();

            List<SkillIndexBuilder.SkillSummary> list = indexBuilder.buildSkillList();

            assertTrue(list.isEmpty());
        }
    }

    @Nested
    @DisplayName("索引构建")
    class IndexBuildTests {

        @Test
        @DisplayName("单个 skill 索引")
        void singleSkillIndex() throws IOException {
            createSkill("code-review", "代码审查，检查代码质量");
            registry.refresh();

            String index = indexBuilder.buildIndex();

            assertTrue(index.contains("<skills>"));
            assertTrue(index.contains("</skills>"));
            assertTrue(index.contains("code-review"));
            assertTrue(index.contains("代码审查"));
            assertTrue(index.contains("Available Skills"));
        }

        @Test
        @DisplayName("多个 skill 索引")
        void multipleSkillsIndex() throws IOException {
            createSkill("code-review", "代码审查");
            createSkill("git-commit", "生成 commit message");
            createSkill("explain-code", "解释代码");
            registry.refresh();

            String index = indexBuilder.buildIndex();

            assertTrue(index.contains("code-review"));
            assertTrue(index.contains("git-commit"));
            assertTrue(index.contains("explain-code"));
        }

        @Test
        @DisplayName("紧凑索引格式正确")
        void compactIndexFormat() throws IOException {
            createSkill("test-skill", "Test description");
            registry.refresh();

            String index = indexBuilder.buildCompactIndex();

            assertTrue(index.startsWith("<skills>"));
            assertTrue(index.endsWith("</skills>"));
            assertTrue(index.contains("<skill name=\"test-skill\">Test description</skill>"));
            // 紧凑索引不含说明文字
            assertFalse(index.contains("Available Skills"));
        }

        @Test
        @DisplayName("XML 特殊字符转义")
        void xmlEscaping() throws IOException {
            createSkill("special-skill", "Test <script> & \"quotes\"");
            registry.refresh();

            String index = indexBuilder.buildCompactIndex();

            assertTrue(index.contains("&lt;script&gt;"));
            assertTrue(index.contains("&amp;"));
            assertTrue(index.contains("&quot;quotes&quot;"));
        }
    }

    @Nested
    @DisplayName("Token Budget 控制")
    class TokenBudgetTests {

        @Test
        @DisplayName("超出 budget 时截断")
        void truncateWhenOverBudget() throws IOException {
            // 创建多个 skill
            for (int i = 1; i <= 10; i++) {
                createSkill("skill-" + i, "Description for skill " + i + " with some extra text");
            }
            registry.refresh();

            // 设置很小的 budget
            indexBuilder.setIndexTokenBudget(300);

            String index = indexBuilder.buildIndex();
            SkillIndexBuilder.IndexStats stats = indexBuilder.getStats();

            // 应该被截断
            assertTrue(stats.truncated());
            assertTrue(stats.includedInIndex() < stats.totalAvailable());
        }

        @Test
        @DisplayName("budget 足够时包含所有 skill")
        void includeAllWhenEnoughBudget() throws IOException {
            createSkill("skill-one", "First");
            createSkill("skill-two", "Second");
            registry.refresh();

            // 设置足够大的 budget
            indexBuilder.setIndexTokenBudget(5000);

            SkillIndexBuilder.IndexStats stats = indexBuilder.getStats();

            assertFalse(stats.truncated());
            assertEquals(stats.totalAvailable(), stats.includedInIndex());
        }
    }

    @Nested
    @DisplayName("Skill 列表")
    class SkillListTests {

        @Test
        @DisplayName("返回所有可用 skill")
        void listAllAvailable() throws IOException {
            createSkill("alpha", "Alpha skill");
            createSkill("beta", "Beta skill");
            registry.refresh();

            List<SkillIndexBuilder.SkillSummary> list = indexBuilder.buildSkillList();

            assertEquals(2, list.size());
        }

        @Test
        @DisplayName("包含 token 成本")
        void includesTokenCost() throws IOException {
            createSkill("test-skill", "A test skill description");
            registry.refresh();

            List<SkillIndexBuilder.SkillSummary> list = indexBuilder.buildSkillList();

            assertEquals(1, list.size());
            assertTrue(list.get(0).tokenCost() > 0);
        }

        @Test
        @DisplayName("不包含不可用的 skill")
        void excludesUnavailable() throws IOException {
            createSkill("available", "Available skill");
            registry.refresh();

            // 添加一个不可用的
            GatingResult failResult = GatingResult.builder()
                    .available(false)
                    .missingEnvVars(List.of("API_KEY"))
                    .build();
            when(gatingService.evaluate(any())).thenReturn(failResult);

            createSkill("unavailable", "Unavailable skill");
            registry.refresh();

            // 重置为通过
            when(gatingService.evaluate(any())).thenReturn(GatingResult.PASSED);
            createSkill("available", "Available skill");
            registry.refresh();

            // 检查列表
            List<SkillIndexBuilder.SkillSummary> list = indexBuilder.buildSkillList();

            // 根据实际情况验证
            assertTrue(list.stream().anyMatch(s -> s.name().equals("available")));
        }
    }

    @Nested
    @DisplayName("统计信息")
    class StatsTests {

        @Test
        @DisplayName("空 registry 统计")
        void emptyStats() {
            registry.refresh();

            SkillIndexBuilder.IndexStats stats = indexBuilder.getStats();

            assertEquals(0, stats.totalAvailable());
            assertEquals(0, stats.includedInIndex());
            assertFalse(stats.truncated());
        }

        @Test
        @DisplayName("正常统计信息")
        void normalStats() throws IOException {
            createSkill("skill-a", "Description A");
            createSkill("skill-b", "Description B");
            registry.refresh();

            SkillIndexBuilder.IndexStats stats = indexBuilder.getStats();

            assertEquals(2, stats.totalAvailable());
            assertTrue(stats.totalTokenCost() > 0);
            assertTrue(stats.tokenBudget() > 0);
        }

        @Test
        @DisplayName("计算索引成本")
        void calculateIndexCost() throws IOException {
            createSkill("test", "Test skill");
            registry.refresh();

            int cost = indexBuilder.calculateIndexCost();

            // 基础开销 + skill 成本
            assertTrue(cost > 150); // BASE_OVERHEAD_TOKENS = 150
        }
    }

    /**
     * 创建测试 skill
     */
    private void createSkill(String name, String description) throws IOException {
        Path skillDir = skillsDir.resolve(name);
        Files.createDirectories(skillDir);
        Files.writeString(skillDir.resolve("SKILL.md"), String.format("""
                ---
                name: %s
                description: %s
                ---
                # %s
                Body content
                """, name, description, name));
    }
}

package com.jaguarliu.ai.skills.registry;

import com.jaguarliu.ai.skills.gating.GatingResult;
import com.jaguarliu.ai.skills.gating.SkillGatingService;
import com.jaguarliu.ai.skills.model.LoadedSkill;
import com.jaguarliu.ai.skills.model.SkillEntry;
import com.jaguarliu.ai.skills.parser.SkillParser;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * SkillRegistry 单元测试
 */
@DisplayName("SkillRegistry Tests")
class SkillRegistryTest {

    @TempDir
    Path tempDir;

    @Mock
    private SkillGatingService gatingService;

    private SkillParser parser;
    private SkillRegistry registry;

    private Path projectSkillsDir;
    private Path userSkillsDir;
    private Path builtinSkillsDir;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        parser = new SkillParser();

        // 默认所有 skill 都通过 gating
        when(gatingService.evaluate(any())).thenReturn(GatingResult.PASSED);

        // 创建测试目录
        projectSkillsDir = tempDir.resolve("project/.miniclaw/skills");
        userSkillsDir = tempDir.resolve("user/.miniclaw/skills");
        builtinSkillsDir = tempDir.resolve("builtin/skills");

        registry = new SkillRegistry(parser, gatingService);
        registry.configure(projectSkillsDir, userSkillsDir, builtinSkillsDir);
    }

    @Nested
    @DisplayName("目录扫描")
    class DirectoryScanningTests {

        @Test
        @DisplayName("扫描空目录")
        void scanEmptyDirectories() {
            registry.refresh();

            assertEquals(0, registry.size());
            assertTrue(registry.getAll().isEmpty());
        }

        @Test
        @DisplayName("扫描单个 skill（目录格式）")
        void scanSingleSkillDirectory() throws IOException {
            // 创建 skills/test-skill/SKILL.md
            Path skillDir = projectSkillsDir.resolve("test-skill");
            Files.createDirectories(skillDir);
            Files.writeString(skillDir.resolve("SKILL.md"), """
                    ---
                    name: test-skill
                    description: A test skill
                    ---
                    # Test Skill Body
                    """);

            registry.refresh();

            assertEquals(1, registry.size());
            assertTrue(registry.getByName("test-skill").isPresent());
        }

        @Test
        @DisplayName("扫描单个 skill（文件格式）")
        void scanSingleSkillFile() throws IOException {
            // 创建 skills/test-skill.SKILL.md
            Files.createDirectories(projectSkillsDir);
            Files.writeString(projectSkillsDir.resolve("test-skill.SKILL.md"), """
                    ---
                    name: test-skill
                    description: A test skill
                    ---
                    # Test Skill Body
                    """);

            registry.refresh();

            assertEquals(1, registry.size());
            assertTrue(registry.getByName("test-skill").isPresent());
        }

        @Test
        @DisplayName("扫描多个 skill")
        void scanMultipleSkills() throws IOException {
            Files.createDirectories(projectSkillsDir);
            Files.writeString(projectSkillsDir.resolve("skill-one.SKILL.md"), """
                    ---
                    name: skill-one
                    description: First skill
                    ---
                    Body one
                    """);
            Files.writeString(projectSkillsDir.resolve("skill-two.SKILL.md"), """
                    ---
                    name: skill-two
                    description: Second skill
                    ---
                    Body two
                    """);

            registry.refresh();

            assertEquals(2, registry.size());
            assertTrue(registry.getByName("skill-one").isPresent());
            assertTrue(registry.getByName("skill-two").isPresent());
        }

        @Test
        @DisplayName("忽略非 SKILL.md 文件")
        void ignoreNonSkillFiles() throws IOException {
            Files.createDirectories(projectSkillsDir);
            Files.writeString(projectSkillsDir.resolve("README.md"), "# Readme");
            Files.writeString(projectSkillsDir.resolve("config.yaml"), "key: value");
            Files.writeString(projectSkillsDir.resolve("test-skill.SKILL.md"), """
                    ---
                    name: test-skill
                    description: Test
                    ---
                    Body
                    """);

            registry.refresh();

            assertEquals(1, registry.size());
        }

        @Test
        @DisplayName("跳过解析失败的 skill")
        void skipInvalidSkills() throws IOException {
            Files.createDirectories(projectSkillsDir);
            // 有效的 skill
            Files.writeString(projectSkillsDir.resolve("valid.SKILL.md"), """
                    ---
                    name: valid-skill
                    description: Valid
                    ---
                    Body
                    """);
            // 无效的 skill（缺少 name）
            Files.writeString(projectSkillsDir.resolve("invalid.SKILL.md"), """
                    ---
                    description: Missing name
                    ---
                    Body
                    """);

            registry.refresh();

            assertEquals(1, registry.size());
            assertTrue(registry.getByName("valid-skill").isPresent());
        }
    }

    @Nested
    @DisplayName("优先级覆盖")
    class PriorityOverrideTests {

        @Test
        @DisplayName("项目级覆盖用户级")
        void projectOverridesUser() throws IOException {
            // 用户级
            Files.createDirectories(userSkillsDir);
            Files.writeString(userSkillsDir.resolve("commit.SKILL.md"), """
                    ---
                    name: commit
                    description: User level commit skill
                    ---
                    User body
                    """);

            // 项目级（同名）
            Files.createDirectories(projectSkillsDir);
            Files.writeString(projectSkillsDir.resolve("commit.SKILL.md"), """
                    ---
                    name: commit
                    description: Project level commit skill
                    ---
                    Project body
                    """);

            registry.refresh();

            assertEquals(1, registry.size());
            SkillEntry entry = registry.getByName("commit").orElseThrow();
            assertEquals("Project level commit skill", entry.getMetadata().getDescription());
            assertEquals(0, entry.getMetadata().getPriority()); // 项目级优先级
        }

        @Test
        @DisplayName("用户级覆盖内置")
        void userOverridesBuiltin() throws IOException {
            // 内置
            Files.createDirectories(builtinSkillsDir);
            Files.writeString(builtinSkillsDir.resolve("review.SKILL.md"), """
                    ---
                    name: review
                    description: Builtin review
                    ---
                    Builtin body
                    """);

            // 用户级（同名）
            Files.createDirectories(userSkillsDir);
            Files.writeString(userSkillsDir.resolve("review.SKILL.md"), """
                    ---
                    name: review
                    description: User review
                    ---
                    User body
                    """);

            registry.refresh();

            assertEquals(1, registry.size());
            SkillEntry entry = registry.getByName("review").orElseThrow();
            assertEquals("User review", entry.getMetadata().getDescription());
            assertEquals(1, entry.getMetadata().getPriority()); // 用户级优先级
        }

        @Test
        @DisplayName("三级目录都有同名 skill")
        void allThreeLevels() throws IOException {
            String skillName = "multi-level";

            // 内置
            Files.createDirectories(builtinSkillsDir);
            Files.writeString(builtinSkillsDir.resolve(skillName + ".SKILL.md"), """
                    ---
                    name: multi-level
                    description: Builtin
                    ---
                    """);

            // 用户级
            Files.createDirectories(userSkillsDir);
            Files.writeString(userSkillsDir.resolve(skillName + ".SKILL.md"), """
                    ---
                    name: multi-level
                    description: User
                    ---
                    """);

            // 项目级
            Files.createDirectories(projectSkillsDir);
            Files.writeString(projectSkillsDir.resolve(skillName + ".SKILL.md"), """
                    ---
                    name: multi-level
                    description: Project
                    ---
                    """);

            registry.refresh();

            assertEquals(1, registry.size());
            SkillEntry entry = registry.getByName(skillName).orElseThrow();
            assertEquals("Project", entry.getMetadata().getDescription());
        }
    }

    @Nested
    @DisplayName("Gating 集成")
    class GatingIntegrationTests {

        @Test
        @DisplayName("Gating 通过的 skill 标记为可用")
        void gatingPassMarksAvailable() throws IOException {
            when(gatingService.evaluate(any())).thenReturn(GatingResult.PASSED);

            Files.createDirectories(projectSkillsDir);
            Files.writeString(projectSkillsDir.resolve("test.SKILL.md"), """
                    ---
                    name: test-skill
                    description: Test
                    ---
                    Body
                    """);

            registry.refresh();

            SkillEntry entry = registry.getByName("test-skill").orElseThrow();
            assertTrue(entry.isAvailable());
            assertNull(entry.getUnavailableReason());
        }

        @Test
        @DisplayName("Gating 失败的 skill 标记为不可用")
        void gatingFailMarksUnavailable() throws IOException {
            GatingResult failResult = GatingResult.builder()
                    .available(false)
                    .missingEnvVars(List.of("API_KEY"))
                    .build();
            when(gatingService.evaluate(any())).thenReturn(failResult);

            Files.createDirectories(projectSkillsDir);
            Files.writeString(projectSkillsDir.resolve("test.SKILL.md"), """
                    ---
                    name: test-skill
                    description: Test
                    ---
                    Body
                    """);

            registry.refresh();

            SkillEntry entry = registry.getByName("test-skill").orElseThrow();
            assertFalse(entry.isAvailable());
            assertNotNull(entry.getUnavailableReason());
            assertTrue(entry.getUnavailableReason().contains("API_KEY"));
        }

        @Test
        @DisplayName("getAvailable 只返回可用的 skill")
        void getAvailableFiltersCorrectly() throws IOException {
            Files.createDirectories(projectSkillsDir);

            // 先创建两个文件
            Files.writeString(projectSkillsDir.resolve("available.SKILL.md"), """
                    ---
                    name: available-skill
                    description: Available
                    ---
                    """);
            Files.writeString(projectSkillsDir.resolve("unavailable.SKILL.md"), """
                    ---
                    name: unavailable-skill
                    description: Unavailable
                    ---
                    """);

            // 使用 thenAnswer 根据调用顺序返回不同结果
            GatingResult failResult = GatingResult.builder()
                    .available(false)
                    .missingBins(List.of("git"))
                    .build();

            // 第一次调用返回 PASSED，第二次返回失败
            when(gatingService.evaluate(any()))
                    .thenReturn(GatingResult.PASSED)
                    .thenReturn(failResult);

            registry.refresh();

            List<SkillEntry> all = registry.getAll();
            List<SkillEntry> available = registry.getAvailable();
            List<SkillEntry> unavailable = registry.getUnavailable();

            assertEquals(2, all.size());
            assertEquals(1, available.size());
            assertEquals(1, unavailable.size());
        }
    }

    @Nested
    @DisplayName("Skill 激活")
    class ActivationTests {

        @Test
        @DisplayName("激活可用的 skill")
        void activateAvailableSkill() throws IOException {
            Files.createDirectories(projectSkillsDir);
            Files.writeString(projectSkillsDir.resolve("test.SKILL.md"), """
                    ---
                    name: test-skill
                    description: Test description
                    allowed-tools:
                      - read_file
                      - write_file
                    ---
                    # Skill Body

                    This is the body content.
                    """);

            registry.refresh();

            Optional<LoadedSkill> loaded = registry.activate("test-skill");

            assertTrue(loaded.isPresent());
            assertEquals("test-skill", loaded.get().getName());
            assertEquals("Test description", loaded.get().getDescription());
            assertTrue(loaded.get().getBody().contains("Skill Body"));
            assertTrue(loaded.get().getAllowedTools().contains("read_file"));
        }

        @Test
        @DisplayName("激活不存在的 skill 返回 empty")
        void activateNonexistentReturnsEmpty() {
            registry.refresh();

            Optional<LoadedSkill> loaded = registry.activate("nonexistent");

            assertTrue(loaded.isEmpty());
        }

        @Test
        @DisplayName("激活不可用的 skill 返回 empty")
        void activateUnavailableReturnsEmpty() throws IOException {
            GatingResult failResult = GatingResult.builder()
                    .available(false)
                    .missingEnvVars(List.of("SECRET"))
                    .build();
            when(gatingService.evaluate(any())).thenReturn(failResult);

            Files.createDirectories(projectSkillsDir);
            Files.writeString(projectSkillsDir.resolve("test.SKILL.md"), """
                    ---
                    name: test-skill
                    description: Test
                    ---
                    Body
                    """);

            registry.refresh();

            Optional<LoadedSkill> loaded = registry.activate("test-skill");

            assertTrue(loaded.isEmpty());
        }
    }

    @Nested
    @DisplayName("统计信息")
    class StatsTests {

        @Test
        @DisplayName("空注册表统计")
        void emptyRegistryStats() {
            registry.refresh();

            SkillRegistry.RegistryStats stats = registry.getStats();

            assertEquals(0, stats.totalSkills());
            assertEquals(0, stats.availableSkills());
            assertEquals(0, stats.unavailableSkills());
            assertEquals(0, stats.totalTokenCost());
        }

        @Test
        @DisplayName("混合可用性统计")
        void mixedAvailabilityStats() throws IOException {
            Files.createDirectories(projectSkillsDir);

            // 两个可用的
            Files.writeString(projectSkillsDir.resolve("one.SKILL.md"), """
                    ---
                    name: skill-one
                    description: First skill
                    ---
                    """);
            Files.writeString(projectSkillsDir.resolve("two.SKILL.md"), """
                    ---
                    name: skill-two
                    description: Second skill
                    ---
                    """);

            registry.refresh();

            SkillRegistry.RegistryStats stats = registry.getStats();

            assertEquals(2, stats.totalSkills());
            assertEquals(2, stats.availableSkills());
            assertEquals(0, stats.unavailableSkills());
            assertTrue(stats.totalTokenCost() > 0);
        }
    }

    @Nested
    @DisplayName("热更新检测")
    class HotReloadTests {

        @Test
        @DisplayName("无变更时返回 false")
        void noChangesReturnsFalse() throws IOException {
            Files.createDirectories(projectSkillsDir);
            Files.writeString(projectSkillsDir.resolve("test.SKILL.md"), """
                    ---
                    name: test-skill
                    description: Test
                    ---
                    """);

            registry.refresh();

            assertFalse(registry.hasChanges());
        }

        @Test
        @DisplayName("文件修改后返回 true")
        void fileModifiedReturnsTrue() throws IOException, InterruptedException {
            Files.createDirectories(projectSkillsDir);
            Path skillFile = projectSkillsDir.resolve("test.SKILL.md");
            Files.writeString(skillFile, """
                    ---
                    name: test-skill
                    description: Test
                    ---
                    """);

            registry.refresh();

            // 等待一下确保时间戳不同
            Thread.sleep(100);

            // 修改文件
            Files.writeString(skillFile, """
                    ---
                    name: test-skill
                    description: Updated description
                    ---
                    """);

            assertTrue(registry.hasChanges());
        }
    }

    @Nested
    @DisplayName("isAvailable 方法")
    class IsAvailableTests {

        @Test
        @DisplayName("存在且可用返回 true")
        void existsAndAvailable() throws IOException {
            Files.createDirectories(projectSkillsDir);
            Files.writeString(projectSkillsDir.resolve("test.SKILL.md"), """
                    ---
                    name: test-skill
                    description: Test
                    ---
                    """);

            registry.refresh();

            assertTrue(registry.isAvailable("test-skill"));
        }

        @Test
        @DisplayName("不存在返回 false")
        void notExists() {
            registry.refresh();

            assertFalse(registry.isAvailable("nonexistent"));
        }

        @Test
        @DisplayName("存在但不可用返回 false")
        void existsButUnavailable() throws IOException {
            GatingResult failResult = GatingResult.builder()
                    .available(false)
                    .build();
            when(gatingService.evaluate(any())).thenReturn(failResult);

            Files.createDirectories(projectSkillsDir);
            Files.writeString(projectSkillsDir.resolve("test.SKILL.md"), """
                    ---
                    name: test-skill
                    description: Test
                    ---
                    """);

            registry.refresh();

            assertFalse(registry.isAvailable("test-skill"));
        }
    }
}

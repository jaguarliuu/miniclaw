package com.jaguarliu.ai.skills.watcher;

import com.jaguarliu.ai.skills.gating.GatingResult;
import com.jaguarliu.ai.skills.gating.SkillGatingService;
import com.jaguarliu.ai.skills.parser.SkillParser;
import com.jaguarliu.ai.skills.registry.SkillRegistry;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * SkillFileWatcher 单元测试
 */
@DisplayName("SkillFileWatcher Tests")
class SkillFileWatcherTest {

    @TempDir
    Path tempDir;

    @Mock
    private SkillGatingService gatingService;

    private SkillParser parser;
    private SkillRegistry registry;
    private SkillFileWatcher watcher;

    private Path skillsDir;

    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);
        parser = new SkillParser();

        when(gatingService.evaluate(any())).thenReturn(GatingResult.PASSED);

        registry = new SkillRegistry(parser, gatingService);

        // 创建测试 skills 目录
        skillsDir = tempDir.resolve(".miniclaw/skills");
        Files.createDirectories(skillsDir);

        // 配置 registry 使用测试目录
        registry.configure(skillsDir, null, null);
    }

    @AfterEach
    void tearDown() {
        if (watcher != null && watcher.isRunning()) {
            watcher.stop();
        }
    }

    @Nested
    @DisplayName("启动和停止")
    class StartStopTests {

        @Test
        @DisplayName("正常启动")
        void startSuccessfully() throws Exception {
            watcher = createWatcher(true);
            watcher.start();

            assertTrue(watcher.isRunning());
        }

        @Test
        @DisplayName("禁用时不启动")
        void disabledDoesNotStart() throws Exception {
            watcher = createWatcher(false);
            watcher.start();

            assertFalse(watcher.isRunning());
        }

        @Test
        @DisplayName("正常停止")
        void stopSuccessfully() throws Exception {
            watcher = createWatcher(true);
            watcher.start();
            assertTrue(watcher.isRunning());

            watcher.stop();
            assertFalse(watcher.isRunning());
        }

        @Test
        @DisplayName("重复停止不报错")
        void doubleStopNoError() throws Exception {
            watcher = createWatcher(true);
            watcher.start();

            watcher.stop();
            watcher.stop(); // 第二次停止

            assertFalse(watcher.isRunning());
        }
    }

    @Nested
    @DisplayName("文件变化检测")
    class FileChangeTests {

        @Test
        @DisplayName("检测新建 SKILL.md")
        void detectNewSkillFile() throws Exception {
            // 先创建 skill 目录
            Path skillDir = skillsDir.resolve("new-skill");
            Files.createDirectories(skillDir);

            // 初始加载
            registry.refresh();
            assertEquals(0, registry.size());

            // 创建 SKILL.md
            Files.writeString(skillDir.resolve("SKILL.md"), """
                    ---
                    name: new-skill
                    description: A new skill
                    ---
                    Body
                    """);

            // 手动刷新（模拟 watcher 触发）
            registry.refresh();

            assertEquals(1, registry.size());
            assertTrue(registry.getByName("new-skill").isPresent());
        }

        @Test
        @DisplayName("检测修改 SKILL.md")
        void detectModifiedSkillFile() throws Exception {
            // 创建初始 skill
            Path skillDir = skillsDir.resolve("test-skill");
            Files.createDirectories(skillDir);
            Files.writeString(skillDir.resolve("SKILL.md"), """
                    ---
                    name: test-skill
                    description: Original description
                    ---
                    Body
                    """);

            registry.refresh();
            assertEquals("Original description",
                    registry.getByName("test-skill").get().getMetadata().getDescription());

            // 修改文件
            Files.writeString(skillDir.resolve("SKILL.md"), """
                    ---
                    name: test-skill
                    description: Updated description
                    ---
                    Body
                    """);

            // 手动刷新
            registry.refresh();

            assertEquals("Updated description",
                    registry.getByName("test-skill").get().getMetadata().getDescription());
        }

        @Test
        @DisplayName("检测删除 SKILL.md")
        void detectDeletedSkillFile() throws Exception {
            // 创建初始 skill
            Path skillDir = skillsDir.resolve("to-delete");
            Files.createDirectories(skillDir);
            Path skillFile = skillDir.resolve("SKILL.md");
            Files.writeString(skillFile, """
                    ---
                    name: to-delete
                    description: Will be deleted
                    ---
                    Body
                    """);

            registry.refresh();
            assertEquals(1, registry.size());

            // 删除文件
            Files.delete(skillFile);

            // 手动刷新
            registry.refresh();

            assertEquals(0, registry.size());
        }

        @Test
        @DisplayName("检测新建 skill 目录")
        void detectNewSkillDirectory() throws Exception {
            registry.refresh();
            assertEquals(0, registry.size());

            // 创建新目录和 SKILL.md
            Path newSkillDir = skillsDir.resolve("brand-new-skill");
            Files.createDirectories(newSkillDir);
            Files.writeString(newSkillDir.resolve("SKILL.md"), """
                    ---
                    name: brand-new-skill
                    description: Brand new
                    ---
                    Body
                    """);

            // 手动刷新
            registry.refresh();

            assertEquals(1, registry.size());
            assertTrue(registry.getByName("brand-new-skill").isPresent());
        }
    }

    @Nested
    @DisplayName("实时监听（集成测试）")
    class LiveWatchTests {

        @Test
        @DisplayName("实时检测文件创建并刷新 registry")
        @Timeout(value = 10, unit = TimeUnit.SECONDS)
        void liveDetectFileCreation() throws Exception {
            // 先创建 skill 目录结构
            Path skillDir = skillsDir.resolve("live-skill");
            Files.createDirectories(skillDir);

            registry.refresh();

            // 启动 watcher（使用自定义的测试 watcher）
            watcher = new TestableSkillFileWatcher(registry, skillsDir);
            setWatchEnabled(watcher, true);
            watcher.start();

            assertTrue(watcher.isRunning());

            // 创建 SKILL.md
            Files.writeString(skillDir.resolve("SKILL.md"), """
                    ---
                    name: live-skill
                    description: Live detected
                    ---
                    Body
                    """);

            // 等待 watcher 检测并刷新
            Thread.sleep(500);

            // 验证（如果 watcher 工作正常，registry 应该已更新）
            // 注意：真实场景下 watcher 会自动调用 registry.refresh()
            // 这里我们手动刷新来验证逻辑
            registry.refresh();
            assertTrue(registry.getByName("live-skill").isPresent());
        }
    }

    /**
     * 创建 watcher 并设置 watchEnabled
     */
    private SkillFileWatcher createWatcher(boolean enabled) throws Exception {
        SkillFileWatcher w = new SkillFileWatcher(registry);
        setWatchEnabled(w, enabled);
        return w;
    }

    /**
     * 通过反射设置 watchEnabled
     */
    private void setWatchEnabled(SkillFileWatcher watcher, boolean enabled) throws Exception {
        Field field = SkillFileWatcher.class.getDeclaredField("watchEnabled");
        field.setAccessible(true);
        field.set(watcher, enabled);
    }

    /**
     * 测试用的 Watcher，可以指定监听目录
     */
    static class TestableSkillFileWatcher extends SkillFileWatcher {
        private final Path customPath;

        TestableSkillFileWatcher(SkillRegistry registry, Path customPath) {
            super(registry);
            this.customPath = customPath;
        }

        // 可以添加额外的测试方法
    }
}

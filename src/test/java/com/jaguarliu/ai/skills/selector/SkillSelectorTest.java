package com.jaguarliu.ai.skills.selector;

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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * SkillSelector 单元测试
 */
@DisplayName("SkillSelector Tests")
class SkillSelectorTest {

    @TempDir
    Path tempDir;

    @Mock
    private SkillGatingService gatingService;

    private SkillParser parser;
    private SkillRegistry registry;
    private SkillSelector selector;

    private Path skillsDir;

    @BeforeEach
    void setUp() throws IOException {
        MockitoAnnotations.openMocks(this);
        parser = new SkillParser();

        when(gatingService.evaluate(any())).thenReturn(GatingResult.PASSED);

        registry = new SkillRegistry(parser, gatingService);
        selector = new SkillSelector(registry);

        skillsDir = tempDir.resolve("skills");
        Files.createDirectories(skillsDir);

        registry.configure(skillsDir, null, null);

        // 创建测试 skill
        createSkill("code-review", "代码审查");
        createSkill("git-commit", "生成 commit message");
        registry.refresh();
    }

    @Nested
    @DisplayName("手动触发（/skill-name）")
    class ManualSelectionTests {

        @Test
        @DisplayName("匹配 /skill-name")
        void matchSlashCommand() {
            SkillSelection result = selector.tryManualSelection("/code-review");

            assertTrue(result.isSelected());
            assertEquals("code-review", result.getSkillName());
            assertNull(result.getArguments());
            assertEquals(SkillSelection.SelectionSource.MANUAL, result.getSource());
        }

        @Test
        @DisplayName("匹配 /skill-name 带参数")
        void matchSlashCommandWithArgs() {
            SkillSelection result = selector.tryManualSelection("/code-review src/main/java/App.java");

            assertTrue(result.isSelected());
            assertEquals("code-review", result.getSkillName());
            assertEquals("src/main/java/App.java", result.getArguments());
        }

        @Test
        @DisplayName("匹配多行参数")
        void matchMultilineArgs() {
            String input = "/code-review 请审查以下代码：\nclass Foo {\n}";
            SkillSelection result = selector.tryManualSelection(input);

            assertTrue(result.isSelected());
            assertTrue(result.getArguments().contains("class Foo"));
        }

        @Test
        @DisplayName("不存在的 skill 返回 none")
        void nonexistentSkillReturnsNone() {
            SkillSelection result = selector.tryManualSelection("/nonexistent-skill");

            assertFalse(result.isSelected());
            assertEquals(SkillSelection.SelectionSource.NONE, result.getSource());
        }

        @Test
        @DisplayName("普通消息不匹配")
        void normalMessageNoMatch() {
            SkillSelection result = selector.tryManualSelection("请帮我审查代码");

            assertFalse(result.isSelected());
        }

        @Test
        @DisplayName("null 输入返回 none")
        void nullInputReturnsNone() {
            SkillSelection result = selector.tryManualSelection(null);

            assertFalse(result.isSelected());
        }

        @Test
        @DisplayName("空字符串返回 none")
        void emptyInputReturnsNone() {
            SkillSelection result = selector.tryManualSelection("");

            assertFalse(result.isSelected());
        }

        @Test
        @DisplayName("前后空格被 trim")
        void trimWhitespace() {
            SkillSelection result = selector.tryManualSelection("  /code-review  ");

            assertTrue(result.isSelected());
            assertEquals("code-review", result.getSkillName());
        }
    }

    @Nested
    @DisplayName("自动选择（[USE_SKILL:xxx]）")
    class AutoSelectionTests {

        @Test
        @DisplayName("匹配 [USE_SKILL:skill-name]")
        void matchUseSkill() {
            String llmResponse = "我会使用代码审查技能来帮您。[USE_SKILL:code-review]";
            SkillSelection result = selector.parseFromLlmResponse(llmResponse, "请审查代码");

            assertTrue(result.isSelected());
            assertEquals("code-review", result.getSkillName());
            assertEquals("请审查代码", result.getArguments());
            assertEquals(SkillSelection.SelectionSource.AUTO, result.getSource());
        }

        @Test
        @DisplayName("回复开头的 USE_SKILL")
        void useSkillAtStart() {
            String llmResponse = "[USE_SKILL:git-commit] 我来帮您生成 commit message";
            SkillSelection result = selector.parseFromLlmResponse(llmResponse, "commit");

            assertTrue(result.isSelected());
            assertEquals("git-commit", result.getSkillName());
        }

        @Test
        @DisplayName("不存在的 skill 返回 none")
        void nonexistentSkillReturnsNone() {
            String llmResponse = "[USE_SKILL:nonexistent]";
            SkillSelection result = selector.parseFromLlmResponse(llmResponse, "test");

            assertFalse(result.isSelected());
        }

        @Test
        @DisplayName("没有 USE_SKILL 标记返回 none")
        void noMarkerReturnsNone() {
            String llmResponse = "我可以帮您审查代码，请发送代码内容。";
            SkillSelection result = selector.parseFromLlmResponse(llmResponse, "审查代码");

            assertFalse(result.isSelected());
        }

        @Test
        @DisplayName("null 回复返回 none")
        void nullResponseReturnsNone() {
            SkillSelection result = selector.parseFromLlmResponse(null, "test");

            assertFalse(result.isSelected());
        }
    }

    @Nested
    @DisplayName("辅助方法")
    class HelperMethodTests {

        @Test
        @DisplayName("isSlashCommand 正确识别")
        void isSlashCommand() {
            assertTrue(selector.isSlashCommand("/code-review"));
            assertTrue(selector.isSlashCommand("/anything"));
            assertTrue(selector.isSlashCommand("/test args"));
            assertFalse(selector.isSlashCommand("not a command"));
            assertFalse(selector.isSlashCommand(null));
            assertFalse(selector.isSlashCommand(""));
        }

        @Test
        @DisplayName("extractSkillName 正确提取")
        void extractSkillName() {
            assertEquals("code-review", selector.extractSkillName("/code-review"));
            assertEquals("test", selector.extractSkillName("/test args"));
            assertNull(selector.extractSkillName("not a command"));
            assertNull(selector.extractSkillName(null));
        }

        @Test
        @DisplayName("containsUseSkill 正确识别")
        void containsUseSkill() {
            assertTrue(selector.containsUseSkill("[USE_SKILL:test]"));
            assertTrue(selector.containsUseSkill("Some text [USE_SKILL:test] more text"));
            assertFalse(selector.containsUseSkill("No marker here"));
            assertFalse(selector.containsUseSkill(null));
        }

        @Test
        @DisplayName("extractSkillNameFromLlm 正确提取")
        void extractSkillNameFromLlm() {
            assertEquals("test", selector.extractSkillNameFromLlm("[USE_SKILL:test]"));
            assertEquals("code-review", selector.extractSkillNameFromLlm("Text [USE_SKILL:code-review] more"));
            assertNull(selector.extractSkillNameFromLlm("No marker"));
            assertNull(selector.extractSkillNameFromLlm(null));
        }

        @Test
        @DisplayName("removeUseSkillMarker 正确移除")
        void removeUseSkillMarker() {
            // 移除标记后可能留下多余空格，trim 会处理首尾
            assertEquals("Some text  more text",
                    selector.removeUseSkillMarker("Some text [USE_SKILL:test] more text"));
            assertEquals("Clean text",
                    selector.removeUseSkillMarker("[USE_SKILL:test] Clean text"));
            assertEquals("No change",
                    selector.removeUseSkillMarker("No change"));
            assertNull(selector.removeUseSkillMarker(null));
        }
    }

    @Nested
    @DisplayName("SkillSelection 模型")
    class SkillSelectionModelTests {

        @Test
        @DisplayName("isManual 和 isAuto")
        void isManualAndIsAuto() {
            SkillSelection manual = SkillSelection.manual("test", "args", "/test args");
            assertTrue(manual.isManual());
            assertFalse(manual.isAuto());

            SkillSelection auto = SkillSelection.auto("test", "args");
            assertFalse(auto.isManual());
            assertTrue(auto.isAuto());

            SkillSelection none = SkillSelection.none("input");
            assertFalse(none.isManual());
            assertFalse(none.isAuto());
        }

        @Test
        @DisplayName("静态工厂方法")
        void staticFactoryMethods() {
            SkillSelection none = SkillSelection.none("input");
            assertFalse(none.isSelected());
            assertEquals(SkillSelection.SelectionSource.NONE, none.getSource());
            assertEquals("input", none.getOriginalInput());

            SkillSelection manual = SkillSelection.manual("skill", "args", "/skill args");
            assertTrue(manual.isSelected());
            assertEquals("skill", manual.getSkillName());
            assertEquals("args", manual.getArguments());
            assertEquals("/skill args", manual.getOriginalInput());

            SkillSelection auto = SkillSelection.auto("skill", "args");
            assertTrue(auto.isSelected());
            assertEquals("skill", auto.getSkillName());
            assertEquals("args", auto.getArguments());
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

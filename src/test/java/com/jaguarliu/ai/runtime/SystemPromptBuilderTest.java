package com.jaguarliu.ai.runtime;

import com.jaguarliu.ai.memory.search.MemorySearchService;
import com.jaguarliu.ai.skills.index.SkillIndexBuilder;
import com.jaguarliu.ai.tools.ToolDefinition;
import com.jaguarliu.ai.tools.ToolRegistry;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * SystemPromptBuilder 单元测试
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SystemPromptBuilder Tests")
class SystemPromptBuilderTest {

    @Mock
    private ToolRegistry toolRegistry;

    @Mock
    private SkillIndexBuilder skillIndexBuilder;

    @Mock
    private MemorySearchService memorySearchService;

    @InjectMocks
    private SystemPromptBuilder builder;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(builder, "workspace", "./workspace");
        ReflectionTestUtils.setField(builder, "customSystemPrompt", "");
    }

    // ==================== NONE 模式测试 ====================

    @Nested
    @DisplayName("PromptMode.NONE")
    class NoneModeTests {

        @Test
        @DisplayName("返回简短的身份行")
        void returnsMinimalIdentity() {
            String result = builder.build(SystemPromptBuilder.PromptMode.NONE);

            assertEquals("You are MiniClaw, an AI coding assistant.", result);
        }
    }

    // ==================== MINIMAL 模式测试 ====================

    @Nested
    @DisplayName("PromptMode.MINIMAL")
    class MinimalModeTests {

        @Test
        @DisplayName("包含 Identity 段落")
        void containsIdentity() {
            when(toolRegistry.listDefinitions()).thenReturn(List.of());

            String result = builder.build(SystemPromptBuilder.PromptMode.MINIMAL);

            assertTrue(result.contains("You are MiniClaw"));
        }

        @Test
        @DisplayName("不包含 Safety 段落")
        void doesNotContainSafety() {
            when(toolRegistry.listDefinitions()).thenReturn(List.of());

            String result = builder.build(SystemPromptBuilder.PromptMode.MINIMAL);

            assertFalse(result.contains("Safety Guidelines"));
        }

        @Test
        @DisplayName("不包含 Memory 段落")
        void doesNotContainMemory() {
            when(toolRegistry.listDefinitions()).thenReturn(List.of());

            String result = builder.build(SystemPromptBuilder.PromptMode.MINIMAL);

            assertFalse(result.contains("## Memory"));
        }

        @Test
        @DisplayName("不包含 Skills 段落")
        void doesNotContainSkills() {
            when(toolRegistry.listDefinitions()).thenReturn(List.of());

            String result = builder.build(SystemPromptBuilder.PromptMode.MINIMAL);

            assertFalse(result.contains("## Skills"));
        }

        @Test
        @DisplayName("包含 Workspace 段落")
        void containsWorkspace() {
            when(toolRegistry.listDefinitions()).thenReturn(List.of());

            String result = builder.build(SystemPromptBuilder.PromptMode.MINIMAL);

            assertTrue(result.contains("## Workspace"));
        }

        @Test
        @DisplayName("包含 Runtime 段落")
        void containsRuntime() {
            when(toolRegistry.listDefinitions()).thenReturn(List.of());

            String result = builder.build(SystemPromptBuilder.PromptMode.MINIMAL);

            assertTrue(result.contains("## Runtime"));
            assertTrue(result.contains("Mode: minimal"));
        }
    }

    // ==================== FULL 模式测试 ====================

    @Nested
    @DisplayName("PromptMode.FULL")
    class FullModeTests {

        @Test
        @DisplayName("包含 Identity 段落")
        void containsIdentity() {
            when(toolRegistry.listDefinitions()).thenReturn(List.of());
            when(skillIndexBuilder.buildIndex()).thenReturn("");

            String result = builder.build(SystemPromptBuilder.PromptMode.FULL);

            assertTrue(result.contains("You are MiniClaw"));
        }

        @Test
        @DisplayName("包含 Safety 段落")
        void containsSafety() {
            when(toolRegistry.listDefinitions()).thenReturn(List.of());
            when(skillIndexBuilder.buildIndex()).thenReturn("");

            String result = builder.build(SystemPromptBuilder.PromptMode.FULL);

            assertTrue(result.contains("## Safety Guidelines"));
        }

        @Test
        @DisplayName("包含 Memory 段落")
        void containsMemory() {
            when(toolRegistry.listDefinitions()).thenReturn(List.of());
            when(skillIndexBuilder.buildIndex()).thenReturn("");

            String result = builder.build(SystemPromptBuilder.PromptMode.FULL);

            assertTrue(result.contains("## Memory"));
            assertTrue(result.contains("global, cross-session"));
            assertTrue(result.contains("memory_search"));
            assertTrue(result.contains("memory_get"));
            assertTrue(result.contains("memory_write"));
        }

        @Test
        @DisplayName("Memory 段落包含正确的工具说明")
        void memoryContainsToolDescriptions() {
            when(toolRegistry.listDefinitions()).thenReturn(List.of());
            when(skillIndexBuilder.buildIndex()).thenReturn("");

            String result = builder.build(SystemPromptBuilder.PromptMode.FULL);

            // 检查 memory_search 说明
            assertTrue(result.contains("memory_search(query)"));
            assertTrue(result.contains("historical memories"));

            // 检查 memory_get 说明
            assertTrue(result.contains("memory_get(path)"));

            // 检查 memory_write 说明
            assertTrue(result.contains("memory_write(content, target)"));
            assertTrue(result.contains("target=\"core\""));
            assertTrue(result.contains("target=\"daily\""));
            assertTrue(result.contains("MEMORY.md"));
        }

        @Test
        @DisplayName("Memory 段落强调跨会话特性")
        void memoryEmphasizesCrossSession() {
            when(toolRegistry.listDefinitions()).thenReturn(List.of());
            when(skillIndexBuilder.buildIndex()).thenReturn("");

            String result = builder.build(SystemPromptBuilder.PromptMode.FULL);

            assertTrue(result.contains("cross-session"));
            assertTrue(result.contains("personal assistant"));
            assertTrue(result.contains("not multi-tenant"));
        }

        @Test
        @DisplayName("包含 Workspace 段落")
        void containsWorkspace() {
            when(toolRegistry.listDefinitions()).thenReturn(List.of());
            when(skillIndexBuilder.buildIndex()).thenReturn("");

            String result = builder.build(SystemPromptBuilder.PromptMode.FULL);

            assertTrue(result.contains("## Workspace"));
        }

        @Test
        @DisplayName("包含 Current Date & Time 段落")
        void containsDateTime() {
            when(toolRegistry.listDefinitions()).thenReturn(List.of());
            when(skillIndexBuilder.buildIndex()).thenReturn("");

            String result = builder.build(SystemPromptBuilder.PromptMode.FULL);

            assertTrue(result.contains("## Current Date & Time"));
        }

        @Test
        @DisplayName("包含 Runtime 段落")
        void containsRuntime() {
            when(toolRegistry.listDefinitions()).thenReturn(List.of());
            when(skillIndexBuilder.buildIndex()).thenReturn("");

            String result = builder.build(SystemPromptBuilder.PromptMode.FULL);

            assertTrue(result.contains("## Runtime"));
            assertTrue(result.contains("Mode: full"));
        }
    }

    // ==================== 工具段落测试 ====================

    @Nested
    @DisplayName("Tooling Section")
    class ToolingSectionTests {

        @Test
        @DisplayName("无工具时不显示工具段落")
        void noToolsNoSection() {
            when(toolRegistry.listDefinitions()).thenReturn(List.of());

            String result = builder.build(SystemPromptBuilder.PromptMode.MINIMAL);

            assertFalse(result.contains("## Available Tools"));
        }

        @Test
        @DisplayName("有工具时显示工具列表")
        void withToolsShowsList() {
            ToolDefinition readFile = ToolDefinition.builder()
                    .name("read_file")
                    .description("读取文件内容")
                    .hitl(false)
                    .build();
            ToolDefinition bash = ToolDefinition.builder()
                    .name("bash")
                    .description("执行 shell 命令")
                    .hitl(true)
                    .build();
            when(toolRegistry.listDefinitions()).thenReturn(List.of(readFile, bash));

            String result = builder.build(SystemPromptBuilder.PromptMode.MINIMAL);

            assertTrue(result.contains("## Available Tools"));
            assertTrue(result.contains("**read_file**"));
            assertTrue(result.contains("**bash**"));
            assertTrue(result.contains("_(requires confirmation)_"));
        }

        @Test
        @DisplayName("工具白名单过滤")
        void toolWhitelistFiltering() {
            ToolDefinition readFile = ToolDefinition.builder()
                    .name("read_file")
                    .description("读取文件内容")
                    .hitl(false)
                    .build();
            ToolDefinition bash = ToolDefinition.builder()
                    .name("bash")
                    .description("执行 shell 命令")
                    .hitl(true)
                    .build();
            when(toolRegistry.listDefinitions()).thenReturn(List.of(readFile, bash));

            String result = builder.build(SystemPromptBuilder.PromptMode.MINIMAL, Set.of("read_file"));

            assertTrue(result.contains("**read_file**"));
            assertFalse(result.contains("**bash**"));
        }
    }

    // ==================== Skills 段落测试 ====================

    @Nested
    @DisplayName("Skills Section")
    class SkillsSectionTests {

        @Test
        @DisplayName("无技能时不显示 Skills 段落")
        void noSkillsNoSection() {
            when(toolRegistry.listDefinitions()).thenReturn(List.of());
            when(skillIndexBuilder.buildIndex()).thenReturn("");

            String result = builder.build(SystemPromptBuilder.PromptMode.FULL);

            assertFalse(result.contains("## Skills"));
        }

        @Test
        @DisplayName("有技能时显示 Skills 段落")
        void withSkillsShowsSection() {
            when(toolRegistry.listDefinitions()).thenReturn(List.of());
            when(skillIndexBuilder.buildIndex()).thenReturn("<skills>...</skills>");
            when(skillIndexBuilder.buildCompactIndex()).thenReturn("<skills>...</skills>");

            String result = builder.build(SystemPromptBuilder.PromptMode.FULL);

            assertTrue(result.contains("## Skills"));
            assertTrue(result.contains("Manual trigger"));
            assertTrue(result.contains("Auto trigger"));
            assertTrue(result.contains("[USE_SKILL:"));
        }
    }

    // ==================== SubAgent 段落测试 ====================

    @Nested
    @DisplayName("SubAgent Section")
    class SubagentSectionTests {

        private final ToolDefinition spawnTool = ToolDefinition.builder()
                .name("sessions_spawn")
                .description("派生子代理执行异步任务")
                .hitl(false)
                .build();

        @Test
        @DisplayName("FULL 模式有 sessions_spawn 工具时包含 SubAgent 段落")
        void fullModeWithSpawnToolContainsSubagentSection() {
            when(toolRegistry.listDefinitions()).thenReturn(List.of(spawnTool));
            when(skillIndexBuilder.buildIndex()).thenReturn("");

            String result = builder.build(SystemPromptBuilder.PromptMode.FULL);

            assertTrue(result.contains("## SubAgent (sessions_spawn)"));
            assertTrue(result.contains("sessions_spawn"));
            assertTrue(result.contains("Long-running tasks"));
            assertTrue(result.contains("Parallel independent tasks"));
        }

        @Test
        @DisplayName("FULL 模式无 sessions_spawn 工具时不包含 SubAgent 段落")
        void fullModeWithoutSpawnToolNoSubagentSection() {
            ToolDefinition otherTool = ToolDefinition.builder()
                    .name("read_file")
                    .description("读取文件")
                    .build();
            when(toolRegistry.listDefinitions()).thenReturn(List.of(otherTool));
            when(skillIndexBuilder.buildIndex()).thenReturn("");

            String result = builder.build(SystemPromptBuilder.PromptMode.FULL);

            assertFalse(result.contains("## SubAgent (sessions_spawn)"));
        }

        @Test
        @DisplayName("MINIMAL 模式不包含 SubAgent 段落")
        void minimalModeNoSubagentSection() {
            when(toolRegistry.listDefinitions()).thenReturn(List.of(spawnTool));

            String result = builder.build(SystemPromptBuilder.PromptMode.MINIMAL);

            assertFalse(result.contains("## SubAgent (sessions_spawn)"));
        }

        @Test
        @DisplayName("白名单不含 sessions_spawn 时不包含 SubAgent 段落")
        void whitelistWithoutSpawnNoSubagentSection() {
            when(toolRegistry.listDefinitions()).thenReturn(List.of(spawnTool));
            when(skillIndexBuilder.buildIndex()).thenReturn("");

            String result = builder.build(SystemPromptBuilder.PromptMode.FULL, Set.of("read_file"));

            assertFalse(result.contains("## SubAgent (sessions_spawn)"));
        }
    }

    // ==================== 自定义提示测试 ====================

    @Nested
    @DisplayName("Custom System Prompt")
    class CustomPromptTests {

        @Test
        @DisplayName("附加自定义提示")
        void appendsCustomPrompt() {
            when(toolRegistry.listDefinitions()).thenReturn(List.of());
            when(skillIndexBuilder.buildIndex()).thenReturn("");
            ReflectionTestUtils.setField(builder, "customSystemPrompt", "Always reply in Chinese.");

            String result = builder.build(SystemPromptBuilder.PromptMode.FULL);

            assertTrue(result.contains("## Custom Instructions"));
            assertTrue(result.contains("Always reply in Chinese."));
        }

        @Test
        @DisplayName("空白自定义提示不显示")
        void blankCustomPromptNotShown() {
            when(toolRegistry.listDefinitions()).thenReturn(List.of());
            when(skillIndexBuilder.buildIndex()).thenReturn("");
            ReflectionTestUtils.setField(builder, "customSystemPrompt", "   ");

            String result = builder.build(SystemPromptBuilder.PromptMode.FULL);

            assertFalse(result.contains("## Custom Instructions"));
        }
    }

    // ==================== 段落顺序测试 ====================

    @Nested
    @DisplayName("Section Order")
    class SectionOrderTests {

        @Test
        @DisplayName("FULL 模式段落顺序正确")
        void fullModeCorrectOrder() {
            when(toolRegistry.listDefinitions()).thenReturn(List.of(
                    ToolDefinition.builder().name("test").description("test").build()
            ));
            when(skillIndexBuilder.buildIndex()).thenReturn("<skills>test</skills>");
            when(skillIndexBuilder.buildCompactIndex()).thenReturn("<skills>test</skills>");

            String result = builder.build(SystemPromptBuilder.PromptMode.FULL);

            int identityIdx = result.indexOf("You are MiniClaw");
            int toolsIdx = result.indexOf("## Available Tools");
            int safetyIdx = result.indexOf("## Safety Guidelines");
            int memoryIdx = result.indexOf("## Memory");
            int skillsIdx = result.indexOf("## Skills");
            int workspaceIdx = result.indexOf("## Workspace");
            int datetimeIdx = result.indexOf("## Current Date & Time");
            int runtimeIdx = result.indexOf("## Runtime");

            assertTrue(identityIdx < toolsIdx, "Identity should come before Tools");
            assertTrue(toolsIdx < safetyIdx, "Tools should come before Safety");
            assertTrue(safetyIdx < memoryIdx, "Safety should come before Memory");
            assertTrue(memoryIdx < skillsIdx, "Memory should come before Skills");
            assertTrue(skillsIdx < workspaceIdx, "Skills should come before Workspace");
            assertTrue(workspaceIdx < datetimeIdx, "Workspace should come before DateTime");
            assertTrue(datetimeIdx < runtimeIdx, "DateTime should come before Runtime");
        }
    }
}

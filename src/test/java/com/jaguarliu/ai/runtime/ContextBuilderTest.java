package com.jaguarliu.ai.runtime;

import com.jaguarliu.ai.llm.model.LlmRequest;
import com.jaguarliu.ai.skills.index.SkillIndexBuilder;
import com.jaguarliu.ai.skills.model.LoadedSkill;
import com.jaguarliu.ai.skills.model.SkillEntry;
import com.jaguarliu.ai.skills.model.SkillMetadata;
import com.jaguarliu.ai.skills.registry.SkillRegistry;
import com.jaguarliu.ai.skills.selector.SkillSelection;
import com.jaguarliu.ai.skills.selector.SkillSelector;
import com.jaguarliu.ai.skills.template.SkillTemplateEngine;
import com.jaguarliu.ai.tools.ToolRegistry;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * ContextBuilder 单元测试
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ContextBuilder Tests")
class ContextBuilderTest {

    @Mock
    private ToolRegistry toolRegistry;

    @Mock
    private SkillRegistry skillRegistry;

    @Mock
    private SkillIndexBuilder skillIndexBuilder;

    @Mock
    private SkillSelector skillSelector;

    @Mock
    private SkillTemplateEngine templateEngine;

    @InjectMocks
    private ContextBuilder contextBuilder;

    private static final String DEFAULT_SYSTEM_PROMPT = "你是一个有帮助的 AI 助手。";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(contextBuilder, "defaultSystemPrompt", DEFAULT_SYSTEM_PROMPT);
        ReflectionTestUtils.setField(contextBuilder, "autoSelectEnabled", true);
    }

    @Nested
    @DisplayName("基本构建方法")
    class BasicBuildTests {

        @Test
        @DisplayName("build(userPrompt) 构建简单请求")
        void buildSimpleRequest() {
            LlmRequest request = contextBuilder.build("你好");

            assertNotNull(request);
            assertEquals(2, request.getMessages().size());
            assertEquals("system", request.getMessages().get(0).getRole());
            assertEquals(DEFAULT_SYSTEM_PROMPT, request.getMessages().get(0).getContent());
            assertEquals("user", request.getMessages().get(1).getRole());
            assertEquals("你好", request.getMessages().get(1).getContent());
        }

        @Test
        @DisplayName("build(systemPrompt, history, userPrompt) 构建完整请求")
        void buildFullRequest() {
            List<LlmRequest.Message> history = List.of(
                    LlmRequest.Message.user("之前的问题"),
                    LlmRequest.Message.assistant("之前的回答")
            );

            LlmRequest request = contextBuilder.build("自定义系统提示", history, "当前问题");

            assertEquals(4, request.getMessages().size());
            assertEquals("自定义系统提示", request.getMessages().get(0).getContent());
            assertEquals("之前的问题", request.getMessages().get(1).getContent());
            assertEquals("之前的回答", request.getMessages().get(2).getContent());
            assertEquals("当前问题", request.getMessages().get(3).getContent());
        }

        @Test
        @DisplayName("buildWithHistory 构建带历史的请求")
        void buildWithHistory() {
            List<LlmRequest.Message> history = List.of(
                    LlmRequest.Message.user("问题1"),
                    LlmRequest.Message.assistant("回答1")
            );

            LlmRequest request = contextBuilder.buildWithHistory(history, "问题2");

            assertEquals(4, request.getMessages().size());
            assertEquals(DEFAULT_SYSTEM_PROMPT, request.getMessages().get(0).getContent());
        }

        @Test
        @DisplayName("空历史不影响构建")
        void buildWithEmptyHistory() {
            LlmRequest request = contextBuilder.build(null, null, "问题");

            assertEquals(2, request.getMessages().size());
        }
    }

    @Nested
    @DisplayName("带工具的构建")
    class BuildWithToolsTests {

        @Test
        @DisplayName("启用工具时添加工具定义")
        void buildWithToolsEnabled() {
            when(toolRegistry.size()).thenReturn(3);
            when(toolRegistry.toOpenAiTools()).thenReturn(List.of(
                    Map.of("type", "function", "function", Map.of("name", "read_file")),
                    Map.of("type", "function", "function", Map.of("name", "write_file")),
                    Map.of("type", "function", "function", Map.of("name", "bash"))
            ));

            LlmRequest request = contextBuilder.buildWithTools(null, "帮我读取文件", true);

            assertNotNull(request.getTools());
            assertEquals(3, request.getTools().size());
            assertEquals("auto", request.getToolChoice());
        }

        @Test
        @DisplayName("禁用工具时不添加工具定义")
        void buildWithToolsDisabled() {
            LlmRequest request = contextBuilder.buildWithTools(null, "帮我读取文件", false);

            assertNull(request.getTools());
        }

        @Test
        @DisplayName("无工具时不添加")
        void buildWithNoTools() {
            when(toolRegistry.size()).thenReturn(0);

            LlmRequest request = contextBuilder.buildWithTools(null, "帮我读取文件", true);

            assertNull(request.getTools());
        }
    }

    @Nested
    @DisplayName("带 Skill 索引的构建")
    class BuildWithSkillIndexTests {

        @Test
        @DisplayName("注入 skill 索引到 system prompt")
        void buildWithSkillIndex() {
            String skillIndex = "\n---\n\n## Available Skills\n<skills>\n  <skill name=\"code-review\">代码审查</skill>\n</skills>";
            when(skillIndexBuilder.buildIndex()).thenReturn(skillIndex);
            // skillRegistry.getAvailable() 只在 log 中使用，不需要 stub

            LlmRequest request = contextBuilder.buildWithSkillIndex(null, "审查代码", false);

            String systemContent = request.getMessages().get(0).getContent();
            assertTrue(systemContent.contains(DEFAULT_SYSTEM_PROMPT));
            assertTrue(systemContent.contains("Available Skills"));
            assertTrue(systemContent.contains("code-review"));
        }
    }

    @Nested
    @DisplayName("激活 Skill 后的构建")
    class BuildWithActiveSkillTests {

        private LoadedSkill createTestSkill() {
            return LoadedSkill.builder()
                    .name("code-review")
                    .description("代码审查")
                    .body("请审查以下代码：$ARGUMENTS")
                    .allowedTools(Set.of("read_file", "grep"))
                    .confirmBefore(Set.of("write_file"))
                    .build();
        }

        @Test
        @DisplayName("激活 skill 后构建请求")
        void buildWithActiveSkill() {
            LoadedSkill skill = createTestSkill();

            SkillTemplateEngine.TemplateContext mockContext = mock(SkillTemplateEngine.TemplateContext.class);
            when(mockContext.withArguments(anyString())).thenReturn(mockContext);
            when(mockContext.withProjectRoot(anyString())).thenReturn(mockContext);
            when(mockContext.withCwd(anyString())).thenReturn(mockContext);
            when(templateEngine.createContext()).thenReturn(mockContext);
            when(templateEngine.render(eq("请审查以下代码：$ARGUMENTS"), any())).thenReturn("请审查以下代码：test.java");
            when(toolRegistry.size()).thenReturn(2);
            when(toolRegistry.toOpenAiTools(Set.of("read_file", "grep"))).thenReturn(List.of(
                    Map.of("type", "function", "function", Map.of("name", "read_file")),
                    Map.of("type", "function", "function", Map.of("name", "grep"))
            ));

            ContextBuilder.SkillAwareRequest result = contextBuilder.buildWithActiveSkill(
                    skill,
                    "test.java",
                    null,
                    "test.java",
                    true
            );

            assertNotNull(result);
            assertTrue(result.hasActiveSkill());
            assertEquals("code-review", result.activeSkillName());
            assertEquals(Set.of("read_file", "grep"), result.allowedTools());
            assertEquals(Set.of("write_file"), result.confirmBefore());

            // 检查 system prompt 包含 skill 内容
            String systemContent = result.request().getMessages().get(0).getContent();
            assertTrue(systemContent.contains("Active Skill: code-review"));
            assertTrue(systemContent.contains("请审查以下代码：test.java"));
        }

        @Test
        @DisplayName("无工具限制时使用所有工具")
        void buildWithActiveSkillNoToolRestriction() {
            LoadedSkill skill = LoadedSkill.builder()
                    .name("chat")
                    .description("聊天")
                    .body("简单聊天")
                    .allowedTools(null)  // 无限制
                    .build();

            SkillTemplateEngine.TemplateContext mockContext = mock(SkillTemplateEngine.TemplateContext.class);
            when(mockContext.withArguments(anyString())).thenReturn(mockContext);
            when(mockContext.withProjectRoot(anyString())).thenReturn(mockContext);
            when(mockContext.withCwd(anyString())).thenReturn(mockContext);
            when(templateEngine.createContext()).thenReturn(mockContext);
            when(templateEngine.render(anyString(), any())).thenReturn("简单聊天");
            when(toolRegistry.size()).thenReturn(5);
            when(toolRegistry.toOpenAiTools()).thenReturn(List.of(
                    Map.of("type", "function", "function", Map.of("name", "tool1")),
                    Map.of("type", "function", "function", Map.of("name", "tool2"))
            ));

            ContextBuilder.SkillAwareRequest result = contextBuilder.buildWithActiveSkill(
                    skill, null, null, "hi", true
            );

            assertNull(result.allowedTools());
            assertTrue(result.isToolAllowed("any_tool"));
        }

        @Test
        @DisplayName("禁用工具时不添加工具定义")
        void buildWithActiveSkillToolsDisabled() {
            LoadedSkill skill = LoadedSkill.builder()
                    .name("chat")
                    .description("聊天")
                    .body("简单聊天")
                    .build();

            SkillTemplateEngine.TemplateContext mockContext = mock(SkillTemplateEngine.TemplateContext.class);
            when(mockContext.withArguments(anyString())).thenReturn(mockContext);
            when(mockContext.withProjectRoot(anyString())).thenReturn(mockContext);
            when(mockContext.withCwd(anyString())).thenReturn(mockContext);
            when(templateEngine.createContext()).thenReturn(mockContext);
            when(templateEngine.render(anyString(), any())).thenReturn("简单聊天");

            ContextBuilder.SkillAwareRequest result = contextBuilder.buildWithActiveSkill(
                    skill, null, null, "hi", false  // enableTools = false
            );

            assertNull(result.request().getTools());
        }
    }

    @Nested
    @DisplayName("智能构建")
    class BuildSmartTests {

        @Test
        @DisplayName("检测到 slash command 时激活 skill")
        void buildSmartWithSlashCommand() {
            SkillSelection manualSelection = SkillSelection.manual("code-review", "test.java", "/code-review test.java");
            when(skillSelector.tryManualSelection("/code-review test.java")).thenReturn(manualSelection);

            LoadedSkill skill = LoadedSkill.builder()
                    .name("code-review")
                    .description("代码审查")
                    .body("审查代码")
                    .build();
            when(skillRegistry.activate("code-review")).thenReturn(Optional.of(skill));

            SkillTemplateEngine.TemplateContext mockContext = mock(SkillTemplateEngine.TemplateContext.class);
            when(mockContext.withArguments(anyString())).thenReturn(mockContext);
            when(mockContext.withProjectRoot(anyString())).thenReturn(mockContext);
            when(mockContext.withCwd(anyString())).thenReturn(mockContext);
            when(templateEngine.createContext()).thenReturn(mockContext);
            when(templateEngine.render(anyString(), any())).thenReturn("审查代码");
            // enableTools = false，不需要 stub toolRegistry.size()

            ContextBuilder.SkillAwareRequest result = contextBuilder.buildSmart(null, "/code-review test.java", false);

            assertTrue(result.hasActiveSkill());
            assertEquals("code-review", result.activeSkillName());
        }

        @Test
        @DisplayName("普通消息且有可用 skill 时注入索引")
        void buildSmartWithNormalMessageAndAvailableSkills() {
            SkillSelection noneSelection = SkillSelection.none("帮我审查代码");
            when(skillSelector.tryManualSelection("帮我审查代码")).thenReturn(noneSelection);

            // 返回非空列表，触发 buildWithSkillIndex
            SkillEntry entry = SkillEntry.builder()
                    .metadata(SkillMetadata.builder().name("code-review").description("审查").build())
                    .available(true)
                    .build();
            when(skillRegistry.getAvailable()).thenReturn(List.of(entry));
            when(skillIndexBuilder.buildIndex()).thenReturn("<skills></skills>");

            ContextBuilder.SkillAwareRequest result = contextBuilder.buildSmart(null, "帮我审查代码", false);

            assertFalse(result.hasActiveSkill());
            verify(skillIndexBuilder).buildIndex();
        }

        @Test
        @DisplayName("普通消息且无可用 skill 时不注入索引")
        void buildSmartWithNormalMessageNoSkills() {
            SkillSelection noneSelection = SkillSelection.none("帮我审查代码");
            when(skillSelector.tryManualSelection("帮我审查代码")).thenReturn(noneSelection);
            when(skillRegistry.getAvailable()).thenReturn(List.of());  // 空列表

            ContextBuilder.SkillAwareRequest result = contextBuilder.buildSmart(null, "帮我审查代码", false);

            assertFalse(result.hasActiveSkill());
            verify(skillIndexBuilder, never()).buildIndex();
        }

        @Test
        @DisplayName("autoSelect 禁用时不注入索引")
        void buildSmartWithAutoSelectDisabled() {
            ReflectionTestUtils.setField(contextBuilder, "autoSelectEnabled", false);

            SkillSelection noneSelection = SkillSelection.none("帮我审查代码");
            when(skillSelector.tryManualSelection("帮我审查代码")).thenReturn(noneSelection);

            ContextBuilder.SkillAwareRequest result = contextBuilder.buildSmart(null, "帮我审查代码", false);

            assertFalse(result.hasActiveSkill());
            verify(skillIndexBuilder, never()).buildIndex();
        }
    }

    @Nested
    @DisplayName("处理 LLM 自动选择")
    class HandleAutoSkillSelectionTests {

        @Test
        @DisplayName("检测到 [USE_SKILL:xxx] 时激活 skill")
        void handleAutoSkillSelection() {
            String llmResponse = "我会使用代码审查技能来帮您。[USE_SKILL:code-review]";
            SkillSelection autoSelection = SkillSelection.auto("code-review", "原始输入");
            when(skillSelector.parseFromLlmResponse(llmResponse, "原始输入")).thenReturn(autoSelection);

            LoadedSkill skill = LoadedSkill.builder()
                    .name("code-review")
                    .description("代码审查")
                    .body("审查代码")
                    .build();
            when(skillRegistry.activate("code-review")).thenReturn(Optional.of(skill));

            SkillTemplateEngine.TemplateContext mockContext = mock(SkillTemplateEngine.TemplateContext.class);
            when(mockContext.withArguments(anyString())).thenReturn(mockContext);
            when(mockContext.withProjectRoot(anyString())).thenReturn(mockContext);
            when(mockContext.withCwd(anyString())).thenReturn(mockContext);
            when(templateEngine.createContext()).thenReturn(mockContext);
            when(templateEngine.render(anyString(), any())).thenReturn("审查代码");
            // enableTools = false，不需要 stub toolRegistry.size()

            Optional<ContextBuilder.SkillAwareRequest> result = contextBuilder.handleAutoSkillSelection(
                    llmResponse, "原始输入", null, false
            );

            assertTrue(result.isPresent());
            assertEquals("code-review", result.get().activeSkillName());
        }

        @Test
        @DisplayName("无 USE_SKILL 标记时返回 empty")
        void handleAutoSkillSelectionNoMarker() {
            String llmResponse = "我可以帮您审查代码。";
            SkillSelection noneSelection = SkillSelection.none("原始输入");
            when(skillSelector.parseFromLlmResponse(llmResponse, "原始输入")).thenReturn(noneSelection);

            Optional<ContextBuilder.SkillAwareRequest> result = contextBuilder.handleAutoSkillSelection(
                    llmResponse, "原始输入", null, false
            );

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("skill 不存在时返回 empty")
        void handleAutoSkillSelectionSkillNotFound() {
            String llmResponse = "[USE_SKILL:nonexistent]";
            SkillSelection autoSelection = SkillSelection.auto("nonexistent", "原始输入");
            when(skillSelector.parseFromLlmResponse(llmResponse, "原始输入")).thenReturn(autoSelection);
            when(skillRegistry.activate("nonexistent")).thenReturn(Optional.empty());

            Optional<ContextBuilder.SkillAwareRequest> result = contextBuilder.handleAutoSkillSelection(
                    llmResponse, "原始输入", null, false
            );

            assertTrue(result.isEmpty());
        }
    }

    @Nested
    @DisplayName("SkillAwareRequest 模型")
    class SkillAwareRequestTests {

        @Test
        @DisplayName("hasActiveSkill 判断")
        void hasActiveSkill() {
            ContextBuilder.SkillAwareRequest withSkill = new ContextBuilder.SkillAwareRequest(
                    null, "test-skill", null, null
            );
            assertTrue(withSkill.hasActiveSkill());

            ContextBuilder.SkillAwareRequest withoutSkill = new ContextBuilder.SkillAwareRequest(
                    null, null, null, null
            );
            assertFalse(withoutSkill.hasActiveSkill());
        }

        @Test
        @DisplayName("isToolAllowed 判断")
        void isToolAllowed() {
            Set<String> allowed = Set.of("read_file", "grep");
            ContextBuilder.SkillAwareRequest request = new ContextBuilder.SkillAwareRequest(
                    null, "skill", allowed, null
            );

            assertTrue(request.isToolAllowed("read_file"));
            assertTrue(request.isToolAllowed("grep"));
            assertFalse(request.isToolAllowed("write_file"));

            // 无限制时所有工具都允许
            ContextBuilder.SkillAwareRequest noRestriction = new ContextBuilder.SkillAwareRequest(
                    null, "skill", null, null
            );
            assertTrue(noRestriction.isToolAllowed("any_tool"));

            ContextBuilder.SkillAwareRequest emptyRestriction = new ContextBuilder.SkillAwareRequest(
                    null, "skill", Set.of(), null
            );
            assertTrue(emptyRestriction.isToolAllowed("any_tool"));
        }

        @Test
        @DisplayName("requiresConfirmation 判断")
        void requiresConfirmation() {
            Set<String> confirmBefore = Set.of("write_file", "bash");
            ContextBuilder.SkillAwareRequest request = new ContextBuilder.SkillAwareRequest(
                    null, "skill", null, confirmBefore
            );

            assertTrue(request.requiresConfirmation("write_file"));
            assertTrue(request.requiresConfirmation("bash"));
            assertFalse(request.requiresConfirmation("read_file"));

            // 无确认列表时都不需要确认
            ContextBuilder.SkillAwareRequest noConfirm = new ContextBuilder.SkillAwareRequest(
                    null, "skill", null, null
            );
            assertFalse(noConfirm.requiresConfirmation("any_tool"));
        }
    }

    @Nested
    @DisplayName("buildMessages 方法")
    class BuildMessagesTests {

        @Test
        @DisplayName("构建消息列表")
        void buildMessages() {
            List<LlmRequest.Message> history = List.of(
                    LlmRequest.Message.user("问题1"),
                    LlmRequest.Message.assistant("回答1")
            );

            List<LlmRequest.Message> messages = contextBuilder.buildMessages(history, "问题2");

            assertEquals(4, messages.size());
            assertEquals("system", messages.get(0).getRole());
            assertEquals(DEFAULT_SYSTEM_PROMPT, messages.get(0).getContent());
            assertEquals("user", messages.get(1).getRole());
            assertEquals("assistant", messages.get(2).getRole());
            assertEquals("user", messages.get(3).getRole());
            assertEquals("问题2", messages.get(3).getContent());
        }

        @Test
        @DisplayName("返回可变列表")
        void buildMessagesReturnsMutableList() {
            List<LlmRequest.Message> messages = contextBuilder.buildMessages(null, "问题");

            // 应该可以添加元素
            assertDoesNotThrow(() -> messages.add(LlmRequest.Message.assistant("回答")));
            assertEquals(3, messages.size());
        }
    }
}

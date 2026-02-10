package com.jaguarliu.ai.tools;

import com.jaguarliu.ai.nodeconsole.RemoteCommandClassifier;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * ToolDispatcher 单元测试
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ToolDispatcher Tests")
class ToolDispatcherTest {

    @Mock
    private ToolRegistry toolRegistry;

    @Mock
    private ToolConfigProperties toolConfigProperties;

    @Mock
    private DangerousCommandDetector dangerousCommandDetector;

    @Mock
    private RemoteCommandClassifier remoteCommandClassifier;

    @InjectMocks
    private ToolDispatcher dispatcher;

    private Tool mockTool;

    @BeforeEach
    void setUp() {
        mockTool = mock(Tool.class);
        ToolDefinition definition = ToolDefinition.builder()
                .name("read_file")
                .description("读取文件")
                .hitl(false)
                .build();
        lenient().when(mockTool.getDefinition()).thenReturn(definition);
        lenient().when(mockTool.getName()).thenReturn("read_file");
        // Mock toolConfigProperties 的默认行为
        lenient().when(toolConfigProperties.isAlwaysConfirmTool(anyString())).thenReturn(false);
    }

    @Nested
    @DisplayName("dispatch 工具调度")
    class DispatchTests {

        @Test
        @DisplayName("正常调度工具")
        void dispatchSuccess() {
            when(toolRegistry.get("read_file")).thenReturn(Optional.of(mockTool));
            when(mockTool.execute(anyMap())).thenReturn(Mono.just(ToolResult.success("file content")));

            ToolResult r = dispatcher.dispatch("read_file", Map.of("path", "test.txt")).block();

            assertNotNull(r);
            assertTrue(r.isSuccess());
            assertEquals("file content", r.getContent());
        }

        @Test
        @DisplayName("工具名为空时报错")
        void dispatchEmptyToolName() {
            ToolResult r = dispatcher.dispatch(null, Map.of()).block();

            assertNotNull(r);
            assertFalse(r.isSuccess());
        }

        @Test
        @DisplayName("工具不存在时报错")
        void dispatchToolNotFound() {
            when(toolRegistry.get("nonexistent")).thenReturn(Optional.empty());

            ToolResult r = dispatcher.dispatch("nonexistent", Map.of()).block();

            assertNotNull(r);
            assertFalse(r.isSuccess());
            assertTrue(r.getContent().contains("not found"));
        }

        @Test
        @DisplayName("白名单拒绝时报错")
        void dispatchDeniedByAllowedList() {
            Set<String> allowed = Set.of("grep", "bash");

            ToolResult r = dispatcher.dispatch("read_file", Map.of(), allowed).block();

            assertNotNull(r);
            assertFalse(r.isSuccess());
            assertTrue(r.getContent().contains("not allowed"));
            verify(toolRegistry, never()).get(anyString());
        }

        @Test
        @DisplayName("白名单允许时正常执行")
        void dispatchAllowedByAllowedList() {
            Set<String> allowed = Set.of("read_file", "grep");
            when(toolRegistry.get("read_file")).thenReturn(Optional.of(mockTool));
            when(mockTool.execute(anyMap())).thenReturn(Mono.just(ToolResult.success("ok")));

            ToolResult r = dispatcher.dispatch("read_file", Map.of(), allowed).block();

            assertNotNull(r);
            assertTrue(r.isSuccess());
        }

        @Test
        @DisplayName("白名单为 null 时允许所有")
        void dispatchNullAllowedList() {
            when(toolRegistry.get("read_file")).thenReturn(Optional.of(mockTool));
            when(mockTool.execute(anyMap())).thenReturn(Mono.just(ToolResult.success("ok")));

            ToolResult r = dispatcher.dispatch("read_file", Map.of(), null).block();

            assertNotNull(r);
            assertTrue(r.isSuccess());
        }

        @Test
        @DisplayName("工具执行异常时返回错误")
        void dispatchToolException() {
            when(toolRegistry.get("read_file")).thenReturn(Optional.of(mockTool));
            when(mockTool.execute(anyMap())).thenReturn(Mono.error(new RuntimeException("IO error")));

            ToolResult r = dispatcher.dispatch("read_file", Map.of()).block();

            assertNotNull(r);
            assertFalse(r.isSuccess());
            assertTrue(r.getContent().contains("IO error"));
        }
    }

    @Nested
    @DisplayName("requiresHitl HITL 检查")
    class RequiresHitlTests {

        @Test
        @DisplayName("工具默认不需要 HITL")
        void defaultNoHitl() {
            when(toolRegistry.get("read_file")).thenReturn(Optional.of(mockTool));

            assertFalse(dispatcher.requiresHitl("read_file"));
        }

        @Test
        @DisplayName("工具配置了 HITL")
        void toolWithHitl() {
            Tool hitlTool = mock(Tool.class);
            ToolDefinition hitlDef = ToolDefinition.builder()
                    .name("write_file")
                    .description("写入文件")
                    .hitl(true)
                    .build();
            when(hitlTool.getDefinition()).thenReturn(hitlDef);
            when(toolRegistry.get("write_file")).thenReturn(Optional.of(hitlTool));

            assertTrue(dispatcher.requiresHitl("write_file"));
        }

        @Test
        @DisplayName("工具不存在时返回 false")
        void toolNotFoundReturnsFalse() {
            when(toolRegistry.get("nonexistent")).thenReturn(Optional.empty());

            assertFalse(dispatcher.requiresHitl("nonexistent"));
        }

        @Test
        @DisplayName("skill confirm-before 覆盖：强制需要确认")
        void skillConfirmBeforeOverride() {
            // read_file 默认 hitl=false，但 skill 要求确认
            Set<String> confirmBefore = Set.of("read_file", "grep");

            assertTrue(dispatcher.requiresHitl("read_file", confirmBefore));
        }

        @Test
        @DisplayName("skill confirm-before 不包含工具时用默认配置")
        void skillConfirmBeforeFallbackToDefault() {
            when(toolRegistry.get("read_file")).thenReturn(Optional.of(mockTool));
            Set<String> confirmBefore = Set.of("bash", "write_file");

            assertFalse(dispatcher.requiresHitl("read_file", confirmBefore));
        }

        @Test
        @DisplayName("skill confirm-before 为 null 时用默认配置")
        void skillConfirmBeforeNullFallback() {
            when(toolRegistry.get("read_file")).thenReturn(Optional.of(mockTool));

            assertFalse(dispatcher.requiresHitl("read_file", null));
        }
    }

    @Nested
    @DisplayName("isToolAllowed 权限检查")
    class IsToolAllowedTests {

        @Test
        @DisplayName("白名单为 null 时允许所有")
        void nullAllowedAllows() {
            assertTrue(dispatcher.isToolAllowed("any_tool", null));
        }

        @Test
        @DisplayName("白名单为空时允许所有")
        void emptyAllowedAllows() {
            assertTrue(dispatcher.isToolAllowed("any_tool", Set.of()));
        }

        @Test
        @DisplayName("工具在白名单内")
        void toolInAllowedList() {
            assertTrue(dispatcher.isToolAllowed("read_file", Set.of("read_file", "grep")));
        }

        @Test
        @DisplayName("工具不在白名单内")
        void toolNotInAllowedList() {
            assertFalse(dispatcher.isToolAllowed("bash", Set.of("read_file", "grep")));
        }
    }
}

package com.jaguarliu.ai.tools.builtin;

import com.jaguarliu.ai.memory.index.MemoryIndexer;
import com.jaguarliu.ai.memory.store.MemoryStore;
import com.jaguarliu.ai.tools.ToolDefinition;
import com.jaguarliu.ai.tools.ToolResult;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * MemoryWriteTool 单元测试
 */
@DisplayName("MemoryWriteTool Tests")
@ExtendWith(MockitoExtension.class)
class MemoryWriteToolTest {

    @Mock
    private MemoryStore memoryStore;

    @Mock
    private MemoryIndexer memoryIndexer;

    @InjectMocks
    private MemoryWriteTool tool;

    // ==================== getDefinition 测试 ====================

    @Nested
    @DisplayName("getDefinition")
    class GetDefinitionTests {

        @Test
        @DisplayName("返回正确的工具名称")
        void returnsCorrectName() {
            ToolDefinition def = tool.getDefinition();
            assertEquals("memory_write", def.getName());
        }

        @Test
        @DisplayName("不需要 HITL 确认")
        void doesNotRequireHitl() {
            assertFalse(tool.requiresHitl());
        }

        @Test
        @DisplayName("包含 target 和 content 参数定义")
        void hasRequiredParameters() {
            ToolDefinition def = tool.getDefinition();
            Map<String, Object> params = def.getParameters();

            @SuppressWarnings("unchecked")
            Map<String, Object> properties = (Map<String, Object>) params.get("properties");
            assertTrue(properties.containsKey("target"));
            assertTrue(properties.containsKey("content"));

            @SuppressWarnings("unchecked")
            List<String> required = (List<String>) params.get("required");
            assertTrue(required.contains("target"));
            assertTrue(required.contains("content"));
        }

        @Test
        @DisplayName("target 参数有 enum 限制")
        void targetHasEnumConstraint() {
            ToolDefinition def = tool.getDefinition();

            @SuppressWarnings("unchecked")
            Map<String, Object> properties = (Map<String, Object>) def.getParameters().get("properties");
            @SuppressWarnings("unchecked")
            Map<String, Object> targetDef = (Map<String, Object>) properties.get("target");
            @SuppressWarnings("unchecked")
            List<String> enumValues = (List<String>) targetDef.get("enum");

            assertTrue(enumValues.contains("core"));
            assertTrue(enumValues.contains("daily"));
            assertEquals(2, enumValues.size());
        }
    }

    // ==================== execute 测试 ====================

    @Nested
    @DisplayName("execute")
    class ExecuteTests {

        @Test
        @DisplayName("target 为 null 时返回错误")
        void nullTargetReturnsError() {
            ToolResult result = tool.execute(Map.of("content", "test")).block();

            assertNotNull(result);
            assertFalse(result.isSuccess());
            assertTrue(result.getContent().contains("Missing required parameter: target"));
        }

        @Test
        @DisplayName("content 为 null 时返回错误")
        void nullContentReturnsError() {
            ToolResult result = tool.execute(Map.of("target", "core")).block();

            assertNotNull(result);
            assertFalse(result.isSuccess());
            assertTrue(result.getContent().contains("Missing required parameter: content"));
        }

        @Test
        @DisplayName("target 为空白时返回错误")
        void blankTargetReturnsError() {
            ToolResult result = tool.execute(Map.of("target", "   ", "content", "test")).block();

            assertNotNull(result);
            assertFalse(result.isSuccess());
            assertTrue(result.getContent().contains("Missing required parameter: target"));
        }

        @Test
        @DisplayName("content 为空白时返回错误")
        void blankContentReturnsError() {
            ToolResult result = tool.execute(Map.of("target", "core", "content", "   ")).block();

            assertNotNull(result);
            assertFalse(result.isSuccess());
            assertTrue(result.getContent().contains("Missing required parameter: content"));
        }

        @Test
        @DisplayName("无效的 target 值返回错误")
        void invalidTargetReturnsError() {
            ToolResult result = tool.execute(Map.of("target", "invalid", "content", "test")).block();

            assertNotNull(result);
            assertFalse(result.isSuccess());
            assertTrue(result.getContent().contains("Invalid target"));
        }

        @Test
        @DisplayName("target=core 写入核心记忆")
        void coreTargetWritesToCore() throws IOException {
            ToolResult result = tool.execute(Map.of("target", "core", "content", "Important info")).block();

            assertNotNull(result);
            assertTrue(result.isSuccess());
            verify(memoryStore).appendToCore("Important info");
            verify(memoryIndexer).indexFile("MEMORY.md");
            assertTrue(result.getContent().contains("core memory"));
            assertTrue(result.getContent().contains("MEMORY.md"));
        }

        @Test
        @DisplayName("target=daily 写入今日日记")
        void dailyTargetWritesToDaily() throws IOException {
            ToolResult result = tool.execute(Map.of("target", "daily", "content", "Today's note")).block();

            assertNotNull(result);
            assertTrue(result.isSuccess());
            verify(memoryStore).appendToDaily("Today's note");

            String todayFileName = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE) + ".md";
            verify(memoryIndexer).indexFile(todayFileName);
            assertTrue(result.getContent().contains("daily memory"));
        }

        @Test
        @DisplayName("返回内容包含字符数")
        void resultIncludesCharCount() throws IOException {
            String content = "Hello World";
            ToolResult result = tool.execute(Map.of("target", "core", "content", content)).block();

            assertNotNull(result);
            assertTrue(result.getContent().contains("11 chars"));
        }

        @Test
        @DisplayName("写入失败时返回错误")
        void writeFailureReturnsError() throws IOException {
            doThrow(new IOException("Disk full")).when(memoryStore).appendToCore(anyString());

            ToolResult result = tool.execute(Map.of("target", "core", "content", "test")).block();

            assertNotNull(result);
            assertFalse(result.isSuccess());
            assertTrue(result.getContent().contains("Memory write failed"));
            assertTrue(result.getContent().contains("Disk full"));
        }

        @Test
        @DisplayName("索引失败不影响写入成功")
        void indexFailureDoesNotAffectWrite() throws IOException {
            doThrow(new RuntimeException("Index error")).when(memoryIndexer).indexFile(anyString());

            ToolResult result = tool.execute(Map.of("target", "core", "content", "test")).block();

            assertNotNull(result);
            assertTrue(result.isSuccess()); // 写入仍然成功
            verify(memoryStore).appendToCore("test");
        }

        @Test
        @DisplayName("写入大量内容")
        void writeLargeContent() throws IOException {
            String largeContent = "x".repeat(10000);
            ToolResult result = tool.execute(Map.of("target", "core", "content", largeContent)).block();

            assertNotNull(result);
            assertTrue(result.isSuccess());
            verify(memoryStore).appendToCore(largeContent);
            assertTrue(result.getContent().contains("10000 chars"));
        }
    }

    // ==================== Tool 接口便捷方法测试 ====================

    @Nested
    @DisplayName("Tool interface methods")
    class ToolInterfaceMethodsTests {

        @Test
        @DisplayName("getName 返回正确名称")
        void getNameReturnsCorrectName() {
            assertEquals("memory_write", tool.getName());
        }

        @Test
        @DisplayName("requiresHitl 返回 false")
        void requiresHitlReturnsFalse() {
            assertFalse(tool.requiresHitl());
        }
    }
}

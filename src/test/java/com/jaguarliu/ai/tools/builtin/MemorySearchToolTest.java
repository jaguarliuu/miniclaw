package com.jaguarliu.ai.tools.builtin;

import com.jaguarliu.ai.memory.search.MemorySearchService;
import com.jaguarliu.ai.memory.search.SearchResult;
import com.jaguarliu.ai.tools.ToolDefinition;
import com.jaguarliu.ai.tools.ToolResult;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * MemorySearchTool 单元测试
 */
@DisplayName("MemorySearchTool Tests")
@ExtendWith(MockitoExtension.class)
class MemorySearchToolTest {

    @Mock
    private MemorySearchService searchService;

    @InjectMocks
    private MemorySearchTool tool;

    // ==================== getDefinition 测试 ====================

    @Nested
    @DisplayName("getDefinition")
    class GetDefinitionTests {

        @Test
        @DisplayName("返回正确的工具名称")
        void returnsCorrectName() {
            ToolDefinition def = tool.getDefinition();
            assertEquals("memory_search", def.getName());
        }

        @Test
        @DisplayName("不需要 HITL 确认")
        void doesNotRequireHitl() {
            assertFalse(tool.requiresHitl());
        }

        @Test
        @DisplayName("包含 query 参数定义")
        void hasQueryParameter() {
            ToolDefinition def = tool.getDefinition();
            Map<String, Object> params = def.getParameters();

            assertNotNull(params);
            assertEquals("object", params.get("type"));

            @SuppressWarnings("unchecked")
            Map<String, Object> properties = (Map<String, Object>) params.get("properties");
            assertTrue(properties.containsKey("query"));

            @SuppressWarnings("unchecked")
            List<String> required = (List<String>) params.get("required");
            assertTrue(required.contains("query"));
        }
    }

    // ==================== execute 测试 ====================

    @Nested
    @DisplayName("execute")
    class ExecuteTests {

        @Test
        @DisplayName("query 为 null 时返回错误")
        void nullQueryReturnsError() {
            ToolResult result = tool.execute(Map.of()).block();

            assertNotNull(result);
            assertFalse(result.isSuccess());
            assertTrue(result.getContent().contains("Missing required parameter"));
        }

        @Test
        @DisplayName("query 为空白时返回错误")
        void blankQueryReturnsError() {
            ToolResult result = tool.execute(Map.of("query", "   ")).block();

            assertNotNull(result);
            assertFalse(result.isSuccess());
            assertTrue(result.getContent().contains("Missing required parameter"));
        }

        @Test
        @DisplayName("无搜索结果时返回提示信息")
        void noResultsReturnsMessage() {
            when(searchService.search("unknown query")).thenReturn(List.of());

            ToolResult result = tool.execute(Map.of("query", "unknown query")).block();

            assertNotNull(result);
            assertTrue(result.isSuccess());
            assertTrue(result.getContent().contains("No relevant memories found"));
        }

        @Test
        @DisplayName("有搜索结果时返回格式化内容")
        void returnsFormattedResults() {
            List<SearchResult> searchResults = List.of(
                    SearchResult.builder()
                            .filePath("MEMORY.md")
                            .lineStart(1)
                            .lineEnd(10)
                            .snippet("User prefers Python for scripting")
                            .score(0.85)
                            .source("vector")
                            .build(),
                    SearchResult.builder()
                            .filePath("2026-01-15.md")
                            .lineStart(20)
                            .lineEnd(25)
                            .snippet("Discussed Python async programming")
                            .score(0.72)
                            .source("fts")
                            .build()
            );
            when(searchService.search("Python")).thenReturn(searchResults);

            ToolResult result = tool.execute(Map.of("query", "Python")).block();

            assertNotNull(result);
            assertTrue(result.isSuccess());
            assertTrue(result.getContent().contains("Found 2 relevant memories"));
            assertTrue(result.getContent().contains("MEMORY.md"));
            assertTrue(result.getContent().contains("L1-L10"));
            assertTrue(result.getContent().contains("0.85"));
            assertTrue(result.getContent().contains("vector"));
            assertTrue(result.getContent().contains("User prefers Python"));
        }

        @Test
        @DisplayName("搜索服务异常时返回错误")
        void searchExceptionReturnsError() {
            when(searchService.search(anyString()))
                    .thenThrow(new RuntimeException("Database error"));

            ToolResult result = tool.execute(Map.of("query", "test")).block();

            assertNotNull(result);
            assertFalse(result.isSuccess());
            assertTrue(result.getContent().contains("Memory search failed"));
            assertTrue(result.getContent().contains("Database error"));
        }

        @Test
        @DisplayName("结果包含序号")
        void resultsIncludeIndex() {
            List<SearchResult> searchResults = List.of(
                    SearchResult.builder()
                            .filePath("test.md")
                            .lineStart(1)
                            .lineEnd(5)
                            .snippet("content")
                            .score(0.9)
                            .source("vector")
                            .build()
            );
            when(searchService.search("test")).thenReturn(searchResults);

            ToolResult result = tool.execute(Map.of("query", "test")).block();

            assertNotNull(result);
            assertTrue(result.getContent().contains("[1]"));
        }

        @Test
        @DisplayName("分数格式化为两位小数")
        void scoreFormattedToTwoDecimals() {
            List<SearchResult> searchResults = List.of(
                    SearchResult.builder()
                            .filePath("test.md")
                            .lineStart(1)
                            .lineEnd(5)
                            .snippet("content")
                            .score(0.8765)
                            .source("vector")
                            .build()
            );
            when(searchService.search("test")).thenReturn(searchResults);

            ToolResult result = tool.execute(Map.of("query", "test")).block();

            assertNotNull(result);
            assertTrue(result.getContent().contains("0.88")); // 四舍五入
        }
    }

    // ==================== Tool 接口便捷方法测试 ====================

    @Nested
    @DisplayName("Tool interface methods")
    class ToolInterfaceMethodsTests {

        @Test
        @DisplayName("getName 返回正确名称")
        void getNameReturnsCorrectName() {
            assertEquals("memory_search", tool.getName());
        }

        @Test
        @DisplayName("requiresHitl 返回 false")
        void requiresHitlReturnsFalse() {
            assertFalse(tool.requiresHitl());
        }
    }
}

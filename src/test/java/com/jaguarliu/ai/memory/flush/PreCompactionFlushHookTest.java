package com.jaguarliu.ai.memory.flush;

import com.jaguarliu.ai.llm.LlmClient;
import com.jaguarliu.ai.llm.model.LlmRequest;
import com.jaguarliu.ai.llm.model.LlmResponse;
import com.jaguarliu.ai.memory.MemoryProperties;
import com.jaguarliu.ai.memory.index.MemoryIndexer;
import com.jaguarliu.ai.memory.store.MemoryStore;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * PreCompactionFlushHook 单元测试
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PreCompactionFlushHook Tests")
class PreCompactionFlushHookTest {

    @Mock
    private MemoryProperties properties;

    @Mock
    private MemoryProperties.FlushConfig flushConfig;

    @Mock
    private LlmClient llmClient;

    @Mock
    private MemoryStore memoryStore;

    @Mock
    private MemoryIndexer indexer;

    @InjectMocks
    private PreCompactionFlushHook hook;

    @BeforeEach
    void setUp() {
        lenient().when(properties.getFlush()).thenReturn(flushConfig);
    }

    // ==================== checkAndFlush 测试 ====================

    @Nested
    @DisplayName("checkAndFlush")
    class CheckAndFlushTests {

        @Test
        @DisplayName("flush 禁用时返回 false")
        void disabledReturnsFalse() {
            when(flushConfig.isEnabled()).thenReturn(false);

            List<LlmRequest.Message> messages = createMessages(10000);
            boolean result = hook.checkAndFlush("run-1", messages);

            assertFalse(result);
            verifyNoInteractions(llmClient);
        }

        @Test
        @DisplayName("token 未达阈值时返回 false")
        void belowThresholdReturnsFalse() {
            when(flushConfig.isEnabled()).thenReturn(true);
            when(flushConfig.getTokenThreshold()).thenReturn(6000);

            // 创建少量消息（大约 100 tokens）
            List<LlmRequest.Message> messages = List.of(
                    LlmRequest.Message.user("Hello"),
                    LlmRequest.Message.assistant("Hi there!")
            );

            boolean result = hook.checkAndFlush("run-1", messages);

            assertFalse(result);
            verifyNoInteractions(llmClient);
        }

        @Test
        @DisplayName("token 达到阈值时执行 flush")
        void aboveThresholdTriggersFlush() throws IOException {
            when(flushConfig.isEnabled()).thenReturn(true);
            when(flushConfig.getTokenThreshold()).thenReturn(100);

            LlmResponse response = LlmResponse.builder()
                    .content("## Summary\n\nThis is a test summary.")
                    .build();
            when(llmClient.chat(any())).thenReturn(response);

            List<LlmRequest.Message> messages = createMessages(500);
            boolean result = hook.checkAndFlush("run-1", messages);

            assertTrue(result);
            verify(llmClient).chat(any());
            verify(memoryStore).appendToDaily(contains("Session Summary"));
            verify(indexer).indexFile(anyString());
        }

        @Test
        @DisplayName("同一 run 不会重复 flush")
        void sameRunDoesNotFlushTwice() throws IOException {
            when(flushConfig.isEnabled()).thenReturn(true);
            when(flushConfig.getTokenThreshold()).thenReturn(100);

            LlmResponse response = LlmResponse.builder()
                    .content("Summary content")
                    .build();
            when(llmClient.chat(any())).thenReturn(response);

            List<LlmRequest.Message> messages = createMessages(500);

            // 第一次 flush
            boolean result1 = hook.checkAndFlush("run-1", messages);
            assertTrue(result1);

            // 第二次调用应返回 false
            boolean result2 = hook.checkAndFlush("run-1", messages);
            assertFalse(result2);

            // LLM 只调用了一次
            verify(llmClient, times(1)).chat(any());
        }

        @Test
        @DisplayName("不同 run 可以分别 flush")
        void differentRunsCanFlush() throws IOException {
            when(flushConfig.isEnabled()).thenReturn(true);
            when(flushConfig.getTokenThreshold()).thenReturn(100);

            LlmResponse response = LlmResponse.builder()
                    .content("Summary content")
                    .build();
            when(llmClient.chat(any())).thenReturn(response);

            List<LlmRequest.Message> messages = createMessages(500);

            boolean result1 = hook.checkAndFlush("run-1", messages);
            boolean result2 = hook.checkAndFlush("run-2", messages);

            assertTrue(result1);
            assertTrue(result2);
            verify(llmClient, times(2)).chat(any());
        }

        @Test
        @DisplayName("LLM 返回空内容时不写入")
        void emptyResponseDoesNotWrite() throws IOException {
            when(flushConfig.isEnabled()).thenReturn(true);
            when(flushConfig.getTokenThreshold()).thenReturn(100);

            LlmResponse response = LlmResponse.builder()
                    .content("   ")  // 空白内容
                    .build();
            when(llmClient.chat(any())).thenReturn(response);

            List<LlmRequest.Message> messages = createMessages(500);
            boolean result = hook.checkAndFlush("run-1", messages);

            assertTrue(result);  // flush 被触发
            verify(llmClient).chat(any());
            verify(memoryStore, never()).appendToDaily(anyString());  // 但没有写入
        }

        @Test
        @DisplayName("LLM 调用失败时返回 false")
        void llmFailureReturnsFalse() {
            when(flushConfig.isEnabled()).thenReturn(true);
            when(flushConfig.getTokenThreshold()).thenReturn(100);
            when(llmClient.chat(any())).thenThrow(new RuntimeException("API error"));

            List<LlmRequest.Message> messages = createMessages(500);
            boolean result = hook.checkAndFlush("run-1", messages);

            assertFalse(result);
        }

        @Test
        @DisplayName("写入失败时返回 false")
        void writeFailureReturnsFalse() throws IOException {
            when(flushConfig.isEnabled()).thenReturn(true);
            when(flushConfig.getTokenThreshold()).thenReturn(100);

            LlmResponse response = LlmResponse.builder()
                    .content("Summary content")
                    .build();
            when(llmClient.chat(any())).thenReturn(response);
            doThrow(new IOException("Disk full")).when(memoryStore).appendToDaily(anyString());

            List<LlmRequest.Message> messages = createMessages(500);
            boolean result = hook.checkAndFlush("run-1", messages);

            assertFalse(result);
        }
    }

    // ==================== clearRun 测试 ====================

    @Nested
    @DisplayName("clearRun")
    class ClearRunTests {

        @Test
        @DisplayName("清理后可以再次 flush")
        void clearAllowsReflush() throws IOException {
            when(flushConfig.isEnabled()).thenReturn(true);
            when(flushConfig.getTokenThreshold()).thenReturn(100);

            LlmResponse response = LlmResponse.builder()
                    .content("Summary content")
                    .build();
            when(llmClient.chat(any())).thenReturn(response);

            List<LlmRequest.Message> messages = createMessages(500);

            // 第一次 flush
            hook.checkAndFlush("run-1", messages);

            // 清理
            hook.clearRun("run-1");

            // 可以再次 flush
            boolean result = hook.checkAndFlush("run-1", messages);

            assertTrue(result);
            verify(llmClient, times(2)).chat(any());
        }
    }

    // ==================== 请求构建测试 ====================

    @Nested
    @DisplayName("Request Building")
    class RequestBuildingTests {

        @Test
        @DisplayName("flush 请求包含 FLUSH_PROMPT")
        void requestContainsFlushPrompt() throws IOException {
            when(flushConfig.isEnabled()).thenReturn(true);
            when(flushConfig.getTokenThreshold()).thenReturn(1);  // 极低阈值，确保触发

            LlmResponse response = LlmResponse.builder()
                    .content("Summary")
                    .build();
            when(llmClient.chat(any())).thenReturn(response);

            List<LlmRequest.Message> messages = List.of(
                    LlmRequest.Message.user("Hello, this is a test message.")
            );

            hook.checkAndFlush("run-1", messages);

            ArgumentCaptor<LlmRequest> captor = ArgumentCaptor.forClass(LlmRequest.class);
            verify(llmClient).chat(captor.capture());

            LlmRequest captured = captor.getValue();
            // 原始消息 + FLUSH_PROMPT
            assertEquals(2, captured.getMessages().size());

            String lastMessage = captured.getMessages().get(1).getContent();
            assertTrue(lastMessage.contains("总结当前对话"));
            assertTrue(lastMessage.contains("全局记忆"));
        }
    }

    // ==================== 索引更新测试 ====================

    @Nested
    @DisplayName("Index Update")
    class IndexUpdateTests {

        @Test
        @DisplayName("flush 后更新今日日记索引")
        void updatesCorrectIndexFile() throws IOException {
            when(flushConfig.isEnabled()).thenReturn(true);
            when(flushConfig.getTokenThreshold()).thenReturn(100);

            LlmResponse response = LlmResponse.builder()
                    .content("Summary")
                    .build();
            when(llmClient.chat(any())).thenReturn(response);

            List<LlmRequest.Message> messages = createMessages(500);
            hook.checkAndFlush("run-1", messages);

            String expectedFileName = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE) + ".md";
            verify(indexer).indexFile(expectedFileName);
        }
    }

    // ==================== 辅助方法 ====================

    /**
     * 创建指定 token 数量的消息列表
     */
    private List<LlmRequest.Message> createMessages(int targetTokens) {
        List<LlmRequest.Message> messages = new ArrayList<>();
        int currentTokens = 0;

        while (currentTokens < targetTokens) {
            String content = "这是一段测试文本，用于模拟对话内容。" +
                    "This is some test content for simulating conversation.";
            messages.add(LlmRequest.Message.user(content));
            // 粗略估算：中文 ×2 + 英文 ×0.3 ≈ 每条约 50-60 tokens
            currentTokens += 55;
        }

        return messages;
    }
}

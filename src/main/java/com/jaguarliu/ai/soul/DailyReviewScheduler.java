package com.jaguarliu.ai.soul;

import com.jaguarliu.ai.llm.model.LlmRequest;
import com.jaguarliu.ai.memory.store.MemoryStore;
import com.jaguarliu.ai.runtime.AgentRuntime;
import com.jaguarliu.ai.runtime.ContextBuilder;
import com.jaguarliu.ai.session.MessageService;
import com.jaguarliu.ai.session.RunService;
import com.jaguarliu.ai.session.SessionService;
import com.jaguarliu.ai.storage.entity.MessageEntity;
import com.jaguarliu.ai.storage.entity.RunEntity;
import com.jaguarliu.ai.storage.entity.SessionEntity;
import com.jaguarliu.ai.storage.repository.MessageRepository;
import com.jaguarliu.ai.storage.repository.SessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 每日回顾定时任务
 * 每天晚上 12 点触发，回顾当天内容并写入持久记忆
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DailyReviewScheduler {

    private final SessionRepository sessionRepository;
    private final MessageRepository messageRepository;
    private final MemoryStore memoryStore;
    private final AgentRuntime agentRuntime;
    private final SoulConfigService soulConfigService;
    private final SessionService sessionService;
    private final RunService runService;
    private final MessageService messageService;
    private final ContextBuilder contextBuilder;

    /**
     * 每天晚上 12 点执行
     * cron: 秒 分 时 日 月 周
     */
    @Scheduled(cron = "0 0 0 * * *")
    public void performDailyReview() {
        log.info("Starting daily review task...");

        try {
            // 获取今天的日期范围
            LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
            LocalDateTime endOfDay = startOfDay.plusDays(1);

            // 获取今天的所有 session
            List<SessionEntity> todaySessions = sessionRepository.findByCreatedAtBetween(startOfDay, endOfDay);

            if (todaySessions.isEmpty()) {
                log.info("No sessions found for today, skipping review");
                return;
            }

            log.info("Found {} sessions for today", todaySessions.size());

            // 收集所有消息
            StringBuilder conversationLog = new StringBuilder();
            int totalMessages = 0;

            for (SessionEntity session : todaySessions) {
                List<MessageEntity> messages = messageRepository.findBySessionIdOrderByCreatedAtAsc(session.getId());
                if (!messages.isEmpty()) {
                    conversationLog.append("## Session: ").append(session.getName()).append("\n\n");
                    for (MessageEntity msg : messages) {
                        conversationLog.append("**").append(msg.getRole()).append("**: ")
                                .append(truncate(msg.getContent(), 500))
                                .append("\n\n");
                    }
                    totalMessages += messages.size();
                }
            }

            if (totalMessages == 0) {
                log.info("No messages found in today's sessions");
                return;
            }

            log.info("Processing {} messages from {} sessions", totalMessages, todaySessions.size());

            // 生成回顾提示词
            String reviewPrompt = generateReviewPrompt(conversationLog.toString());

            // 执行回顾分析（异步）
            performReviewAnalysis(reviewPrompt);

        } catch (Exception e) {
            log.error("Daily review task failed", e);
        }
    }

    private String generateReviewPrompt(String conversationLog) {
        return """
                # Daily Review Task

                Please review today's conversations and extract valuable information:

                ## Tasks:
                1. **Persistent Memory**: Identify important facts, preferences, or context that should be remembered long-term
                2. **Personality Enrichment**: Note any interactions that reveal user preferences or communication styles
                3. **Action Items**: Identify any tasks or reminders that need follow-up tomorrow

                ## Today's Conversations:

                """ + conversationLog + """

                ## Instructions:
                - For persistent memory: Extract facts in clear, concise statements
                - For personality: Note user preferences, work patterns, communication style
                - For action items: List specific tasks with context

                Please provide your analysis in the following format:

                ### Persistent Memory
                [List important facts to remember]

                ### Personality Insights
                [List user preferences and patterns]

                ### Action Items
                [List tasks requiring follow-up tomorrow]
                """;
    }

    private void performReviewAnalysis(String prompt) {
        try {
            log.info("Starting daily review analysis...");

            // 1. 创建定时任务会话
            SessionEntity scheduledSession = sessionService.createScheduledSession("定时触发");
            String sessionId = scheduledSession.getId();
            String runId = UUID.randomUUID().toString();
            String connectionId = "__scheduled__";  // 特殊占位 connectionId

            // 2. 创建 Run
            RunEntity run = runService.create(sessionId, prompt);

            // 3. 构建消息上下文
            List<LlmRequest.Message> messages = new ArrayList<>();
            messages.add(LlmRequest.Message.user(prompt));

            // 4. 调用 Agent 执行分析
            log.info("Invoking agent for daily review analysis...");
            String response = agentRuntime.executeLoop(connectionId, run.getId(), sessionId, messages, prompt);

            // 5. 保存用户消息和助手响应
            messageService.saveUserMessage(sessionId, run.getId(), prompt);
            messageService.saveAssistantMessage(sessionId, run.getId(), response);

            log.info("Daily review analysis completed, response length: {}", response.length());

            // 6. 解析响应并写入持久记忆
            parseAndStoreReview(response);

            // 7. 提取 action items 并创建提醒任务
            extractAndScheduleActions(response);

        } catch (Exception e) {
            log.error("Failed to perform daily review analysis", e);
        }
    }

    /**
     * 解析 Agent 响应并写入持久记忆
     */
    private void parseAndStoreReview(String response) {
        try {
            // 提取 "Persistent Memory" 部分
            String persistentMemory = extractSection(response, "### Persistent Memory");
            if (persistentMemory != null && !persistentMemory.isBlank()) {
                String today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
                String content = String.format("## Daily Review - %s\n\n%s\n", today, persistentMemory);
                memoryStore.appendToCore(content);
                log.info("Persistent memory written to MEMORY.md");
            }

            // 提取 "Personality Insights" 部分
            String personalityInsights = extractSection(response, "### Personality Insights");
            if (personalityInsights != null && !personalityInsights.isBlank()) {
                String content = String.format("## Personality Insights\n\n%s\n", personalityInsights);
                memoryStore.appendToDaily(content);
                log.info("Personality insights written to daily log");
            }

        } catch (Exception e) {
            log.error("Failed to parse and store review", e);
        }
    }

    /**
     * 提取 action items 并创建提醒任务
     */
    private void extractAndScheduleActions(String response) {
        try {
            String actionItems = extractSection(response, "### Action Items");
            if (actionItems != null && !actionItems.isBlank()) {
                // TODO: 实现创建明天的提醒任务
                // 这需要与 Schedule 系统集成
                log.info("Action items found:\n{}", actionItems);

                // 临时方案：写入 daily log 作为提醒
                String content = String.format("## Action Items for Tomorrow\n\n%s\n", actionItems);
                memoryStore.appendToDaily(content);
            }
        } catch (Exception e) {
            log.error("Failed to extract and schedule actions", e);
        }
    }

    /**
     * 从响应中提取指定章节内容
     */
    private String extractSection(String response, String sectionHeader) {
        int startIndex = response.indexOf(sectionHeader);
        if (startIndex == -1) {
            return null;
        }

        // 跳过标题行
        startIndex = response.indexOf("\n", startIndex);
        if (startIndex == -1) {
            return null;
        }
        startIndex++;

        // 查找下一个章节或文档结尾
        int endIndex = response.indexOf("\n### ", startIndex);
        if (endIndex == -1) {
            endIndex = response.length();
        }

        return response.substring(startIndex, endIndex).trim();
    }

    private String truncate(String text, int maxLength) {
        if (text == null) return "";
        return text.length() > maxLength ? text.substring(0, maxLength) + "..." : text;
    }
}

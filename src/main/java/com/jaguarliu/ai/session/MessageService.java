package com.jaguarliu.ai.session;

import com.jaguarliu.ai.llm.model.LlmRequest;
import com.jaguarliu.ai.storage.entity.MessageEntity;
import com.jaguarliu.ai.storage.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Message 服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MessageService {

    private final MessageRepository messageRepository;

    /**
     * 保存用户消息
     */
    @Transactional
    public MessageEntity saveUserMessage(String sessionId, String runId, String content) {
        return saveMessage(sessionId, runId, "user", content);
    }

    /**
     * 保存助手消息
     */
    @Transactional
    public MessageEntity saveAssistantMessage(String sessionId, String runId, String content) {
        return saveMessage(sessionId, runId, "assistant", content);
    }

    /**
     * 保存消息
     */
    private MessageEntity saveMessage(String sessionId, String runId, String role, String content) {
        MessageEntity message = MessageEntity.builder()
                .id(UUID.randomUUID().toString())
                .sessionId(sessionId)
                .runId(runId)
                .role(role)
                .content(content)
                .build();

        message = messageRepository.save(message);
        log.debug("Saved message: sessionId={}, role={}, length={}", sessionId, role, content.length());
        return message;
    }

    /**
     * 获取 session 的历史消息
     */
    public List<MessageEntity> getSessionHistory(String sessionId) {
        return messageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);
    }

    /**
     * 获取 session 的历史消息（限制数量，取最近的 N 条）
     */
    public List<MessageEntity> getSessionHistory(String sessionId, int limit) {
        List<MessageEntity> all = messageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);
        if (all.size() <= limit) {
            return all;
        }
        // 取最近的 limit 条
        return all.subList(all.size() - limit, all.size());
    }

    /**
     * 将历史消息转换为 LlmRequest.Message 列表
     */
    public List<LlmRequest.Message> toRequestMessages(List<MessageEntity> messages) {
        return messages.stream()
                .map(m -> new LlmRequest.Message(m.getRole(), m.getContent()))
                .toList();
    }
}

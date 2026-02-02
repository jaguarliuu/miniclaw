package com.jaguarliu.ai.llm.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.util.List;

/**
 * LLM 请求模型
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LlmRequest {

    /**
     * 消息列表
     */
    private List<Message> messages;

    /**
     * 模型名称（可选，默认使用配置）
     */
    private String model;

    /**
     * 温度参数
     */
    private Double temperature;

    /**
     * 最大 token 数
     */
    private Integer maxTokens;

    /**
     * 是否流式输出
     */
    private Boolean stream;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Message {
        private String role;    // system, user, assistant
        private String content;

        public static Message system(String content) {
            return new Message("system", content);
        }

        public static Message user(String content) {
            return new Message("user", content);
        }

        public static Message assistant(String content) {
            return new Message("assistant", content);
        }
    }
}

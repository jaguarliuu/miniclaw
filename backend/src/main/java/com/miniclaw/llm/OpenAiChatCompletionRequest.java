package com.miniclaw.llm;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
class OpenAiChatCompletionRequest {

    private String model;
    private List<OpenAiChatMessage> messages;
    private Double temperature;

    @JsonProperty("max_tokens")
    private Integer maxTokens;

    private Boolean stream;
    private List<Map<String, Object>> tools;

    @JsonProperty("tool_choice")
    private String toolChoice;

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    static class OpenAiChatMessage {
        private String role;
        private Object content;

        @JsonProperty("tool_calls")
        private List<OpenAiChatToolCall> toolCalls;

        @JsonProperty("tool_call_id")
        private String toolCallId;
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    static class OpenAiChatToolCall {
        private String id;
        private String type;
        private Function function;

        @Data
        @AllArgsConstructor
        @NoArgsConstructor
        static class Function {
            private String name;
            private String arguments;
        }
    }
}

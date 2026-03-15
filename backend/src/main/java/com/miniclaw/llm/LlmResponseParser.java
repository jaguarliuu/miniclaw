package com.miniclaw.llm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniclaw.llm.model.LlmChunk;
import com.miniclaw.llm.model.LlmResponse;
import com.miniclaw.llm.model.ToolCall;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
class LlmResponseParser {

    private final ObjectMapper objectMapper;

    LlmResponseParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    LlmResponse parseChat(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            throw new LlmException(LlmErrorType.INVALID_RESPONSE, false, null, "LLM returned an empty response body");
        }

        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode choices = root.get("choices");

            if (choices == null || choices.isEmpty()) {
                throw new LlmException(LlmErrorType.INVALID_RESPONSE, false, null, "LLM response did not contain choices");
            }

            JsonNode firstChoice = choices.get(0);
            JsonNode message = firstChoice.get("message");
            if (message == null || message.isNull()) {
                throw new LlmException(LlmErrorType.INVALID_RESPONSE, false, null, "LLM response did not contain a message");
            }

            String content = null;
            if (message.has("content") && !message.get("content").isNull()) {
                content = message.get("content").asText();
            }

            String finishReason = firstChoice.has("finish_reason")
                    ? firstChoice.get("finish_reason").asText()
                    : null;

            List<ToolCall> toolCalls = parseToolCalls(message);
            LlmResponse.Usage usage = parseUsage(root);

            return LlmResponse.builder()
                    .content(content)
                    .toolCalls(toolCalls)
                    .finishReason(finishReason)
                    .usage(usage)
                    .build();
        } catch (JsonProcessingException e) {
            throw new LlmException(LlmErrorType.INVALID_RESPONSE, false, null, "Failed to parse LLM response", e);
        }
    }

    Optional<LlmChunk> parseSseLine(String line) {
        String data = line;
        if (line.startsWith("data:")) {
            data = line.substring(5).trim();
        }

        if (data.isEmpty()) {
            return Optional.empty();
        }

        if ("[DONE]".equals(data)) {
            return Optional.of(LlmChunk.builder().done(true).build());
        }

        try {
            JsonNode root = objectMapper.readTree(data);
            JsonNode choices = root.get("choices");
            if (choices == null || choices.isEmpty()) {
                return Optional.empty();
            }

            JsonNode firstChoice = choices.get(0);
            JsonNode delta = firstChoice.get("delta");
            JsonNode finishReasonNode = firstChoice.get("finish_reason");

            String content = null;
            if (delta != null && delta.has("content") && !delta.get("content").isNull()) {
                content = delta.get("content").asText();
            }

            String finishReason = null;
            if (finishReasonNode != null && !finishReasonNode.isNull()) {
                finishReason = finishReasonNode.asText();
            }

            boolean done = finishReason != null;
            if (content == null && !done) {
                return Optional.empty();
            }

            return Optional.of(LlmChunk.builder()
                    .delta(content)
                    .finishReason(finishReason)
                    .done(done)
                    .build());
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse SSE chunk: {}", data);
            return Optional.empty();
        }
    }

    private List<ToolCall> parseToolCalls(JsonNode message) {
        if (!message.has("tool_calls")) {
            return null;
        }

        List<ToolCall> toolCalls = new ArrayList<>();
        for (JsonNode tcNode : message.get("tool_calls")) {
            ToolCall toolCall = ToolCall.builder()
                    .id(tcNode.get("id").asText())
                    .type(tcNode.has("type")
                            ? tcNode.get("type").asText()
                            : "function")
                    .function(ToolCall.FunctionCall.builder()
                            .name(tcNode.get("function").get("name").asText())
                            .arguments(tcNode.get("function").get("arguments").asText())
                            .build())
                    .build();
            toolCalls.add(toolCall);
        }
        return toolCalls;
    }

    private LlmResponse.Usage parseUsage(JsonNode root) {
        if (!root.has("usage")) {
            return null;
        }

        JsonNode usageNode = root.get("usage");
        return LlmResponse.Usage.builder()
                .promptTokens(usageNode.get("prompt_tokens").asInt())
                .completionTokens(usageNode.get("completion_tokens").asInt())
                .totalTokens(usageNode.get("total_tokens").asInt())
                .build();
    }
}

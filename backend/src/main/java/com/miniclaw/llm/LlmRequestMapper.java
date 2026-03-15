package com.miniclaw.llm;

import com.miniclaw.config.LlmProviderConfig;
import com.miniclaw.config.LlmProperties;
import com.miniclaw.llm.model.LlmRequest;

import java.util.ArrayList;
import java.util.List;

class LlmRequestMapper {

    private final LlmProperties properties;

    LlmRequestMapper(LlmProperties properties) {
        this.properties = properties;
    }

    OpenAiChatCompletionRequest map(LlmRequest request, ResolvedLlmContext context, boolean stream) {
        List<OpenAiChatCompletionRequest.OpenAiChatMessage> messages = request.getMessages().stream()
                .map(this::convertMessage)
                .toList();

        OpenAiChatCompletionRequest.OpenAiChatCompletionRequestBuilder builder = OpenAiChatCompletionRequest.builder()
                .model(resolveModel(request, context))
                .messages(messages)
                .temperature(request.getTemperature() != null
                        ? request.getTemperature()
                        : properties.getTemperature())
                .maxTokens(request.getMaxTokens() != null
                        ? request.getMaxTokens()
                        : properties.getMaxTokens())
                .stream(stream);

        if (request.getTools() != null && !request.getTools().isEmpty()) {
            builder.tools(request.getTools());
            builder.toolChoice(request.getToolChoice() != null
                    ? request.getToolChoice()
                    : "auto");
        }

        return builder.build();
    }

    private String resolveModel(LlmRequest request, ResolvedLlmContext context) {
        boolean multimodalRequest = isMultimodalRequest(request);
        LlmProviderConfig provider = context.getProvider();

        if (request.getModel() != null && !request.getModel().isBlank()) {
            if (multimodalRequest) {
                ensureMultimodalModel(provider, context.getProviderId(), request.getModel());
            }
            return request.getModel();
        }

        if (multimodalRequest) {
            if (provider == null) {
                throw new LlmException(LlmErrorType.BAD_REQUEST, false, null,
                        "Multimodal requests require provider-based multimodal model configuration");
            }

            String multimodalModel = provider.getDefaultMultimodalModel();
            if (multimodalModel == null || multimodalModel.isBlank()) {
                throw new LlmException(LlmErrorType.BAD_REQUEST, false, null,
                        "Provider " + provider.getId() + " does not have a multimodal model configured");
            }
            return multimodalModel;
        }

        if (provider != null) {
            String defaultModel = provider.getDefaultModel();
            if (defaultModel != null && !defaultModel.isBlank()) {
                return defaultModel;
            }
        }

        return properties.getDefaultModelName();
    }

    private boolean isMultimodalRequest(LlmRequest request) {
        if (request.getMessages() == null || request.getMessages().isEmpty()) {
            return false;
        }

        return request.getMessages().stream().anyMatch(LlmRequest.Message::hasImageContent);
    }

    private void ensureMultimodalModel(LlmProviderConfig provider, String providerId, String model) {
        if (provider == null) {
            throw new LlmException(LlmErrorType.BAD_REQUEST, false, null,
                    "Multimodal requests require provider-based multimodal model configuration");
        }

        if (!provider.supportsMultimodal(model)) {
            throw new LlmException(LlmErrorType.BAD_REQUEST, false, null,
                    "Provider " + providerId + " does not support multimodal model " + model);
        }
    }

    private OpenAiChatCompletionRequest.OpenAiChatMessage convertMessage(LlmRequest.Message message) {
        OpenAiChatCompletionRequest.OpenAiChatMessage chatMessage = new OpenAiChatCompletionRequest.OpenAiChatMessage();
        chatMessage.setRole(message.getRole());
        if (message.hasContentParts()) {
            chatMessage.setContent(message.getContentParts());
        } else {
            chatMessage.setContent(message.getContent());
        }

        if (message.getToolCalls() != null && !message.getToolCalls().isEmpty()) {
            List<OpenAiChatCompletionRequest.OpenAiChatToolCall> chatToolCalls = new ArrayList<>();
            message.getToolCalls().forEach(toolCall -> {
                OpenAiChatCompletionRequest.OpenAiChatToolCall chatToolCall =
                        new OpenAiChatCompletionRequest.OpenAiChatToolCall();
                chatToolCall.setId(toolCall.getId());
                chatToolCall.setType(toolCall.getType());
                chatToolCall.setFunction(new OpenAiChatCompletionRequest.OpenAiChatToolCall.Function(
                        toolCall.getFunction().getName(),
                        toolCall.getFunction().getArguments()
                ));
                chatToolCalls.add(chatToolCall);
            });
            chatMessage.setToolCalls(chatToolCalls);
        }

        if ("tool".equals(message.getRole())) {
            chatMessage.setToolCallId(message.getToolCallId());
        }

        return chatMessage;
    }
}

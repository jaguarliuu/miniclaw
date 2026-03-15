package com.miniclaw.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniclaw.config.LlmProperties;
import com.miniclaw.llm.model.LlmChunk;
import com.miniclaw.llm.model.LlmRequest;
import com.miniclaw.llm.model.LlmResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.SynchronousSink;

@Slf4j
@Component
public class OpenAiCompatibleLlmClient implements LlmClient {

    private final LlmProviderRegistry providerRegistry;
    private final LlmRequestMapper requestMapper;
    private final LlmExecutionSupport executionSupport;
    private final LlmResponseParser responseParser;

    @Autowired
    public OpenAiCompatibleLlmClient(LlmProperties properties, ObjectMapper objectMapper) {
        this(
                new LlmProviderRegistry(properties),
                new LlmRequestMapper(properties),
                new LlmExecutionSupport(properties, objectMapper),
                new LlmResponseParser(objectMapper)
        );
    }

    OpenAiCompatibleLlmClient(
            LlmProviderRegistry providerRegistry,
            LlmRequestMapper requestMapper,
            LlmExecutionSupport executionSupport,
            LlmResponseParser responseParser
    ) {
        this.providerRegistry = providerRegistry;
        this.requestMapper = requestMapper;
        this.executionSupport = executionSupport;
        this.responseParser = responseParser;
    }

    @Override
    public LlmResponse chat(LlmRequest request) {
        ResolvedLlmContext context = providerRegistry.resolve(request);
        OpenAiChatCompletionRequest apiRequest = requestMapper.map(request, context, false);

        try {
            LlmResponse response = executionSupport.executeChat(context, apiRequest)
                    .map(responseParser::parseChat)
                    .onErrorMap(executionSupport::asLlmException)
                    .block();

            if (response == null) {
                throw new LlmException(LlmErrorType.INVALID_RESPONSE, false, null, "LLM returned an empty response");
            }
            return response;
        } catch (RuntimeException e) {
            LlmException failure = executionSupport.asLlmException(e);
            log.error("LLM chat request failed: type={}, status={}, retryable={}, message={}",
                    failure.getErrorType(), failure.getHttpStatus(), failure.isRetryable(), failure.getMessage());
            throw failure;
        }
    }

    @Override
    public Flux<LlmChunk> stream(LlmRequest request) {
        ResolvedLlmContext context = providerRegistry.resolve(request);
        OpenAiChatCompletionRequest apiRequest = requestMapper.map(request, context, true);

        return executionSupport.executeStream(context, apiRequest)
                .handle((String line, SynchronousSink<LlmChunk> sink) ->
                        responseParser.parseSseLine(line).ifPresent(sink::next))
                .onErrorMap(executionSupport::asLlmException)
                .doOnError(e -> {
                    LlmException failure = executionSupport.asLlmException(e);
                    log.error("LLM stream request failed: type={}, status={}, retryable={}, message={}",
                            failure.getErrorType(), failure.getHttpStatus(), failure.isRetryable(), failure.getMessage());
                });
    }
}

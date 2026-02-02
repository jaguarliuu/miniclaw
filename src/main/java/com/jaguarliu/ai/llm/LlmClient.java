package com.jaguarliu.ai.llm;

import com.jaguarliu.ai.llm.model.LlmChunk;
import com.jaguarliu.ai.llm.model.LlmRequest;
import com.jaguarliu.ai.llm.model.LlmResponse;
import reactor.core.publisher.Flux;

/**
 * LLM 客户端接口
 */
public interface LlmClient {

    /**
     * 同步调用 LLM
     */
    LlmResponse chat(LlmRequest request);

    /**
     * 流式调用 LLM
     */
    Flux<LlmChunk> stream(LlmRequest request);
}

package com.miniclaw.llm;

import com.miniclaw.llm.model.LlmChunk;
import com.miniclaw.llm.model.LlmRequest;
import com.miniclaw.llm.model.LlmResponse;
import reactor.core.publisher.Flux;

/**
 * LLM 客户端接口
 * 
 * 为什么需要这个接口？
 * - 抽象 LLM 调用，支持多种实现（OpenAI、DeepSeek、Ollama）
 * - 统一的 API，业务层不需要关心具体 LLM 提供商
 * - 便于测试（可以用 Mock 实现）
 * 
 * 为什么用接口而不是直接用 Spring AI？
 * 1. 学习目的：理解底层原理，而不是只会调库
 * 2. 灵活性：可以完全控制请求/响应格式
 * 3. 轻量级：不引入额外依赖，只用了 WebClient
 * 4. 兼容性：支持任何 OpenAI 兼容的 API
 */
public interface LlmClient {

    /**
     * 同步调用 LLM
     * 
     * 适用场景：
     * - 单次问答
     * - 批量处理
     * - 后台任务
     * 
     * @param request LLM 请求
     * @return LLM 响应（完整内容）
     */
    LlmResponse chat(LlmRequest request);

    /**
     * 流式调用 LLM
     * 
     * 适用场景：
     * - 实时对话（逐字显示）
     * - 长文本生成（避免超时）
     * - 用户体验优化
     * 
     * 为什么返回 Flux 而不是 List？
     * - Flux 是响应式流，数据到达时立即推送
     * - List 需要等待所有数据收集完毕
     * - Flux 支持背压（backpressure）
     * 
     * @param request LLM 请求
     * @return 响应式流，逐 chunk 返回
     */
    Flux<LlmChunk> stream(LlmRequest request);
}

# 第4.1节：为什么不用 Spring AI？LLM 客户端架构怎么选？

> **学习目标**：理解 LLM 客户端的架构选型，设计清晰的接口
> **预计时长**：25 分钟
> **难度**：入门

### 前置知识检查

**你应该已经掌握**：
- [x] 第3章：开发环境和项目骨架已搭建
- [ ] HTTP 客户端的基本使用
- [ ] 接口和实现的概念

**如果你不确定**：
- HTTP 不熟 → 本节会讲解 WebClient
- 接口不理解 → 本节会从"为什么需要接口"开始

---

### 为什么这节很重要？

在开始写 LLM 客户端代码之前，我们需要先回答一个关键问题：

**用 Spring AI 还是自己写？**

这个选择会影响：
- 学习曲线
- 代码复杂度
- 灵活性和可控性
- 课程的教学目标

让我们深入分析。

---

### Spring AI 是什么？

**Spring AI** 是 Spring 官方推出的 AI 集成框架，2023 年发布。

**核心功能**：
- 统一的 LLM API（OpenAI、Azure、Ollama 等）
- 向量数据库集成
- RAG（检索增强生成）支持
- Function Calling 封装
- Prompt 模板管理

**示例代码**：
```java
@Service
public class ChatService {
    
    private final ChatClient chatClient;
    
    public ChatService(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }
    
    public String chat(String message) {
        return chatClient.call(message);
    }
}
```

看起来很简单，对吧？那为什么我们不选它？

---

### Spring AI vs 手写客户端对比

| 维度 | Spring AI | 手写客户端 |
|------|-----------|-----------|
| **学习曲线** | 需要学习框架特有概念 | 只需要懂 HTTP 和 JSON |
| **代码量** | 少（框架帮你做了） | 多（自己写解析逻辑） |
| **灵活性** | 受框架限制 | 完全自由 |
| **调试难度** | 需要理解框架内部 | 每行代码都是自己写的 |
| **依赖大小** | ~10MB | ~1MB（只有 WebClient） |
| **原理理解** | 黑盒，不知道内部做了什么 | 白盒，完全透明 |
| **面试价值** | "会用框架" | "懂底层原理" |

#### 详细分析

**Spring AI 的优势**：
1. 开箱即用，快速集成
2. 多 LLM 提供商统一 API
3. RAG、向量数据库开箱即用
4. 社区支持，文档完善

**Spring AI 的劣势**（对于本课程）：
1. **抽象层太厚**：你不知道它内部做了什么
2. **学习框架而不是原理**：换一个框架就不会了
3. **调试困难**：出问题时不知道哪里错了
4. **过度设计**：很多功能我们用不到

**手写客户端的优势**（对于本课程）：
1. **理解原理**：每一行代码都知道为什么
2. **面试加分**：能讲清楚 LLM 调用的完整流程
3. **完全可控**：想怎么改就怎么改
4. **轻量级**：只依赖 WebClient

**手写客户端的劣势**：
1. 需要自己处理细节（重试、超时、错误解析）
2. 代码量更多
3. 需要自己测试兼容性

---

### 为什么本课程选择手写？

#### 理由一：教学目标不同

本课程的目标是：**让你理解 AI Agent 的底层原理**。

如果用 Spring AI：
```java
// 你只学会了这个
String response = chatClient.call("Hello");
// 内部发生了什么？不知道
```

如果手写：
```java
// 你会理解完整的调用链
WebClient.post()
    .uri("/chat/completions")
    .body(request)
    .retrieve()
    .bodyToMono(String.class)
    .map(this::parseResponse);
// HTTP 请求怎么发？
// JSON 怎么解析？
// 流式输出怎么处理？
// 这些你都会清楚
```

**面试时**：
- "你会用 Spring AI" → HR 觉得你会调库
- "我手写过 LLM 客户端" → 技术面试官眼前一亮

#### 理由二：掌握核心原理，框架一通百通

理解了底层原理，再学 Spring AI 只需要 1 小时。

不理解原理，Spring AI 出问题你不知道怎么修。

**类比**：
- Spring AI 就像是"自动挡汽车"
- 手写就像是"手动挡汽车"
- 会开手动挡，自动挡轻松上手
- 只会自动挡，遇到手动挡就傻眼

#### 理由三：灵活应对各种场景

实际工作中，你可能遇到：
- 公司不用 Spring，用 Quarkus
- 需要对接非标准的 LLM API
- 需要深度定制请求/响应格式
- 性能优化，需要精细控制

只会框架，遇到这些就束手无策。

---

### LLM 客户端架构设计

#### 核心接口

```java
public interface LlmClient {

    /**
     * 同步调用 LLM
     * 
     * 适用场景：单次问答、批量处理、后台任务
     */
    LlmResponse chat(LlmRequest request);

    /**
     * 流式调用 LLM
     * 
     * 适用场景：实时对话、长文本生成、用户体验优化
     */
    Flux<LlmChunk> stream(LlmRequest request);
}
```

#### 为什么提供两个方法？

**同步调用**：
- 简单直接
- 适合后台任务
- 等待完整响应

**流式调用**：
- 实时反馈
- 用户体验好
- 避免超时

**类比**：
- 同步 = 下载整个视频再看
- 流式 = 边下载边看

#### 为什么返回 Flux 而不是 List？

```java
// ❌ List：需要等待所有数据
List<LlmChunk> stream(LlmRequest request);

// ✅ Flux：数据到达时立即推送
Flux<LlmChunk> stream(LlmRequest request);
```

**Flux 的优势**：
1. **实时性**：第一个 chunk 到达就推送
2. **背压支持**：消费者可以控制速率
3. **内存友好**：不需要缓存所有数据
4. **可组合**：可以用 filter、map、flatMap 等操作符

#### 接口设计原则

**为什么用接口而不是具体类？**

```java
// ✅ 好的设计：面向接口
LlmClient client = new OpenAiCompatibleLlmClient(properties);

// ❌ 不好的设计：面向实现
OpenAiCompatibleLlmClient client = new OpenAiCompatibleLlmClient(properties);
```

**好处**：
1. **多实现**：可以有 OpenAI、DeepSeek、Ollama 等多个实现
2. **易测试**：可以 Mock LlmClient 进行单元测试
3. **解耦**：业务层不依赖具体实现

---

### 配置类设计

```java
@Data
@Component
@ConfigurationProperties(prefix = "llm")
public class LlmProperties {

    private String endpoint = "https://api.deepseek.com";
    private String apiKey;
    private String model = "deepseek-chat";
    private Double temperature = 0.7;
    private Integer maxTokens = 2048;
    private Integer timeout = 60;
    private Integer maxRetries = 3;
}
```

**对应配置文件**：
```yaml
llm:
  endpoint: https://api.deepseek.com
  api-key: ${LLM_API_KEY}
  model: deepseek-chat
  temperature: 0.7
  max-tokens: 2048
  timeout: 60
  max-retries: 3
```

#### 为什么这样设计？

1. **类型安全**：用 Java 类而不是字符串
2. **IDE 支持**：自动补全、重构
3. **验证**：可以在 setter 中做校验
4. **默认值**：提供合理的默认配置

---

### 技术选型：为什么用 WebClient？

**选项对比**：

| HTTP 客户端 | 特点 | 适用场景 |
|------------|------|----------|
| `RestTemplate` | 同步阻塞 | 传统 Spring 应用 |
| `WebClient` | 异步非阻塞 | 响应式应用 |
| `OkHttp` | 高性能同步 | Android、高性能场景 |
| `Apache HttpClient` | 功能全面 | 企业级应用 |

**为什么选 WebClient**：

1. **原生响应式**：返回 `Mono` 和 `Flux`
2. **流式支持**：天然支持 SSE
3. **Spring 生态**：与 Spring Boot 无缝集成
4. **非阻塞**：高并发场景性能更好

**对比示例**：

```java
// RestTemplate（阻塞）
String response = restTemplate.postForObject(url, request, String.class);
// 线程会一直等待，直到响应返回

// WebClient（非阻塞）
Mono<String> response = webClient.post()
    .uri(url)
    .bodyValue(request)
    .retrieve()
    .bodyToMono(String.class);
// 线程不会阻塞，响应到达时回调
```

---

### OpenAI 兼容协议

我们选择的方案是：**实现 OpenAI 兼容的 LLM 客户端**。

**为什么？**

1. **标准协议**：OpenAI API 已成事实标准
2. **广泛支持**：DeepSeek、通义千问、Ollama 都兼容
3. **一次实现，多处使用**：换 LLM 只需要改 endpoint

**支持的 LLM**：

| LLM | Endpoint | 兼容性 |
|-----|----------|--------|
| OpenAI | `https://api.openai.com` | ✅ 原生 |
| DeepSeek | `https://api.deepseek.com` | ✅ 完全兼容 |
| 通义千问 | `https://dashscope.aliyuncs.com/compatible-mode/v1` | ✅ 兼容模式 |
| Ollama | `http://localhost:11434/v1` | ✅ 完全兼容 |
| 智谱 AI | `https://open.bigmodel.cn/api/paas/v4` | ✅ 兼容 |

---

### 架构全景图

```
┌─────────────────────────────────────────────┐
│              业务层（AgentRuntime）           │
└────────────────────┬────────────────────────┘
                     │ 依赖接口
                     ▼
┌─────────────────────────────────────────────┐
│              LlmClient（接口）                │
│  - chat(LlmRequest): LlmResponse            │
│  - stream(LlmRequest): Flux<LlmChunk>       │
└────────────────────┬────────────────────────┘
                     │ 实现
                     ▼
┌─────────────────────────────────────────────┐
│      OpenAiCompatibleLlmClient（实现）       │
│  - WebClient（HTTP 客户端）                  │
│  - SSE 解析（流式输出）                       │
│  - 重试机制（错误处理）                       │
└────────────────────┬────────────────────────┘
                     │ HTTP 请求
                     ▼
┌─────────────────────────────────────────────┐
│         LLM API（OpenAI/DeepSeek/...）       │
└─────────────────────────────────────────────┘
```

---

### 本节代码结构

```
backend/src/main/java/com/miniclaw/llm/
├── LlmClient.java           # 接口定义
├── LlmProperties.java       # 配置类
└── model/                   # 数据模型（下节）
    ├── LlmRequest.java
    ├── LlmResponse.java
    ├── LlmChunk.java
    └── ToolCall.java
```

---

### 动手实践

**任务**：创建 LLM 客户端接口和配置类

**步骤**：
1. 创建 `llm` 包
2. 创建 `LlmClient` 接口
3. 创建 `LlmProperties` 配置类
4. 配置 `application.yml`

**预期结果**：
- 代码编译通过
- 理解接口设计的原因

---

### 自检：你真的掌握了吗？

**问题 1**：为什么本课程选择手写 LLM 客户端而不是用 Spring AI？
> 如果答不上来，重读「为什么本课程选择手写？」

你的答案：
```


```

参考答案：
<details>
<summary>点击展开</summary>

本课程选择手写的三个核心理由：

1. **教学目标**：让你理解 AI Agent 的底层原理，而不是只会调框架
2. **面试价值**：能讲清楚 LLM 调用的完整流程，"我手写过 LLM 客户端"比"我会用 Spring AI"更有含金量
3. **原理掌握**：理解了底层原理，再学任何框架都是 1 小时的事；不理解原理，框架出问题就束手无策

类比：会开手动挡，自动挡轻松上手；只会自动挡，遇到手动挡就傻眼。

</details>

---

**问题 2**：`Flux<LlmChunk>` 和 `List<LlmChunk>` 有什么区别？
> 如果卡住，说明需要更多练习

你的答案：
```


```

参考答案：
<details>
<summary>点击展开</summary>

| 特性 | Flux | List |
|------|-------|------|
| 数据返回时机 | 第一个数据到达就推送 | 等所有数据收集完毕 |
| 内存占用 | 不需要缓存所有数据 | 需要存储所有元素 |
| 背压支持 | 支持（消费者可控制速率） | 不支持 |
| 实时性 | 实时推送 | 批量返回 |
| 适用场景 | 流式输出、实时更新 | 批量处理、一次性返回 |

**流式输出场景**：Flux 更合适，因为可以让用户立即看到第一个字，而不是等待整个响应。

</details>

---

**问题 3**（选做）：如果要支持多个 LLM 提供商（DeepSeek、OpenAI、Ollama），架构应该怎么设计？

你的答案：
```


```

参考答案：
<details>
<summary>点击展开</summary>

**方案一：配置化切换**（MiniClaw 采用）
- 一个 `OpenAiCompatibleLlmClient` 实现
- 通过 `LlmProperties.endpoint` 切换提供商
- 优点：简单、代码复用
- 限制：只支持 OpenAI 兼容 API

**方案二：多实现**
```java
public interface LlmClient { ... }

public class OpenAiLlmClient implements LlmClient { ... }
public class DeepSeekLlmClient implements LlmClient { ... }
public class OllamaLlmClient implements LlmClient { ... }
```
- 优点：每个实现可以针对性优化
- 缺点：代码重复、维护成本高

**方案三：策略模式**
```java
@Service
public class LlmClientFactory {
    private final Map<String, LlmClient> clients;
    
    public LlmClient getClient(String providerId) {
        return clients.get(providerId);
    }
}
```
- 优点：灵活、支持运行时切换
- 适用：需要同时使用多个 LLM 的场景

MiniClaw 采用方案一，因为课程目标是教学原理，保持简单。

</details>

---

### 掌握度自评

| 状态 | 标准 | 建议 |
|------|------|------|
| 🟢 已掌握 | 3题全对，实践任务完成 | 进入下一节 |
| 🟡 基本掌握 | 2题正确，实践任务部分完成 | 再复习一遍，重做实践 |
| 🔴 需要加强 | 1题及以下 | 重读本节，务必动手实践 |

---

### 本节小结

- 我们学习了 LLM 客户端的架构选型
- 关键要点：
  - Spring AI 适合快速开发，但不适合学习原理
  - 手写客户端让你理解底层细节
  - 接口设计要考虑多实现、易测试
  - WebClient 是响应式 HTTP 客户端的最佳选择
  - OpenAI 兼容协议让我们一次实现，多处使用
- 下一节我们将设计请求/响应数据模型

---

### 扩展阅读（可选）

- [Spring AI 官方文档](https://docs.spring.io/spring-ai/reference/)
- [OpenAI API 文档](https://platform.openai.com/docs/api-reference/chat)
- [WebClient 官方文档](https://docs.spring.io/spring-framework/docs/current/reference/html/web-reactive.html#webflux-client)
- [Reactor Flux 详解](https://projectreactor.io/docs/core/release/reference/#flux)

# 第4.3节：同步调用实现 - chat() 方法

> **学习目标**：实现 LLM 的同步调用，理解 WebClient 的使用
> **预计时长**：25 分钟
> **难度**：入门

### 前置知识检查

**你应该已经掌握**：
- [x] 4.1 架构设计
- [x] 4.2 数据模型
- [ ] WebClient 基本使用

**如果你不确定**：
- WebClient 没用过 → 本节会详细讲解
- JSON 解析不熟 → 本节会演示 Jackson 的使用

---

### 为什么这节很重要？

同步调用是 LLM 客户端最基础的功能。

**使用场景**：
- 批量处理文本
- 后台任务
- 单次问答
- 测试验证

**虽然流式调用体验更好，但同步调用更简单**，适合作为起点。

---

### WebClient 基础

#### 什么是 WebClient？

**WebClient** 是 Spring WebFlux 提供的响应式 HTTP 客户端。

**与 RestTemplate 的对比**：

| 特性 | RestTemplate | WebClient |
|------|--------------|-----------|
| 模型 | 同步阻塞 | 异步非阻塞 |
| 返回类型 | 直接返回结果 | Mono/Flux |
| 流式支持 | 不支持 | 原生支持 |
| 性能 | 一般 | 更好 |
| 推荐度 | 已过时 | 推荐使用 |

**为什么选 WebClient**：
1. **响应式**：返回 `Mono` 和 `Flux`，天然支持响应式
2. **流式**：原生支持 SSE 流式响应
3. **性能**：非阻塞，高并发性能更好
4. **未来**：Spring 官方推荐，RestTemplate 已进入维护模式

#### WebClient 基本用法

```java
// 创建 WebClient
WebClient client = WebClient.builder()
        .baseUrl("https://api.openai.com/v1")
        .defaultHeader("Authorization", "Bearer " + apiKey)
        .build();

// 发送 GET 请求
String result = client.get()
        .uri("/models")
        .retrieve()
        .bodyToMono(String.class)
        .block();

// 发送 POST 请求
String response = client.post()
        .uri("/chat/completions")
        .bodyValue(requestBody)
        .retrieve()
        .bodyToMono(String.class)
        .block();
```

#### Mono vs Flux

| 类型 | 含义 | 返回数量 |
|------|------|----------|
| `Mono<T>` | 0 或 1 个元素 | 单次响应 |
| `Flux<T>` | 0 到 N 个元素 | 流式响应 |

**类比**：
- `Mono` = 一个包裹（一次送达）
- `Flux` = 流水线（持续推送）

---

### 第一步：初始化 WebClient

```java
@Slf4j
@Component
public class OpenAiCompatibleLlmClient implements LlmClient {

    private final LlmProperties properties;
    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public OpenAiCompatibleLlmClient(LlmProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.webClient = buildWebClient();
        
        log.info("LLM Client initialized: endpoint={}, model={}", 
                properties.getEndpoint(), properties.getModel());
    }

    private WebClient buildWebClient() {
        String endpoint = normalizeEndpoint(properties.getEndpoint());
        
        return WebClient.builder()
                .baseUrl(endpoint)
                .defaultHeader("Authorization", "Bearer " + properties.getApiKey())
                .defaultHeader("Content-Type", "application/json")
                .build();
    }
}
```

#### 为什么在构造函数中初始化 WebClient？

**错误做法**：每次请求都创建新的 WebClient
```java
// ❌ 性能差，浪费资源
public LlmResponse chat(LlmRequest request) {
    WebClient client = WebClient.builder()...build();
    // 每次都创建新连接池
}
```

**正确做法**：复用 WebClient
```java
// ✅ 复用连接池，性能好
private final WebClient webClient;

public OpenAiCompatibleLlmClient(...) {
    this.webClient = WebClient.builder()...build();
}
```

**好处**：
- 复用连接池
- 减少资源消耗
- 提高性能

#### endpoint 规范化

```java
private String normalizeEndpoint(String endpoint) {
    // 移除末尾的斜杠
    endpoint = endpoint.replaceAll("/+$", "");
    
    // 如果已经包含 /v1，直接返回
    if (endpoint.matches(".*?/v\\d+$")) {
        return endpoint;
    }
    
    // 否则添加 /v1
    return endpoint + "/v1";
}
```

**为什么需要规范化？**

用户可能配置：
- `https://api.openai.com`
- `https://api.openai.com/`
- `https://api.openai.com/v1`

我们需要统一成：`https://api.openai.com/v1`

---

### 第二步：实现 chat() 方法

```java
@Override
public LlmResponse chat(LlmRequest request) {
    // 1. 构建 API 请求
    ChatCompletionRequest apiRequest = buildApiRequest(request, false);
    
    try {
        // 2. 发送请求并获取响应
        String responseBody = webClient.post()
                .uri("/chat/completions")
                .bodyValue(apiRequest)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(properties.getTimeout()))
                .block();
        
        // 3. 解析响应
        return parseResponse(responseBody);
        
    } catch (Exception e) {
        log.error("LLM request failed", e);
        throw new RuntimeException("LLM request failed: " + e.getMessage(), e);
    }
}
```

#### 步骤详解

**步骤 1：构建请求**

将 `LlmRequest` 转换为 OpenAI API 需要的格式：

```java
private ChatCompletionRequest buildApiRequest(LlmRequest request, boolean stream) {
    List<ChatMessage> messages = request.getMessages().stream()
            .map(this::convertMessage)
            .toList();
    
    String model = request.getModel() != null ? 
            request.getModel() : properties.getModel();
    
    return ChatCompletionRequest.builder()
            .model(model)
            .messages(messages)
            .temperature(request.getTemperature() != null ? 
                    request.getTemperature() : properties.getTemperature())
            .maxTokens(request.getMaxTokens() != null ? 
                    request.getMaxTokens() : properties.getMaxTokens())
            .stream(stream)
            .build();
}
```

**步骤 2：发送请求**

```java
String responseBody = webClient.post()
        .uri("/chat/completions")          // API 路径
        .bodyValue(apiRequest)              // 请求体（自动序列化为 JSON）
        .retrieve()                         // 获取响应
        .bodyToMono(String.class)           // 响应体转为 String
        .timeout(Duration.ofSeconds(60))    // 超时设置
        .block();                           // 阻塞等待结果
```

**步骤 3：解析响应**

```java
private LlmResponse parseResponse(String responseBody) {
    JsonNode root = objectMapper.readTree(responseBody);
    JsonNode choices = root.get("choices");
    
    JsonNode message = choices.get(0).get("message");
    
    String content = message.get("content").asText();
    String finishReason = choices.get(0).get("finish_reason").asText();
    
    return LlmResponse.builder()
            .content(content)
            .finishReason(finishReason)
            .build();
}
```

---

### 第三步：处理 Function Calling

当 LLM 决定调用工具时，响应中会有 `tool_calls` 字段：

```java
// 解析 tool_calls
List<ToolCall> toolCalls = null;
if (message.has("tool_calls")) {
    toolCalls = new ArrayList<>();
    for (JsonNode tcNode : message.get("tool_calls")) {
        ToolCall tc = ToolCall.builder()
                .id(tcNode.get("id").asText())
                .type(tcNode.get("type").asText())
                .function(ToolCall.FunctionCall.builder()
                        .name(tcNode.get("function").get("name").asText())
                        .arguments(tcNode.get("function").get("arguments").asText())
                        .build())
                .build();
        toolCalls.add(tc);
    }
}
```

---

### 完整的请求/响应流程

```
┌─────────────────────────────────────────────────┐
│              应用层调用                          │
│  LlmResponse response = client.chat(request);   │
└────────────────────┬────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────────┐
│          buildApiRequest()                      │
│  LlmRequest → ChatCompletionRequest            │
│  转换为 OpenAI API 格式                          │
└────────────────────┬────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────────┐
│          WebClient.post()                       │
│  发送 HTTP POST 请求                             │
│  Content-Type: application/json                 │
└────────────────────┬────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────────┐
│          OpenAI API Server                      │
│  https://api.openai.com/v1/chat/completions    │
└────────────────────┬────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────────┐
│          parseResponse()                        │
│  JSON → LlmResponse                            │
│  提取 content、toolCalls、usage                 │
└────────────────────┬────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────────┐
│          返回给应用层                            │
│  LlmResponse {                                  │
│    content: "你好！有什么...",                   │
│    finishReason: "stop"                        │
│  }                                              │
└─────────────────────────────────────────────────┘
```

---

### 动手实践

**任务**：实现 LLM 同步调用

**步骤**：
1. 创建 `OpenAiCompatibleLlmClient` 类
2. 实现 `WebClient` 初始化
3. 实现 `chat()` 方法
4. 编写测试用例

**测试代码**：
```java
@SpringBootTest
class LlmClientTest {

    @Autowired
    private LlmClient llmClient;

    @Test
    void testChat() {
        LlmRequest request = LlmRequest.builder()
                .messages(List.of(
                    Message.system("你是一个有帮助的助手"),
                    Message.user("你好")
                ))
                .build();
        
        LlmResponse response = llmClient.chat(request);
        
        assertNotNull(response.getContent());
        assertEquals("stop", response.getFinishReason());
    }
}
```

---

### 自检：你真的掌握了吗？

**问题 1**：为什么用 `block()` 方法？它做了什么？
> 如果答不上来，重读「WebClient 基本用法」

你的答案：
```


```

参考答案：
<details>
<summary>点击展开</summary>

**block() 的作用**：
- 阻塞当前线程，直到 Mono 返回结果
- 将异步操作转换为同步操作

**工作原理**：
1. WebClient 的 `bodyToMono()` 返回 `Mono<String>`（异步）
2. 调用 `block()` 后，线程会等待 HTTP 响应
3. 响应到达后，返回结果并继续执行

**什么时候用 block()**：
- 同步场景：需要等待结果才能继续
- 后台任务：不需要实时响应
- 测试代码：简化异步处理

**什么时候不用 block()**：
- 流式输出：需要用 `Flux`
- 高并发：应该用异步，避免阻塞线程

</details>

---

**问题 2**：WebClient 为什么要复用而不是每次创建新的？
> 如果卡住，说明需要更多练习

你的答案：
```


```

参考答案：
<details>
<summary>点击展开</summary>

**复用 WebClient 的原因**：

1. **连接池复用**：
   - WebClient 内部维护连接池
   - 复用 TCP 连接，减少握手开销
   - 每次创建新 WebClient = 新建连接池 = 浪费资源

2. **性能提升**：
   - 避免重复初始化
   - 减少内存分配
   - 提高并发性能

3. **资源管理**：
   - 连接池大小可控
   - 避免连接泄漏

**最佳实践**：
```java
// ✅ 好的做法：构造函数中初始化
public class LlmClient {
    private final WebClient webClient;
    
    public LlmClient() {
        this.webClient = WebClient.builder()...build();
    }
}

// ❌ 坏的做法：每次请求都创建
public void request() {
    WebClient client = WebClient.create(); // 不要这样做！
}
```

</details>

---

**问题 3**（选做）：如果 LLM API 返回错误（如 401 Unauthorized），应该如何处理？

你的答案：
```


```

参考答案：
<details>
<summary>点击展开</summary>

**当前实现**：
```java
try {
    String response = webClient.post()...
    return parseResponse(response);
} catch (Exception e) {
    throw new RuntimeException("LLM request failed", e);
}
```

**问题**：没有区分错误类型，所有错误都抛出相同的异常。

**更好的处理**：
```java
try {
    String response = webClient.post()
            .uri("/chat/completions")
            .bodyValue(apiRequest)
            .retrieve()
            .onStatus(
                status -> status.is4xxClientError(),
                response -> response.bodyToMono(String.class)
                    .map(body -> new LlmClientException("Client error: " + body))
            )
            .onStatus(
                status -> status.is5xxServerError(),
                response -> response.bodyToMono(String.class)
                    .map(body -> new LlmServerException("Server error: " + body))
            )
            .bodyToMono(String.class)
            .block();
} catch (LlmClientException e) {
    // 4xx 错误：认证失败、参数错误等
    log.error("Client error: {}", e.getMessage());
    throw e;
} catch (LlmServerException e) {
    // 5xx 错误：服务器过载等
    log.error("Server error: {}", e.getMessage());
    // 可以重试
}
```

**常见错误码**：
- 401：API Key 无效
- 429：请求过于频繁（限流）
- 500：服务器内部错误
- 503：服务不可用

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

- 我们实现了 LLM 的同步调用
- 关键要点：
  - WebClient 是响应式 HTTP 客户端，应复用
  - `block()` 将 Mono 转为同步结果
  - 构建请求时需要转换格式
  - 解析响应时提取 content、toolCalls、usage
  - Function Calling 需要特殊处理
- 下一节我们将学习 SSE 协议原理

---

### 扩展阅读（可选）

- [WebClient 官方文档](https://docs.spring.io/spring-framework/docs/current/reference/html/web-reactive.html#webflux-client)
- [OpenAI Chat Completions API](https://platform.openai.com/docs/api-reference/chat)
- [Reactor Mono 文档](https://projectreactor.io/docs/core/release/api/reactor/core/publisher/Mono.html)

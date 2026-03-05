# 第4.3节：同步调用实现 - 从零手写 chat() 方法

> **学习目标**：从零实现 LLM 同步调用，每一步都能运行验证
> **预计时长**：25 分钟
> **难度**：入门

### 前置知识检查

**你应该已经掌握**：
- [x] 4.2 数据模型已创建
- [ ] Maven 依赖管理

---

### 为什么这节很重要？

同步调用是 LLM 客户端最基础的功能。虽然流式调用体验更好，但同步调用更简单，适合作为起点。

**学完本节，你将能够**：
- 调用 DeepSeek/OpenAI API 获取响应
- 理解 HTTP 请求的完整流程
- 处理 JSON 响应

---

### 第一步：添加依赖

**1.1 打开 pom.xml**

在 `backend/pom.xml` 的 `<dependencies>` 中添加：

```xml
<!-- WebClient：响应式 HTTP 客户端 -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-webflux</artifactId>
</dependency>
```

**1.2 刷新 Maven**

```bash
cd backend
./mvnw clean compile
```

看到 `BUILD SUCCESS` 就对了。

---

### 第二步：创建 OpenAiCompatibleLlmClient.java

**2.1 创建空类**

在 `llm/` 包下创建 `OpenAiCompatibleLlmClient.java`：

```java
package com.miniclaw.llm;

import org.springframework.stereotype.Component;

/**
 * OpenAI 兼容的 LLM 客户端
 */
@Component
public class OpenAiCompatibleLlmClient implements LlmClient {
    
    // 待实现
}
```

**此时会报错**：`LlmClient` 有未实现的方法。没关系，继续。

**2.2 实现 LlmClient 接口**

```java
package com.miniclaw.llm;

import com.miniclaw.llm.model.LlmChunk;
import com.miniclaw.llm.model.LlmRequest;
import com.miniclaw.llm.model.LlmResponse;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

/**
 * OpenAI 兼容的 LLM 客户端
 */
@Component
public class OpenAiCompatibleLlmClient implements LlmClient {

    @Override
    public LlmResponse chat(LlmRequest request) {
        // TODO: 待实现
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public Flux<LlmChunk> stream(LlmRequest request) {
        // TODO: 下节实现
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
```

**验证一下**：保存，编译通过。

---

### 第三步：添加依赖注入

**3.1 添加构造函数**

```java
package com.miniclaw.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniclaw.llm.model.LlmChunk;
import com.miniclaw.llm.model.LlmRequest;
import com.miniclaw.llm.model.LlmResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

/**
 * OpenAI 兼容的 LLM 客户端
 */
@Slf4j
@Component
public class OpenAiCompatibleLlmClient implements LlmClient {

    private final LlmProperties properties;
    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    /**
     * 构造函数 - Spring 自动注入依赖
     */
    public OpenAiCompatibleLlmClient(LlmProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.webClient = buildWebClient();
        
        log.info("LLM Client initialized: endpoint={}", properties.getEndpoint());
    }

    // ... 其他方法
}
```

**3.2 实现 buildWebClient()**

在类中添加方法：

```java
    /**
     * 构建 WebClient
     */
    private WebClient buildWebClient() {
        String endpoint = normalizeEndpoint(properties.getEndpoint());
        
        return WebClient.builder()
                .baseUrl(endpoint)
                .defaultHeader("Authorization", "Bearer " + properties.getApiKey())
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    /**
     * 规范化 endpoint
     */
    private String normalizeEndpoint(String endpoint) {
        if (endpoint == null || endpoint.isBlank()) {
            return "http://localhost:11434/v1";
        }
        
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

**验证一下**：保存，编译通过。

---

### 第四步：实现 chat() 方法

**4.1 编写基础框架**

```java
    @Override
    public LlmResponse chat(LlmRequest request) {
        // 1. 构建 API 请求
        ChatCompletionRequest apiRequest = buildApiRequest(request, false);
        
        try {
            // 2. 发送请求
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

**需要导入**：
```java
import java.time.Duration;
```

**4.2 添加内部类（API 请求格式）**

在类的**末尾**添加：

```java
    // ==================== 内部类：API 请求格式 ====================

    /**
     * OpenAI Chat Completions API 请求格式
     */
    @Data
    @lombok.Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    static class ChatCompletionRequest {
        private String model;
        private List<ChatMessage> messages;
        private Double temperature;
        @JsonProperty("max_tokens")
        private Integer maxTokens;
        private Boolean stream;
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    static class ChatMessage {
        private String role;
        private String content;
    }
```

**需要导入**：
```java
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
```

**4.3 实现 buildApiRequest()**

```java
    /**
     * 构建 API 请求
     */
    private ChatCompletionRequest buildApiRequest(LlmRequest request, boolean stream) {
        // 转换消息格式
        List<ChatMessage> messages = request.getMessages().stream()
                .map(m -> {
                    ChatMessage msg = new ChatMessage();
                    msg.setRole(m.getRole());
                    msg.setContent(m.getContent());
                    return msg;
                })
                .toList();
        
        // 确定使用的模型
        String model = request.getModel() != null ? 
                request.getModel() : properties.getModel();
        
        // 构建请求
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

**4.4 实现 parseResponse()**

```java
    /**
     * 解析响应
     */
    private LlmResponse parseResponse(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode choices = root.get("choices");
            
            if (choices == null || choices.isEmpty()) {
                throw new RuntimeException("No choices in response");
            }
            
            JsonNode firstChoice = choices.get(0);
            JsonNode message = firstChoice.get("message");
            
            // 提取内容
            String content = null;
            if (message.has("content") && !message.get("content").isNull()) {
                content = message.get("content").asText();
            }
            
            // 提取 finish_reason
            String finishReason = firstChoice.has("finish_reason") ?
                    firstChoice.get("finish_reason").asText() : null;
            
            // 解析 usage
            LlmResponse.Usage usage = null;
            if (root.has("usage")) {
                JsonNode usageNode = root.get("usage");
                usage = LlmResponse.Usage.builder()
                        .promptTokens(usageNode.get("prompt_tokens").asInt())
                        .completionTokens(usageNode.get("completion_tokens").asInt())
                        .totalTokens(usageNode.get("total_tokens").asInt())
                        .build();
            }
            
            return LlmResponse.builder()
                    .content(content)
                    .finishReason(finishReason)
                    .usage(usage)
                    .build();
            
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to parse LLM response", e);
        }
    }
```

**需要导入**：
```java
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
```

**验证一下**：保存，编译通过。

---

### 第五步：测试调用

**5.1 设置 API Key**

在 `application.yml` 中添加：

```yaml
llm:
  endpoint: https://api.deepseek.com
  api-key: ${LLM_API_KEY}  # 从环境变量读取
  model: deepseek-chat
```

设置环境变量：
```bash
export LLM_API_KEY=your-api-key-here
```

**5.2 创建测试类**

```java
package com.miniclaw.llm;

import com.miniclaw.llm.model.LlmRequest;
import com.miniclaw.llm.model.LlmRequest.Message;
import com.miniclaw.llm.model.LlmResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

@SpringBootTest
class LlmClientTest {

    @Autowired
    private LlmClient llmClient;

    @Test
    void testChat() {
        // 创建请求
        LlmRequest request = LlmRequest.builder()
                .messages(List.of(
                    Message.system("你是一个有帮助的助手"),
                    Message.user("用一句话介绍你自己")
                ))
                .build();
        
        // 调用 LLM
        LlmResponse response = llmClient.chat(request);
        
        // 验证
        System.out.println("响应内容: " + response.getContent());
        System.out.println("完成原因: " + response.getFinishReason());
        System.out.println("Token 用量: " + response.getUsage().getTotalTokens());
        
        assert response.getContent() != null;
        assert "stop".equals(response.getFinishReason());
    }
}
```

**5.3 运行测试**

```bash
cd backend
./mvnw test -Dtest=LlmClientTest#testChat
```

看到响应内容输出，测试就通过了！

---

### 常见问题

**Q: 报错 "No LLM client configured"？**

检查 `application.yml` 中的配置：
```yaml
llm:
  endpoint: https://api.deepseek.com  # 确保 endpoint 正确
  api-key: ${LLM_API_KEY}              # 确保环境变量已设置
```

**Q: 报错 "Connection refused"？**

1. 检查网络连接
2. 确认 endpoint URL 正确（DeepSeek 是 `https://api.deepseek.com`）

**Q: 报错 "401 Unauthorized"？**

API Key 无效或过期，检查环境变量：
```bash
echo $LLM_API_KEY
```

---

### 动手实践

**任务**：实现同步调用并测试

**检查清单**：
- [ ] 添加 WebFlux 依赖
- [ ] 创建 `OpenAiCompatibleLlmClient.java`
- [ ] 实现 `chat()` 方法
- [ ] 配置 API Key
- [ ] 运行测试，看到响应输出

---

### 自检：你真的掌握了吗？

**问题 1**：为什么 WebClient 要在构造函数中创建，而不是每次请求时创建？

你的答案：
```


```

参考答案：
<details>
<summary>点击展开</summary>

**原因**：WebClient 内部有连接池，复用可以：
1. **提高性能**：避免重复初始化
2. **节省资源**：复用 TCP 连接
3. **避免泄漏**：统一管理连接池

**错误做法**：
```java
// ❌ 每次请求都创建，浪费资源
public LlmResponse chat(LlmRequest request) {
    WebClient client = WebClient.builder()...build();
}
```

**正确做法**：
```java
// ✅ 构造函数中创建，复用连接池
private final WebClient webClient;

public OpenAiCompatibleLlmClient(...) {
    this.webClient = WebClient.builder()...build();
}
```

</details>

---

**问题 2**：`block()` 是什么？为什么要用它？

你的答案：
```


```

参考答案：
<details>
<summary>点击展开</summary>

**block()** = 阻塞当前线程，等待 Mono 返回结果

**工作原理**：
1. WebClient 返回 `Mono<String>`（异步）
2. 调用 `block()` 后，线程等待 HTTP 响应
3. 响应到达后，返回结果

**什么时候用**：
- 同步调用：需要等待结果
- 后台任务：不需要实时响应
- 测试代码：简化异步处理

**什么时候不用**：
- 流式输出：用 `Flux`
- 高并发：用异步，避免阻塞

</details>

---

### 本节小结

- 我们从零实现了 LLM 同步调用
- 关键要点：
  - WebClient 是响应式 HTTP 客户端
  - `block()` 将异步转为同步
  - 构造函数注入依赖，复用 WebClient
  - JSON 解析使用 Jackson 的 ObjectMapper
- 下一节我们将学习 SSE 协议

---

### 完整代码

**OpenAiCompatibleLlmClient.java**（完整版）：

见本节末尾附录（因篇幅限制，完整代码在课程仓库中）。

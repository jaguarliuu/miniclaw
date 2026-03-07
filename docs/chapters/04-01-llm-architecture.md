# 第4.1节：为什么不用 Spring AI？

> **学习目标**：理解技术选型的思考过程，而不是只会"调库"
> **预计时长**：15 分钟
> **难度**：入门

### 前置知识检查

**你应该已经掌握**：
- [x] 第3章：开发环境和项目骨架已搭建
- [ ] Spring Boot 基础

**你不必担心**：
- 不需要了解 Spring AI 的细节
- 不需要懂 LLM API 的具体格式

---

### 开篇：一个真实的选择题

假设你是技术负责人，团队要开发一个 AI Agent 系统。

**选项 A**：用 Spring AI
```java
// 只需要几行代码
@Service
public class ChatService {
    private final ChatClient chatClient;
    
    public String chat(String message) {
        return chatClient.call(message);
    }
}
```

**选项 B**：手写 LLM 客户端
```java
// 需要几百行代码
public class LlmClient {
    public String chat(LlmRequest request) {
        // 构建 HTTP 请求
        // 解析 JSON 响应
        // 处理错误
        // ...
    }
}
```

**你选哪个？**

如果只是做一个 Demo，**选 A**，快。

但这是一门**教学课程**，目标是让你**理解原理**，所以**选 B**。

让我们深入分析原因。

---

### Spring AI 是什么？

**Spring AI** 是 Spring 官方在 2023 年推出的 AI 集成框架。

**核心能力**：
- 统一的 LLM API（OpenAI、Azure、Ollama 等）
- 向量数据库集成（Pinecone、Milvus 等）
- RAG（检索增强生成）支持
- Function Calling 封装
- Prompt 模板管理

**听起来很美好，对吧？**

让我们看看实际使用。

---

### Spring AI 示例

```java
@Service
public class AiService {
    
    private final ChatClient chatClient;
    
    public AiService(ChatClient.Builder builder) {
        this.chatClient = builder
                .defaultSystem("你是一个有帮助的助手")
                .build();
    }
    
    public String chat(String userMessage) {
        return chatClient.call(userMessage);
    }
}
```

**看起来很简洁，但你真的理解它在做什么吗？**

- `ChatClient` 内部怎么发 HTTP 请求？
- `call()` 怎么解析 JSON 响应？
- 流式输出是怎么实现的？
- Function Calling 是怎么处理的？

**如果你答不上来，说明你只是在"用框架"，而不是"懂原理"。**

---

### Spring AI vs 手写客户端对比

| 维度 | Spring AI | 手写客户端 |
|------|-----------|-----------|
| **开发速度** | 快（几行代码） | 慢（几百行代码） |
| **学习曲线** | 需要学习框架特有概念 | 只需懂 HTTP 和 JSON |
| **理解深度** | 黑盒，不知道内部做了什么 | 白盒，每行代码都是自己写的 |
| **调试难度** | 框架报错，不知道哪里问题 | 每行代码都能调试 |
| **灵活性** | 受框架限制 | 完全自由 |
| **面试价值** | "会用框架" | "懂底层原理" |
| **依赖大小** | ~10MB | ~1MB（只有 WebClient） |

---

### 为什么本课程选择手写？

#### 理由一：教学目标不同

本课程的目标是：**让你理解 AI Agent 的底层原理**。

**如果用 Spring AI**：
```java
// 你只学会了这个
String response = chatClient.call("你好");
// 内部发生了什么？不知道
// HTTP 请求怎么发？不知道
// JSON 怎么解析？不知道
```

**如果手写**：
```java
// 你会理解完整的调用链
WebClient.post()
    .uri("/chat/completions")
    .body(request)
    .retrieve()
    .bodyToMono(String.class)
    .map(this::parseResponse);

// 你会知道：
// - HTTP 请求怎么构建
// - JSON 怎么解析
// - 流式输出怎么处理
// - 错误怎么处理
```

**面试时**：
- "你会用 Spring AI" → HR 觉得你会调库
- "我手写过 LLM 客户端" → 技术面试官眼前一亮

#### 理由二：掌握原理，框架一通百通

**真相**：理解了底层原理，再学 Spring AI 只需要 1 小时。

**对比**：
- 只会框架：换一个框架就不会了
- 懂原理：LangChain、LlamaIndex、Spring AI 都能快速上手

**类比**：
- Spring AI = 自动挡汽车
- 手写客户端 = 手动挡汽车
- 会开手动挡 → 自动挡轻松上手
- 只会自动挡 → 遇到手动挡就傻眼

#### 理由三：应对真实工作场景

实际工作中，你可能遇到：
- 公司不用 Spring，用 Quarkus
- 需要对接非标准的 LLM API
- 需要深度定制请求/响应格式
- 性能优化，需要精细控制

**只会框架，遇到这些就束手无策。**

**懂原理，就知道如何应对。**

---

### 什么时候应该用 Spring AI？

**不是说要永远手写**。Spring AI 有它的适用场景：

**适合用 Spring AI**：
- ✅ 快速原型开发
- ✅ 对 LLM 原理已经理解
- ✅ 项目不需要深度定制
- ✅ 团队统一使用 Spring 生态

**不适合用 Spring AI**（本课程场景）：
- ❌ 学习 LLM 原理
- ❌ 需要理解底层实现
- ❌ 需要深度定制
- ❌ 面试/技术提升

**总结**：**先学原理，再用框架**。

---

### 我们将学到什么？

通过手写 LLM 客户端，你将掌握：

**第 4.2-4.3 节：请求与响应**
- OpenAI API 的请求格式
- 如何构建 HTTP 请求
- 如何解析 JSON 响应
- 错误处理

**第 4.4-4.5 节：流式输出**
- SSE 协议原理
- 如何处理流式数据
- 如何累积增量响应

**第 4.6-4.7 节：生产就绪**
- 重试机制
- 多 LLM 提供商适配

**学完这些，你再看 Spring AI 源码，会发现：**

> "原来 Spring AI 内部就是这么实现的！"

---

### 自检：你真的理解了吗？

**问题 1**：为什么本课程选择手写 LLM 客户端而不是用 Spring AI？

你的答案：
```


```

参考答案：
<details>
<summary>点击展开</summary>

本课程选择手写的三个核心理由：

1. **教学目标**：让你理解 AI Agent 的底层原理，而不是只会调框架
2. **面试价值**：能讲清楚 LLM 调用的完整流程，"我手写过 LLM 客户端"比"我会用 Spring AI"更有含金量
3. **原理掌握**：理解了底层原理，再学任何框架都是 1 小时的事；不理解原理，换框架就不会了

**核心思想**：先学原理，再用框架。而不是相反。

</details>

---

**问题 2**：什么情况下应该用 Spring AI？

你的答案：
```


```

参考答案：
<details>
<summary>点击展开</summary>

**适合用 Spring AI**：
- 快速原型开发，需要尽快上线
- 已经理解了 LLM 原理，不需要重复学习
- 项目不需要深度定制
- 团队统一使用 Spring 生态

**不适合用 Spring AI**：
- 正在学习 LLM 原理
- 需要深度理解底层实现
- 需要定制请求/响应格式
- 面试/技术提升

**建议**：先学原理（手写），再用框架（Spring AI）。

</details>

---

**问题 3**：学完手写 LLM 客户端后，再学 Spring AI 会怎样？

你的答案：
```


```

参考答案：
<details>
<summary>点击展开</summary>

**你会发现**：
1. Spring AI 的代码你能看懂了
2. 原来它内部就是 WebClient + JSON 解析
3. 流式输出就是 SSE 协议
4. Function Calling 就是特殊的 JSON 格式

**学习曲线**：
- 不懂原理学 Spring AI：1-2 周，还只是会用
- 先学原理再学 Spring AI：1 小时就能看懂源码

**类比**：
- 不懂原理 = 盲人摸象
- 懂原理 = 俯瞰全貌

</details>

---

### 本节小结

- 我们讨论了"为什么不用 Spring AI"这个核心问题
- 关键要点：
  - Spring AI 适合快速开发，但**不适合学习原理**
  - 手写客户端让你理解底层细节
  - **先学原理，再用框架**
  - 理解原理后，框架一通百通
- 下一节我们将从零开始设计 LLM 请求和响应的数据模型

---

### 扩展阅读（可选）

- [Spring AI 官方文档](https://docs.spring.io/spring-ai/reference/)
- [OpenAI API 文档](https://platform.openai.com/docs/api-reference/chat)

# 第4.2节：数据模型设计 - LLM 请求与响应

> **学习目标**：设计 LLM 请求和响应的数据模型，理解 OpenAI API 格式
> **预计时长**：20 分钟
> **难度**：入门

### 前置知识检查

**你应该已经掌握**：
- [x] 4.1 LLM 客户端架构
- [ ] JSON 基本格式
- [ ] Lombok 注解的使用

**如果你不确定**：
- JSON 不熟 → 本节会讲解 OpenAI API 的 JSON 格式
- Lombok 没用过 → 本节会解释每个注解的作用

---

### 为什么这节很重要？

在写 LLM 客户端代码之前，我们需要先定义清楚**数据要长什么样**。

**没有数据模型**：
```java
// ❌ 使用 Map，类型不安全
Map<String, Object> request = new HashMap<>();
request.put("messages", messages);
request.put("model", "gpt-4");
request.put("temperature", 0.7);
// 拼写错误？编译器不会告诉你
// 类型错误？运行时才报错
```

**有数据模型**：
```java
// ✅ 使用 POJO，类型安全
LlmRequest request = LlmRequest.builder()
    .messages(messages)
    .model("gpt-4")
    .temperature(0.7)
    .build();
// 拼写错误？编译器报错
// 类型错误？编译器报错
// IDE 自动补全
```

**数据模型 = 代码世界的"契约"**：
- 定义了请求必须有哪些字段
- 定义了响应会有哪些字段
- 所有代码都基于这个"契约"

---

### OpenAI Chat Completions API 格式

在定义数据模型之前，先了解 OpenAI API 的格式。

#### 请求格式

```json
{
  "model": "gpt-4",
  "messages": [
    {"role": "system", "content": "你是一个有帮助的助手"},
    {"role": "user", "content": "你好"}
  ],
  "temperature": 0.7,
  "max_tokens": 2048,
  "tools": [
    {
      "type": "function",
      "function": {
        "name": "get_weather",
        "description": "获取指定城市的天气",
        "parameters": {
          "type": "object",
          "properties": {
            "city": {"type": "string"}
          }
        }
      }
    }
  ],
  "tool_choice": "auto"
}
```

#### 响应格式

```json
{
  "choices": [
    {
      "message": {
        "role": "assistant",
        "content": "你好！有什么我可以帮你的吗？"
      },
      "finish_reason": "stop"
    }
  ],
  "usage": {
    "prompt_tokens": 20,
    "completion_tokens": 15,
    "total_tokens": 35
  }
}
```

#### 流式响应格式

```
data: {"choices":[{"delta":{"content":"你"},"finish_reason":null}]}

data: {"choices":[{"delta":{"content":"好"},"finish_reason":null}]}

data: {"choices":[{"delta":{"content":"！"},"finish_reason":"stop"}]}

data: [DONE]
```

---

### 第一步：创建 LlmRequest 模型

```java
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LlmRequest {

    /**
     * 消息列表（必填）
     */
    private List<Message> messages;

    /**
     * 模型名称（可选）
     */
    private String model;

    /**
     * 温度参数（可选，0-2）
     */
    private Double temperature;

    /**
     * 最大 token 数（可选）
     */
    private Integer maxTokens;

    /**
     * 工具定义列表（可选）
     */
    private List<Map<String, Object>> tools;

    /**
     * 工具选择策略（可选）
     */
    private String toolChoice;

    /**
     * 消息模型
     */
    @Data
    @Builder
    public static class Message {
        private String role;
        private String content;
        private List<ToolCall> toolCalls;
        private String toolCallId;
    }
}
```

#### Lombok 注解解释

| 注解 | 作用 | 生成的代码 |
|------|------|-----------|
| `@Data` | 组合注解 | getter/setter/toString/equals/hashCode |
| `@NoArgsConstructor` | 无参构造器 | `public LlmRequest() {}` |
| `@AllArgsConstructor` | 全参构造器 | `public LlmRequest(所有字段) {}` |
| `@Builder` | 建造者模式 | `LlmRequest.builder().messages(xxx).build()` |

**为什么用 @Builder？**

```java
// ❌ 传统方式：参数顺序容易搞混
new LlmRequest(messages, "gpt-4", 0.7, 2048, null, null);

// ✅ Builder 模式：清晰、可读
LlmRequest.builder()
    .messages(messages)
    .model("gpt-4")
    .temperature(0.7)
    .maxTokens(2048)
    .build();
```

#### Message 的角色

| 角色 | 作用 | 示例 |
|------|------|------|
| `system` | 定义 AI 行为 | "你是一个代码审查专家" |
| `user` | 用户消息 | "帮我审查这段代码" |
| `assistant` | AI 回复 | "好的，我看到以下问题..." |
| `tool` | 工具结果 | `{"result": "文件内容..."}` |

#### 便捷工厂方法

```java
public static class Message {
    // 创建系统消息
    public static Message system(String content) {
        return Message.builder().role("system").content(content).build();
    }

    // 创建用户消息
    public static Message user(String content) {
        return Message.builder().role("user").content(content).build();
    }

    // 创建助手消息
    public static Message assistant(String content) {
        return Message.builder().role("assistant").content(content).build();
    }
}
```

**使用示例**：
```java
List<Message> messages = List.of(
    Message.system("你是一个有帮助的助手"),
    Message.user("你好")
);
```

---

### 第二步：创建 LlmResponse 模型

```java
@Data
@Builder
public class LlmResponse {

    /**
     * 响应内容（有 tool_calls 时可能为 null）
     */
    private String content;

    /**
     * 工具调用列表（可选）
     */
    private List<ToolCall> toolCalls;

    /**
     * 完成原因
     */
    private String finishReason;

    /**
     * Token 使用量
     */
    private Usage usage;

    /**
     * 判断是否有工具调用
     */
    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isEmpty();
    }

    @Data
    @Builder
    public static class Usage {
        private Integer promptTokens;
        private Integer completionTokens;
        private Integer totalTokens;
    }
}
```

#### finishReason 的含义

| 值 | 含义 | 处理方式 |
|----|------|----------|
| `stop` | 正常结束 | 直接显示 content |
| `length` | 达到 token 限制 | 提示用户或继续对话 |
| `tool_calls` | 调用了工具 | 执行工具，返回结果 |
| `content_filter` | 内容被过滤 | 提示用户修改输入 |

---

### 第三步：创建 LlmChunk 模型

```java
@Data
@Builder
public class LlmChunk {

    /**
     * 内容增量
     */
    private String delta;

    /**
     * 工具调用列表（可选）
     */
    private List<ToolCall> toolCalls;

    /**
     * 完成原因
     */
    private String finishReason;

    /**
     * 是否是最后一个 chunk
     */
    private boolean done;

    /**
     * Token 使用量（仅最后一个 chunk 有）
     */
    private LlmResponse.Usage usage;
}
```

#### Chunk vs Response 的区别

| 特性 | LlmResponse | LlmChunk |
|------|-------------|----------|
| 返回时机 | 一次性返回 | 分片返回 |
| 内容字段 | `content`（完整） | `delta`（增量） |
| 用途 | 同步调用 | 流式调用 |
| 需要累积 | 否 | 是 |

**流式输出过程**：
```
Chunk 1: delta = "你"
Chunk 2: delta = "好"
Chunk 3: delta = "！"
累积结果: "你好！"
```

---

### 第四步：创建 ToolCall 模型

```java
@Data
@Builder
public class ToolCall {

    /**
     * 调用 ID
     */
    private String id;

    /**
     * 类型（默认 "function"）
     */
    @Builder.Default
    private String type = "function";

    /**
     * 函数调用信息
     */
    private FunctionCall function;

    @Data
    @Builder
    public static class FunctionCall {
        private String name;
        private String arguments;  // JSON 字符串
    }
}
```

#### Function Calling 流程

```
用户："北京今天天气怎么样？"
    ↓
LLM 返回 ToolCall:
  id = "call_abc123"
  function.name = "get_weather"
  function.arguments = "{\"city\":\"北京\"}"
    ↓
应用执行：get_weather("北京")
    ↓
返回结果："北京今天晴，25°C"
    ↓
发送 tool 消息给 LLM
    ↓
LLM 生成最终回复："北京今天天气晴朗，温度 25°C"
```

---

### 数据模型全景图

```
LlmRequest（请求）
├── messages: List<Message>
│   └── Message
│       ├── role: String
│       ├── content: String
│       ├── toolCalls: List<ToolCall>
│       └── toolCallId: String
├── model: String
├── temperature: Double
├── maxTokens: Integer
├── tools: List<Map>
└── toolChoice: String

LlmResponse（同步响应）
├── content: String
├── toolCalls: List<ToolCall>
│   └── ToolCall
│       ├── id: String
│       ├── type: String
│       └── function: FunctionCall
│           ├── name: String
│           └── arguments: String
├── finishReason: String
└── usage: Usage

LlmChunk（流式响应块）
├── delta: String
├── toolCalls: List<ToolCall>
├── finishReason: String
├── done: boolean
└── usage: Usage
```

---

### 动手实践

**任务**：创建 LLM 数据模型

**步骤**：
1. 创建 `llm/model` 包
2. 创建 `LlmRequest.java`
3. 创建 `LlmResponse.java`
4. 创建 `LlmChunk.java`
5. 创建 `ToolCall.java`

**预期结果**：
- 代码编译通过
- 理解每个字段的含义

---

### 自检：你真的掌握了吗？

**问题 1**：`LlmChunk.delta` 和 `LlmResponse.content` 有什么区别？
> 如果答不上来，重读「Chunk vs Response 的区别」

你的答案：
```


```

参考答案：
<details>
<summary>点击展开</summary>

| 字段 | 作用 | 使用场景 |
|------|------|----------|
| `LlmResponse.content` | 完整的响应内容 | 同步调用，一次性返回 |
| `LlmChunk.delta` | 增量的内容片段 | 流式调用，需要累积 |

**示例**：
- 同步调用：`content = "你好！"`
- 流式调用：`delta = "你"`, `delta = "好"`, `delta = "！"` → 累积为 "你好！"

**为什么设计不同**：
- 同步调用等待完整响应，直接返回完整内容
- 流式调用实时推送，每次只返回新到达的部分

</details>

---

**问题 2**：`finishReason = "tool_calls"` 意味着什么？应该怎么处理？
> 如果卡住，说明需要更多练习

你的答案：
```


```

参考答案：
<details>
<summary>点击展开</summary>

**含义**：LLM 决定调用工具，而不是直接返回文本。

**处理流程**：
1. 检查 `response.hasToolCalls()` 或 `finishReason.equals("tool_calls")`
2. 遍历 `toolCalls`，获取 `function.name` 和 `function.arguments`
3. 执行对应的工具函数
4. 将执行结果作为 `tool` 角色的消息
5. 把 tool 消息加入 messages，再次调用 LLM
6. LLM 会基于工具结果生成最终回复

**代码示例**：
```java
if (response.hasToolCalls()) {
    for (ToolCall tc : response.getToolCalls()) {
        String result = executeTool(tc.getName(), tc.getArguments());
        messages.add(Message.toolResult(tc.getId(), result));
    }
    // 再次调用 LLM
    LlmResponse finalResponse = llmClient.chat(request);
}
```

</details>

---

**问题 3**（选做）：为什么 `ToolCall.function.arguments` 是 String 而不是 Map？

你的答案：
```


```

参考答案：
<details>
<summary>点击展开</summary>

**原因一：JSON 是通用格式**
- LLM 直接输出 JSON 字符串
- 不需要 LLM 客户端解析，交给应用层解析
- 支持任意复杂的参数结构

**原因二：延迟解析**
- 应用可以按需解析（用 Jackson、Gson 等）
- 可以做参数校验、类型转换
- 更灵活

**原因三：流式输出兼容**
- 流式输出时，arguments 是分片到达的
- 累积完成后才是完整的 JSON
- String 类型方便累积

**使用时解析**：
```java
String argumentsJson = toolCall.getArguments();
Map<String, Object> args = objectMapper.readValue(argumentsJson, Map.class);
String city = (String) args.get("city");
```

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

- 我们设计了 LLM 的核心数据模型
- 关键要点：
  - `LlmRequest`：请求模型，包含 messages、model、temperature 等
  - `LlmResponse`：同步响应，包含完整的 content
  - `LlmChunk`：流式响应块，包含增量的 delta
  - `ToolCall`：工具调用，包含函数名和参数
  - Lombok 注解简化了 POJO 代码
- 下一节我们将实现同步调用

---

### 扩展阅读（可选）

- [OpenAI Chat Completions API](https://platform.openai.com/docs/api-reference/chat)
- [OpenAI Function Calling](https://platform.openai.com/docs/guides/function-calling)
- [Lombok 官方文档](https://projectlombok.org/features/)

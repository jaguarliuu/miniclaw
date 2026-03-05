# 第4.4节：SSE 协议原理 - 流式输出的底层机制

> **学习目标**：深入理解 SSE 协议，掌握流式输出的数据格式和解析方法
> **预计时长**：25 分钟
> **难度**：进阶

### 前置知识检查

**你应该已经掌握**：
- [x] 4.3 同步调用实现
- [ ] HTTP 基础知识
- [ ] JSON 格式

**你不必担心**：WebSocket 不是本节内容，我们只用 SSE！

---

### 为什么这节至关重要？

SSE（Server-Sent Events）是实现 AI 流式输出的核心协议。

**如果你不理解 SSE**：
- 看不懂 LLM API 返回的数据格式
- 不知道如何解析"一个字一个字"的响应
- 无法实现真正的流式输出

**如果你理解了 SSE**：
- 清楚地知道每个数据块的格式
- 能够正确解析和累积响应
- 可以处理 Function Calling 的流式调用

---

### 什么是 SSE？

#### 直觉理解

**SSE = 服务器给客户端的"短信轰炸"**

- 服务器有一条长消息要发
- 不想一次发完（太慢）
- 于是分成很多小短信，一条条发
- 客户端收到一条，显示一条

**类比**：
- 同步 HTTP = 下载完整视频后观看
- SSE = 边下载边播放（流媒体）

#### 技术定义

**SSE（Server-Sent Events）**：
- 基于 HTTP 的单向推送协议
- 服务器可以持续向客户端发送数据
- 客户端通过 EventSource API 接收

**与 WebSocket 的区别**：

| 特性 | SSE | WebSocket |
|------|-----|-----------|
| 方向 | 单向（服务器→客户端） | 双向 |
| 协议 | HTTP | WS/WSS |
| 格式 | 文本 | 文本/二进制 |
| 断线重连 | 浏览器自动 | 需手动实现 |
| 适用场景 | 服务器推送、流式输出 | 聊天、游戏 |

**为什么 LLM 用 SSE 而不是 WebSocket？**
- LLM 只需要服务器推送，不需要双向
- SSE 更简单，基于 HTTP
- 兼容性更好

---

### SSE 数据格式

#### 基本格式

SSE 数据由多个 `data:` 行组成：

```
data: 第一条消息

data: 第二条消息

data: 第三条消息
```

**注意**：每个消息之间用空行分隔！

#### OpenAI API 的 SSE 格式

```
data: {"id":"chatcmpl-123","choices":[{"delta":{"content":"你"},"finish_reason":null}]}

data: {"id":"chatcmpl-123","choices":[{"delta":{"content":"好"},"finish_reason":null}]}

data: {"id":"chatcmpl-123","choices":[{"delta":{"content":"！"},"finish_reason":"stop"}]}

data: [DONE]
```

**结构解析**：
1. 每行以 `data:` 开头
2. 后面是 JSON 数据
3. 最后以 `data: [DONE]` 结束

#### 空行的作用

**重要**：SSE 用空行分隔消息！

```
data: 消息1
                   ← 空行表示消息结束
data: 消息2
                   ← 空行表示消息结束
data: 消息3
```

**如果忘记空行**：
```
data: 消息1
data: 消息2
```
这会被当作**一条消息**：`消息1\ndata: 消息2`

---

### Delta 增量机制

#### 什么是 Delta？

**Delta = 增量**，每次只返回新增的内容。

**示例流程**：

```
Chunk 1: delta = { "content": "你" }
累积结果: "你"

Chunk 2: delta = { "content": "好" }
累积结果: "你好"

Chunk 3: delta = { "content": "！" }
累积结果: "你好！"
```

**客户端需要累积拼接**：
```javascript
let fullContent = "";
eventSource.onmessage = (event) => {
    const chunk = JSON.parse(event.data);
    if (chunk.choices[0].delta.content) {
        fullContent += chunk.choices[0].delta.content;
        console.log("当前累积:", fullContent);
    }
};
```

#### 完整的 SSE 响应结构

```json
{
  "id": "chatcmpl-123456",
  "object": "chat.completion.chunk",
  "created": 1699000000,
  "model": "gpt-4",
  "choices": [
    {
      "index": 0,
      "delta": {
        "content": "你"
      },
      "finish_reason": null
    }
  ]
}
```

**关键字段**：

| 字段 | 作用 | 值示例 |
|------|------|--------|
| `delta.content` | 增量内容 | "你" |
| `finish_reason` | 结束原因 | null / "stop" / "tool_calls" |
| `delta.tool_calls` | 工具调用增量 | （见下文） |

---

### Function Calling 的流式输出

#### 工具调用的 SSE 格式

当 LLM 决定调用工具时，`tool_calls` 也是增量到达的：

```
data: {"choices":[{"delta":{"tool_calls":[{"index":0,"id":"call_abc","type":"function","function":{"name":"get_weather"}}]}}]}

data: {"choices":[{"delta":{"tool_calls":[{"index":0,"function":{"arguments":"{\"ci"}}]}}]}

data: {"choices":[{"delta":{"tool_calls":[{"index":0,"function":{"arguments":"ty\":"}}]}}]}

data: {"choices":[{"delta":{"tool_calls":[{"index":0,"function":{"arguments":"\"北京\"}"}}]}}]}

data: {"choices":[{"delta":{},"finish_reason":"tool_calls"}]}
```

**累积过程**：

```
Chunk 1: name = "get_weather"
Chunk 2: arguments = "{\"ci"
Chunk 3: arguments += "ty\":"
Chunk 4: arguments += "\"北京\"}"
最终结果: {
  "id": "call_abc",
  "function": {
    "name": "get_weather",
    "arguments": "{\"city\":\"北京\"}"
  }
}
```

#### 为什么用 index？

在流式输出中，可能有**多个**工具调用：

```json
[
  {"index": 0, "function": {"name": "get_weather", ...}},
  {"index": 1, "function": {"name": "read_file", ...}}
]
```

`index` 用于标识是第几个工具调用，确保增量正确累积。

---

### SSE 解析实现

#### 步骤一：识别数据行

```java
public Flux<LlmChunk> parseSseLine(String line) {
    // 跳过空行
    if (line.isBlank()) {
        return Flux.empty();
    }
    
    // 提取 data: 后面的内容
    String data = line;
    if (line.startsWith("data:")) {
        data = line.substring(5).trim();
    }
    
    // 检查是否结束
    if (data.equals("[DONE]") || data.isEmpty()) {
        return Flux.just(LlmChunk.builder().done(true).build());
    }
    
    // 解析 JSON
    return parseJsonChunk(data);
}
```

#### 步骤二：解析 JSON

```java
private Flux<LlmChunk> parseJsonChunk(String data) {
    try {
        JsonNode root = objectMapper.readTree(data);
        JsonNode choices = root.get("choices");
        
        if (choices == null || choices.isEmpty()) {
            return Flux.empty();
        }
        
        JsonNode firstChoice = choices.get(0);
        JsonNode delta = firstChoice.get("delta");
        JsonNode finishReason = firstChoice.get("finish_reason");
        
        // 提取内容
        String content = null;
        if (delta != null && delta.has("content")) {
            content = delta.get("content").asText();
        }
        
        // 构建 chunk
        return Flux.just(LlmChunk.builder()
                .delta(content)
                .finishReason(finishReason != null ? finishReason.asText() : null)
                .done(finishReason != null)
                .build());
        
    } catch (JsonProcessingException e) {
        log.warn("Failed to parse SSE chunk: {}", data, e);
        return Flux.empty();
    }
}
```

#### 步骤三：累积 Tool Calls

```java
// 用 Map 累积每个 index 的工具调用
Map<Integer, ToolCallAccumulator> accumulators = new HashMap<>();

private void accumulateToolCall(JsonNode toolCallsDelta) {
    for (JsonNode tc : toolCallsDelta) {
        int index = tc.get("index").asInt();
        
        ToolCallAccumulator acc = accumulators
            .computeIfAbsent(index, k -> new ToolCallAccumulator());
        
        if (tc.has("id")) {
            acc.id = tc.get("id").asText();
        }
        if (tc.has("function")) {
            JsonNode func = tc.get("function");
            if (func.has("name")) {
                acc.functionName = func.get("name").asText();
            }
            if (func.has("arguments")) {
                acc.arguments.append(func.get("arguments").asText());
            }
        }
    }
}

// 累积器
class ToolCallAccumulator {
    String id;
    String functionName;
    StringBuilder arguments = new StringBuilder();
    
    ToolCall build() {
        return ToolCall.builder()
                .id(id)
                .function(ToolCall.FunctionCall.builder()
                        .name(functionName)
                        .arguments(arguments.toString())
                        .build())
                .build();
    }
}
```

---

### 完整的 SSE 流程

```
1. 客户端发起请求
   POST /v1/chat/completions
   { "stream": true, ... }
        ↓
2. 服务器开始流式返回
   HTTP/1.1 200 OK
   Content-Type: text/event-stream
        ↓
3. 数据块持续到达
   data: {"choices":[{"delta":{"content":"你"}}]}
   data: {"choices":[{"delta":{"content":"好"}}]}
   data: {"choices":[{"delta":{"content":"！"}}]}
        ↓
4. 客户端解析每个 chunk
   - 提取 delta.content
   - 累积到完整内容
   - 实时显示
        ↓
5. 流结束
   data: [DONE]
```

---

### 常见问题

#### Q: 为什么有时 content 是 null？

**A**: 可能有几种情况：
1. 这是结束帧（`finish_reason` 有值）
2. 这是工具调用帧（`tool_calls` 有值）
3. 这是空帧（可忽略）

```java
// 正确处理 null content
if (content == null && finishReason == null) {
    return Flux.empty();  // 跳过空帧
}
```

#### Q: finish_reason 什么时候出现？

**A**: 只在**最后一个 chunk** 出现：

```
data: {"delta":{"content":"你"}, "finish_reason":null}
data: {"delta":{"content":"好"}, "finish_reason":null}
data: {"delta":{"content":"！"}, "finish_reason":"stop"}  ← 最后一帧
```

#### Q: [DONE] 和 finish_reason 有什么区别？

**A**:
- `[DONE]`：SSE 流的结束信号
- `finish_reason`：LLM 生成结束的原因

**顺序**：
```
data: {"finish_reason":"stop"}  ← LLM 结束
data: [DONE]                    ← SSE 流结束
```

---

### 动手实践

**任务**：手动解析 SSE 数据

**给定的 SSE 数据**：
```
data: {"choices":[{"delta":{"content":"He"},"finish_reason":null}]}

data: {"choices":[{"delta":{"content":"llo"},"finish_reason":null}]}

data: {"choices":[{"delta":{"content":"!"},"finish_reason":"stop"}]}

data: [DONE]
```

**问题**：
1. 完整的内容是什么？
2. 有几个 chunk？
3. 哪个 chunk 标志着结束？

**答案**：
<details>
<summary>点击展开</summary>

1. 完整内容：`Hello!`
2. 3 个 chunk（不包括 [DONE]）
3. 第 3 个 chunk（finish_reason = "stop"）

**累积过程**：
- Chunk 1: "He" → 累积: "He"
- Chunk 2: "llo" → 累积: "Hello"
- Chunk 3: "!" → 累积: "Hello!" + 结束标志

</details>

---

### 自检：你真的掌握了吗？

**问题 1**：SSE 和 WebSocket 有什么区别？为什么 LLM API 用 SSE？
> 如果答不上来，重读「什么是 SSE？」

你的答案：
```


```

参考答案：
<details>
<summary>点击展开</summary>

**主要区别**：

| 维度 | SSE | WebSocket |
|------|-----|-----------|
| 方向 | 单向（服务器→客户端） | 双向 |
| 协议 | HTTP | WS（独立协议） |
| 格式 | 文本（UTF-8） | 文本/二进制 |
| 断线重连 | 浏览器自动 | 需要手动实现 |
| 复杂度 | 简单 | 复杂 |

**为什么 LLM 用 SSE**：
1. **单向足够**：LLM 只需要推送，不需要客户端频繁发消息
2. **基于 HTTP**：兼容性好，不需要特殊协议
3. **简单可靠**：自动重连，易于调试
4. **文本友好**：LLM 返回的是文本，SSE 天然支持

</details>

---

**问题 2**：Delta 是什么？为什么要累积？
> 如果卡住，说明需要更多练习

你的答案：
```


```

参考答案：
<details>
<summary>点击展开</summary>

**Delta** = 增量（Incremental Change）

**为什么要累积**：
- 流式输出每次只返回新增的部分
- 客户端需要累积拼接才能得到完整内容

**示例**：
```
Chunk 1: delta = "你"
Chunk 2: delta = "好"
Chunk 3: delta = "！"

累积过程：
  "" + "你" = "你"
  "你" + "好" = "你好"
  "你好" + "！" = "你好！"
```

**代码实现**：
```java
StringBuilder fullContent = new StringBuilder();

// 每次 chunk 到达
fullContent.append(chunk.getDelta());

// 最终结果
String result = fullContent.toString();
```

</details>

---

**问题 3**（选做）：如何处理流式输出的 Function Calling？

你的答案：
```


```

参考答案：
<details>
<summary>点击展开</summary>

**挑战**：
- `tool_calls` 的 `arguments` 是分片到达的
- 需要累积拼接才能得到完整的 JSON

**解决方案**：使用累积器（Accumulator）

```java
class ToolCallAccumulator {
    String id;
    String functionName;
    StringBuilder arguments = new StringBuilder();
}

// 用 Map 按 index 累积
Map<Integer, ToolCallAccumulator> accumulators = new HashMap<>();

// 每次收到 delta
for (JsonNode tc : deltaToolCalls) {
    int index = tc.get("index").asInt();
    ToolCallAccumulator acc = accumulators.get(index);
    
    if (tc.has("function")) {
        if (tc.get("function").has("name")) {
            acc.functionName = tc.get("function").get("name").asText();
        }
        if (tc.get("function").has("arguments")) {
            acc.arguments.append(tc.get("function").get("arguments").asText());
        }
    }
}

// 流结束时
if ("tool_calls".equals(finishReason)) {
    List<ToolCall> toolCalls = accumulators.values().stream()
            .map(acc -> ToolCall.builder()
                    .id(acc.id)
                    .function(FunctionCall.builder()
                            .name(acc.functionName)
                            .arguments(acc.arguments.toString())
                            .build())
                    .build())
            .toList();
}
```

**关键点**：
1. 用 `index` 区分不同的工具调用
2. `arguments` 是字符串，需要累积拼接
3. 在 `finish_reason = "tool_calls"` 时构建完整的 ToolCall

</details>

---

### 掌握度自评

| 状态 | 标准 | 建议 |
|------|------|------|
| 🟢 已掌握 | 3题全对，能手动解析 SSE | 进入下一节 |
| 🟡 基本掌握 | 2题正确，理解基本概念 | 再复习一遍 |
| 🔴 需要加强 | 1题及以下 | 重读本节，务必动手实践 |

---

### 本节小结

- 我们深入学习了 SSE 协议
- 关键要点：
  - SSE 是单向推送协议，基于 HTTP
  - 数据格式：`data: JSON\n\n`
  - Delta 是增量，需要累积
  - `finish_reason` 标志着流结束
  - Function Calling 的 arguments 是分片到达的
- 下一节我们将实现完整的流式调用

---

### 扩展阅读（可选）

- [MDN: Server-Sent Events](https://developer.mozilla.org/en-US/docs/Web/API/Server-sent_events)
- [OpenAI Streaming API](https://platform.openai.com/docs/api-reference/streaming)
- [SSE vs WebSocket](https://ably.com/blog/sse-vs-websockets)

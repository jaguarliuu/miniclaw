# 第4.2节：数据模型设计 - 从零开始写 LLM 请求与响应

> **学习目标**：从零创建 LLM 请求和响应的数据模型，每个文件都能编译通过
> **预计时长**：20 分钟
> **难度**：入门

### 前置知识检查

**你应该已经掌握**：
- [x] 4.1 LLM 客户端架构
- [ ] Lombok 基本使用

**如果你不确定**：
- Lombok 没用过 → 本节会边写边讲

---

### 为什么需要数据模型？

假设你要调用 DeepSeek API：

**没有数据模型**：
```java
// ❌ 用 Map，容易出错
Map<String, Object> request = new HashMap<>();
request.put("messsages", messages);  // 拼写错误！编译器不报错
request.put("temprature", 0.7);       // 又拼错了！
String response = httpClient.post(request);
// 运行时才发现错误，浪费时间和 API 额度
```

**有数据模型**：
```java
// ✅ 类型安全
LlmRequest request = LlmRequest.builder()
    .messages(messages)    // IDE 自动补全
    .temperature(0.7)      // 拼写错误会报错
    .build();
```

**数据模型 = 代码世界的"合同"**，定义清楚请求和响应的格式。

---

### 第一步：创建包结构

打开你的项目，创建以下目录：

```bash
# 在 backend/src/main/java/com/miniclaw/ 下创建
mkdir -p llm/model
```

最终结构：
```
backend/src/main/java/com/miniclaw/
└── llm/
    ├── LlmClient.java          # 上一节创建的接口
    ├── LlmProperties.java      # 上一节创建的配置
    └── model/                   # 本节要创建的
        ├── LlmRequest.java
        ├── LlmResponse.java
        ├── LlmChunk.java
        └── ToolCall.java
```

---

### 第二步：创建 LlmRequest.java

**2.1 创建空文件**

在 `llm/model/` 下创建 `LlmRequest.java`：

```java
package com.miniclaw.llm.model;

/**
 * LLM 请求模型
 */
public class LlmRequest {
    // 待添加
}
```

**2.2 添加 Lombok 注解**

```java
package com.miniclaw.llm.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

/**
 * LLM 请求模型
 */
@Data                      // 自动生成 getter/setter/toString
@NoArgsConstructor         // 自动生成无参构造器
@AllArgsConstructor        // 自动生成全参构造器
@Builder                   // 自动生成 Builder 模式
public class LlmRequest {
    // 待添加字段
}
```

**验证一下**：保存文件，确保没有编译错误。

**2.3 添加核心字段**

```java
package com.miniclaw.llm.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.util.List;
import java.util.Map;

/**
 * LLM 请求模型
 * 对应 OpenAI Chat Completions API 的请求格式
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LlmRequest {

    /**
     * 消息列表（必填）
     * 包含 system/user/assistant/tool 等角色的消息
     */
    private List<Message> messages;

    /**
     * 模型名称（可选）
     * 如：gpt-4o、deepseek-chat
     */
    private String model;

    /**
     * 温度参数（可选，0-2）
     * 0 = 确定性输出
     * 0.7 = 平衡
     * 1.5+ = 高随机性
     */
    private Double temperature;

    /**
     * 最大 token 数（可选）
     */
    private Integer maxTokens;

    /**
     * 工具定义列表（可选，用于 Function Calling）
     */
    private List<Map<String, Object>> tools;

    /**
     * 工具选择策略（可选）
     * auto/none/required
     */
    private String toolChoice;
}
```

**验证一下**：保存，确保编译通过。

**2.4 添加内部类 Message**

在 `LlmRequest` 类的**内部**添加：

```java
    /**
     * 消息模型
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Message {
        
        /**
         * 角色：system/user/assistant/tool
         */
        private String role;

        /**
         * 消息内容
         */
        private String content;

        /**
         * 工具调用列表（仅 assistant 有）
         */
        private List<ToolCall> toolCalls;

        /**
         * 工具调用 ID（仅 tool 有）
         */
        private String toolCallId;
    }
```

**注意**：`ToolCall` 会报错，因为还没创建。没关系，继续下一步。

**2.5 添加便捷工厂方法**

在 `Message` 类的**内部**继续添加：

```java
        // ==================== 便捷方法 ====================

        /**
         * 创建系统消息
         */
        public static Message system(String content) {
            return Message.builder()
                    .role("system")
                    .content(content)
                    .build();
        }

        /**
         * 创建用户消息
         */
        public static Message user(String content) {
            return Message.builder()
                    .role("user")
                    .content(content)
                    .build();
        }

        /**
         * 创建助手消息
         */
        public static Message assistant(String content) {
            return Message.builder()
                    .role("assistant")
                    .content(content)
                    .build();
        }

        /**
         * 创建工具结果消息
         */
        public static Message toolResult(String toolCallId, String content) {
            return Message.builder()
                    .role("tool")
                    .toolCallId(toolCallId)
                    .content(content)
                    .build();
        }
```

**完整代码**：见本节末尾。

---

### 第三步：创建 ToolCall.java

**3.1 创建文件**

在 `llm/model/` 下创建 `ToolCall.java`：

```java
package com.miniclaw.llm.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

/**
 * LLM 工具调用
 * 对应 OpenAI Function Calling 的响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
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

    /**
     * 函数调用信息
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FunctionCall {
        
        /**
         * 函数名称
         */
        private String name;

        /**
         * 参数 JSON 字符串
         */
        private String arguments;
    }

    /**
     * 便捷方法：获取函数名
     */
    public String getName() {
        return function != null ? function.getName() : null;
    }

    /**
     * 便捷方法：获取参数
     */
    public String getArguments() {
        return function != null ? function.getArguments() : null;
    }
}
```

**验证一下**：保存，`LlmRequest.java` 中的 `ToolCall` 报错应该消失了。

---

### 第四步：创建 LlmResponse.java

在 `llm/model/` 下创建 `LlmResponse.java`：

```java
package com.miniclaw.llm.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.util.List;

/**
 * LLM 响应模型（同步调用）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LlmResponse {

    /**
     * 响应内容
     */
    private String content;

    /**
     * 工具调用列表
     */
    private List<ToolCall> toolCalls;

    /**
     * 完成原因：stop/length/tool_calls
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

    /**
     * Token 使用量
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Usage {
        private Integer promptTokens;
        private Integer completionTokens;
        private Integer totalTokens;
    }
}
```

**验证一下**：保存，编译通过。

---

### 第五步：创建 LlmChunk.java

在 `llm/model/` 下创建 `LlmChunk.java`：

```java
package com.miniclaw.llm.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.util.List;

/**
 * LLM 流式响应块
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LlmChunk {

    /**
     * 内容增量（流式输出的每一小段）
     */
    private String delta;

    /**
     * 工具调用列表（流结束时才有）
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

    /**
     * 判断是否有工具调用
     */
    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isEmpty();
    }
}
```

**验证一下**：保存，编译通过。

---

### 第六步：验证使用

创建一个测试类验证模型是否正确：

```java
package com.miniclaw.llm;

import com.miniclaw.llm.model.LlmRequest;
import com.miniclaw.llm.model.LlmRequest.Message;
import org.junit.jupiter.api.Test;

import java.util.List;

class LlmRequestTest {

    @Test
    void testCreateRequest() {
        // 创建请求
        LlmRequest request = LlmRequest.builder()
                .messages(List.of(
                    Message.system("你是一个有帮助的助手"),
                    Message.user("你好")
                ))
                .model("deepseek-chat")
                .temperature(0.7)
                .build();
        
        // 验证
        assert request.getMessages().size() == 2;
        assert "deepseek-chat".equals(request.getModel());
        assert request.getTemperature() == 0.7;
        
        System.out.println("测试通过！");
    }
}
```

运行测试，应该看到"测试通过！"。

---

### 完整代码

**LlmRequest.java**（完整版）：

```java
package com.miniclaw.llm.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LlmRequest {

    private List<Message> messages;
    private String model;
    private Double temperature;
    private Integer maxTokens;
    private List<Map<String, Object>> tools;
    private String toolChoice;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Message {
        private String role;
        private String content;
        private List<ToolCall> toolCalls;
        private String toolCallId;

        public static Message system(String content) {
            return Message.builder().role("system").content(content).build();
        }

        public static Message user(String content) {
            return Message.builder().role("user").content(content).build();
        }

        public static Message assistant(String content) {
            return Message.builder().role("assistant").content(content).build();
        }

        public static Message toolResult(String toolCallId, String content) {
            return Message.builder().role("tool").toolCallId(toolCallId).content(content).build();
        }
    }
}
```

---

### 动手实践

**任务**：按照上面的步骤，创建所有数据模型

**检查清单**：
- [ ] 创建 `llm/model/` 目录
- [ ] 创建 `LlmRequest.java` 并编译通过
- [ ] 创建 `ToolCall.java` 并编译通过
- [ ] 创建 `LlmResponse.java` 并编译通过
- [ ] 创建 `LlmChunk.java` 并编译通过
- [ ] 运行测试用例验证

---

### 自检：你真的掌握了吗？

**问题 1**：`Message.system()` 这个方法是什么？为什么要提供它？

你的答案：
```


```

参考答案：
<details>
<summary>点击展开</summary>

这是**静态工厂方法**（Static Factory Method）。

**好处**：
1. **简化创建**：`Message.system("内容")` 比 `new Message("system", "内容", null, null)` 更简洁
2. **类型安全**：不会把 role 写错
3. **可读性好**：代码即文档

**对比**：
```java
// ❌ 传统方式：参数顺序容易搞混
new Message("system", "你好", null, null);

// ✅ 工厂方法：清晰、简洁
Message.system("你好");
```

</details>

---

**问题 2**：`LlmChunk` 和 `LlmResponse` 有什么区别？为什么需要两个类？

你的答案：
```


```

参考答案：
<details>
<summary>点击展开</summary>

| 类 | 用途 | 内容字段 |
|----|------|----------|
| `LlmResponse` | 同步调用，一次性返回 | `content`（完整内容） |
| `LlmChunk` | 流式调用，增量返回 | `delta`（增量片段） |

**为什么需要两个类**：
- 同步调用：等待完整响应，直接返回 `LlmResponse`
- 流式调用：实时推送，每次返回 `LlmChunk`，需要累积

**类比**：
- `LlmResponse` = 下载完整文件
- `LlmChunk` = 数据流的一个数据包

</details>

---

### 本节小结

- 我们从零创建了 4 个数据模型
- 关键要点：
  - Lombok 简化了 POJO 代码
  - 工厂方法让对象创建更清晰
  - `LlmRequest` 是请求，`LlmResponse` 是同步响应，`LlmChunk` 是流式响应块
- 下一节我们将实现同步调用

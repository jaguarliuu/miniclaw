# 第4.2节：接口与数据模型 - 从零定义 LLM 客户端

> **学习目标**：从零定义 LLM 客户端接口、配置类和数据模型
> **预计时长**：25 分钟
> **难度**：入门

### 前置知识检查

**你应该已经掌握**：
- [x] 4.1 为什么不用 Spring AI
- [ ] 接口和实现的概念
- [ ] Lombok 基本使用

**如果你不确定**：
- 接口不理解 → 本节会从"为什么需要接口"开始
- Lombok 没用过 → 本节会边写边讲

---

### 为什么需要接口？

#### 真实场景

假设你要实现 LLM 客户端。

**不用接口**：
```java
// ❌ 直接写实现类
public class OpenAiLlmClient {
    public String chat(String message) { ... }
}

// 业务代码直接依赖实现类
public class AgentService {
    private OpenAiLlmClient client;  // 直接依赖实现
}
```

**问题**：
1. 换 LLM 提供商需要改业务代码
2. 无法单元测试（无法 Mock）
3. 业务层和具体实现强耦合

**用接口**：
```java
// ✅ 定义接口
public interface LlmClient {
    String chat(LlmRequest request);
}

// 多个实现
public class OpenAiLlmClient implements LlmClient { ... }
public class DeepSeekLlmClient implements LlmClient { ... }
public class MockLlmClient implements LlmClient { ... }  // 测试用

// 业务代码依赖接口
public class AgentService {
    private LlmClient client;  // 依赖接口，不依赖实现
}
```

**好处**：
1. 换实现不需要改业务代码
2. 可以用 Mock 进行单元测试
3. 业务层和实现解耦

---

### 第一步：创建包结构

**1.1 创建目录**

```bash
# 在 backend/src/main/java/com/miniclaw/ 下创建
mkdir -p llm/model
mkdir -p config
```

最终结构：
```
backend/src/main/java/com/miniclaw/
├── MiniClawApplication.java
├── config/
│   └── LlmProperties.java     # 本节创建
└── llm/
    ├── LlmClient.java          # 本节创建
    ├── LlmProperties.java      # （移动到 config/ 下）
    └── model/                  # 本节创建
        ├── LlmRequest.java
        ├── LlmResponse.java
        ├── LlmChunk.java
        └── ToolCall.java
```

---

### 第二步：定义 LlmClient 接口

**2.1 创建接口文件**

创建 `llm/LlmClient.java`：

```java
package com.miniclaw.llm;

/**
 * LLM 客户端接口
 * 
 * 为什么需要这个接口？
 * - 抽象 LLM 调用，支持多种实现
 * - 业务层不依赖具体实现
 * - 便于测试（可以用 Mock）
 */
public interface LlmClient {

    /**
     * 同步调用 LLM
     * 
     * 适用场景：
     * - 单次问答
     * - 批量处理
     * - 后台任务
     */
    LlmResponse chat(LlmRequest request);

    /**
     * 流式调用 LLM
     * 
     * 适用场景：
     * - 实时对话（逐字显示）
     * - 长文本生成
     */
    Flux<LlmChunk> stream(LlmRequest request);
}
```

**此时会报错**：`LlmResponse`、`LlmRequest`、`Flux`、`LlmChunk` 未定义。没关系，继续。

**2.2 添加导入**

```java
package com.miniclaw.llm;

import com.miniclaw.llm.model.LlmChunk;
import com.miniclaw.llm.model.LlmRequest;
import com.miniclaw.llm.model.LlmResponse;
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
```

**还是会报错**，但我们要先创建配置类。

---

### 第三步：创建 LlmProperties 配置类

**3.1 创建配置类**

创建 `config/LlmProperties.java`：

```java
package com.miniclaw.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * LLM 配置属性
 * 
 * 从 application.yml 读取 llm.* 配置
 * 自动绑定到这个类的字段
 */
@Data
@Component
@ConfigurationProperties(prefix = "llm")
public class LlmProperties {

    /**
     * LLM API 端点
     */
    private String endpoint = "https://api.deepseek.com";

    /**
     * API 密钥
     */
    private String apiKey;

    /**
     * 模型名称
     */
    private String model = "deepseek-chat";

    /**
     * 温度参数（0-2）
     */
    private Double temperature = 0.7;

    /**
     * 最大 token 数
     */
    private Integer maxTokens = 2048;

    /**
     * 请求超时（秒）
     */
    private Integer timeout = 60;

    /**
     * 最大重试次数
     */
    private Integer maxRetries = 3;
}
```

**3.2 验证编译**

```bash
cd backend
./mvnw clean compile
```

应该看到 `BUILD SUCCESS`。

---

### 第四步：创建 LlmRequest 数据模型

**4.1 创建请求类**

创建 `llm/model/LlmRequest.java`：

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
 */
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
     * 温度参数（可选）
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
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Message {
        
        private String role;
        private String content;
        private List<ToolCall> toolCalls;
        private String toolCallId;

        // 便捷工厂方法
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

**此时会报错**：`ToolCall` 未定义。继续。

---

### 第五步：创建 ToolCall 数据模型

创建 `llm/model/ToolCall.java`：

```java
package com.miniclaw.llm.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * LLM 工具调用
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolCall {

    private String id;
    
    @Builder.Default
    private String type = "function";
    
    private FunctionCall function;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FunctionCall {
        private String name;
        private String arguments;
    }

    public String getName() {
        return function != null ? function.getName() : null;
    }

    public String getArguments() {
        return function != null ? function.getArguments() : null;
    }
}
```

**验证一下**：`LlmRequest.java` 的报错应该消失了。

---

### 第六步：创建 LlmResponse 数据模型

创建 `llm/model/LlmResponse.java`：

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

    private String content;
    private List<ToolCall> toolCalls;
    private String finishReason;
    private Usage usage;

    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isEmpty();
    }

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

---

### 第七步：创建 LlmChunk 数据模型

创建 `llm/model/LlmChunk.java`：

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

    private String delta;
    private List<ToolCall> toolCalls;
    private String finishReason;
    private boolean done;
    private LlmResponse.Usage usage;

    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isEmpty();
    }
}
```

---

### 第八步：验证接口编译

现在回到 `LlmClient.java`，确认所有导入都正确：

```java
package com.miniclaw.llm;

import com.miniclaw.llm.model.LlmChunk;
import com.miniclaw.llm.model.LlmRequest;
import com.miniclaw.llm.model.LlmResponse;
import reactor.core.publisher.Flux;

public interface LlmClient {
    LlmResponse chat(LlmRequest request);
    Flux<LlmChunk> stream(LlmRequest request);
}
```

**验证编译**：

```bash
./mvnw clean compile
```

看到 `BUILD SUCCESS` 就对了！

---

### 第九步：创建测试验证

创建测试类验证模型是否正确：

```java
package com.miniclaw.llm;

import com.miniclaw.llm.model.LlmRequest;
import com.miniclaw.llm.model.LlmRequest.Message;
import org.junit.jupiter.api.Test;

import java.util.List;

class LlmModelsTest {

    @Test
    void testCreateRequest() {
        LlmRequest request = LlmRequest.builder()
                .messages(List.of(
                    Message.system("你是一个有帮助的助手"),
                    Message.user("你好")
                ))
                .model("deepseek-chat")
                .temperature(0.7)
                .build();
        
        assert request.getMessages().size() == 2;
        assert "deepseek-chat".equals(request.getModel());
        assert request.getTemperature() == 0.7;
        
        System.out.println("测试通过！");
    }
}
```

运行测试：
```bash
./mvnw test -Dtest=LlmModelsTest
```

---

### 动手实践

**任务**：创建 LLM 客户端接口和数据模型

**步骤**：
1. 创建 `llm/` 和 `llm/model/` 目录
2. 创建 `LlmClient.java` 接口
3. 创建 `config/LlmProperties.java` 配置类
4. 创建 `LlmRequest.java`
5. 创建 `ToolCall.java`
6. 创建 `LlmResponse.java`
7. 创建 `LlmChunk.java`
8. 编译验证
9. 运行测试

**检查清单**：
- [ ] 接口定义完成
- [ ] 配置类定义完成
- [ ] 4 个数据模型全部创建
- [ ] 编译通过
- [ ] 测试通过

---

### 自检：你真的掌握了吗？

**问题 1**：为什么用接口而不是直接写实现类？

你的答案：
```


```

参考答案：
<details>
<summary>点击展开</summary>

**接口的好处**：
1. **解耦**：业务层不依赖具体实现
2. **可替换**：换实现不需要改业务代码
3. **可测试**：可以用 Mock 进行单元测试
4. **多实现**：可以有 OpenAI、DeepSeek、Mock 等多个实现

**类比**：
- 接口 = USB 接口标准
- 实现类 = 鼠标、键盘、U盘

USB 接口定义了标准，任何设备都可以插。

</details>

---

**问题 2**：`@ConfigurationProperties(prefix = "llm")` 做了什么？

你的答案：
```


```

参考答案：
<details>
<summary>点击展开</summary>

这个注解让 Spring Boot 自动将 `application.yml` 中的 `llm.*` 配置绑定到类的字段。

**示例**：
```yaml
llm:
  endpoint: https://api.deepseek.com
  model: deepseek-chat
```

自动绑定到：
```java
@ConfigurationProperties(prefix = "llm")
public class LlmProperties {
    private String endpoint;  // 自动注入
    private String model;      // 自动注入
}
```

**好处**：
- 类型安全
- IDE 自动补全
- 配置验证

</details>

---

**问题 3**：`LlmChunk` 和 `LlmResponse` 有什么区别？

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

**使用场景**：
- `LlmResponse`：`chat()` 方法返回，等待完整响应
- `LlmChunk`：`stream()` 方法返回，每次返回一小段

**类比**：
- `LlmResponse` = 下载完整文件
- `LlmChunk` = 数据流的一个数据包

</details>

---

### 本节小结

- 我们从零定义了 LLM 客户端的接口和数据模型
- 关键要点：
  - 接口定义行为，实现类负责具体逻辑
  - `@ConfigurationProperties` 自动绑定配置
  - `LlmRequest` 是请求，`LlmResponse` 是同步响应，`LlmChunk` 是流式响应
  - 工厂方法让对象创建更清晰
- 下一节我们将实现 `chat()` 同步调用

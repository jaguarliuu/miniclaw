# 第3.5节：配置管理 - application.yml 与环境变量

> **学习目标**：掌握配置管理最佳实践，实现多环境配置和敏感信息保护
> **预计时长**：15 分钟
> **难度**：入门

### 前置知识检查

**你应该已经掌握**：
- [x] 3.1-3.4 项目骨架已搭建
- [ ] YAML 基本语法

**如果你不确定**：
- YAML 不熟 → 本节会详细讲解语法
- 没用过多环境配置 → 本节从零开始

**学习路径**：
- **路径A（有基础）**：直接跳到「多环境配置」
- **路径B（从零开始）**：按顺序阅读全部内容

---

### 为什么需要配置管理？

#### 真实场景

假设你开发 MiniClaw，需要连接数据库和 LLM API。

**硬编码配置（错误做法）**：
```java
// ❌ 糟糕的做法
public class LlmClient {
    private String endpoint = "https://api.deepseek.com";
    private String apiKey = "sk-xxxxx";  // 泄露到代码仓库！
    private String model = "deepseek-chat";
}
```

**问题**：
1. 换环境（开发/测试/生产）需要改代码
2. API Key 泄露到 Git 仓库
3. 团队成员配置不一致
4. 无法动态调整配置

**外部化配置（正确做法）**：
```yaml
# application.yml
llm:
  endpoint: ${LLM_ENDPOINT:https://api.deepseek.com}
  api-key: ${LLM_API_KEY}  # 从环境变量读取
  model: ${LLM_MODEL:deepseek-chat}
```

**好处**：
- 不同环境使用不同配置，不改代码
- 敏感信息通过环境变量注入，不进代码仓库
- 团队成员可以使用各自的配置
- 运行时可以动态调整

#### 直觉理解

**配置管理就像"餐厅菜单"**：
- 菜单（配置文件）写在墙上，所有人都能看到
- 菜品（应用）根据菜单做菜
- 换菜单（改配置）不需要换厨师（改代码）
- VIP 客户（环境变量）可以有特殊待遇

#### 技术定义

**外部化配置**：将配置从代码中分离出来，存储在外部文件或环境变量中。

**Spring Boot 配置加载顺序**（优先级从高到低）：
1. 命令行参数
2. 环境变量
3. `application-{profile}.yml`（环境特定配置）
4. `application.yml`（默认配置）

---

### 第一步：理解 YAML 语法

YAML（YAML Ain't Markup Language）是 Spring Boot 推荐的配置格式。

#### 基本语法

```yaml
# 注释
key: value              # 键值对
nested:                 # 嵌套对象
  key: value
list:                   # 列表
  - item1
  - item2
```

#### 对比 Properties

```yaml
# YAML 格式
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/miniclaw
    username: miniclaw
```

```properties
# Properties 格式
spring.datasource.url=jdbc:postgresql://localhost:5432/miniclaw
spring.datasource.username=miniclaw
```

**YAML 的优势**：
- 层级更清晰
- 支持复杂结构（列表、嵌套对象）
- 更易读

---

### 第二步：创建基础配置

在 `src/main/resources/` 创建 `application.yml`：

```yaml
# MiniClaw 应用配置
# 这个文件定义了应用运行时的所有可配置参数

spring:
  application:
    name: miniclaw
  
  # 数据源配置
  datasource:
    url: jdbc:postgresql://localhost:5432/miniclaw
    driver-class-name: org.postgresql.Driver
    username: miniclaw
    password: miniclaw123
  
  # JPA 配置
  jpa:
    hibernate:
      ddl-auto: validate  # 使用 Flyway 管理表结构
    show-sql: true        # 显示 SQL（开发环境）
    properties:
      hibernate:
        format_sql: true
        dialect: org.hibernate.dialect.PostgreSQLDialect
  
  # Flyway 配置
  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: true
    validate-on-migrate: true

# 服务端口
server:
  port: 8080

# LLM 配置
llm:
  endpoint: ${LLM_ENDPOINT:https://api.deepseek.com}
  api-key: ${LLM_API_KEY:your-api-key-here}
  model: ${LLM_MODEL:deepseek-chat}

# 日志配置
logging:
  level:
    root: INFO
    com.miniclaw: DEBUG
    org.hibernate.SQL: DEBUG
```

#### 环境变量注入语法

```yaml
${VAR_NAME}           # 必须存在，否则启动失败
${VAR_NAME:default}   # 如果不存在，使用默认值
```

**示例**：
```yaml
llm:
  api-key: ${LLM_API_KEY}                    # 必须提供
  model: ${LLM_MODEL:deepseek-chat}          # 默认使用 deepseek-chat
```

---

### 第三步：多环境配置

创建不同环境的配置文件：

**开发环境** `application-dev.yml`：
```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/miniclaw
    username: miniclaw
    password: miniclaw123

server:
  port: 8080

logging:
  level:
    com.miniclaw: DEBUG

llm:
  endpoint: https://api.deepseek.com
  model: deepseek-chat
```

**生产环境** `application-prod.yml`：
```yaml
spring:
  datasource:
    url: jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/${DB_NAME:miniclaw}
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}

server:
  port: ${SERVER_PORT:8080}

logging:
  level:
    com.miniclaw: INFO

llm:
  endpoint: ${LLM_ENDPOINT}
  api-key: ${LLM_API_KEY}
  model: ${LLM_MODEL}
```

#### 激活环境

```bash
# 方式一：命令行参数
java -jar miniclaw.jar --spring.profiles.active=prod

# 方式二：环境变量
export SPRING_PROFILES_ACTIVE=prod
java -jar miniclaw.jar

# 方式三：application.yml 中配置
spring:
  profiles:
    active: dev
```

---

### 第四步：敏感信息管理

**永远不要把敏感信息提交到 Git！**

创建 `.gitignore`：
```
# 敏感配置文件
application-local.yml
.env

# IDE 配置
.idea/
*.iml
```

使用 `.env` 文件（本地开发）：
```bash
# .env
LLM_API_KEY=sk-your-real-api-key
DB_PASSWORD=your-db-password
```

加载 `.env` 文件：
```bash
# 使用 dotenv 加载环境变量
export $(cat .env | xargs)
java -jar miniclaw.jar
```

---

### 第五步：配置类绑定

创建配置类，类型安全地读取配置：

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
    private String endpoint;
    
    /**
     * API 密钥
     */
    private String apiKey;
    
    /**
     * 模型名称
     */
    private String model;
    
    /**
     * 请求超时（毫秒）
     */
    private int timeout = 60000;
}
```

**使用配置**：
```java
@Service
public class LlmClient {
    
    private final LlmProperties properties;
    
    public LlmClient(LlmProperties properties) {
        this.properties = properties;
    }
    
    public String chat(String message) {
        String endpoint = properties.getEndpoint();
        String apiKey = properties.getApiKey();
        // 使用配置...
    }
}
```

---

### 动手实践

**任务**：配置 MiniClaw 多环境

**步骤**：
1. 创建 `application.yml`
2. 创建 `application-dev.yml` 和 `application-prod.yml`
3. 配置环境变量 `LLM_API_KEY`
4. 创建 `LlmProperties` 配置类
5. 验证配置加载

**预期结果**：
- 开发环境使用 `application-dev.yml`
- 敏感信息通过环境变量注入
- 配置类正确读取配置

---

### 自检：你真的掌握了吗？

**问题 1**：`${LLM_API_KEY:default-key}` 是什么意思？
> 如果答不上来，重读「环境变量注入语法」

你的答案：
```


```

参考答案：
<details>
<summary>点击展开</summary>

这是 Spring Boot 的环境变量注入语法：
- `${LLM_API_KEY}`：尝试读取环境变量 `LLM_API_KEY`
- `:default-key`：如果环境变量不存在，使用默认值 `default-key`

**使用场景**：
- 必须提供的配置：`${LLM_API_KEY}`（不提供默认值，启动时报错）
- 可选配置：`${LLM_MODEL:deepseek-chat}`（有合理的默认值）

</details>

---

**问题 2**：为什么要用 `application-{profile}.yml` 而不是一个文件？
> 如果卡住，说明需要更多练习

你的答案：
```


```

参考答案：
<details>
<summary>点击展开</summary>

**多环境配置的好处**：
1. **环境隔离**：开发/测试/生产配置分离
2. **避免错误**：不会误用生产配置
3. **安全**：敏感信息只在生产环境配置
4. **灵活**：每个环境可以有不同的参数

**示例**：
- 开发环境：本地数据库、DEBUG 日志
- 生产环境：云数据库、INFO 日志、更高的超时

</details>

---

**问题 3**（选做）：如何在不重新打包的情况下修改配置？

你的答案：
```


```

参考答案：
<details>
<summary>点击展开</summary>

**方式一：环境变量**（推荐）
```bash
export LLM_API_KEY=new-key
java -jar miniclaw.jar
```

**方式二：命令行参数**
```bash
java -jar miniclaw.jar --llm.api-key=new-key
```

**方式三：外部配置文件**
```bash
java -jar miniclaw.jar --spring.config.location=/path/to/application.yml
```

**好处**：
- 不需要重新编译打包
- 运维可以独立修改配置
- 支持动态配置中心（如 Nacos、Consul）

</details>

---

### 掌握度自评

| 状态 | 标准 | 建议 |
|------|------|------|
| 🟢 已掌握 | 3题全对，实践任务完成 | 进入下一章 |
| 🟡 基本掌握 | 2题正确，实践任务部分完成 | 再复习一遍，重做实践 |
| 🔴 需要加强 | 1题及以下 | 重读本节，务必动手实践 |

---

### 本节小结

- 我们学习了 Spring Boot 配置管理
- 关键要点：
  - `application.yml` 集中管理配置
  - 环境变量注入敏感信息：`${VAR:default}`
  - 多环境配置：`application-{profile}.yml`
  - 配置类绑定：`@ConfigurationProperties`
  - 敏感信息永远不要提交到 Git
- 第3章完成！下一章我们将实现 LLM 客户端

---

### 扩展阅读（可选）

- [Spring Boot 配置文档](https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.external-config)
- [YAML 语法教程](https://yaml.org/spec/1.2/spec.html)
- [12-Factor App - 配置](https://12factor.net/zh_cn/config)

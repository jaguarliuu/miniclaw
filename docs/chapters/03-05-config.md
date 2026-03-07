# 第3.5节：配置管理 - 从单一配置到多环境

> **学习目标**：从零开始管理配置，从单文件到多环境，保护敏感信息
> **预计时长**：15 分钟
> **难度**：入门

### 前置知识检查

**你应该已经掌握**：
- [x] 3.1-3.4 项目骨架已搭建
- [ ] YAML 基本语法

**如果你不确定**：
- YAML 不熟 → 本节会边写边讲

---

### 为什么需要配置管理？

#### 真实场景

假设你在开发 MiniClaw，需要连接数据库和 LLM API。

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
4. 无法动态调整

**外部化配置（正确做法）**：
```yaml
# application.yml
llm:
  endpoint: https://api.deepseek.com
  api-key: ${LLM_API_KEY}  # 从环境变量读取
  model: deepseek-chat
```

**好处**：
- 不同环境使用不同配置，不改代码
- 敏感信息通过环境变量注入
- 团队配置统一
- 运行时可调整

---

### 第一步：创建基础配置文件

**1.1 创建 application.yml**

在 `src/main/resources/` 下创建 `application.yml`：

```yaml
# 应用名称
spring:
  application:
    name: miniclaw

# 服务端口
server:
  port: 8080
```

**验证一下**：启动应用，看到端口 8080。

**1.2 添加数据源配置**

```yaml
spring:
  application:
    name: miniclaw
  
  # 数据源配置
  datasource:
    url: jdbc:postgresql://localhost:5432/miniclaw
    driver-class-name: org.postgresql.Driver
    username: miniclaw
    password: miniclaw123

server:
  port: 8080
```

**验证一下**：启动应用，应该能连接数据库。

**1.3 添加 JPA 配置**

```yaml
spring:
  application:
    name: miniclaw
  
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

server:
  port: 8080
```

---

### 第二步：添加 LLM 配置

**2.1 添加自定义配置**

```yaml
spring:
  application:
    name: miniclaw
  
  datasource:
    url: jdbc:postgresql://localhost:5432/miniclaw
    driver-class-name: org.postgresql.Driver
    username: miniclaw
    password: miniclaw123
  
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: true
    properties:
      hibernate:
        format_sql: true

server:
  port: 8080

# LLM 配置（自定义配置）
llm:
  endpoint: https://api.deepseek.com
  api-key: your-api-key-here  # 临时硬编码，稍后会改
  model: deepseek-chat
```

**2.2 创建配置类**

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
    private String endpoint;

    /**
     * API 密钥
     */
    private String apiKey;

    /**
     * 模型名称
     */
    private String model = "deepseek-chat";  // 默认值

    /**
     * 请求超时（秒）
     */
    private int timeout = 60;

    /**
     * 最大重试次数
     */
    private int maxRetries = 3;
}
```

**验证一下**：编译通过。

---

### 第三步：环境变量注入

**3.1 修改配置文件**

```yaml
llm:
  endpoint: ${LLM_ENDPOINT:https://api.deepseek.com}
  api-key: ${LLM_API_KEY}  # 从环境变量读取
  model: ${LLM_MODEL:deepseek-chat}
```

**语法解释**：
- `${LLM_API_KEY}`：必须存在，否则启动失败
- `${LLM_MODEL:deepseek-chat}`：如果不存在，使用默认值

**3.2 设置环境变量**

```bash
# 临时设置（当前终端）
export LLM_API_KEY=your-real-api-key

# 永久设置（添加到 ~/.bashrc 或 ~/.zshrc）
echo 'export LLM_API_KEY=your-real-api-key' >> ~/.bashrc
source ~/.bashrc
```

**3.3 验证**

启动应用，如果报错 `Could not resolve placeholder 'LLM_API_KEY'`，说明环境变量没设置。

---

### 第四步：多环境配置

**4.1 创建开发环境配置**

创建 `application-dev.yml`：

```yaml
# 开发环境配置
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
    org.hibernate.SQL: DEBUG

llm:
  endpoint: https://api.deepseek.com
  api-key: ${LLM_API_KEY}
  model: deepseek-chat
```

**4.2 创建生产环境配置**

创建 `application-prod.yml`：

```yaml
# 生产环境配置
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
    org.hibernate.SQL: WARN

llm:
  endpoint: ${LLM_ENDPOINT}
  api-key: ${LLM_API_KEY}
  model: ${LLM_MODEL}
```

**4.3 激活环境**

**方式一：配置文件**（在 `application.yml` 中）：
```yaml
spring:
  profiles:
    active: dev  # 激活开发环境
```

**方式二：命令行参数**：
```bash
java -jar miniclaw.jar --spring.profiles.active=prod
```

**方式三：环境变量**：
```bash
export SPRING_PROFILES_ACTIVE=prod
java -jar miniclaw.jar
```

---

### 第五步：敏感信息保护

**5.1 创建 .gitignore**

在项目根目录添加：

```
# 敏感配置文件
application-local.yml
.env

# IDE 配置
.idea/
*.iml

# 日志
logs/
*.log
```

**5.2 使用 .env 文件（本地开发）**

创建 `.env`（不要提交到 Git！）：
```bash
LLM_API_KEY=your-real-api-key
DB_PASSWORD=your-db-password
```

加载环境变量：
```bash
# Linux/Mac
export $(cat .env | xargs)

# 或使用 source
set -a; source .env; set +a
```

**5.3 最佳实践**

| 配置项 | 开发环境 | 生产环境 |
|--------|----------|----------|
| `api-key` | 环境变量 | 密钥管理服务 |
| `db.password` | 硬编码（临时） | 环境变量 |
| `show-sql` | true | false |
| `log.level` | DEBUG | INFO |

---

### 配置加载优先级

Spring Boot 配置加载顺序（优先级从高到低）：

1. 命令行参数
2. 环境变量
3. `application-{profile}.yml`
4. `application.yml`

**示例**：
```bash
# 命令行参数优先级最高
java -jar miniclaw.jar --server.port=9090

# 即使 application.yml 配置了 port=8080
# 最终也是 9090
```

---

### 动手实践

**任务**：配置 MiniClaw 多环境

**步骤**：
1. 创建 `application.yml`
2. 创建 `application-dev.yml`
3. 创建 `application-prod.yml`
4. 设置环境变量 `LLM_API_KEY`
5. 创建 `LlmProperties.java`
6. 验证配置加载

**验证方法**：
```java
@SpringBootTest
class ConfigTest {
    
    @Autowired
    private LlmProperties properties;
    
    @Test
    void testConfig() {
        System.out.println("Endpoint: " + properties.getEndpoint());
        System.out.println("Model: " + properties.getModel());
        // 不要打印 API Key！
    }
}
```

---

### 自检：你真的掌握了吗？

**问题 1**：`${LLM_API_KEY:default-key}` 是什么意思？

你的答案：
```


```

参考答案：
<details>
<summary>点击展开</summary>

这是 Spring Boot 的**环境变量注入语法**：
- `${LLM_API_KEY}`：尝试读取环境变量 `LLM_API_KEY`
- `:default-key`：如果环境变量不存在，使用默认值

**两种写法对比**：
- `${LLM_API_KEY}`：必须存在，否则启动失败
- `${LLM_API_KEY:default}`：可选，不存在时用默认值

**使用建议**：
- 敏感信息（API Key、密码）：不提供默认值，强制设置
- 可选配置（超时、模型）：提供合理的默认值

</details>

---

**问题 2**：为什么要用 `application-{profile}.yml` 而不是一个文件？

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

**配置复用**：
`application.yml` 配置公共部分，`application-{profile}.yml` 只配置差异部分。

</details>

---

**问题 3**：如何在不重新打包的情况下修改配置？

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

### 本节小结

- 我们学习了配置管理的最佳实践
- 关键要点：
  - `application.yml` 集中管理配置
  - 环境变量注入敏感信息：`${VAR:default}`
  - 多环境配置：`application-{profile}.yml`
  - 配置类绑定：`@ConfigurationProperties`
  - 敏感信息永远不要提交到 Git
- 第3章完成！下一章我们将实现 LLM 客户端

---

### 完整代码

**application.yml**：
```yaml
spring:
  application:
    name: miniclaw
  profiles:
    active: dev

server:
  port: 8080
```

**application-dev.yml**：
```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/miniclaw
    username: miniclaw
    password: miniclaw123
  jpa:
    show-sql: true

logging:
  level:
    com.miniclaw: DEBUG

llm:
  endpoint: https://api.deepseek.com
  api-key: ${LLM_API_KEY}
  model: deepseek-chat
```

**application-prod.yml**：
```yaml
spring:
  datasource:
    url: jdbc:postgresql://${DB_HOST}:${DB_PORT}/${DB_NAME}
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
  jpa:
    show-sql: false

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

**LlmProperties.java**：
```java
@Data
@Component
@ConfigurationProperties(prefix = "llm")
public class LlmProperties {
    private String endpoint;
    private String apiKey;
    private String model = "deepseek-chat";
    private int timeout = 60;
    private int maxRetries = 3;
}
```

# 第3章：开发环境与基础底座

## 第3.1节：项目初始化与工程结构

> **学习目标**：搭建 MiniClaw 后端项目的基础工程结构，理解每个文件的作用
> **预计时长**：30 分钟
> **难度**：入门

### 前置知识检查

**你应该已经掌握**：
- [ ] Java 基础语法（类、接口、注解）
- [ ] Maven 构建工具的基本使用
- [ ] Spring Boot 是什么（不需要精通，知道概念即可）

**如果你不确定**：
- Java 基础不熟 → 建议先补 Java 语法
- Maven 没用过 → 没关系，本节会详细讲解每个配置

---

### 为什么需要「工程结构」？

想象你要盖一栋房子。

你会怎么开始？直接堆砖头吗？当然不是。你需要先画图纸：客厅在哪、卧室在哪、水管怎么走、电线怎么布。

**写代码也是一样。**

如果你直接开始写代码，很快就会遇到这些问题：
- 文件到处乱放，找不到
- 依赖冲突，A 库要 X 版本，B 库要 Y 版本
- 配置散落各处，改一个地方要改十个文件
- 别人 clone 你的代码，跑不起来

**工程结构就是代码世界的「建筑图纸」。** 它规定：
- 代码放在哪个目录
- 配置怎么管理
- 依赖如何声明
- 项目怎么构建、怎么运行

好的工程结构让你：
- ✅ 一眼就知道文件在哪
- ✅ 新人 clone 代码就能跑
- ✅ 依赖清晰，版本统一管理
- ✅ 易于扩展和维护

本节我们要搭建的，就是这个「图纸」。

---

### MiniClaw 工程结构全景图

先看一眼我们要搭建的项目长什么样：

```
miniclaw/
├── backend/                        # 后端项目
│   ├── pom.xml                     # Maven 配置文件（重要！）
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/               # Java 源代码
│   │   │   │   └── com/miniclaw/
│   │   │   │       ├── MiniClawApplication.java    # 启动类
│   │   │   │       ├── config/                     # 配置类
│   │   │   │       ├── controller/                 # HTTP 控制器
│   │   │   │       ├── service/                    # 业务逻辑
│   │   │   │       ├── repository/                 # 数据访问
│   │   │   │       ├── model/                      # 数据模型
│   │   │   │       └── util/                       # 工具类
│   │   │   └── resources/           # 资源文件
│   │   │       ├── application.yml  # 应用配置（重要！）
│   │   │       └── db/              # 数据库迁移脚本
│   │   └── test/                    # 测试代码
│   └── mvnw                         # Maven Wrapper（可执行脚本）
│
└── frontend/                        # 前端项目（后续章节）
```

**你可能会问：为什么要分这么多目录？**

这是「分层架构」的体现：
- **controller**：接待客人（接收 HTTP 请求）
- **service**：干活的人（业务逻辑）
- **repository**：管仓库的（数据存储）
- **model**：货物清单（数据定义）
- **config**：公司规章（配置管理）

各司其职，互不干扰。后面我们会深入每一层。

---

### 第一步：创建 Maven 项目

Maven 是 Java 世界的「包管理器 + 构建工具」，类似于 Node.js 的 npm。

#### 为什么用 Maven？

你可能听说过 Gradle。两者都是 Java 构建工具，我们选择 Maven 的原因：
- ✅ 更成熟，生态更完善
- ✅ XML 配置虽然啰嗦，但清晰易懂
- ✅ Spring Boot 官方推荐

#### 创建项目目录

```bash
# 创建项目根目录
mkdir -p miniclaw/backend

# 进入后端目录
cd miniclaw/backend
```

#### 创建 pom.xml

`pom.xml` 是 Maven 的核心配置文件，全称是 **Project Object Model**（项目对象模型）。

它告诉 Maven：
1. 项目叫什么名字
2. 需要哪些依赖库
3. 怎么构建项目

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 
         https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    
    <!-- 
      为什么需要 parent？
      Spring Boot 提供了一个 "BOM"（Bill of Materials），
      它预配置好了所有依赖的版本，我们不用自己管理版本冲突。
    -->
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.4.3</version>
        <relativePath/>
    </parent>
    
    <!-- 项目坐标：Maven 世界的「身份证」 -->
    <groupId>com.miniclaw</groupId>        <!-- 组织/公司名 -->
    <artifactId>miniclaw</artifactId>       <!-- 项目名 -->
    <version>0.0.1-SNAPSHOT</version>       <!-- 版本号 -->
    <name>MiniClaw</name>
    <description>MiniClaw - A Minimal AI Agent Framework</description>
    
    <!-- Java 版本 -->
    <properties>
        <java.version>21</java.version>
    </properties>
    
    <!-- 依赖库 -->
    <dependencies>
        <!-- 
          WebFlux vs WebMVC：
          - WebMVC：传统 Servlet，同步阻塞
          - WebFlux：响应式，异步非阻塞
          
          为什么选 WebFlux？
          因为 AI Agent 需要流式输出（一个字一个字蹦出来），
          WebFlux 天生支持流式响应。
        -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-webflux</artifactId>
        </dependency>
        
        <!-- 
          JPA：Java Persistence API
          用于操作数据库，不用手写 SQL
        -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa</artifactId>
        </dependency>
        
        <!-- 
          H2：内存数据库
          开发阶段用内存数据库，不用安装 PostgreSQL
          生产环境再切换到真实数据库
        -->
        <dependency>
            <groupId>com.h2database</groupId>
            <artifactId>h2</artifactId>
            <scope>runtime</scope>
        </dependency>
        
        <!-- 
          Lombok：减少样板代码
          自动生成 getter/setter/toString 等
        -->
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>
        
        <!-- 测试依赖 -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
    
    <build>
        <plugins>
            <!-- Spring Boot Maven 插件：打包可执行 JAR -->
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <configuration>
                    <excludes>
                        <exclude>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok</artifactId>
                        </exclude>
                    </excludes>
                </configuration>
            </plugin>
        </plugins>
    </build>
    
</project>
```

#### 常见误区

> ❌ **误区**：依赖版本自己指定
> 
> ```xml
> <!-- 错误示例 -->
> <dependency>
>     <groupId>org.springframework.boot</groupId>
>     <artifactId>spring-boot-starter-webflux</artifactId>
>     <version>3.4.3</version>  <!-- 不需要！parent 已经管理了 -->
> </dependency>
> ```

> ✅ **正确理解**：parent 已经管理了所有 Spring 生态的版本，我们不需要再指定。

---

### 第二步：创建应用启动类

每个 Spring Boot 应用都需要一个「入口类」。

```java
package com.miniclaw;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * MiniClaw 应用程序入口
 * 
 * 这是整个 AI Agent 框架的起点。
 * Spring Boot 会自动扫描当前包及子包下的所有组件。
 */
@SpringBootApplication
public class MiniClawApplication {

    public static void main(String[] args) {
        SpringApplication.run(MiniClawApplication.class, args);
    }
}
```

#### @BootApplication 注解做了什么？

这个注解是一个「组合注解」，相当于三个注解合体：

```java
@SpringBootApplication = 
    @SpringBootConfiguration    // 这是一个配置类
    + @EnableAutoConfiguration  // 自动配置（Spring Boot 的魔法）
    + @ComponentScan             // 扫描当前包及子包的组件
```

**这意味着**：只要你的类在 `com.miniclaw` 或其子包下，并加上 `@Component`/`@Service`/`@Repository` 等注解，Spring 会自动发现并注册为 Bean。

---

### 第三步：创建配置文件

Spring Boot 使用 `application.yml`（或 `.properties`）管理配置。

为什么用 YAML 而不是 Properties？
- ✅ 层级更清晰
- ✅ 复杂配置更易读
- ✅ Spring Boot 官方推荐

```yaml
# MiniClaw 应用配置
# 这个文件定义了应用运行时的所有可配置参数

spring:
  application:
    name: miniclaw
  
  # 数据源配置 - 使用 H2 内存数据库（开发环境）
  # 生产环境建议切换到 PostgreSQL
  datasource:
    url: jdbc:h2:mem:miniclaw;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE
    driver-class-name: org.h2.Driver
    username: sa
    password: 
  
  # JPA/Hibernate 配置
  jpa:
    hibernate:
      ddl-auto: update  # 开发环境自动更新表结构
    show-sql: true      # 显示 SQL 语句，方便调试
    properties:
      hibernate:
        format_sql: true
  
  # H2 控制台（仅开发环境使用）
  h2:
    console:
      enabled: true
      path: /h2-console

# LLM 配置
# 我们会在后续章节中实现 LLM 客户端
llm:
  endpoint: https://api.deepseek.com
  api-key: ${LLM_API_KEY:your-api-key-here}
  model: deepseek-chat

# 服务端口
server:
  port: 8080
```

#### 配置项解释

| 配置项 | 作用 | 为什么这样配置 |
|--------|------|----------------|
| `spring.datasource.url` | 数据库连接地址 | H2 内存数据库，重启数据丢失，适合开发 |
| `spring.jpa.hibernate.ddl-auto` | 表结构自动更新 | `update` 会根据实体类自动创建/更新表 |
| `spring.jpa.show-sql` | 显示 SQL | 开发阶段方便调试，生产环境建议关闭 |
| `spring.h2.console.enabled` | H2 管理界面 | 访问 `/h2-console` 可以查看数据库 |
| `llm.api-key` | LLM API 密钥 | 使用环境变量，避免硬编码敏感信息 |

#### 关于环境变量

你可能注意到 `${LLM_API_KEY:your-api-key-here}` 这种写法。

这是 **环境变量注入**：
- 先尝试读取环境变量 `LLM_API_KEY`
- 如果不存在，使用默认值 `your-api-key-here`

**最佳实践**：敏感信息（API Key、密码）永远不要硬编码在代码里！

---

### 第四步：创建目录结构

现在创建各个分层目录：

```bash
# 在 backend/src/main/java/com/miniclaw 下创建目录
mkdir -p config
mkdir -p controller
mkdir -p service
mkdir -p repository
mkdir -p model
mkdir -p util
```

```
src/main/java/com/miniclaw/
├── MiniClawApplication.java    # 启动类
├── config/                     # 配置类（数据源、WebFlux 配置等）
├── controller/                 # HTTP 控制器（接收请求）
├── service/                    # 业务逻辑（核心代码）
├── repository/                 # 数据访问（操作数据库）
├── model/                      # 数据模型（Entity、DTO）
└── util/                       # 工具类
```

#### 为什么这样分层？

这是经典的 **分层架构**：

```
请求 → Controller → Service → Repository → 数据库
         ↓            ↓
       Model        Model
```

- **Controller**：只负责接收请求、返回响应，不写业务逻辑
- **Service**：核心业务逻辑，不关心 HTTP 细节
- **Repository**：只管数据库操作，不关心业务
- **Model**：数据载体，在各层之间传递

**好处**：
- 每层只做一件事（单一职责）
- 层与层之间松耦合
- 易于测试（可以单独测 Service）

---

### 第五步：验证项目可以运行

现在来验证项目是否能正常启动。

```bash
# 进入 backend 目录
cd miniclaw/backend

# 编译项目
./mvnw clean compile
```

**预期输出**：
```
[INFO] BUILD SUCCESS
[INFO] Total time:  X.XXX s
```

如果看到 `BUILD SUCCESS`，恭喜你，项目框架搭建完成！

---

### 动手实践

**任务**：搭建一个最小可运行的 Spring Boot 项目骨架

**步骤**：
1. 创建 `backend/pom.xml`（复制上面的内容）
2. 创建 `backend/src/main/java/com/miniclaw/MiniClawApplication.java`
3. 创建 `backend/src/main/resources/application.yml`
4. 创建各个分层目录（config/controller/service/repository/model/util）
5. 运行 `./mvnw clean compile`

**预期结果**：
- 编译成功，看到 `BUILD SUCCESS`
- 如果出现问题，检查：
  - Java 版本是否是 21+
  - 文件路径是否正确
  - XML/JSON 语法是否正确

**完整代码**：

项目结构：
```
backend/
├── pom.xml
├── src/
│   └── main/
│       ├── java/
│       │   └── com/
│       │       └── miniclaw/
│       │           ├── MiniClawApplication.java
│       │           ├── config/
│       │           ├── controller/
│       │           ├── service/
│       │           ├── repository/
│       │           ├── model/
│       │           └── util/
│       └── resources/
│           └── application.yml
```

---

### 自检：你真的掌握了吗？

**问题 1**：Spring Boot 的 `@SpringBootApplication` 注解做了什么？
> 如果答不上来，重读「第二步：创建应用启动类」

你的答案：
```


```

参考答案：
<details>
<summary>点击展开</summary>

`@SpringBootApplication` 是一个组合注解，相当于三个注解的效果：
1. `@SpringBootConfiguration` - 声明这是一个配置类
2. `@EnableAutoConfiguration` - 启用 Spring Boot 的自动配置
3. `@ComponentScan` - 自动扫描当前包及子包下的组件

这使得只要你的类在 `com.miniclaw` 或子包下，并加上 `@Component`/`@Service` 等注解，Spring 会自动发现并注册为 Bean。

</details>

---

**问题 2**：为什么要用 `application.yml` 而不是硬编码配置？
> 如果卡住，说明需要更多练习

你的答案：
```


```

参考答案：
<details>
<summary>点击展开</summary>

1. **环境隔离**：开发/测试/生产环境可以使用不同的配置
2. **敏感信息保护**：API Key、密码等不应硬编码，应使用环境变量注入
3. **可维护性**：配置集中管理，修改时不需要改代码
4. **12-Factor App 原则**：配置与代码分离是现代应用的最佳实践

</details>

---

**问题 3**（选做）：如果我想把数据库从 H2 切换到 PostgreSQL，需要改哪些地方？

你的答案：
```


```

参考答案：
<details>
<summary>点击展开</summary>

需要修改两处：

1. **pom.xml** - 添加 PostgreSQL 驱动：
```xml
<dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
    <scope>runtime</scope>
</dependency>
```

2. **application.yml** - 修改数据源配置：
```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/miniclaw
    driver-class-name: org.postgresql.Driver
    username: ${DB_USERNAME:postgres}
    password: ${DB_PASSWORD:postgres}
```

这就是 Spring Boot 的强大之处：切换数据库只需要改配置，不需要改代码！

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

- 我们学习了如何搭建一个标准的 Spring Boot 项目骨架
- 关键要点：
  - `pom.xml` 定义项目坐标和依赖
  - `@SpringBootApplication` 是应用的入口
  - `application.yml` 集中管理配置
  - 分层架构（controller/service/repository）各司其职
- 下一节我们将配置数据库连接和 ORM 映射

---

### 扩展阅读（可选）

- [Spring Boot 官方文档 - 项目结构](https://docs.spring.io/spring-boot/docs/current/reference/htmlsingle/#features.structuring-your-code)
- [Maven POM 文件详解](https://maven.apache.org/pom.html)
- [12-Factor App - 配置](https://12factor.net/zh_cn/config)

# 第3.4节：Spring Boot 项目骨架 - 模块划分与依赖管理

> **学习目标**：搭建 Spring Boot 项目骨架，理解模块划分和依赖管理
> **预计时长**：20 分钟
> **难度**：入门

### 前置知识检查

**你应该已经掌握**：
- [x] 3.1-3.3 环境和数据库已配置
- [ ] Maven 基本概念（groupId、artifactId、dependency）

**如果你不确定**：
- Maven 不熟 → 本节会详细讲解 pom.xml
- Spring Boot 没用过 → 本节从零开始搭建

**学习路径**：
- **路径A（有基础）**：直接跳到「创建 pom.xml」
- **路径B（从零开始）**：按顺序阅读全部内容

---

### 为什么需要项目骨架？

#### 真实场景

假设你要开发 MiniClaw，从零开始。

**没有项目骨架**：
1. 手动创建目录结构
2. 一个个下载 jar 包（200+ 个）
3. 手动配置 classpath
4. 版本冲突：A 需要 X 1.0，B 需要 X 2.0
5. 每次构建都要重新配置

**有了 Spring Boot 骨架**：
```bash
# 一行命令创建项目
curl https://start.spring.io/starter.tar.gz | tar -xzf -

# 或者用 IDEA 创建 Spring Boot 项目
```

所有依赖、配置、目录结构都自动生成，你只需要写业务代码。

#### 直觉理解

**项目骨架就像"精装房"**：
- 毛坯房 = 空目录
- 精装房 = Spring Boot 项目骨架
  - 水电（依赖管理）
  - 墙壁（目录结构）
  - 家具（配置文件）
  - 你只需要带行李（业务代码）

#### 技术定义

**Spring Boot**：简化 Spring 应用开发的框架，核心特性：
- **自动配置**：根据依赖自动配置 Spring
- **起步依赖**：一个依赖包含所有需要的东西
- **内嵌服务器**：不需要安装 Tomcat
- **生产就绪**：健康检查、指标监控等

---

### 第一步：理解项目结构

Spring Boot 项目标准结构：

```
backend/
├── pom.xml                          # Maven 配置文件
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/
│   │   │       └── miniclaw/
│   │   │           ├── MiniClawApplication.java  # 启动类
│   │   │           ├── config/                    # 配置类
│   │   │           ├── controller/                # HTTP 控制器
│   │   │           ├── service/                   # 业务逻辑
│   │   │           ├── repository/                # 数据访问
│   │   │           ├── model/                     # 数据模型
│   │   │           └── util/                      # 工具类
│   │   └── resources/
│   │       ├── application.yml       # 应用配置
│   │       └── db/
│   │           └── migration/        # Flyway 迁移脚本
│   └── test/
│       └── java/
│           └── com/
│               └── miniclaw/         # 测试代码
└── target/                           # 编译输出（自动生成）
```

#### 为什么这样分层？

这是经典的 **分层架构**：

```
┌─────────────────────────────────┐
│         Controller              │  ← 接收 HTTP 请求
├─────────────────────────────────┤
│          Service                │  ← 业务逻辑
├─────────────────────────────────┤
│        Repository               │  ← 数据访问
├─────────────────────────────────┤
│          Model                  │  ← 数据模型
└─────────────────────────────────┘
```

**每层职责**：
- **Controller**：接收请求、参数校验、返回响应
- **Service**：业务逻辑、事务管理
- **Repository**：数据库操作
- **Model**：数据定义

**好处**：
- 单一职责，易于测试
- 层与层之间松耦合
- 可以独立替换每一层

---

### 第二步：创建 pom.xml

在 `backend/` 目录创建 `pom.xml`：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 
         https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    
    <!-- 
      为什么需要 parent？
      Spring Boot BOM（Bill of Materials）预配置了所有依赖版本
      不需要自己管理版本冲突
    -->
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.4.3</version>
        <relativePath/>
    </parent>
    
    <!-- 项目坐标：Maven 世界的"身份证" -->
    <groupId>com.miniclaw</groupId>
    <artifactId>miniclaw</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <name>MiniClaw</name>
    <description>MiniClaw - A Minimal AI Agent Framework</description>
    
    <properties>
        <java.version>21</java.version>
    </properties>
    
    <dependencies>
        <!-- WebFlux：响应式 Web 框架 -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-webflux</artifactId>
        </dependency>
        
        <!-- JPA：ORM 框架 -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa</artifactId>
        </dependency>
        
        <!-- PostgreSQL 驱动 -->
        <dependency>
            <groupId>org.postgresql</groupId>
            <artifactId>postgresql</artifactId>
            <scope>runtime</scope>
        </dependency>
        
        <!-- Flyway：数据库迁移 -->
        <dependency>
            <groupId>org.flywaydb</groupId>
            <artifactId>flyway-core</artifactId>
        </dependency>
        <dependency>
            <groupId>org.flywaydb</groupId>
            <artifactId>flyway-database-postgresql</artifactId>
        </dependency>
        
        <!-- Lombok：减少样板代码 -->
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
        <dependency>
            <groupId>io.projectreactor</groupId>
            <artifactId>reactor-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
    
    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
    
</project>
```

#### pom.xml 核心概念

| 元素 | 作用 | 示例 |
|------|------|------|
| `groupId` | 组织/公司标识 | `com.miniclaw` |
| `artifactId` | 项目名称 | `miniclaw` |
| `version` | 版本号 | `0.0.1-SNAPSHOT` |
| `parent` | 继承父 POM | `spring-boot-starter-parent` |
| `dependencies` | 依赖列表 | WebFlux、JPA 等 |
| `build.plugins` | 构建插件 | Spring Boot Maven 插件 |

#### 为什么选 WebFlux 而不是 WebMVC？

| 特性 | WebMVC | WebFlux |
|------|--------|---------|
| 模型 | 同步阻塞 | 异步非阻塞 |
| 线程 | 每个请求一个线程 | 事件循环，少量线程 |
| 流式响应 | 需要额外配置 | 原生支持 |
| WebSocket | 需要额外配置 | 原生支持 |
| 适用场景 | 传统 CRUD | 高并发、流式、实时 |

**MiniClaw 选 WebFlux 的原因**：
- AI 响应需要流式输出（一个字一个字显示）
- WebSocket 双向通信
- 更高的并发能力

---

### 第三步：创建启动类

在 `src/main/java/com/miniclaw/` 创建 `MiniClawApplication.java`：

```java
package com.miniclaw;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * MiniClaw 应用程序入口
 * 
 * 这是整个 AI Agent 框架的起点。
 * Spring Boot 会自动扫描当前包及子包下的所有组件。
 * 
 * @SpringBootApplication 是一个组合注解，包含：
 * - @SpringBootConfiguration：这是一个配置类
 * - @EnableAutoConfiguration：启用自动配置
 * - @ComponentScan：扫描当前包及子包的组件
 */
@SpringBootApplication
public class MiniClawApplication {

    public static void main(String[] args) {
        SpringApplication.run(MiniClawApplication.class, args);
    }
}
```

#### @SpringBootApplication 做了什么？

这是一个**组合注解**：

```java
@SpringBootApplication =
    @SpringBootConfiguration    // 等同于 @Configuration
    + @EnableAutoConfiguration  // 自动配置
    + @ComponentScan             // 组件扫描
```

**自动配置的工作原理**：
1. 扫描 classpath 下的依赖
2. 根据依赖自动配置 Spring Bean
3. 例如：检测到 WebFlux → 自动配置 WebServer

---

### 第四步：创建分层目录

```bash
# 创建各层目录
cd backend/src/main/java/com/miniclaw
mkdir -p config controller service repository model util
```

**各目录作用**：

| 目录 | 作用 | 示例类 |
|------|------|--------|
| `config/` | 配置类 | `WebConfig`、`LlmConfig` |
| `controller/` | HTTP 控制器 | `SessionController` |
| `service/` | 业务逻辑 | `SessionService`、`AgentService` |
| `repository/` | 数据访问 | `SessionRepository` |
| `model/` | 数据模型 | `Session`、`Message` |
| `util/` | 工具类 | `JsonUtils`、`DateUtils` |

---

### 第五步：编译验证

```bash
cd backend

# 编译项目
./mvnw clean compile

# 运行测试
./mvnw test

# 打包
./mvnw package
```

**预期输出**：
```
[INFO] BUILD SUCCESS
[INFO] Total time:  X.XXX s
```

---

### 动手实践

**任务**：搭建 MiniClaw 后端项目骨架

**步骤**：
1. 创建 `backend/pom.xml`
2. 创建 `MiniClawApplication.java`
3. 创建分层目录
4. 运行 `./mvnw clean compile` 验证

**预期结果**：
- 编译成功
- 目录结构符合规范

---

### 自检：你真的掌握了吗？

**问题 1**：为什么选择 WebFlux 而不是 WebMVC？
> 如果答不上来，重读「为什么选 WebFlux」

你的答案：
```


```

参考答案：
<details>
<summary>点击展开</summary>

选择 WebFlux 的原因：
1. **流式响应**：AI 输出需要逐字显示，WebFlux 原生支持
2. **WebSocket**：双向实时通信，不需要额外配置
3. **高并发**：异步非阻塞，少量线程处理大量请求
4. **响应式**：Reactor 提供强大的流式处理能力

WebMVC 是同步阻塞模型，每个请求占用一个线程，不适合流式和实时场景。

</details>

---

**问题 2**：`@SpringBootApplication` 注解做了什么？
> 如果卡住，说明需要更多练习

你的答案：
```


```

参考答案：
<details>
<summary>点击展开</summary>

`@SpringBootApplication` 是三个注解的组合：
1. `@SpringBootConfiguration`：声明这是一个配置类
2. `@EnableAutoConfiguration`：启用自动配置（Spring Boot 的核心魔法）
3. `@ComponentScan`：扫描当前包及子包的组件

**效果**：
- 自动配置 Spring 容器
- 自动注册 `@Component`、`@Service`、`@Repository` 等注解的类
- 根据依赖自动配置相关功能

</details>

---

**问题 3**（选做）：分层架构的好处是什么？

你的答案：
```


```

参考答案：
<details>
<summary>点击展开</summary>

分层架构的好处：
1. **单一职责**：每层只做一件事
2. **松耦合**：层与层之间通过接口通信，可以独立替换
3. **易于测试**：可以单独测试每一层
4. **可维护性**：修改一层不影响其他层
5. **团队协作**：不同人负责不同层

**示例**：
- 换数据库：只需改 Repository 层
- 换 API 格式：只需改 Controller 层
- 换业务逻辑：只需改 Service 层

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

- 我们搭建了 Spring Boot 项目骨架
- 关键要点：
  - `pom.xml` 管理依赖和构建
  - WebFlux 适合流式和实时场景
  - 分层架构：Controller → Service → Repository → Model
  - `@SpringBootApplication` 是应用的入口
- 下一节我们将学习配置管理

---

### 扩展阅读（可选）

- [Spring Boot 官方文档](https://docs.spring.io/spring-boot/docs/current/reference/html/)
- [WebFlux vs WebMVC](https://docs.spring.io/spring-framework/docs/current/reference/html/web-reactive.html#webflux)
- [分层架构最佳实践](https://www.baeldung.com/java-dao-pattern)

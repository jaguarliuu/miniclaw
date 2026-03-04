# 第3章：开发环境与基础底座

## 第3.2节：数据库配置与 ORM 映射

> **学习目标**：配置数据库连接，创建实体类和 Repository，理解 ORM 映射原理
> **预计时长**：40 分钟
> **难度**：入门

### 前置知识检查

**你应该已经掌握**：
- [x] 第3.1节：项目框架已搭建完成
- [ ] SQL 基础（SELECT、INSERT、UPDATE、DELETE）
- [ ] 了解什么是数据库表

**如果你不确定**：
- SQL 不熟 → 本节会讲解必要的 SQL 概念
- 没用过 JPA → 本节从零开始讲

---

### 为什么需要数据库？

假设你正在和 AI Agent 对话。

你：「帮我写一个 Python 爬虫」
Agent：「好的，我来帮你写...」

第二天，你打开电脑，想继续昨天的对话。

**问题来了：昨天的对话去哪了？**

如果没有数据库：
- ❌ 关闭页面，对话消失
- ❌ 无法查看历史记录
- ❌ 无法分析用户行为
- ❌ 无法统计成本

**有了数据库：**
- ✅ 对话永久保存
- ✅ 随时恢复历史会话
- ✅ 可以构建 Memory 系统
- ✅ 可以追踪 token 消耗

**数据库是 AI Agent 的「记忆中枢」。**

---

### ORM：让 Java 对象变成数据库表

#### 直觉理解

想象你有一个 Excel 表格：
- 每个标签页是一张「表」
- 每一行是一条「记录」
- 每一列是一个「字段」

**ORM（Object-Relational Mapping）就是 Java 对象和数据库表之间的「翻译官」。**

```
Java 对象          数据库表
    Session    →    sessions 表
    Message    →    messages 表
    
Java 字段          数据库列
    String id     →    id VARCHAR
    String title  →    title VARCHAR
```

你只需要写 Java 代码，ORM 会自动帮你生成 SQL。

#### 技术定义

**JPA（Java Persistence API）**：Java 的持久化标准，定义了一套 ORM 规范。

**Hibernate**：JPA 的实现者，真正干活的那个。

**Spring Data JPA**：在 Hibernate 之上，提供更简洁的 API。

```
你的代码
    ↓ 调用
Spring Data JPA（简化 API）
    ↓ 使用
Hibernate（JPA 实现）
    ↓ 生成
SQL 语句
    ↓ 执行
数据库
```

---

### 第一步：配置数据源

在 `application.yml` 中，我们已经配置了 H2 数据库：

```yaml
spring:
  datasource:
    url: jdbc:h2:mem:miniclaw;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE
    driver-class-name: org.h2.Driver
    username: sa
    password: 
  
  jpa:
    hibernate:
      ddl-auto: update  # 自动更新表结构
    show-sql: true      # 显示 SQL 语句
```

#### 配置项解释

| 配置 | 作用 | 为什么这样配置 |
|------|------|----------------|
| `url: jdbc:h2:mem:miniclaw` | 内存数据库 | 开发阶段不需要安装数据库，重启数据丢失适合测试 |
| `ddl-auto: update` | 自动更新表结构 | 实体类改了，表结构自动跟着改，不用手写 DDL |
| `show-sql: true` | 显示 SQL | 开发时看到执行的 SQL，方便调试 |

#### 常见误区

> ❌ **误区**：`ddl-auto: update` 生产环境也用
> 
> ✅ **正确理解**：生产环境应该用 `validate` 或 `none`，通过 Flyway/Liquibase 管理迁移脚本。

---

### 第二步：创建实体类

实体类 = 数据库表的 Java 表示。

#### Session 实体

```java
package com.miniclaw.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 会话实体
 * 
 * 为什么需要 Session 实体？
 * - AI Agent 的对话是有状态的，需要持久化保存
 * - 一个 Session 代表一次完整的对话会话
 * - 后续的 Memory 系统需要基于 Session 进行检索
 */
@Entity                     // 标记这是一个 JPA 实体
@Table(name = "sessions")   // 指定表名
@Data                       // Lombok: 自动生成 getter/setter
@NoArgsConstructor           // JPA 要求无参构造器
@AllArgsConstructor          // 全参构造器
public class Session {
    
    /**
     * 会话唯一标识
     * 
     * 为什么用 UUID 而不是自增 ID？
     * - UUID 在分布式系统中不会冲突
     * - 可以在客户端生成，不需要等待数据库返回
     * - 更安全，不会被遍历猜测
     */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private String id;
    
    /**
     * 会话标题（可选）
     */
    @Column(name = "title")
    private String title;
    
    /**
     * 用户标识
     */
    @Column(name = "user_id", nullable = false)
    private String userId;
    
    /**
     * Agent 标识
     */
    @Column(name = "agent_id")
    private String agentId;
    
    /**
     * 会话状态
     */
    @Enumerated(EnumType.STRING)  // 存储枚举名称而非序号
    @Column(name = "status", nullable = false)
    private SessionStatus status = SessionStatus.ACTIVE;
    
    /**
     * 创建时间（自动填充）
     */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    /**
     * 更新时间（自动更新）
     */
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    
    /**
     * 软删除标记
     * 
     * 为什么用软删除而不是物理删除？
     * - 用户可能误删，需要恢复功能
     * - 数据分析需要历史数据
     * - 关联数据不会成为孤儿
     */
    @Column(name = "deleted", nullable = false)
    private Boolean deleted = false;
    
    /**
     * 扩展元数据（JSON 格式）
     */
    @Column(name = "metadata", columnDefinition = "TEXT")
    private String metadata;
}
```

#### 关键注解解释

| 注解 | 作用 | 使用场景 |
|------|------|----------|
| `@Entity` | 标记为 JPA 实体 | 每个实体类必须有 |
| `@Table` | 指定表名 | 默认是类名，建议显式指定 |
| `@Id` | 标记主键 | 每个实体必须有一个 |
| `@GeneratedValue` | 主键生成策略 | UUID/IDENTITY/SEQUENCE |
| `@Column` | 配置列属性 | 可以指定长度、是否可空等 |
| `@Enumerated` | 枚举存储方式 | STRING 存名称，ORDINAL 存序号 |
| `@CreationTimestamp` | 自动填充创建时间 | 插入时自动设置 |
| `@UpdateTimestamp` | 自动更新时间 | 每次更新自动更新 |

#### Message 实体

```java
package com.miniclaw.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "messages")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Message {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    
    /**
     * 所属会话
     * 
     * @ManyToOne: 多条消息属于一个会话
     * FetchType.LAZY: 懒加载，只有访问时才查询
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private Session session;
    
    /**
     * 消息角色
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private MessageRole role;
    
    /**
     * 消息内容
     */
    @Column(name = "content", columnDefinition = "TEXT", nullable = false)
    private String content;
    
    /**
     * Token 数量（用于成本计算）
     */
    @Column(name = "token_count")
    private Integer tokenCount;
    
    /**
     * 模型名称
     */
    @Column(name = "model")
    private String model;
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "metadata", columnDefinition = "TEXT")
    private String metadata;
}
```

#### 关系映射

```
Session (1) ←→ (N) Message

一个会话包含多条消息
一条消息属于一个会话
```

```java
// 在 Message 中
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "session_id")
private Session session;

// 这会生成外键约束
// ALTER TABLE messages ADD CONSTRAINT fk_session 
//   FOREIGN KEY (session_id) REFERENCES sessions(id)
```

---

### 第三步：创建 Repository

Repository = 数据访问层，封装数据库操作。

#### SessionRepository

```java
package com.miniclaw.repository;

import com.miniclaw.model.Session;
import com.miniclaw.model.SessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SessionRepository extends JpaRepository<Session, String> {
    
    /**
     * 根据用户 ID 查找所有会话
     * 
     * Spring Data JPA 自动生成 SQL：
     * SELECT * FROM sessions WHERE user_id = ? AND deleted = false
     */
    List<Session> findByUserIdAndDeletedFalse(String userId);
    
    /**
     * 根据 ID 和用户 ID 查找会话
     * 
     * 安全性：防止用户 A 访问用户 B 的会话
     */
    Optional<Session> findByIdAndUserIdAndDeletedFalse(String id, String userId);
    
    /**
     * 统计用户的会话数量
     */
    long countByUserIdAndDeletedFalse(String userId);
}
```

#### 方法命名规则

Spring Data JPA 根据方法名自动生成查询：

| 方法名 | 生成的 SQL |
|--------|-----------|
| `findByUserId` | `WHERE user_id = ?` |
| `findByUserIdAndDeletedFalse` | `WHERE user_id = ? AND deleted = false` |
| `countByUserId` | `SELECT COUNT(*) WHERE user_id = ?` |
| `deleteByUserId` | `DELETE WHERE user_id = ?` |
| `existsByUserId` | `SELECT EXISTS(SELECT 1 WHERE user_id = ?)` |

#### JpaRepository 提供的方法

```java
// 不需要定义，JpaRepository 自动提供
Session session = sessionRepository.save(session);   // 保存
Optional<Session> opt = sessionRepository.findById("id");  // 查找
sessionRepository.delete(session);                    // 删除
List<Session> all = sessionRepository.findAll();      // 查全部
long count = sessionRepository.count();               // 统计
```

---

### 第四步：验证配置

启动应用，查看表是否自动创建：

```bash
cd backend
./mvnw spring-boot:run
```

**预期输出**：
```
Hibernate: create table sessions (id varchar(255) not null, ...)
Hibernate: create table messages (id varchar(255) not null, ...)
```

访问 H2 控制台：http://localhost:8080/h2-console

- JDBC URL: `jdbc:h2:mem:miniclaw`
- User Name: `sa`
- Password: （留空）

---

### 动手实践

**任务**：创建一个简单的 Session 并保存到数据库

**步骤**：
1. 创建一个测试类 `SessionRepositoryTest`
2. 注入 `SessionRepository`
3. 创建并保存一个 Session
4. 查询验证

**完整代码**：

```java
package com.miniclaw.repository;

import com.miniclaw.model.Session;
import com.miniclaw.model.SessionStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest  // 只测试 JPA 组件
class SessionRepositoryTest {
    
    @Autowired
    private SessionRepository sessionRepository;
    
    @Test
    void shouldSaveAndFindSession() {
        // 创建会话
        Session session = new Session();
        session.setUserId("user-123");
        session.setTitle("测试会话");
        session.setStatus(SessionStatus.ACTIVE);
        
        // 保存
        Session saved = sessionRepository.save(session);
        
        // 验证
        assertNotNull(saved.getId());
        assertEquals("user-123", saved.getUserId());
        
        // 查询
        var found = sessionRepository.findById(saved.getId());
        assertTrue(found.isPresent());
        assertEquals("测试会话", found.get().getTitle());
    }
}
```

**预期结果**：
- 测试通过
- 控制台显示 INSERT SQL 语句

---

### 自检：你真的掌握了吗？

**问题 1**：为什么用 ORM 而不是手写 SQL？
> 如果答不上来，重读「ORM：让 Java 对象变成数据库表」

你的答案：
```


```

参考答案：
<details>
<summary>点击展开</summary>

1. **开发效率**：不需要手写 CRUD SQL，自动生成
2. **类型安全**：编译时检查字段类型，减少运行时错误
3. **数据库无关**：切换数据库只需要改配置，不需要改代码
4. **缓存支持**：一级缓存、二级缓存自动管理
5. **关系映射**：自动处理外键关系，不用手动 JOIN

但 ORM 也有缺点：
- 复杂查询性能不如手写 SQL
- 学习曲线
- 可能生成低效的 SQL

</details>

---

**问题 2**：`@ManyToOne(fetch = FetchType.LAZY)` 是什么意思？
> 如果卡住，说明需要更多练习

你的答案：
```


```

参考答案：
<details>
<summary>点击展开</summary>

- `@ManyToOne`：定义多对一关系（多条消息属于一个会话）
- `fetch = FetchType.LAZY`：懒加载策略
  - **懒加载**：只有真正访问 `message.getSession()` 时才查询数据库
  - **立即加载（EAGER）**：查询 Message 时自动 JOIN 查询 Session

**为什么用 LAZY？**
- 避免 N+1 查询问题：查 N 条消息不会触发 N 次额外查询
- 按需加载，减少不必要的数据传输

</details>

---

**问题 3**（选做）：如何防止用户 A 访问用户 B 的会话？

你的答案：
```


```

参考答案：
<details>
<summary>点击展开</summary>

**方法一：查询时过滤**
```java
// 同时匹配 id 和 userId
Optional<Session> findByIdAndUserIdAndDeletedFalse(String id, String userId);
```

**方法二：权限拦截器**
```java
@PreAuthorize("@sessionService.isOwner(#id, authentication.name)")
public Session getSession(String id) { ... }
```

**方法三：行级安全**
- PostgreSQL 支持 Row Level Security
- 数据库层面自动过滤

推荐方法一，简单可靠。生产环境可以结合方法二。

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

- 我们学习了 JPA/Hibernate ORM 框架
- 创建了 Session 和 Message 实体类
- 定义了 Repository 接口进行数据访问
- 关键要点：
  - 实体类用 `@Entity` 注解
  - 关系用 `@ManyToOne`/`@OneToMany` 映射
  - Repository 继承 `JpaRepository` 获得基础 CRUD
  - 方法名自动推导查询
- 下一节我们将实现 LLM 客户端

---

### 扩展阅读（可选）

- [Spring Data JPA 官方文档](https://docs.spring.io/spring-data/jpa/docs/current/reference/html/)
- [Hibernate 注解详解](https://docs.jboss.org/hibernate/orm/6.4/userguide/html_single/Hibernate_User_Guide.html#annotations)
- [JPA 常见问题](https://www.baeldung.com/spring-data-jpa-common-problems)

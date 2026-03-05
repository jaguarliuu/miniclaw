# 第3.3节：数据库版本控制 - Flyway 迁移脚本

> **学习目标**：使用 Flyway 管理数据库版本，编写迁移脚本定义表结构
> **预计时长**：25 分钟
> **难度**：入门

### 前置知识检查

**你应该已经掌握**：
- [x] 3.1 开发环境准备
- [x] 3.2 Docker Compose 已配置 PostgreSQL
- [ ] 基本的 SQL 语法

**如果你不确定**：
- SQL 不熟 → 本节会详细讲解每个语句
- 没用过 Flyway → 本节从零开始讲

**学习路径**：
- **路径A（有基础）**：直接跳到「编写迁移脚本」
- **路径B（从零开始）**：按顺序阅读全部内容

---

### 为什么需要 Flyway？

#### 真实场景

假设你和队友小王一起开发 MiniClaw。

**没有版本控制**：
1. 你在本地创建了 `sessions` 表
2. 小王也创建了 `sessions` 表，但字段不一样
3. 你加了 `user_id` 索引，小王忘了加
4. 上线时，你不知道执行了哪些 SQL，哪些没执行
5. 生产环境表结构和开发环境不一致，程序崩溃

**有了 Flyway**：
```
db/migration/
├── V1__create_sessions_table.sql   # 你提交的
├── V2__create_messages_table.sql   # 小王提交的
└── V3__add_user_index.sql          # 你后来加的索引
```

每个人 pull 代码后，Flyway 自动执行**还没执行过**的迁移脚本，所有人数据库结构**完全一致**。

#### 直觉理解

**Flyway 就像是"数据库的 Git"**：
- Git 管理代码版本，Flyway 管理数据库版本
- 每个 SQL 文件就是一个"commit"
- Flyway 记录哪些 SQL 已执行，哪些还没执行

**对应关系**：
- Git 仓库 = 数据库
- Git commit = Flyway 迁移脚本
- `git log` = `flyway_schema_history` 表
- `git pull` = `flyway migrate`

#### 技术定义

**Flyway**：数据库迁移管理工具，核心功能：
1. **版本控制**：追踪哪些 SQL 已执行
2. **自动迁移**：启动时自动执行未执行的 SQL
3. **团队协作**：所有人使用相同的数据库结构
4. **回滚支持**：可以撤销错误的迁移（付费版）

---

### 第一步：创建迁移目录

在 Spring Boot 项目中，Flyway 迁移脚本放在：

```
backend/src/main/resources/db/migration/
```

```bash
# 创建迁移目录
mkdir -p backend/src/main/resources/db/migration
```

---

### 第二步：编写第一个迁移脚本

创建 `V1__create_sessions_table.sql`：

```sql
-- Flyway 迁移脚本：创建 sessions 表
-- 
-- 文件命名规则：V{版本号}__{描述}.sql
-- - V：固定前缀
-- - 1：版本号（数字，递增）
-- - __：双下划线分隔
-- - create_sessions_table：描述（下划线分隔）
-- - .sql：文件后缀
--
-- Flyway 会按照版本号顺序执行迁移脚本

-- 创建 sessions 表
-- 存储 AI Agent 的会话信息
CREATE TABLE sessions (
    id VARCHAR(36) PRIMARY KEY,
    title VARCHAR(255),
    user_id VARCHAR(255) NOT NULL,
    agent_id VARCHAR(255),
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    metadata TEXT
);

-- 创建索引
-- user_id 是高频查询字段，需要索引
CREATE INDEX idx_sessions_user_id ON sessions(user_id);
CREATE INDEX idx_sessions_status ON sessions(status);
CREATE INDEX idx_sessions_created_at ON sessions(created_at);

-- 添加注释
COMMENT ON TABLE sessions IS 'AI Agent 会话表';
COMMENT ON COLUMN sessions.id IS '会话唯一标识（UUID）';
COMMENT ON COLUMN sessions.title IS '会话标题';
COMMENT ON COLUMN sessions.user_id IS '用户标识';
COMMENT ON COLUMN sessions.agent_id IS 'Agent 标识';
COMMENT ON COLUMN sessions.status IS '会话状态：ACTIVE/ARCHIVED/ENDED';
COMMENT ON COLUMN sessions.created_at IS '创建时间';
COMMENT ON COLUMN sessions.updated_at IS '更新时间';
COMMENT ON COLUMN sessions.deleted IS '软删除标记';
COMMENT ON COLUMN sessions.metadata IS '扩展元数据（JSON）';
```

#### 文件命名规则

**必须严格遵守**：

```
V{版本号}__{描述}.sql
│    │       │      │
│    │       │      └── 文件后缀
│    │       └──────── 描述（用下划线分隔单词）
│    └──────────────── 双下划线（必须是两个下划线！）
└───────────────────── 版本号（数字，按顺序递增）
```

**正确示例**：
- `V1__create_sessions_table.sql` ✅
- `V2__create_messages_table.sql` ✅
- `V3__add_user_index.sql` ✅

**错误示例**：
- `v1__create_sessions.sql` ❌（V 必须大写）
- `V1_create_sessions.sql` ❌（必须是双下划线）
- `01_create_sessions.sql` ❌（必须以 V 开头）

---

### 第三步：编写第二个迁移脚本

创建 `V2__create_messages_table.sql`：

```sql
-- Flyway 迁移脚本：创建 messages 表
-- 
-- 版本号：2（在 V1 之后执行）

-- 创建 messages 表
-- 存储 AI Agent 对话中的每条消息
CREATE TABLE messages (
    id VARCHAR(36) PRIMARY KEY,
    session_id VARCHAR(36) NOT NULL,
    role VARCHAR(50) NOT NULL,
    content TEXT NOT NULL,
    token_count INTEGER,
    model VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    metadata TEXT,
    
    -- 外键约束：消息必须属于有效的会话
    CONSTRAINT fk_messages_session 
        FOREIGN KEY (session_id) 
        REFERENCES sessions(id) 
        ON DELETE CASCADE
);

-- 创建索引
-- session_id 是高频查询字段，需要索引
CREATE INDEX idx_messages_session_id ON messages(session_id);
CREATE INDEX idx_messages_role ON messages(role);
CREATE INDEX idx_messages_created_at ON messages(created_at);

-- 添加注释
COMMENT ON TABLE messages IS 'AI Agent 消息表';
COMMENT ON COLUMN messages.id IS '消息唯一标识（UUID）';
COMMENT ON COLUMN messages.session_id IS '所属会话ID';
COMMENT ON COLUMN messages.role IS '消息角色：USER/ASSISTANT/SYSTEM/TOOL';
COMMENT ON COLUMN messages.content IS '消息内容';
COMMENT ON COLUMN messages.token_count IS 'Token 数量（用于成本计算）';
COMMENT ON COLUMN messages.model IS '生成该消息的模型名称';
COMMENT ON COLUMN messages.created_at IS '创建时间';
COMMENT ON COLUMN messages.metadata IS '扩展元数据（JSON）';
```

#### 外键约束

```sql
CONSTRAINT fk_messages_session 
    FOREIGN KEY (session_id) 
    REFERENCES sessions(id) 
    ON DELETE CASCADE
```

**含义**：
- `session_id` 必须引用有效的 `sessions.id`
- `ON DELETE CASCADE`：删除会话时，自动删除所有关联消息

---

### 第四步：创建向量索引表

创建 `V3__create_memory_chunks_table.sql`：

```sql
-- Flyway 迁移脚本：创建 memory_chunks 表（向量检索）
-- 
-- 版本号：3（在 V2 之后执行）
-- 这个表用于 Memory 系统的语义检索

-- 创建 memory_chunks 表
-- 存储分块后的记忆向量和原始文本
CREATE TABLE memory_chunks (
    id VARCHAR(36) PRIMARY KEY,
    source_type VARCHAR(50) NOT NULL,
    source_path VARCHAR(500) NOT NULL,
    chunk_index INTEGER NOT NULL,
    content TEXT NOT NULL,
    embedding vector(1536),  -- OpenAI embedding 维度是 1536
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    -- 唯一约束：同一来源的同一块只能存在一次
    CONSTRAINT uk_memory_chunks_source UNIQUE (source_type, source_path, chunk_index)
);

-- 创建向量索引（使用 pgvector 的 IVFFlat 索引）
-- vector_cosine_ops：余弦相似度
-- lists = 100：聚类中心数量（数据量大时可以增加）
CREATE INDEX idx_memory_chunks_embedding ON memory_chunks 
    USING ivfflat (embedding vector_cosine_ops)
    WITH (lists = 100);

-- 创建普通索引
CREATE INDEX idx_memory_chunks_source ON memory_chunks(source_type, source_path);

-- 添加注释
COMMENT ON TABLE memory_chunks IS 'Memory 分块向量索引表';
COMMENT ON COLUMN memory_chunks.id IS '分块唯一标识（UUID）';
COMMENT ON COLUMN memory_chunks.source_type IS '来源类型：MEMORY/DIARY/SKILL';
COMMENT ON COLUMN memory_chunks.source_path IS '来源路径（文件路径）';
COMMENT ON COLUMN memory_chunks.chunk_index IS '分块序号';
COMMENT ON COLUMN memory_chunks.content IS '分块文本内容';
COMMENT ON COLUMN memory_chunks.embedding IS '向量嵌入（1536维）';
COMMENT ON COLUMN memory_chunks.created_at IS '创建时间';
```

**注意**：`vector(1536)` 是 pgvector 提供的向量类型，用于存储 1536 维向量（OpenAI embedding 的维度）。

---

### 第五步：配置 Flyway

在 `application.yml` 中添加 Flyway 配置：

```yaml
spring:
  # 数据源配置
  datasource:
    url: jdbc:postgresql://localhost:5432/miniclaw
    driver-class-name: org.postgresql.Driver
    username: miniclaw
    password: miniclaw123
  
  # JPA 配置
  jpa:
    hibernate:
      ddl-auto: validate  # 使用 Flyway 管理表结构，JPA 只验证
    show-sql: true
  
  # Flyway 配置
  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: true
    validate-on-migrate: true
```

#### 配置项解释

| 配置 | 作用 | 为什么这样配置 |
|------|------|----------------|
| `flyway.enabled: true` | 启用 Flyway | 必须开启 |
| `flyway.locations` | 迁移脚本位置 | 默认就是 `db/migration` |
| `baseline-on-migrate: true` | 允许在非空数据库上执行 | 已有数据库时需要 |
| `ddl-auto: validate` | JPA 只验证，不自动创建表 | 避免和 Flyway 冲突 |

#### 常见误区

> ❌ **误区**：同时用 `ddl-auto: update` 和 Flyway
> 
> ✅ **正确理解**：Flyway 管理表结构，JPA 只负责 ORM 映射。`ddl-auto` 应设为 `validate` 或 `none`。

---

### 第六步：验证迁移

启动应用后，Flyway 会自动执行迁移。查看 `flyway_schema_history` 表：

```sql
SELECT * FROM flyway_schema_history ORDER BY installed_rank;
```

**预期输出**：
```
 installed_rank | version | description              | type | script                              | success
----------------+---------+--------------------------+------+-------------------------------------+---------
              1 | 1       | create sessions table    | SQL  | V1__create_sessions_table.sql       | t
              2 | 2       | create messages table    | SQL  | V2__create_messages_table.sql       | t
              3 | 3       | create memory chunks tab | SQL  | V3__create_memory_chunks_table.sql  | t
```

---

### 动手实践

**任务**：创建 MiniClaw 数据库表结构

**步骤**：
1. 创建迁移目录 `db/migration`
2. 编写 V1、V2、V3 迁移脚本
3. 配置 Flyway
4. 启动应用，验证表已创建

**预期结果**：
- `flyway_schema_history` 表有 3 条记录
- `sessions`、`messages`、`memory_chunks` 表已创建
- 所有索引和约束都正确

---

### 自检：你真的掌握了吗？

**问题 1**：Flyway 迁移脚本的命名规则是什么？为什么必须严格遵守？
> 如果答不上来，重读「文件命名规则」

你的答案：
```


```

参考答案：
<details>
<summary>点击展开</summary>

命名规则：`V{版本号}__{描述}.sql`

**各部分含义**：
- `V`：固定前缀，必须大写
- 版本号：数字，按顺序递增（1, 2, 3...）
- `__`：双下划线分隔符
- 描述：用下划线分隔单词
- `.sql`：文件后缀

**为什么必须严格遵守**：
- Flyway 根据文件名判断执行顺序
- 命名错误会导致迁移失败
- 版本号不连续会导致中间的脚本被跳过

</details>

---

**问题 2**：为什么要用 `ddl-auto: validate` 而不是 `update`？
> 如果卡住，说明需要更多练习

你的答案：
```


```

参考答案：
<details>
<summary>点击展开</summary>

- `ddl-auto: update`：Hibernate 自动更新表结构
- `ddl-auto: validate`：Hibernate 只验证表结构是否匹配实体

**为什么用 validate**：
1. **避免冲突**：Flyway 和 Hibernate 同时管理表结构会冲突
2. **版本控制**：Flyway 提供完整的迁移历史记录
3. **团队协作**：迁移脚本可以 code review，自动生成的表结构无法审查
4. **生产安全**：`update` 在生产环境可能导致意外修改

</details>

---

**问题 3**（选做）：如果迁移脚本执行失败，如何处理？

你的答案：
```


```

参考答案：
<details>
<summary>点击展开</summary>

**步骤**：
1. **查看错误日志**：Flyway 会记录失败原因
2. **修复 SQL 脚本**：修正错误
3. **手动清理**（如果部分执行）：
   ```sql
   -- 删除失败的记录
   DELETE FROM flyway_schema_history WHERE success = false;
   ```
4. **重新执行**：`flyway migrate`

**预防措施**：
- 在开发环境充分测试迁移脚本
- 使用事务（部分数据库支持）
- 重要变更先在测试环境验证

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

- 我们学习了 Flyway 数据库版本控制
- 创建了 sessions、messages、memory_chunks 三张表
- 关键要点：
  - 迁移脚本命名规则：`V{版本号}__{描述}.sql`
  - Flyway 自动追踪已执行的脚本
  - `flyway_schema_history` 记录迁移历史
  - JPA 的 `ddl-auto` 应设为 `validate`
- 下一节我们将搭建 Spring Boot 项目骨架

---

### 扩展阅读（可选）

- [Flyway 官方文档](https://flywaydb.org/documentation/)
- [Spring Boot 集成 Flyway](https://docs.spring.io/spring-boot/docs/current/reference/html/howto.html#howto.data-initialization.migration-tool.flyway)
- [pgvector 文档](https://github.com/pgvector/pgvector)

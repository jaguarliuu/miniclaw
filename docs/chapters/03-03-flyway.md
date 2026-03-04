# 第3.3节：数据库版本控制 - Flyway 迁移脚本

> **学习目标**：使用 Flyway 管理数据库版本，编写迁移脚本定义表结构
> **预计时长**：25 分钟
> **难度**：入门

### 前置知识检查

**你应该已经掌握**：
- [x] 3.1 开发环境准备
- [x] 3.2 Docker Compose 编排基础设施
- [ ] 基本的 SQL 语法

**如果你不确定**：
- SQL 不熟 → 本节会详细讲解每个语句
- 没用过 Flyway → 本节从零开始讲

**学习路径**：
- **路径A（有基础）**：直接跳到「编写迁移脚本」
- **路径B（从零开始）**：按顺序阅读全部内容

---

### 为什么需要数据库版本控制？

#### 真实场景

你和队友小王一起开发 MiniClaw。

**第一天**：
- 你创建了 `sessions` 表
- 提交代码到 Git

**第二天**：
- 小王 pull 了代码
- 启动项目，报错：`Table "sessions" doesn't exist`
- 小王问你："表结构在哪？"
- 你说："我手动在数据库里建的，没提交到 Git"

**一周后**：
- 你给 `sessions` 表加了 `agent_id` 字段
- 忘记告诉小王
- 小王启动项目，报错：`Unknown column "agent_id"`

**一个月后**：
- 生产环境要部署
- 你不记得执行过哪些 SQL 脚本
- 只能手动对比开发和生产的表结构
- 漏了一个字段，线上事故

**有了 Flyway**：
```
版本 1：创建 sessions 表
版本 2：给 sessions 表加 agent_id 字段
版本 3：创建 messages 表
...
```

- 所有变更都有记录，可追溯
- 新环境自动执行所有迁移脚本
- 团队成员 pull 代码后，数据库自动同步

#### 直觉理解

**Flyway 就像是"数据库的 Git"**：
- Git 管理代码版本，Flyway 管理数据库版本
- 每次数据库变更都是一个"迁移脚本"（类似于 Git commit）
- 新环境自动从"版本 1"执行到"最新版本"

**对应关系**：
- Git commit = Flyway migration
- `git log` = `flyway info`（查看执行历史）
- `git clone` = `docker compose up`（自动执行所有迁移）

#### 技术定义

**Flyway**：数据库版本管理工具，用 SQL 脚本管理数据库结构变更。

**迁移脚本**：描述数据库变更的 SQL 文件，命名规则：`V{版本号}__{描述}.sql`

**工作原理**：
1. Flyway 启动时，扫描 `db/migration` 目录
2. 检查数据库中的 `flyway_schema_history` 表（记录已执行的迁移）
3. 对比文件系统和历史记录，找出未执行的迁移
4. 按版本号顺序执行未执行的迁移
5. 执行成功后，更新 `flyway_schema_history` 表

---

### 第一步：配置 Flyway

在 Spring Boot 项目中，Flyway 是自动配置的，只需要添加依赖：

```xml
<!-- pom.xml -->
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-database-postgresql</artifactId>
</dependency>
```

在 `application.yml` 中配置：

```yaml
spring:
  flyway:
    # 启用 Flyway
    enabled: true
    
    # 迁移脚本位置（默认就是这个路径）
    locations: classpath:db/migration
    
    # 清理数据库（开发环境可以开启，生产环境必须关闭！）
    clean-disabled: true
    
    # 基线版本（用于已有数据库）
    baseline-on-migrate: true
```

---

### 第二步：创建迁移脚本目录

```bash
# 在 backend/src/main/resources 下创建目录
mkdir -p db/migration
```

```
backend/src/main/resources/
├── application.yml
└── db/
    └── migration/
        ├── V1__init_schema.sql           # 初始化表结构
        ├── V2__add_session_index.sql     # 添加索引（后续）
        └── ...
```

---

### 第三步：编写第一个迁移脚本

创建 `V1__init_schema.sql`：

**命名规则**：
- `V` 开头（大写）
- 版本号（数字，可以是 `1`、`1.1`、`1.0.0` 等）
- 两个下划线 `__`
- 描述（用下划线分隔单词）
- `.sql` 后缀

```sql
-- MiniClaw 数据库初始化迁移脚本
-- 
-- Flyway 迁移脚本命名规则：V{版本号}__{描述}.sql
-- 例如：V1__init_schema.sql
-- 
-- 版本号必须是递增的，Flyway 会按顺序执行

-- ============================================================
-- Session 会话表
-- ============================================================

CREATE TABLE sessions (
    -- 主键：使用 UUID 而不是自增 ID
    id VARCHAR(36) PRIMARY KEY,
    
    -- 会话标题
    title VARCHAR(255),
    
    -- 用户标识
    user_id VARCHAR(255) NOT NULL,
    
    -- Agent 标识
    agent_id VARCHAR(255),
    
    -- 会话状态
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    
    -- 创建时间
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    -- 更新时间
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    -- 软删除标记
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    
    -- 扩展元数据
    metadata TEXT
);

-- 索引：加速按用户查询
CREATE INDEX idx_sessions_user_id ON sessions(user_id);

-- ============================================================
-- Message 消息表
-- ============================================================

CREATE TABLE messages (
    id VARCHAR(36) PRIMARY KEY,
    
    -- 外键：关联到 Session
    session_id VARCHAR(36) NOT NULL,
    
    -- 消息角色
    role VARCHAR(20) NOT NULL,
    
    -- 消息内容
    content TEXT NOT NULL,
    
    -- Token 数量
    token_count INTEGER,
    
    -- 模型名称
    model VARCHAR(100),
    
    -- 创建时间
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    -- 扩展元数据
    metadata TEXT,
    
    -- 外键约束
    CONSTRAINT fk_messages_session 
        FOREIGN KEY (session_id) 
        REFERENCES sessions(id) 
        ON DELETE CASCADE
);

-- 索引
CREATE INDEX idx_messages_session_id ON messages(session_id);
```

#### 关键概念解释

**1. VARCHAR(36) 用于 UUID**

```sql
id VARCHAR(36) PRIMARY KEY
```

为什么不用自增 ID？
- UUID 在分布式系统中不会冲突
- 可以在客户端生成，不需要等待数据库返回
- 更安全，不能被遍历猜测

**2. TEXT vs VARCHAR**

```sql
content TEXT NOT NULL        -- 长文本，无长度限制
title VARCHAR(255)           -- 短文本，有长度限制
```

**3. 外键约束**

```sql
CONSTRAINT fk_messages_session 
    FOREIGN KEY (session_id) 
    REFERENCES sessions(id) 
    ON DELETE CASCADE
```

`ON DELETE CASCADE` 的含义：
- 删除 Session 时，自动删除关联的所有 Message
- 防止孤儿数据

**4. 索引**

```sql
CREATE INDEX idx_sessions_user_id ON sessions(user_id);
```

为什么需要索引？
- 加速查询：`WHERE user_id = 'xxx'`
- 没有索引 = 全表扫描（慢）
- 有索引 = 直接定位（快）

---

### 第四步：执行迁移

启动 Spring Boot 应用，Flyway 会自动执行迁移脚本。

**查看执行记录**：

```sql
SELECT * FROM flyway_schema_history ORDER BY installed_rank;
```

**预期输出**：
```
 installed_rank | version | description    | type | script                   | checksum    | installed_on
----------------+---------+----------------+------+--------------------------+-------------+--------------------
              1 | 1       | init schema    | SQL  | V1__init_schema.sql      | 1234567890  | 2026-03-05 00:30:00
```

---

### 第五步：添加新的迁移脚本

假设我们需要给 `sessions` 表添加一个新字段 `tags`：

创建 `V2__add_session_tags.sql`：

```sql
-- 给 sessions 表添加 tags 字段

ALTER TABLE sessions 
ADD COLUMN tags TEXT[];

-- 注释
COMMENT ON COLUMN sessions.tags IS '会话标签（数组）';
```

**注意**：
- ✅ 新的变更 = 新的迁移脚本
- ❌ 不要修改已执行过的迁移脚本
- ❌ 不要删除已执行过的迁移脚本

---

### 最佳实践

#### 1. 迁移脚本一旦执行，不要修改

**错误做法**：
```bash
# 执行了 V1__init.sql
# 发现表结构有问题，直接修改 V1__init.sql
# ❌ Flyway 会检测到 checksum 不匹配，报错
```

**正确做法**：
```bash
# 执行了 V1__init.sql
# 发现表结构有问题，创建 V2__fix_xxx.sql
# ✅ 新的迁移脚本来修复
```

#### 2. 迁移脚本要幂等（可选）

虽然 Flyway 保证每个脚本只执行一次，但编写幂等的 SQL 是好习惯：

```sql
-- 幂等的创建索引
CREATE INDEX IF NOT EXISTS idx_sessions_user_id ON sessions(user_id);

-- 幂等的添加列
DO $$ 
BEGIN 
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns 
                   WHERE table_name='sessions' AND column_name='tags') THEN
        ALTER TABLE sessions ADD COLUMN tags TEXT[];
    END IF;
END $$;
```

#### 3. 生产环境禁用 `flyway.clean()`

```yaml
spring:
  flyway:
    clean-disabled: true  # 永远不要在生产环境开启
```

---

### 常见问题

#### Q: 迁移脚本执行失败怎么办？

Flyway 会标记该迁移为失败，后续启动会报错。

**解决方法**：
1. 手动修复数据库（执行剩余的 SQL）
2. 修复迁移脚本
3. 清除 `flyway_schema_history` 中的失败记录：
   ```sql
   DELETE FROM flyway_schema_history WHERE success = FALSE;
   ```

#### Q: 如何回滚迁移？

Flyway 社区版不支持自动回滚。

**解决方法**：
- 手动编写回滚 SQL
- 或使用 `flyway.undo`（需要专业版）

#### Q: 团队协作时，迁移脚本冲突怎么办？

**场景**：你和小王都创建了 `V2__xxx.sql`

**解决方法**：
1. 约定版本号分配规则
2. 或者使用时间戳：`V20260305003000__xxx.sql`

---

### 动手实践

**任务**：创建 MiniClaw 的数据库迁移脚本

**步骤**：
1. 创建 `db/migration` 目录
2. 创建 `V1__init_schema.sql`
3. 启动 Docker Compose（PostgreSQL）
4. 启动 Spring Boot 应用
5. 验证表已创建

**验证命令**：
```bash
# 连接数据库
docker exec -it miniclaw-postgres psql -U miniclaw -d miniclaw

# 查看所有表
\dt

# 查看 flyway 历史记录
SELECT * FROM flyway_schema_history;
```

**预期结果**：
- 看到 `sessions`、`messages` 表
- `flyway_schema_history` 有一条记录

---

### 自检：你真的掌握了吗？

**问题 1**：为什么用 Flyway 而不是手动执行 SQL？
> 如果答不上来，重读「为什么需要数据库版本控制？」

你的答案：
```


```

参考答案：
<details>
<summary>点击展开</summary>

1. **版本追踪**：所有数据库变更都有记录，可追溯
2. **自动化**：新环境自动执行所有迁移脚本，无需手动操作
3. **团队协作**：团队成员 pull 代码后，数据库自动同步
4. **可重复**：开发/测试/生产环境使用相同的迁移脚本
5. **安全性**：避免忘记执行某个 SQL 脚本导致的问题

</details>

---

**问题 2**：迁移脚本的命名规则是什么？为什么有两个下划线？
> 如果卡住，说明需要更多练习

你的答案：
```


```

参考答案：
<details>
<summary>点击展开</summary>

**命名规则**：`V{版本号}__{描述}.sql`

**示例**：
- `V1__init_schema.sql`
- `V2__add_session_tags.sql`
- `V1.1__add_index.sql`

**两个下划线的原因**：
- Flyway 用双下划线 `__` 分隔版本号和描述
- 描述中可以用单下划线 `_` 分隔单词
- 这样可以避免歧义

</details>

---

**问题 3**（选做）：如果需要给已存在的表添加字段，应该怎么做？

你的答案：
```


```

参考答案：
<details>
<summary>点击展开</summary>

**创建新的迁移脚本**，不要修改已执行的脚本：

```sql
-- V2__add_session_tags.sql

ALTER TABLE sessions 
ADD COLUMN tags TEXT[];

-- 可选：添加索引、注释等
```

**步骤**：
1. 创建新的迁移脚本 `V2__xxx.sql`
2. 启动应用，Flyway 自动执行新脚本
3. 验证字段已添加

**错误做法**：
- ❌ 修改 `V1__init_schema.sql`（会导致 checksum 不匹配）
- ❌ 手动在数据库中添加字段（团队成员不知道）

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

- 我们学习了 Flyway 数据库版本管理
- 创建了 MiniClaw 的数据库迁移脚本
- 关键要点：
  - 迁移脚本命名：`V{版本号}__{描述}.sql`
  - 迁移脚本一旦执行，不要修改
  - 新的变更 = 新的迁移脚本
  - Flyway 自动追踪执行历史
- 下一节我们将搭建 Spring Boot 项目骨架

---

### 扩展阅读（可选）

- [Flyway 官方文档](https://flywaydb.org/documentation/)
- [Spring Boot Flyway 集成](https://docs.spring.io/spring-boot/docs/current/reference/html/howto.html#howto.data-initialization.migration-tool.flyway)
- [数据库迁移最佳实践](https://flywaydb.org/documentation/concepts/bestpractices)

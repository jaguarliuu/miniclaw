# 第3.3节：Flyway 迁移脚本 - 从零管理数据库版本

> **学习目标**：循序渐进地使用 Flyway 管理数据库，从最简单的表开始
> **预计时长**：25 分钟
> **难度**：入门

### 前置知识检查

**你应该已经掌握**：
- [x] 3.1-3.2 环境和 Docker Compose 已配置
- [ ] 基本 SQL 语法

**如果你不确定**：
- SQL 不熟 → 本节会边写边讲

---

### 为什么需要 Flyway？

#### 真实场景

假设你和队友小王一起开发 MiniClaw。

**没有版本控制**：
```
你（周一）：创建了 sessions 表
你（周二）：加了 user_id 字段
小王（周三）：也加了 user_id 字段，但类型不一样
你（周四）：加了索引，但忘了告诉小王
小王（周五）：代码跑不起来，因为表结构不一致
```

**有了 Flyway**：
```
V1__create_sessions_table.sql   ← 你提交
V2__add_user_id_index.sql       ← 你提交
V3__create_messages_table.sql   ← 小王提交
```

每个人 pull 代码后，Flyway 自动执行**还没执行过**的脚本，所有人表结构**完全一致**。

#### 直觉理解

**Flyway = 数据库的 Git**
- 每个 SQL 文件 = 一个 commit
- `flyway_schema_history` 表 = `git log`
- Flyway 自动追踪已执行的脚本

---

### 第一步：添加 Flyway 依赖

**1.1 打开 pom.xml**

确认 `flyway-core` 和 `flyway-database-postgresql` 依赖已添加：

```xml
<!-- Flyway：数据库版本控制 -->
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-core</artifactId>
</dependency>
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-database-postgresql</artifactId>
</dependency>
```

**1.2 刷新 Maven**

```bash
cd backend
./mvnw clean compile
```

看到 `BUILD SUCCESS` 就对了。

---

### 第二步：创建迁移目录

**2.1 创建目录结构**

```bash
# 在 backend/src/main/resources 下创建
mkdir -p db/migration
```

最终结构：
```
backend/src/main/resources/
├── application.yml
└── db/
    └── migration/
        └── （SQL 脚本将放在这里）
```

**2.2 为什么是这个路径？**

Flyway 默认扫描 `classpath:db/migration` 目录下的 SQL 文件。

---

### 第三步：编写第一个迁移脚本

**3.1 创建 V1__create_sessions_table.sql**

**注意文件命名**：
- `V1`：版本号
- `__`：**双下划线**（必须是两个下划线！）
- `create_sessions_table`：描述
- `.sql`：文件后缀

**3.2 写入基础结构**

创建文件 `db/migration/V1__create_sessions_table.sql`：

```sql
-- 创建 sessions 表
-- 存储 AI Agent 的会话信息

CREATE TABLE sessions (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

**3.3 验证语法**

这只是最简单的表，我们先验证 Flyway 能正常工作。

---

### 第四步：配置 Flyway

**4.1 修改 application.yml**

```yaml
spring:
  # 数据源配置
  datasource:
    url: jdbc:postgresql://localhost:5432/miniclaw
    driver-class-name: org.postgresql.Driver
    username: miniclaw
    password: miniclaw123
  
  # Flyway 配置
  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: true
  
  # JPA 配置
  jpa:
    hibernate:
      ddl-auto: validate  # 重要：使用 Flyway 管理表结构
```

**关键配置解释**：
- `flyway.enabled: true`：启用 Flyway
- `ddl-auto: validate`：JPA 只验证，不自动创建表（让 Flyway 管理）

---

### 第五步：启动并验证

**5.1 启动数据库**

```bash
cd ~/clawd/miniclaw
docker compose up -d
```

**5.2 启动应用**

```bash
cd backend
./mvnw spring-boot:run
```

**5.3 查看日志**

应该看到类似输出：
```
Flyway: Successfully applied 1 migration to schema "public"
```

**5.4 连接数据库验证**

```bash
docker exec -it miniclaw-postgres psql -U miniclaw -d miniclaw

# 查看表
\dt

# 查看 Flyway 历史
SELECT * FROM flyway_schema_history;

# 退出
\q
```

**预期结果**：
```
miniclaw=# \dt
            List of relations
 Schema |     Name      | Type  |   Owner   
--------+---------------+-------+-----------
 public | flyway_schema_history | table | miniclaw
 public | sessions      | table | miniclaw
```

---

### 第六步：添加第二个迁移脚本

现在我们有了一个能工作的基础表。**循序渐进**地添加字段。

**6.1 创建 V2__add_sessions_fields.sql**

```sql
-- 添加更多字段到 sessions 表

ALTER TABLE sessions 
ADD COLUMN title VARCHAR(255),
ADD COLUMN status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
ADD COLUMN updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
ADD COLUMN deleted BOOLEAN NOT NULL DEFAULT FALSE;

-- 添加索引（高频查询字段）
CREATE INDEX idx_sessions_user_id ON sessions(user_id);
CREATE INDEX idx_sessions_status ON sessions(status);
```

**6.2 重启应用**

```bash
./mvnw spring-boot:run
```

**6.3 验证**

```sql
-- 连接数据库
\d sessions

-- 查看 Flyway 历史
SELECT version, description, success FROM flyway_schema_history;
```

**预期结果**：
```
 version |      description       | success 
---------+------------------------+---------
 1       | create sessions table  | t
 2       | add sessions fields    | t
```

---

### 第七步：创建 messages 表

**7.1 创建 V3__create_messages_table.sql**

```sql
-- 创建 messages 表
-- 存储对话中的每条消息

CREATE TABLE messages (
    id VARCHAR(36) PRIMARY KEY,
    session_id VARCHAR(36) NOT NULL,
    role VARCHAR(50) NOT NULL,
    content TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    -- 外键：消息属于某个会话
    CONSTRAINT fk_messages_session 
        FOREIGN KEY (session_id) 
        REFERENCES sessions(id) 
        ON DELETE CASCADE
);

-- 索引
CREATE INDEX idx_messages_session_id ON messages(session_id);
```

**7.2 重启并验证**

```bash
./mvnw spring-boot:run
```

---

### 迁移脚本命名规则

**必须严格遵守**：

```
V{版本号}__{描述}.sql
│    │       │      │
│    │       │      └── 文件后缀
│    │       └──────── 描述（下划线分隔）
│    └──────────────── 双下划线（必须是两个！）
└───────────────────── 版本号（递增数字）
```

**正确示例**：
- `V1__create_sessions_table.sql` ✅
- `V2__add_sessions_fields.sql` ✅
- `V3__create_messages_table.sql` ✅

**错误示例**：
- `v1__create_sessions.sql` ❌（V 必须大写）
- `V1_create_sessions.sql` ❌（必须是双下划线）
- `create_sessions.sql` ❌（缺少版本号）

---

### 常见问题

#### Q: 迁移脚本执行失败怎么办？

**场景**：V2 脚本有语法错误，执行失败。

**解决步骤**：

1. **查看错误**：检查日志中的错误信息
2. **修复脚本**：修正 SQL 语法
3. **清理失败记录**：
   ```sql
   DELETE FROM flyway_schema_history WHERE success = false;
   ```
4. **重新启动**：`./mvnw spring-boot:run`

#### Q: 如何回滚已执行的迁移？

**Flyway 社区版不支持回滚**。解决方案：

1. **创建新的迁移脚本**（推荐）：
   ```sql
   -- V4__drop_sessions_title.sql
   ALTER TABLE sessions DROP COLUMN title;
   ```

2. **手动修改数据库**（不推荐）

#### Q: 团队协作时版本号冲突怎么办？

**场景**：你和小王都创建了 V3。

**解决方案**：
1. 沟通协调，改用不同版本号
2. 使用时间戳作为版本号（不推荐，可读性差）

**最佳实践**：
- 小团队：口头协调
- 大团队：Pull Request 审查

---

### 动手实践

**任务**：创建 MiniClaw 的前三个迁移脚本

**步骤**：
1. 添加 Flyway 依赖
2. 创建 `db/migration` 目录
3. 创建 `V1__create_sessions_table.sql`（只有基础字段）
4. 启动应用，验证表创建成功
5. 创建 `V2__add_sessions_fields.sql`（添加更多字段）
6. 重启应用，验证字段添加成功
7. 创建 `V3__create_messages_table.sql`

**检查清单**：
- [ ] Flyway 依赖已添加
- [ ] 迁移目录已创建
- [ ] V1 脚本执行成功
- [ ] V2 脚本执行成功
- [ ] V3 脚本执行成功
- [ ] `flyway_schema_history` 表有 3 条记录

---

### 自检：你真的掌握了吗？

**问题 1**：为什么用 Flyway 而不是手动执行 SQL？

你的答案：
```


```

参考答案：
<details>
<summary>点击展开</summary>

1. **版本控制**：每次修改都有记录，可以追溯
2. **团队协作**：所有人执行相同的脚本，表结构一致
3. **自动化**：启动时自动执行，不需要手动运行 SQL
4. **可重复**：新环境一键部署，不需要记住执行了哪些 SQL

**类比**：
- 手动执行 SQL = 手动备份文件
- Flyway = Git 自动同步

</details>

---

**问题 2**：迁移脚本命名 `V2__add_fields.sql` 为什么有两个下划线？

你的答案：
```


```

参考答案：
<details>
<summary>点击展开</summary>

**双下划线分隔版本号和描述**，这是 Flyway 的规范。

**为什么是双下划线**：
- 单下划线可能出现在描述中（如 `add_user_id`）
- 双下划线明确分隔，避免歧义

**解析示例**：
- `V2__add_user_id.sql` → 版本=2, 描述=add_user_id
- `V2_add_user_id.sql` → Flyway 无法正确解析

</details>

---

**问题 3**：如果 V2 脚本执行失败，如何修复？

你的答案：
```


```

参考答案：
<details>
<summary>点击展开</summary>

**步骤**：

1. **查看错误日志**：找到失败原因
2. **修复 SQL 脚本**：修正语法错误
3. **删除失败记录**：
   ```sql
   DELETE FROM flyway_schema_history WHERE success = false;
   ```
4. **重新启动应用**：Flyway 会重新执行修复后的脚本

**注意**：
- 如果脚本部分执行（如创建了部分字段），需要手动清理
- 生产环境建议先在测试环境验证脚本

</details>

---

### 本节小结

- 我们循序渐进地学习了 Flyway
- 关键要点：
  - Flyway 依赖必须添加
  - 迁移脚本放在 `db/migration`
  - 命名规则：`V{版本号}__{描述}.sql`
  - 每次只添加少量修改，逐步完善
  - `flyway_schema_history` 记录执行历史
- 下一节我们将搭建 Spring Boot 项目骨架

---

### 完整代码

**V1__create_sessions_table.sql**：
```sql
CREATE TABLE sessions (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

**V2__add_sessions_fields.sql**：
```sql
ALTER TABLE sessions 
ADD COLUMN title VARCHAR(255),
ADD COLUMN status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
ADD COLUMN updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
ADD COLUMN deleted BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX idx_sessions_user_id ON sessions(user_id);
CREATE INDEX idx_sessions_status ON sessions(status);
```

**V3__create_messages_table.sql**：
```sql
CREATE TABLE messages (
    id VARCHAR(36) PRIMARY KEY,
    session_id VARCHAR(36) NOT NULL,
    role VARCHAR(50) NOT NULL,
    content TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT fk_messages_session 
        FOREIGN KEY (session_id) 
        REFERENCES sessions(id) 
        ON DELETE CASCADE
);

CREATE INDEX idx_messages_session_id ON messages(session_id);
```

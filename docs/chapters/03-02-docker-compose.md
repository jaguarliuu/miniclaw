# 第3.2节：一键启动 - Docker Compose 编排基础设施

> **学习目标**：使用 Docker Compose 编排 PostgreSQL + pgvector，实现一键启动开发环境
> **预计时长**：25 分钟
> **难度**：入门

### 前置知识检查

**你应该已经掌握**：
- [x] 3.1 开发环境准备：Docker 已安装
- [ ] 基本的 Docker 概念（镜像、容器、数据卷）

**如果你不确定**：
- Docker 不熟 → 本节会从零讲解
- 没用过 Docker Compose → 本节会详细解释每个配置

**学习路径**：
- **路径A（有基础）**：直接跳到「编写 docker-compose.yml」
- **路径B（从零开始）**：按顺序阅读全部内容

---

### 为什么需要 Docker Compose？

#### 真实场景

假设你要开发 MiniClaw，需要 PostgreSQL 数据库。

**没有 Docker Compose**：
1. 下载 PostgreSQL 安装包（200MB+）
2. 运行安装程序，点击下一步 10 次
3. 配置用户名、密码、端口
4. 安装 pgvector 扩展（需要编译？）
5. 配置环境变量
6. 记住所有配置，下次换电脑重来一遍

**队友小王也要开发**：
- 他的 Mac 和你的 Windows 配置不一样
- 他的 PostgreSQL 版本是 15，你的是 16
- 他没装 pgvector，代码跑不起来
- "我这能跑，你那怎么不行？"

**有了 Docker Compose**：
```bash
# 一行命令，启动所有服务
docker compose up -d

# 队友小王 clone 代码后，也是一行命令
docker compose up -d
```

所有人使用**完全相同**的环境，不会再有"我这能跑，你那不行"的问题。

#### 直觉理解

**Docker Compose 就像是"外卖套餐"**：
- 你不需要自己买菜、切菜、炒菜
- 只需要点一个"套餐"，所有菜一起送来
- 菜品质量由餐厅保证，不用担心口味不一致

**对应关系**：
- 套餐菜单 = `docker-compose.yml` 配置文件
- 厨房 = Docker 引擎
- 菜品 = PostgreSQL + pgvector 服务
- 外卖盒 = Docker 容器

#### 技术定义

**Docker**：容器化平台，将应用和依赖打包成"镜像"，在任何地方运行。

**Docker Compose**：多容器编排工具，用 YAML 文件定义多个服务，一键启动。

**pgvector**：PostgreSQL 的向量扩展，用于存储和检索向量数据（后续 Memory 系统会用到）。

---

### 第一步：创建项目目录

```bash
# 创建 MiniClaw 项目目录
mkdir -p miniclaw
cd miniclaw
```

---

### 第二步：编写 docker-compose.yml

在项目根目录创建 `docker-compose.yml`：

```yaml
# MiniClaw 开发环境基础设施
# 
# 这个文件定义了 MiniClaw 开发所需的所有服务：
# - PostgreSQL：关系型数据库
# - pgvector：向量扩展，用于 Memory 语义检索
#
# 使用方法：
#   docker compose up -d     # 后台启动
#   docker compose down      # 停止并删除
#   docker compose logs -f   # 查看日志

services:
  # PostgreSQL 数据库 + pgvector 扩展
  postgres:
    image: pgvector/pgvector:pg16
    container_name: miniclaw-postgres
    restart: unless-stopped
    
    environment:
      # 数据库配置
      POSTGRES_DB: miniclaw
      POSTGRES_USER: miniclaw
      POSTGRES_PASSWORD: miniclaw123
      
      # PostgreSQL 性能调优（开发环境）
      POSTGRES_INITDB_ARGS: "--encoding=UTF8 --locale=C.UTF-8"
    
    ports:
      # 映射端口：主机5432 → 容器5432
      - "5432:5432"
    
    volumes:
      # 数据持久化：即使容器删除，数据也不会丢失
      - postgres_data:/var/lib/postgresql/data
      
      # 初始化脚本：首次启动时自动创建 pgvector 扩展
      - ./init-db.sql:/docker-entrypoint-initdb.d/init-db.sql:ro
    
    healthcheck:
      # 健康检查：确保数据库真正可用
      test: ["CMD-SHELL", "pg_isready -U miniclaw -d miniclaw"]
      interval: 5s
      timeout: 5s
      retries: 5

# 数据卷定义
volumes:
  postgres_data:
    name: miniclaw_postgres_data

# 网络定义（可选，用于与其他项目隔离）
networks:
  default:
    name: miniclaw-network
```

#### 配置项详解

| 配置项 | 作用 | 为什么这样配置 |
|--------|------|----------------|
| `image: pgvector/pgvector:pg16` | 使用带 pgvector 的 PostgreSQL 16 镜像 | 预装 pgvector，省去编译安装麻烦 |
| `container_name` | 容器名称 | 便于识别和管理 |
| `restart: unless-stopped` | 自动重启策略 | 开机自动启动，除非手动停止 |
| `ports: "5432:5432"` | 端口映射 | 主机可以通过 localhost:5432 访问 |
| `volumes: postgres_data` | 数据持久化 | 容器删除后数据不丢失 |
| `healthcheck` | 健康检查 | 确保数据库真正可用后再接受连接 |

#### 常见误区

> ❌ **误区**：密码直接写在配置文件里，提交到 Git
> 
> ✅ **正确理解**：生产环境应使用环境变量或密钥管理服务。开发环境可以暂时这样，但要记得在 `.gitignore` 中排除敏感配置。

---

### 第三步：编写初始化脚本

创建 `init-db.sql`：

```sql
-- MiniClaw 数据库初始化脚本
-- 
-- 这个脚本在 PostgreSQL 首次启动时自动执行
-- 用于创建 pgvector 扩展和其他必要的数据库配置

-- 创建 pgvector 扩展
-- pgvector 是 PostgreSQL 的向量相似度搜索扩展
-- 用于 Memory 系统的语义检索
CREATE EXTENSION IF NOT EXISTS vector;

-- 验证扩展已安装
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'vector') THEN
        RAISE NOTICE 'pgvector extension installed successfully';
    ELSE
        RAISE EXCEPTION 'Failed to install pgvector extension';
    END IF;
END $$;
```

**为什么需要初始化脚本？**
- pgvector 扩展需要在每个数据库中手动创建
- Docker 容器首次启动时，会自动执行 `/docker-entrypoint-initdb.d/` 下的脚本
- 这样每次重建容器都不用重新配置

---

### 第四步：启动服务

```bash
# 启动所有服务（后台运行）
docker compose up -d

# 查看服务状态
docker compose ps

# 查看日志
docker compose logs -f postgres
```

**预期输出**：
```
NAME                  STATUS                   PORTS
miniclaw-postgres     Up (healthy)             0.0.0.0:5432->5432/tcp
```

如果看到 `Up (healthy)`，说明数据库启动成功！

---

### 第五步：验证连接

使用 `psql` 或数据库工具验证连接：

```bash
# 使用 psql 连接
docker exec -it miniclaw-postgres psql -U miniclaw -d miniclaw

# 验证 pgvector 扩展
SELECT * FROM pg_extension WHERE extname = 'vector';

# 退出
\q
```

**预期输出**：
```
  oid  | extname | extversion 
-------+---------+------------
 16384 | vector  | 0.5.0
(1 row)
```

---

### 常用命令

```bash
# 启动服务
docker compose up -d

# 停止服务
docker compose down

# 停止并删除数据（重置数据库）
docker compose down -v

# 查看日志
docker compose logs -f

# 进入容器
docker exec -it miniclaw-postgres bash

# 重启服务
docker compose restart
```

---

### 动手实践

**任务**：搭建 MiniClaw 开发环境数据库

**步骤**：
1. 创建项目目录
2. 创建 `docker-compose.yml` 和 `init-db.sql`
3. 运行 `docker compose up -d`
4. 验证数据库连接和 pgvector 扩展

**预期结果**：
- 容器状态为 `Up (healthy)`
- psql 可以连接到数据库
- pgvector 扩展已安装

---

### 自检：你真的掌握了吗？

**问题 1**：为什么用 Docker Compose 而不是手动安装 PostgreSQL？
> 如果答不上来，重读「为什么需要 Docker Compose？」

你的答案：
```


```

参考答案：
<details>
<summary>点击展开</summary>

1. **环境一致性**：所有开发者使用完全相同的环境，不会再有"我这能跑，你那不行"的问题
2. **一键启动**：不需要手动安装配置，`docker compose up -d` 一行命令搞定
3. **易于清理**：`docker compose down -v` 可以完全删除环境，不留痕迹
4. **版本管理**：使用特定版本的镜像，避免版本差异导致的问题
5. **隔离性**：不同项目的数据库互不干扰

</details>

---

**问题 2**：`volumes` 配置的作用是什么？如果不配置会怎样？
> 如果卡住，说明需要更多练习

你的答案：
```


```

参考答案：
<details>
<summary>点击展开</summary>

`volumes` 的作用是**数据持久化**：
- Docker 容器是临时的，删除容器后数据会丢失
- `volumes` 将数据存储在宿主机上，即使容器删除，数据也不会丢失
- 如果不配置 volumes，每次重建容器数据库都是空的

**举例**：
- 配置了 volumes：删除容器后重新启动，之前的数据还在
- 没配置 volumes：删除容器后重新启动，数据库是空的

</details>

---

**问题 3**（选做）：如何在不删除数据的情况下重启数据库服务？

你的答案：
```


```

参考答案：
<details>
<summary>点击展开</summary>

```bash
# 方法一：重启服务（数据保留）
docker compose restart

# 方法二：停止后重新启动（数据保留）
docker compose down
docker compose up -d

# 注意：不要加 -v 参数！
# docker compose down -v  # 这会删除数据卷！
```

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

- 我们学习了 Docker Compose 编排基础设施
- 创建了 PostgreSQL + pgvector 开发环境
- 关键要点：
  - `docker-compose.yml` 定义服务配置
  - `volumes` 实现数据持久化
  - `init-db.sql` 自动初始化数据库
  - 健康检查确保服务可用
- 下一节我们将学习使用 Flyway 管理数据库版本

---

### 扩展阅读（可选）

- [Docker Compose 官方文档](https://docs.docker.com/compose/)
- [pgvector GitHub](https://github.com/pgvector/pgvector)
- [PostgreSQL Docker 镜像说明](https://hub.docker.com/_/postgres)

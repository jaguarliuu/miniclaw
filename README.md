# MiniClaw

```
   __  __ _       _  _____ _
  |  \/  (_)     (_)/ ____| |
  | \  / |_ _ __  _| |    | | __ ___      __
  | |\/| | | '_ \| | |    | |/ _` \ \ /\ / /
  | |  | | | | | | | |____| | (_| |\ V  V /
  |_|  |_|_|_| |_|_|\_____|_|\__,_| \_/\_/

  🤖 A Java Agent that actually gets things done.
```

> **"给我一个 LLM，我能撬动整个世界"**
> —— 某个写了太多 CRUD 的 Java 程序员

![Java](https://img.shields.io/badge/Java-24-ED8B00?style=flat-square&logo=openjdk)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4-6DB33F?style=flat-square&logo=springboot)
![Vue 3](https://img.shields.io/badge/Vue-3.5-4FC08D?style=flat-square&logo=vuedotjs)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-4169E1?style=flat-square&logo=postgresql)
![License](https://img.shields.io/badge/License-MIT-blue?style=flat-square)

---

## 这是什么？

**MiniClaw** 是 [OpenClaw](https://github.com/anthropics/claude-code) 的 Java 学习版复刻。

如果你曾好奇过 Claude Code 背后是怎么工作的 —— 它是怎么理解你的需求、选择工具、执行命令、记住上下文的 —— 那这个项目就是为你准备的。

我们用 **Java + Spring Boot** 重新实现了 OpenClaw 的核心架构，包括：

| 你能学到的 | 对应模块 |
|-----------|---------|
| ReAct 循环是怎么让 AI "思考-行动-观察" 的 | `AgentRuntime` |
| System Prompt 是怎么把工具、技能、安全约束组装在一起的 | `ContextBuilder` |
| Tool Calling 是怎么让 AI 调用外部工具的 | `ToolDispatcher` |
| 技能系统是怎么按需加载、自动选择的 | `SkillRegistry` + `SkillSelector` |
| 长期记忆是怎么存储和检索的 | `MemoryStore` + `MemoryIndexer` |
| WebSocket 是怎么实现流式输出的 | `EventBus` + `GatewayWebSocketHandler` |

---

## 为什么叫 MiniClaw？

因为：

1. **Mini** - 我们只实现了核心功能，没有多租户、没有插件商店、没有分布式调度
2. **Claw** - 致敬 OpenClaw（Claude Code 的内部代号）

```
OpenClaw（完整版）           MiniClaw（学习版）
    🦞                           🦐
    ↓                            ↓
  企业级                        够用就行
  生产就绪                       能跑就行
  功能完整                       核心齐全
```

---

## 30 秒看懂架构

```
┌─────────────────────────────────────────────────────────────┐
│                        你 (Human)                           │
│                           │                                 │
│                    "帮我分析这个项目"                         │
│                           ↓                                 │
├─────────────────────────────────────────────────────────────┤
│  Frontend (Vue 3)                                           │
│  ┌─────────────────────────────────────────────────────┐   │
│  │  💬 Chat UI  │  ⚙️ Settings  │  📊 Skills Browser   │   │
│  └─────────────────────────────────────────────────────┘   │
│                           │ WebSocket                       │
├───────────────────────────┼─────────────────────────────────┤
│  Gateway (Control Plane)  │                                 │
│  ┌────────────┐ ┌────────┴───────┐ ┌──────────────────┐   │
│  │ WS Handler │→│  RPC Router    │→│    EventBus      │   │
│  └────────────┘ └────────────────┘ └──────────────────┘   │
│                           │                                 │
├───────────────────────────┼─────────────────────────────────┤
│  AgentRuntime (执行引擎)   │                                 │
│  ┌────────────────────────┴────────────────────────────┐   │
│  │                                                      │   │
│  │   while (not done) {                                │   │
│  │       🧠 Think  → 调用 LLM，分析该干嘛               │   │
│  │       🔧 Act    → 执行工具 (read_file, shell...)    │   │
│  │       👀 Observe → 把结果喂回给 LLM                  │   │
│  │   }                                                  │   │
│  │                                                      │   │
│  └──────────────────────────────────────────────────────┘   │
│                           │                                 │
├───────────────────────────┼─────────────────────────────────┤
│  Extensions               │                                 │
│  ┌──────────┐ ┌──────────┼──────────┐ ┌────────────────┐   │
│  │  Tools   │ │   Skills  │          │ │    Memory      │   │
│  │ 📁 📝 🖥️  │ │  🎯 自动选择        │ │ 🧠 向量检索    │   │
│  └──────────┘ └───────────┴──────────┘ └────────────────┘   │
│                           │                                 │
├───────────────────────────┼─────────────────────────────────┤
│  Storage                  │                                 │
│  ┌────────────────────────┴────────────────────────────┐   │
│  │  PostgreSQL + pgvector  │  Workspace (Markdown)     │   │
│  └──────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
```

---

## 核心特性

### 🔄 ReAct 循环

AI 不是一次性回答，而是**循环执行**直到任务完成：

```
你：帮我创建一个 hello.py 并运行它

AI 内心独白：
  1. 🧠 用户想创建文件并运行... 我需要 write_file 工具
  2. 🔧 [执行 write_file] → 创建 hello.py
  3. 👀 文件创建成功了
  4. 🧠 现在需要运行它... 我需要 shell 工具
  5. 🔧 [执行 shell] → python hello.py
  6. 👀 输出是 "Hello, World!"
  7. 🧠 任务完成，可以回复用户了

AI：我已经创建了 hello.py 并运行成功，输出是 "Hello, World!"
```

### 🛠️ 工具系统

内置 7 个工具，覆盖文件操作、命令执行、网络请求：

| 工具 | 功能 | 危险等级 |
|-----|------|---------|
| `read_file` | 读文件 | 🟢 安全 |
| `write_file` | 写文件 | 🟢 安全 |
| `shell` | 执行命令 | 🟡 普通命令安全，`rm -rf` 需确认 |
| `shell_start` | 启动后台进程 | 🟡 同上 |
| `shell_status` | 查看进程状态 | 🟢 安全 |
| `shell_kill` | 终止进程 | 🟢 安全 |
| `http_get` | HTTP 请求 | 🟢 安全 |
| `memory_search` | 搜索记忆 | 🟢 安全 |
| `memory_write` | 写入记忆 | 🟢 安全 |

**智能 HITL（Human-in-the-Loop）**：
- `ls`, `npm install`, `git status` → 直接执行
- `rm -rf`, `git push --force`, `password=xxx` → 弹窗确认

### 🎯 技能系统

兼容 Claude Skills 格式，支持：

- **自动选择**：根据用户请求自动匹配最相关的技能
- **手动触发**：`/skill-name` 直接激活
- **热更新**：修改 `SKILL.md` 自动重载
- **权限隔离**：每个技能可以限制能用哪些工具

```markdown
# workspace/.miniclaw/skills/git-helper/SKILL.md

---
name: git-helper
description: Git 操作助手，帮你 commit、push、解决冲突
allowed-tools: [shell, read_file]
---

你是一个 Git 专家。用户的请求：$ARGUMENTS
```

### 🧠 全局记忆

跨会话的长期记忆系统：

- **Markdown 是真相源**：所有记忆存储在 `workspace/memory/` 目录
- **向量检索**：基于 pgvector，语义搜索相关记忆
- **全文检索**：PostgreSQL FTS 作为后备
- **混合排序**：向量相似度 + 关键词匹配 + 时间衰减

```
workspace/memory/
├── MEMORY.md           # 核心长期记忆（用户偏好、重要事实）
├── 2026-01-15.md       # 日记式追加
├── 2026-01-16.md
└── ...
```

### ⏰ Cron 自动化

定时任务调度，让 AI 定期帮你干活：

```yaml
# 每天早上 8 点汇报昨日 Git 提交
name: daily-git-report
cron: "0 8 * * *"
prompt: "帮我汇总昨天的 Git 提交记录"
delivery: log  # 或者 webhook、email
```

---

## 快速开始

### 环境要求

- Java 24+
- Node.js 20+
- PostgreSQL 16+ (推荐用 Docker)
- 一个 OpenAI 兼容的 LLM API（DeepSeek、通义千问、Ollama...）

### 1. 启动数据库

```bash
docker-compose up -d
```

### 2. 配置 LLM

创建 `src/main/resources/application-local.yml`：

```yaml
llm:
  endpoint: https://api.deepseek.com  # 或其他兼容接口
  api-key: sk-xxx
  model: deepseek-chat
```

### 3. 启动后端

```bash
mvn clean package -DskipTests
java -jar target/miniclaw-*.jar --spring.profiles.active=local
```

### 4. 启动前端

```bash
cd miniclaw-ui
npm install
npm run dev
```

打开 http://localhost:5173，开始聊天！

---

## 项目结构

```
miniclaw/
├── src/main/java/com/jaguarliu/ai/
│   ├── gateway/          # 🚪 控制平面：WebSocket、RPC、事件
│   ├── runtime/          # 🔄 执行引擎：ReAct 循环、HITL、上下文构建
│   ├── tools/            # 🔧 工具系统：内置工具、工具注册、危险检测
│   ├── skills/           # 🎯 技能系统：解析、注册、选择、热更新
│   ├── memory/           # 🧠 记忆系统：存储、分块、索引、检索
│   ├── llm/              # 🤖 LLM 接入：OpenAI 兼容客户端
│   ├── session/          # 💬 会话管理
│   └── cron/             # ⏰ 定时任务
├── miniclaw-ui/          # 🖥️ Vue 3 前端
├── workspace/            # 📁 工作目录（技能、记忆都在这里）
│   ├── .miniclaw/skills/ # 技能目录
│   └── memory/           # 记忆目录
└── docs/                 # 📚 设计文档
```

---

## 技术选型

### 为什么选这些技术？

| 选择 | 原因 |
|-----|------|
| **Java 24** | LTS，Virtual Threads 让响应式编程更简单 |
| **Spring WebFlux** | 非阻塞 IO，天然适合流式输出 |
| **PostgreSQL + pgvector** | 一个数据库搞定关系数据 + 向量检索 |
| **Vue 3 Composition API** | 简单直接，组合式 API 很舒服 |
| **原生 CSS** | 不需要 Tailwind，黑白极简风格几百行 CSS 搞定 |

### 与 OpenClaw 的对比

| 特性 | OpenClaw | MiniClaw |
|-----|----------|----------|
| 语言 | TypeScript | Java |
| 运行时 | Node.js | JVM |
| ReAct 循环 | ✅ | ✅ |
| Skills 系统 | ✅ | ✅ |
| Memory 系统 | ✅ | ✅ |
| HITL 确认 | ✅ | ✅ (智能检测危险命令) |
| Cron 调度 | ✅ | ✅ (Quartz) |
| 多租户 | ✅ | ❌ (个人使用) |
| 分布式节点 | ✅ | ❌ |
| MCP 支持 | ✅ | 🚧 (计划中) |
| SubAgent | ✅ | 🚧 (计划中) |
| Sandbox | ✅ | 🚧 (计划中) |

---

## Roadmap

- [x] ReAct 循环核心
- [x] 工具系统
- [x] Skills 系统（Claude Skills 兼容）
- [x] 全局记忆（Markdown + 向量检索）
- [x] HITL 人工确认（智能危险命令检测）
- [x] Cron 定时任务
- [ ] MCP (Model Context Protocol) 接入
- [ ] SubAgent 支持
- [ ] Sandbox 代码执行
- [ ] 更多内置技能

---

## 贡献

欢迎 PR！尤其欢迎：

- 🐛 Bug 修复
- 📝 文档改进
- 🎯 新技能（放到 `docs/skills/` 下）
- 🔧 新工具

---

## 致谢

- [OpenClaw / Claude Code](https://github.com/anthropics/claude-code) - 架构灵感和设计参考
- [ReAct: Synergizing Reasoning and Acting](https://arxiv.org/abs/2210.03629) - ReAct 论文
- [Spring WebFlux](https://docs.spring.io/spring-framework/reference/web/webflux.html) - 响应式 Web 框架

---

## License

MIT

---

<p align="center">
  <sub>Made with ☕ and mass amounts of <code>System.out.println</code></sub>
</p>

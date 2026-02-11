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

我们用 **Java + Spring Boot** 重新实现了 OpenClaw 的核心架构，MVP 功能已全部就绪：

| 你能学到的 | 对应模块 |
|-----------|---------|
| ReAct 循环是怎么让 AI "思考-行动-观察" 的 | `AgentRuntime` |
| System Prompt 是怎么把工具、技能、安全约束组装在一起的 | `ContextBuilder` |
| Tool Calling 是怎么让 AI 调用外部工具的 | `ToolDispatcher` |
| 技能系统是怎么按需加载、自动选择的 | `SkillRegistry` + `SkillSelector` |
| 长期记忆是怎么存储和检索的 | `MemoryStore` + `MemoryIndexer` |
| WebSocket 是怎么实现流式输出的 | `EventBus` + `GatewayWebSocketHandler` |
| 子代理是怎么并行执行和结果回传的 | `SubagentService` + `SubagentCompletionTracker` |
| 定时任务是怎么调度和推送结果的 | `ScheduledTaskService` + `ChannelService` |
| 远程节点是怎么安全连接和审计的 | `NodeService` + `AuditLogService` |

---

## 为什么叫 MiniClaw？

因为：

1. **Mini** - 我们只实现了核心功能，没有多租户、没有插件商店、没有分布式调度
2. **Claw** - 致敬 OpenClaw

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

内置 10 个工具，覆盖文件操作、命令执行、网络请求、记忆和子代理：

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
| `sessions_spawn` | 派生子代理 | 🟢 仅主会话可用 |

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

### 🔀 SubAgent 子代理

主 Agent 可以将子任务异步派生到独立子会话并行执行，完成后自动回传结果并汇总：

```
你：帮我同时 ping baidu.com 和 google.com，汇总结果

AI 内心独白：
  1. 🧠 两个任务可以并行，用 sessions_spawn 派生两个子代理
  2. 🔧 [sessions_spawn] → 子代理 A: ping baidu.com
  3. 🔧 [sessions_spawn] → 子代理 B: ping google.com
  4. ⏳ 等待所有子代理完成...
  5. 📨 子代理 A 完成: baidu.com 可达, 延迟 12ms
  6. 📨 子代理 B 完成: google.com 可达, 延迟 45ms
  7. 🧠 汇总结果回复用户

AI：两个目标均可达。baidu.com 延迟 12ms，google.com 延迟 45ms。
```

核心机制：
- **独立会话**：每个子代理运行在隔离的子会话中，不污染主会话上下文
- **双层队列**：session 内串行 + main/subagent 全局 lane 独立并发配额
- **屏障等待**：主循环自动等待所有子代理完成后再由 LLM 汇总
- **禁止嵌套**：子代理不能再派生子代理
- **右侧面板**：前端点击 SubagentCard 可在右侧面板查看子代理工作详情（类 Claude Artifacts）

### 📡 渠道推送

任务执行结果自动推送到指定渠道：

| 渠道类型 | 说明 |
|----------|------|
| **Email** | SMTP 发送，支持 TLS、HTML/纯文本自动转换 |
| **Webhook** | HTTP POST/PUT，支持自定义 Header 和签名密钥 |

凭据全程 AES 加密，日志中不可见。支持连接测试，确保渠道配置正确。

### 🖥️ 远程节点管理

通过 SSH / Kubernetes 连接远程机器，执行命令并审计：

- **多连接器**：SSH（JSch）和 K8s 两种连接方式
- **安全策略**：`strict` / `relaxed`，影响危险命令确认行为
- **审计日志**：每次远程命令的完整记录（节点、命令、安全等级、执行结果、耗时）
- **凭据加密**：与渠道共用加密组件，密码/密钥永不明文存储

### 📄 文件预览面板

Agent 生成文件时，前端实时渲染预览：

- **流式渲染**：`write_file` 执行时逐字符预览，体验类似 Claude Artifacts
- **双视图**：源码高亮 / 渲染预览（HTML、SVG、Markdown、Mermaid）
- **13+ 语言**：自动识别 JS、TS、Python、Java、SQL、CSS、JSON、HTML、Markdown 等
- **可拖拽宽度**：面板宽度可自由调整

### 🔌 MCP 协议集成

完整支持 **Model Context Protocol (MCP)**，作为客户端动态连接 MCP 服务器：

- **三种传输方式**：STDIO（本地进程）、SSE（服务器推送事件）、HTTP（REST API）
- **自动工具发现**：连接到 MCP 服务器后自动注册所有可用工具
- **资源访问**：支持读取 MCP 服务器提供的资源（文件、数据、API 响应）
- **提示词集成**：将 MCP 服务器的提示词模板集成到系统提示中
- **工具前缀**：避免多服务器工具命名冲突
- **健康检查**：定期 ping 检测连接状态，自动重连断开的服务器
- **HITL 支持**：可配置哪些 MCP 工具需要用户确认

**可连接的 MCP 服务器：**
- 官方服务器：filesystem、fetch、git、postgres、puppeteer...
- 第三方服务器：Tavily Search、GitHub、AWS、Kubernetes...
- 自定义服务器：用 Python/Node.js/Java 开发你自己的 MCP 服务器

详见：[MCP 集成文档](docs/mcp-integration.md)

### 💬 会话自动命名

首次发送消息时，后端调用 LLM 自动生成简短的会话标题，侧边栏实时更新。

### 👤 多 Agent 身份

支持配置多个 Agent Profile，控制不同场景下的工具权限和安全级别：

| Profile | 权限 | 说明 |
|---------|------|------|
| `main` | 全部工具 + 可派生子代理 | 默认主交互 Agent |
| `restricted` | 仅 read_file / http_get / memory_search | 受限只读 Agent |

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
│   ├── subagent/         # 🔀 子代理系统：spawn、announce、完成跟踪、运维
│   ├── skills/           # 🎯 技能系统：解析、注册、选择、热更新
│   ├── memory/           # 🧠 记忆系统：存储、分块、索引、检索
│   ├── agents/           # 👤 Agent 身份：Profile 配置、工具权限、沙箱级别
│   ├── llm/              # 🤖 LLM 接入：OpenAI 兼容客户端
│   ├── session/          # 💬 会话管理
│   ├── schedule/         # ⏰ 定时任务：Cron 调度、自动执行
│   ├── channel/          # 📡 渠道推送：邮件 SMTP、Webhook HTTP
│   ├── nodeconsole/      # 🖥️ 远程节点：SSH/K8s 连接、命令审计
│   ├── mcp/              # 🔌 MCP 协议：客户端管理、工具适配、健康检查
│   └── storage/          # 💾 持久化：JPA 实体、Repository、Flyway 迁移
├── miniclaw-ui/          # 🖥️ Vue 3 前端
│   ├── src/components/   # 组件：聊天、工具卡片、SubAgent 面板、Artifact 预览
│   ├── src/composables/  # 组合式：useChat、useWebSocket、useArtifact、useSubagent
│   └── src/views/        # 视图：Workspace 对话页、Settings 配置页
├── workspace/            # 📁 工作目录（技能、记忆都在这里）
│   ├── .miniclaw/skills/ # 技能目录
│   └── memory/           # 记忆目录
└── docs/                 # 📚 设计文档与实施计划
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
| Cron 调度 | ✅ | ✅ (Spring TaskScheduler + 渠道推送) |
| SubAgent | ✅ | ✅ (独立子会话 + 屏障等待) |
| 渠道推送 | - | ✅ (邮件 / Webhook) |
| 远程节点 | - | ✅ (SSH / K8s + 审计日志) |
| 文件预览 | ✅ (Artifacts) | ✅ (ArtifactPanel 流式渲染) |
| 多 Agent 身份 | ✅ | ✅ (Profile + 工具权限) |
| 多租户 | ✅ | ❌ (个人使用) |
| 分布式节点 | ✅ | ❌ |
| MCP 支持 | ✅ | ✅ (STDIO/SSE/HTTP + 健康检查) |
| Sandbox | ✅ | 🚧 (计划中) |

---

## Roadmap

### MVP (已完成)

- [x] ReAct 循环核心
- [x] 工具系统（10 个内置工具）
- [x] Skills 系统（Claude Skills 兼容）
- [x] 全局记忆（Markdown + 向量检索）
- [x] HITL 人工确认（智能危险命令检测）
- [x] SubAgent 子代理（独立子会话 + 屏障等待 + 右侧面板）
- [x] Cron 定时任务（Spring TaskScheduler + 渠道推送）
- [x] 渠道管理（邮件 SMTP / Webhook HTTP）
- [x] 远程节点管理（SSH / K8s + 审计日志）
- [x] 文件预览面板（流式渲染，类 Claude Artifacts）
- [x] 会话自动命名
- [x] 多 Agent 身份配置
- [x] MCP 协议集成（STDIO/SSE/HTTP + 工具/资源/提示词 + 健康检查）

### Next

- [ ] Sandbox 安全代码执行
- [ ] 更多内置技能

---

## 贡献

欢迎 PR！尤其欢迎：

- 🐛 Bug 修复
- 📝 文档改进
- 🎯 新技能（放到 `workspace/.miniclaw/skills/` 下）
- 🔧 新工具
- 🔌 MCP 服务器开发（用 Python/Node.js/Java 创建自定义 MCP 服务器）

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

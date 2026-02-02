# MiniClaw（Java 版）完善设计方案

> 基于 OpenClaw 复刻，同时作为 AI Agent 实战课程教学项目

## 1. 项目定位与技术选型

### 1.1 项目定位

MiniClaw 是 OpenClaw 的 Java 版复刻，同时作为 AI Agent 实战课程的教学项目。核心目标是让学员理解 Agent 的完整架构：从 WebSocket 网关、ReAct 执行循环、工具系统、Skills 机制、Memory 记忆系统到 Cron 自动化。

### 1.2 核心技术选型

| 领域 | 选型 | 理由 |
|------|------|------|
| Web 框架 | Spring Boot 3 + WebFlux | 响应式流，展示现代 Java |
| LLM 接入 | 自己写 OpenAI-compatible Client | 教学清晰，支持国产模型 + Ollama |
| 数据库 | PostgreSQL + pgvector | 统一存储 + 向量检索 + 全文检索 |
| Embedding | 可配置（远程 API / 本地模型） | adapter 模式，灵活切换 |
| ORM | Spring Data JPA + Flyway | PostgreSQL 天然搭配，减少样板代码 |
| 调度 | Quartz | 持久化任务、misfire 策略成熟 |
| 部署 | Docker Compose | 一键起环境，学员体验一致 |

### 1.3 项目结构

单 Maven 模块，按 package 分层：

```
miniclaw/
  src/main/java/com/example/miniclaw/
    gateway/          # WebSocket + RPC + EventBus
    runtime/          # AgentRuntime + SessionLane + ContextBuilder
    llm/              # LLM Client (OpenAI-compatible)
    tools/            # ToolRegistry + ToolDispatcher + 内置工具
    skills/           # Skill 解析 + 选择 + 编译
    memory/           # Memory 存储 + 索引 + 检索
    cron/             # Quartz 调度 + Delivery
    storage/          # JPA Entity + Repository
```

## 2. 架构分层与数据流

### 2.1 五层架构

```
┌─────────────────────────────────────────────────────────┐
│  Control Plane     │ Gateway (WebSocket + RPC + EventBus)
├─────────────────────────────────────────────────────────┤
│  Execution Plane   │ AgentRuntime (ReAct Loop) + SessionLane
├─────────────────────────────────────────────────────────┤
│  Extension Plane   │ Tools + Skills (自动选择 + 白名单)
├─────────────────────────────────────────────────────────┤
│  State Plane       │ PostgreSQL (runs/sessions/messages)
│                    │ + Markdown (Memory 真相源)
├─────────────────────────────────────────────────────────┤
│  Automation Plane  │ Quartz (Cron 定时唤起)
└─────────────────────────────────────────────────────────┘
```

### 2.2 一次 agent.run 的数据流

```
Client (Web UI / CLI)
    ↓ WebSocket RPC: agent.run
Gateway
    ↓ 创建 runId，写 DB (status=queued)
    ↓ 投递到 SessionLane (同 session 串行)
AgentRuntime
    ↓ ContextBuilder: 历史 + Skills注入 + MemoryRecall
    ↓ ReAct Loop:
    │   ├→ LLM 推理 (streaming)
    │   ├→ Tool 调用 (HITL 确认 → 执行 → 结果)
    │   └→ 循环直到完成或 max_steps
    ↓ Stream events 回推 Gateway
Gateway
    ↓ 推送给 Client: lifecycle.start → assistant.delta → tool.call → tool.result → lifecycle.end
DB
    ↓ 更新 run status=done, 写入 messages/tool_calls
```

## 3. Memory 子系统

### 3.1 核心设计原则

Markdown 为真相源，PostgreSQL 为派生索引。记忆可读、可 git、可手改，索引随时可从 Markdown 重建。

### 3.2 存储结构

```
workspace/
  memory/
    MEMORY.md           # 核心长期记忆（偏好、约束、关键事实）
    2024-01-15.md       # 日记式追加日志
    2024-01-16.md
    ...
```

### 3.3 两个核心工具

| 工具 | 功能 |
|------|------|
| `memory_search(query)` | 语义检索 Markdown chunk，返回 snippet + 文件路径 + 行号 + score |
| `memory_get(path, line, limit)` | 按路径读取指定记忆文件，支持行号范围 |

### 3.4 检索实现

- **Chunking**：~400 tokens/chunk，overlap ~80 tokens
- **向量检索**：pgvector 存 chunk embedding，余弦相似度 top-k
- **全文检索**：PostgreSQL tsvector 作为补充（关键词精确匹配）
- **返回格式**：snippet（~700 chars）+ 文件定位，不返回整篇文档

### 3.5 Pre-compaction Memory Flush

当 token 估算接近窗口阈值时，触发"静默回合"：

1. 强制让 LLM 总结当前对话关键状态
2. 写入 `memory/YYYY-MM-DD.md`
3. 标记本次 compaction 周期已 flush（避免重复）
4. 继续正常执行

## 4. Skills 系统

### 4.1 文件结构

```
.claude/skills/
  code-review/
    SKILL.md
  git-commit/
    SKILL.md
  ...
```

### 4.2 SKILL.md 格式

```yaml
---
name: code-review
description: 代码审查，检查代码质量和潜在问题
allowed-tools:
  - read_file
  - memory_search
confirm-before: []          # 可覆盖工具的 HITL 默认值
---

# Code Review Skill

你是一个代码审查专家...

用户请求: $ARGUMENTS
```

### 4.3 加载策略

| 阶段 | 加载内容 |
|------|---------|
| 启动时 | 只扫描 metadata（name/description），建立索引 |
| 触发时 | Lazy load 正文，编译成 system instruction |

### 4.4 触发方式

1. **手动触发**：`/code-review src/main/java` → 解析 skill name + arguments → 注入 $ARGUMENTS
2. **自动选择**：
   - 用户输入 → embedding → pgvector 检索 skill metadata → top-k 候选
   - LLM 从候选中选择最匹配的 skill（或不选）
   - 选中后自动注入

### 4.5 与工具系统集成

- `allowed-tools`：限制该 skill 下可调用的工具范围
- `confirm-before`：覆盖工具级别的 HITL 默认配置
- 无 skill 激活时，使用全局默认工具白名单

## 5. 工具系统与 HITL 机制

### 5.1 内置工具（MVP）

| 工具 | 功能 | 默认 HITL |
|------|------|----------|
| `read_file(path)` | 读取文件内容 | 否 |
| `write_file(path, content)` | 写入文件 | 是 |
| `http_get(url)` | HTTP GET 请求 | 否 |
| `shell(cmd)` | 执行 shell 命令 | 是 |
| `memory_search(query)` | 语义检索记忆 | 否 |
| `memory_get(path)` | 读取记忆文件 | 否 |

### 5.2 工具注册与执行流程

```
ToolRegistry
  ├── 工具元数据 (name, description, parameters schema)
  ├── 工具 handler (Function<Args, Result>)
  └── 默认 HITL 标记

ToolDispatcher
  ├── 校验参数 (JSON Schema)
  ├── 检查权限 (allowed-tools 白名单)
  ├── HITL 判断 (工具默认 → skill 覆盖)
  ├── 执行或等待确认
  └── 返回结果 + 发送 event
```

### 5.3 HITL 确认流程

```
Tool 调用触发
    ↓ 检查是否需要确认
    ↓ 需要 → 推送 tool.confirm_request 给 Client
    ↓ 等待用户响应 (approve / reject / modify)
    ↓ approve → 执行工具
    ↓ reject → 返回 "用户拒绝执行" 给 LLM
    ↓ modify → 用修改后的参数执行
```

### 5.4 权限分层

1. **工具级别**：默认 HITL 标记
2. **Skill 级别**：`confirm-before` 可覆盖（信任的 skill 可关闭确认）
3. **全局配置**：无 skill 时的默认白名单

## 6. Cron 自动化

### 6.1 功能定位

定时唤起 Agent 执行任务，产出可投递/可存档。典型场景：每日总结、定期检查、自动报告生成。

### 6.2 核心设计

| 组件 | 职责 |
|------|------|
| Quartz Scheduler | 调度引擎，JDBC JobStore 持久化 |
| CronJobService | 增删改查 cron job 配置 |
| CronTriggerJob | 到点执行：创建 session + run |
| DeliveryAdapter | 输出投递（Console / Webhook） |

### 6.3 Cron Job 配置

```json
{
  "jobId": "daily-summary",
  "cron": "0 0 9 * * ?",
  "prompt": "总结昨天的工作进展，写入 memory",
  "skill": "daily-summary",
  "delivery": "console"
}
```

### 6.4 执行流程

```
Quartz 触发
    ↓ 创建 session: cron:<jobId>
    ↓ 调用 AgentRuntime.run(prompt, skill)
    ↓ 执行完成 → 结果写入 session history
    ↓ DeliveryAdapter 投递输出
```

### 6.5 可靠性保障

- **持久化**：Quartz JDBC JobStore，重启不丢任务
- **Misfire 策略**：错过的任务可配置补跑或跳过
- **Session 隔离**：`cron:<jobId>` 命名，不污染用户会话
- **幂等**：同一触发时间点只执行一次

## 7. Gateway 与客户端

### 7.1 WebSocket RPC 协议

自定义 JSON-RPC 格式：

```json
// 请求
{
  "type": "request",
  "id": "req-001",
  "method": "agent.run",
  "payload": {
    "prompt": "帮我重构这个函数",
    "sessionId": "session-abc"
  }
}

// 响应
{
  "type": "response",
  "id": "req-001",
  "payload": { "runId": "run-xyz" }
}

// 事件推送
{
  "type": "event",
  "event": "assistant.delta",
  "runId": "run-xyz",
  "payload": { "content": "好的，" }
}
```

### 7.2 核心事件类型

| 事件 | 说明 |
|------|------|
| `lifecycle.start` | run 开始执行 |
| `assistant.delta` | LLM 流式输出片段 |
| `tool.call` | 发起工具调用 |
| `tool.confirm_request` | HITL 确认请求 |
| `tool.result` | 工具执行结果 |
| `lifecycle.end` | run 执行完成 |
| `lifecycle.error` | run 执行出错 |

### 7.3 客户端实现

| 客户端 | 定位 | 技术 |
|--------|------|------|
| Web UI | 教学演示，可视化 Agent 执行过程 | 单页面，原生 JS 或 Vue |
| CLI | 产品形态，命令行交互 | Java（复用 WebSocket 协议） |

### 7.4 Web UI 核心展示

- 对话流（用户输入 + Agent 回复）
- 流式输出（token 逐个显示）
- 工具调用可视化（调用 → 确认 → 结果）
- ReAct 步骤展示（Think → Act → Observe）

## 8. 模块实施路径

### Phase 1：基础设施 + 主链路

```
目标：WebSocket 连接 → 发送消息 → LLM 流式响应

模块清单：
├── Docker Compose (PostgreSQL + pgvector)
├── Gateway (WebSocket + RPC Router + EventBus)
├── Session & Run (状态机 + 串行 Lane)
├── LLM Client (OpenAI-compatible + SSE 流式解析)
└── 基础 DB schema (sessions/runs/messages)

验收：浏览器连 WS，发 agent.run，收到流式输出
```

### Phase 2：ReAct + 工具系统

```
目标：多步工具调用，完整 ReAct 循环

模块清单：
├── AgentRuntime (ReAct Loop Controller)
├── ContextBuilder (system + history)
├── ToolRegistry + ToolDispatcher
├── 内置工具 (read_file/write_file/http_get/shell)
├── HITL 确认机制
└── max_steps / timeout 防护

验收：给一个需要多步工具的任务，能完成 2+ 轮 tool call
```

### Phase 3：Skills 系统

```
目标：Skill 解析、手动触发、自动选择

模块清单：
├── SkillDiscoveryService (扫描 + metadata 索引)
├── SkillLoader (lazy load 正文)
├── SkillCompiler ($ARGUMENTS 注入)
├── EmbeddingClient (远程 + 本地 adapter)
├── 向量检索 (pgvector)
├── SkillSelector (手动 /skill + 自动选择)
└── allowed-tools / confirm-before 集成

验收：放入 skill 文件夹，/skill 生效 + 自动选择能匹配
```

### Phase 4：Memory 系统

```
目标：记忆存储、检索、Pre-compaction Flush

模块清单：
├── MemoryStore (写 Markdown)
├── MemoryIndexer (chunking + embedding + pgvector)
├── MemorySearchService (向量 + FTS 混合检索)
├── memory_search / memory_get 工具
└── PreCompactionFlushHook

验收：多轮对话后能检索早期信息，flush 后记忆可被检索
```

### Phase 5：Cron + 客户端完善

```
目标：定时任务 + Web UI + CLI

模块清单：
├── Quartz 配置 (JDBC JobStore)
├── CronJobService (CRUD)
├── CronTriggerJob (触发执行)
├── DeliveryAdapter (Console/Webhook)
├── Web UI (对话 + 工具可视化 + ReAct 展示)
└── CLI 客户端

验收：定时任务能执行并持久化，Web UI 完整展示执行过程
```

## 9. 风险策略与教学知识点

### 9.1 关键风险与应对

| 风险 | 应对策略 |
|------|---------|
| ReAct 无限循环 | max_steps + timeout + stop condition |
| Shell 工具安全 | 默认 HITL 确认 + allowed-tools 白名单 |
| LLM 调用不稳定 | 重试策略 + 超时控制 + 错误封装 |
| 向量检索质量 | FTS 兜底 + chunk 策略调优 |
| Memory flush 时机 | 软阈值触发（留 buffer），每周期只执行一次 |
| Skill 自动选择误判 | LLM 可选择"不使用 skill"，保守策略 |

### 9.2 教学知识点主线

| Phase | 核心知识点 |
|-------|-----------|
| Phase 1 | WebSocket 协议、RPC 设计、Reactor Flux/Sinks、SSE 流式解析 |
| Phase 2 | ReAct 循环思想、控制器模式、工具调用协议、HITL 交互设计 |
| Phase 3 | docs-as-config、lazy loading、Embedding 原理、向量检索（ANN vs 暴力） |
| Phase 4 | 记忆工程（真相源 vs 索引）、chunking 策略、compaction 与 flush |
| Phase 5 | 调度器原理、持久化任务、幂等与补偿、CLI 设计哲学 |

### 9.3 贯穿全程的工程思想

- **分层架构**：Control / Execution / Extension / State / Automation
- **Adapter 模式**：LLM Client、Embedding Client、Delivery Adapter
- **分层配置**：全局默认 → Skill 覆盖 → 运行时参数
- **最小权限**：allowed-tools 白名单 + HITL 确认
- **可观测性**：事件流、日志 MDC、工具耗时追踪

---

## 附录：与原设计文档的主要变更

| 原设计 | 变更后 | 理由 |
|--------|--------|------|
| PostgreSQL / SQLite 可选 | PostgreSQL + pgvector | 统一向量 + FTS + 存储 |
| JPA / jOOQ | Spring Data JPA + Flyway | PostgreSQL 天然搭配，减少样板代码 |
| Spring AI 可选 | 自己写 OpenAI-compatible Client | 教学清晰，理解原理 |
| Skills 先手动触发 | 手动 + 自动选择都做 | 体验完整 + 向量检索是教学重点 |
| Cron 可选 | 必做 | OpenClaw 核心亮点 |
| 无 HITL | 工具级 + Skill 级 HITL | 安全 + 教学价值 |
| 只 CLI 或只 Web | Web UI（教学）+ CLI（产品） | 各有定位 |

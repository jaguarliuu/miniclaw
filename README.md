# MiniClaw

> 基于 OpenClaw 设计理念的 Java 版 AI Agent 系统 - 学习与实践

![Java Version](https://img.shields.io/badge/Java-24-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4.5-green)
![Vue](https://img.shields.io/badge/Vue-3.5.27-4FC08D)
![License](https://img.shields.io/badge/License-MIT-blue)

---

## 📋 项目简介

**MiniClaw** 是 OpenClaw 的 Java 版复刻项目，同时也是一个 AI Agent 实战课程的教学项目。OpenClaw 是一个企业级的 AI Agent 系统，其核心理念是通过 **ReAct 循环**（Reasoning + Acting）实现智能体的自主决策和工具调用。

### 什么是 OpenClaw？

OpenClaw 是一个功能完整的 AI Agent 系统，核心特点包括：

- **System Prompt 模块化**：将工具、安全约束、技能、工作空间等信息组装成紧凑的系统提示词
- **Skills 机制**：支持 Claude Skills 兼容的 SKILL.md 格式，实现技能按需加载和自动选择
- **Memory 记忆系统**：基于 Markdown 的真相源 + 向量检索，支持长期记忆和上下文压缩
- **Cron 自动化**：定时任务调度，可投递到多个渠道
- **多层级 Prompt Mode**：支持 `full`、`minimal`、`none` 三种提示词模式，适应不同场景

### MiniClaw 的定位

MiniClaw 作为一个学习 OpenClaw 设计思路的实践项目，旨在：

1. **复刻核心架构**：完整实现 OpenClaw 的五层架构（控制平面、执行平面、扩展平面、状态平面、自动化平面）
2. **技术选型教育化**：使用 Java + Spring Boot 生态，展示企业级技术栈的应用
3. **教学友好**：代码结构清晰，注释详细，适合学习 AI Agent 系统的设计与实现
4. **功能精简但完整**：保留 OpenClaw 核心特性，去除部分高级功能，降低学习门槛

### 核心特性

| 特性 | OpenClaw | MiniClaw |
|------|----------|----------|
| ReAct 循环 | ✅ | ✅ |
| Claude Skills 兼容 | ✅ | ✅ |
| 工具系统 | ✅ | ✅ |
| HITL 人工确认 | ✅ | ✅ |
| 会话串行化 | ✅ | ✅ |
| 流式响应 | ✅ | ✅ |
| 技能热更新 | ✅ | ✅ |
| 向量检索 | ✅ | ✅ (pgvector) |
| Cron 自动化 | ✅ | ✅ |
| 多设备节点 | ✅ | ❌ (计划中) |
| 插件商店 | ✅ | ❌ |

---

## 🏗️ 技术栈

### 后端技术栈

| 类别 | 技术 | 版本 | 用途 |
|------|------|------|------|
| **语言** | Java | 24 | 开发语言 |
| **框架** | Spring Boot | 3.4.5 | 应用框架 |
| **Web** | Spring WebFlux | - | 响应式 Web 框架 |
| **数据库** | PostgreSQL | pg16 | 主数据库 |
| **向量** | pgvector | - | 向量检索扩展 |
| **ORM** | Spring Data JPA | - | 持久化框架 |
| **迁移** | Flyway | - | 数据库迁移 |
| **LLM 接入** | OpenAI Compatible | - | 统一 LLM 接口 |
| **响应式** | Project Reactor | - | 响应式流 |
| **工具库** | Lombok, Jackson | - | 代码简化与解析 |

### 前端技术栈

| 类别 | 技术 | 版本 | 用途 |
|------|------|------|------|
| **框架** | Vue 3 | 3.5.27 | 前端框架 |
| **语言** | TypeScript | 5.9.3 | 类型安全 |
| **构建** | Vite | 7.3.1 | 构建工具 |
| **路由** | Vue Router | 5.0.1 | 路由管理 |
| **Markdown** | Marked | 17.0.1 | Markdown 渲染 |
| **样式** | 原生 CSS | - | 简约黑白风格 |

---

## 🎯 核心架构

MiniClaw 采用与 OpenClaw 一致的**五层架构设计**：

```
┌─────────────────────────────────────────┐
│  Frontend (Web UI / CLI)                │
│  - WebSocket 实时通信                     │
│  - RPC 请求/响应                          │
└─────────────────────────────────────────┘
                ↕ WebSocket
┌─────────────────────────────────────────┐
│  Control Plane      │ Gateway (WS + RPC + EventBus)
│  - GatewayWebSocketHandler               │
│  - RpcRouter (RPC 路由分发)              │
│  - EventBus (事件广播)                   │
└─────────────────────────────────────────┘
                ↕
┌─────────────────────────────────────────┐
│  Execution Plane    │ AgentRuntime (ReAct Loop)
│  - AgentRuntime (ReAct 循环控制器)       │
│  - SessionLaneManager (会话串行化)       │
│  - ContextBuilder (System Prompt 构建)   │
│  - HitlManager (HITL 确认)               │
└─────────────────────────────────────────┘
                ↕
┌─────────────────────────────────────────┐
│  Extension Plane  │ Tools + Skills
│  - ToolRegistry (工具注册中心)           │
│  - SkillRegistry (技能注册中心)          │
│  - SkillSelector (技能选择器)            │
└─────────────────────────────────────────┘
                ↕
┌─────────────────────────────────────────┐
│  State Plane        │ DB + File System
│  - PostgreSQL (持久化)                   │
│  - Workspace (Markdown Memory + Skills)  │
└─────────────────────────────────────────┘
                ↕
┌─────────────────────────────────────────┐
│  Automation Plane  │ Quartz Cron
│  - CronScheduler (定时任务调度)          │
│  - CronJobService (任务管理)             │
│  - DeliveryAdapter (输出投递)            │
└─────────────────────────────────────────┘
```

### 与 OpenClaw 的架构对比

| 层级 | OpenClaw | MiniClaw |
|------|----------|----------|
| Frontend | CLI + Extensions | Web UI + CLI |
| Control Plane | WebSocket + RPC + EventBus | ✅ 完全复刻 |
| Execution Plane | ReAct Loop + Context Builder | ✅ 完全复刻 |
| Extension Plane | Tools + Skills + Memory | ✅ 完全复刻 |
| State Plane | SQLite/PostgreSQL + Workspace | ✅ 复刻 (PostgreSQL) |
| Automation Plane | Cron + Delivery | ✅ 复刻 (Quartz) |

---

## 🚀 快速开始

### 环境要求

- **Java**: 24+
- **Node.js**: 20.19.0 或 22.12.0+
- **PostgreSQL**: 16+ (推荐使用 pgvector 镜像)
- **Maven**: 3.6+

### 1. 启动数据库

```bash
docker-compose up -d
```

### 2. 配置环境变量

创建 `src/main/resources/application-local.yml`:

```yaml
llm:
  endpoint: https://api.deepseek.com/v1/chat/completions
  api-key: your-api-key
  model: deepseek-chat

tools:
  workspace: ./workspace
```

### 3. 启动后端

```bash
# 编译
mvn clean package

# 运行
java -jar target/miniclaw.jar
```

### 4. 启动前端

```bash
cd miniclaw-ui
npm install
npm run dev
```

访问 `http://localhost:5173` 即可使用。

---

## 📚 核心模块

### 1. Gateway 控制平面

**职责**：WebSocket 连接管理、RPC 路由、事件推送

**RPC 方法**：
- `agent.run` - 执行 Agent
- `agent.cancel` - 取消执行
- `session.create/list/delete` - 会话管理
- `message.list` - 消息列表
- `tool.confirm` - HITL 确认
- `skill.list/get` - 技能查询

**事件类型**：
- `lifecycle.start/end/error` - 生命周期事件
- `assistant.delta` - 流式文本增量
- `tool.call/result/confirm_request` - 工具调用事件
- `step.completed` - 步骤完成事件

### 2. Runtime 执行平面

**AgentRuntime - ReAct 循环控制器**：

```
executeLoop() {
    while (!maxStepsReached) {
        // 1. 调用 LLM（带 tools）
        result = streamLlmCall(messages, tools)

        // 2. 执行 tool_calls
        if (result.hasToolCalls()) {
            for (toolCall : result.toolCalls) {
                // HITL 确认（如需要）
                if (requiresHitl(toolCall)) {
                    decision = waitForUserConfirmation()
                    if (!decision.approved) continue
                }
                // 执行工具
                toolResult = executeTool(toolCall)
                messages.add(toolResult)
            }
        }

        // 3. 激活技能
        if (parseSkill(result.content)) {
            activateSkill(skillName)
            continue
        }

        // 4. 无工具调用 → 结束
        break
    }
}
```

### 3. Tools 工具系统

**内置工具**：

| 工具名 | 功能 | HITL |
|--------|------|------|
| `read_file` | 读取文件 | 否 |
| `write_file` | 写入文件 | 否 |
| `shell` | 执行 shell 命令 | 是 |
| `shell_start` | 启动后台进程 | 是 |
| `shell_kill` | 终止进程 | 否 |
| `shell_status` | 查询进程状态 | 否 |
| `http_get` | HTTP GET 请求 | 否 |

**安全机制**：
- 路径白名单（限制在 workspace 和 skill 资源目录）
- 文件大小限制（默认 1MB）
- 命令超时（30 秒）
- 命令输出截断（32KB）

### 4. Skills 技能系统

**Claude Skills 兼容**：
- 扫描 `.miniclaw/skills/` 目录（与 OpenClaw 的 `.claude/skills/` 对应）
- 支持 `SKILL.md` 格式（YAML frontmatter + Markdown body）
- 支持热更新（基于 Java NIO WatchService）

**技能元数据**：
```yaml
---
name: skill-name
description: Skill description
allowed-tools: [read_file, write_file]
confirm-before: [shell]
requires:
  env: ["API_KEY"]
---

Skill description in Markdown...

用户请求: $ARGUMENTS
```

**与 OpenClaw 的 Skills 机制对比**：

| 特性 | OpenClaw | MiniClaw |
|------|----------|----------|
| 文件位置 | `.claude/skills/` | `.miniclaw/skills/` |
| Discovery | 多位置扫描 (workspace/user/bundled) | ✅ 项目级扫描 |
| Gating 检查 | env/bins/config/os | ✅ 环境变量检查 |
| Lazy Loading | ✅ 只加载元数据，正文按需加载 | ✅ 完全复刻 |
| 自动选择 | Embedding 检索 + LLM 选择 | ✅ 复刻 |
| 手动触发 | `/skill-name args` | ✅ 复刻 |

---

## 🌐 前后端交互

### WebSocket + RPC 双协议

MiniClaw 采用与 OpenClaw 类似的 WebSocket RPC 协议设计：

**RPC 请求**：
```json
{
  "type": "request",
  "id": "req-123",
  "method": "agent.run",
  "payload": {
    "sessionId": "xxx",
    "prompt": "Hello"
  }
}
```

**RPC 响应**：
```json
{
  "type": "response",
  "id": "req-123",
  "payload": {
    "runId": "xxx",
    "sessionId": "xxx"
  }
}
```

**事件推送**：
```json
{
  "type": "event",
  "event": "assistant.delta",
  "runId": "xxx",
  "payload": {
    "content": "Hello"
  }
}
```

### 数据流

```
用户输入 → sendMessage() → RPC:agent.run
    → 立即返回 runId
    → 开始监听事件流
    → assistant.delta → 追加文本块
    → tool.call/confirm_request → 创建工具块
    → lifecycle.end → 保存完整消息
```

### 与 OpenClaw 的通信协议对比

| 特性 | OpenClaw | MiniClaw |
|------|----------|----------|
| 协议 | WebSocket + JSON-RPC | ✅ 完全复刻 |
| 事件类型 | lifecycle/delta/tool/skill | ✅ 完全复刻 |
| 流式传输 | SSE / WebSocket | ✅ WebSocket |
| 子 Agent 支持 | promptMode: minimal | ✅ 支持 |

---

## 📁 项目结构

```
miniclaw/
├── src/main/java/com/jaguarliu/ai/
│   ├── gateway/              # 控制平面
│   │   ├── ws/               # WebSocket
│   │   ├── rpc/              # RPC 路由
│   │   └── events/           # 事件总线
│   ├── runtime/              # 执行平面
│   │   ├── AgentRuntime.java     # ReAct 循环
│   │   ├── SessionLaneManager.java
│   │   ├── ContextBuilder.java
│   │   └── HitlManager.java
│   ├── tools/                # 工具系统
│   │   ├── builtin/          # 内置工具
│   │   └── ToolRegistry.java
│   ├── skills/               # 技能系统
│   │   ├── registry/         # 技能注册
│   │   ├── parser/           # SKILL.md 解析
│   │   ├── selector/         # 技能选择
│   │   └── watcher/          # 文件监听
│   ├── llm/                  # LLM 接入
│   ├── session/              # 会话管理
│   └── storage/              # 数据持久化
├── miniclaw-ui/              # 前端
│   ├── src/
│   │   ├── components/       # Vue 组件
│   │   ├── composables/      # 组合式函数
│   │   ├── views/            # 页面视图
│   │   └── types/            # TypeScript 类型
│   └── package.json
├── docs/                     # 文档
├── pom.xml                   # Maven 配置
├── docker-compose.yml        # Docker 配置
└── application.yml           # 应用配置
```

---

## 🔧 配置说明

### 环境变量

| 变量 | 说明 | 默认值 |
|------|------|--------|
| `LLM_ENDPOINT` | LLM API 端点 | - |
| `LLM_API_KEY` | LLM API 密钥 | - |
| `LLM_MODEL` | 模型名称 | - |
| `TOOLS_WORKSPACE` | 工作目录 | `./workspace` |

### 支持的 LLM Provider

- DeepSeek
- 通义千问
- Ollama
- OpenAI
- GLM
- 其他 OpenAI 兼容接口

---

## 📖 使用示例

### 基础对话

```
你：帮我读取 README.md 文件
AI：[调用 read_file 工具] → 读取成功 → 返回文件内容
```

### 多步任务

```
你：帮我分析项目结构并生成报告
AI：[调用 read_file] 读取 pom.xml
    [调用 read_file] 读取主要源码
    [分析] 生成结构报告
```

### 使用技能

```
你：/git-analyzer
AI：激活 git-analyzer 技能，限制只使用 git 相关工具...
```

---

## 🔒 安全特性

- **HITL 确认机制**：危险工具需人工确认
- **路径白名单**：限制文件访问范围
- **超时保护**：防止无限循环
- **参数校验**：JSON Schema 验证
- **命令沙箱**：Shell 命令执行限制

---

## 🎓 技术亮点

| 特性 | 实现方式 | OpenClaw 对应 |
|------|----------|---------------|
| **响应式编程** | Spring WebFlux + Reactor | Node.js Async Streams |
| **事件驱动** | EventBus (Reactor Sinks) | Event Emitters |
| **可扩展性** | Tool 接口 + Claude Skills | Plugin System |
| **安全性** | HITL + 路径白名单 + 超时 | ✅ 完全复刻 |
| **可观测性** | 结构化日志 + 事件流追踪 | ✅ 完全复刻 |

---

## 📚 设计理念

### OpenClaw 的核心设计思想

1. **模块化 System Prompt**：将工具、安全约束、技能等信息按需组装
2. **Docs-as-Config**：技能、配置都以 Markdown 文件形式管理
3. **最小权限原则**：通过 allowed-tools 白名单限制工具访问
4. **记忆分层**：真相源（Markdown）+ 派生索引（向量）
5. **流式优先**：所有操作都通过事件流实时推送

### MiniClaw 的教学价值

- **架构学习**：理解企业级 AI Agent 的分层设计
- **技术实践**：掌握 Spring WebFlux、响应式编程、WebSocket 等
- **AI 原理**：深入理解 ReAct 循环、Tool Calling、System Prompt 构建
- **工程能力**：学习设计模式、错误处理、性能优化

---

---

## 📝 开发指南

### 添加新工具

1. 实现 `Tool` 接口
2. 注册到 `ToolRegistry`
3. 在配置中添加权限控制

### 添加新技能

1. 在 `.miniclaw/skills/` 创建新目录
2. 编写 `SKILL.md` 文件
3. 系统自动扫描并加载

---

## 🤝 贡献指南

欢迎提交 Issue 和 Pull Request！

---

## 📄 许可证

MIT License

---

## 🔗 相关资源

### 项目文档
- [设计文档](docs/design.md) - MiniClaw 完整设计文档
- [实施计划](docs/plans/) - 分阶段实施路径
- [OpenClaw System](docs/openclaw/system.md) - OpenClaw 系统提示词设计

### 学习资源
- **OpenClaw** - 原版 AI Agent 系统
- **Claude Skills** - 技能系统规范
- **ReAct Paper** - ReAct 循环原始论文
- **Spring WebFlux** - 响应式编程框架

---

## 📝 开发指南

### 添加新工具

1. 实现 `Tool` 接口
2. 注册到 `ToolRegistry`
3. 在配置中添加权限控制

### 添加新技能

1. 在 `.miniclaw/skills/` 创建新目录
2. 编写 `SKILL.md` 文件（YAML frontmatter + Markdown body）
3. 系统自动扫描并加载

### 理解 OpenClaw 设计

推荐阅读顺序：
1. [OpenClaw System](docs/openclaw/system.md) - 理解系统提示词设计
2. [设计文档](docs/design.md) - MiniClaw 完整架构
3. 源代码 - 逐步阅读各层实现

---

## 🤝 贡献指南

欢迎提交 Issue 和 Pull Request！

### 贡献方向
- 🐛 Bug 修复
- ✨ 新功能（需符合 OpenClaw 设计理念）
- 📝 文档改进
- 🎓 教学内容优化

---

## 📮 联系方式

如有问题或建议，欢迎提 Issue。

---

## 🙏 致谢

MiniClaw 是 OpenClaw 的 Java 版复刻，感谢 OpenClaw 项目提供的设计灵感和架构参考。

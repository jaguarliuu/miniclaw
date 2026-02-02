# MiniClaw（Java 版）实施计划

## 0) 项目目标与范围

### 0.1 目标（MVP 必达）

- **Gateway（WebSocket 控制平面）**：统一入口，RPC 调用 + 事件流推送
- **Session & Run**：runId、状态机、会话历史、同 session 串行 lane
- **Agent Runtime（ReAct Loop）**：LLM 推理 ↔ 工具调用 ↔ 观察回灌，流式输出
- **Tool System**：工具注册、schema 校验、权限 gating（按 skill 的 allowed-tools）
- **Claude Skills 原生解析**：扫描 .claude/skills/**/SKILL.md，按需加载，支持 /skill 手动触发 + $ARGUMENTS 注入
- **Memory 子系统**：Markdown 为真相源 + 索引检索 + pre-compaction flush（防丢上下文）
- **Cron 定时唤起**：到点创建 cron:<jobId> session run，产出可投递/可存档

### 0.2 非目标（先不做）

- 多端 device node（移动端配对/权限模型完整版）
- 插件商店与强隔离 sandbox（MVP 预留接口）
- 分布式网关/多机调度

## 1) 总体架构（Java 版分层）

### 1.1 分层视图

- **Control Plane**：Gateway WS + RPC Router + Event Bus
- **Execution Plane**：Agent Runtime（ReAct Loop）+ Session Lane Queue
- **Extension Plane**：Tools + Skills（Claude Skills）
- **State Plane**：DB（runs/messages/tool_calls/cron_jobs）+ Workspace 文件系统（memory md、skills）
- **Automation Plane**：Cron Scheduler（Quartz）+ Delivery（输出投递）

### 1.2 数据流（一次 agent.run）

```text
Client/CLI/Web → Gateway WS RPC
    → 创建 runId + 写 DB(queued)
    → 投递到 session lane（串行）
    → AgentRuntime:
         ContextBuilder(历史 + Skills注入 + MemoryRecall)
         ReActLoop: LLM -> tool.call -> tool.result -> LLM ...
         Stream events 回推 Gateway（assistant.delta/tool.call/tool.result）
    → 写 DB(done/error) + 追加 messages/tool_calls
    → 可选：Delivery（发到 channel / 控制台）
```

## 2) 技术栈推荐（稳定、教学友好）

### 2.1 Spring / 网络层

- Spring Boot 3
- Spring WebFlux（强烈推荐，用 Reactor 做 streaming 与 WS 事件非常自然）
- **WebSocket**：Spring WebFlux WebSocketHandler

> 如果你不想引入 WebFlux，也可用 Spring MVC + SseEmitter/线程池，但 WS+streaming 会绕一些。

### 2.2 LLM 接入

两条路线任选（建议先 A 再 B）：

#### A：OpenAI-compatible HTTP Client（自己写 Adapter）

- **优点**：最稳、可控、教学清晰；DeepSeek/许多厂商都兼容

#### B：Spring AI

- **优点**：更省事，工具调用/结构化输出封装好

> **注意**：ReAct 循环控制器仍建议你自己写（更像 OpenClaw/pi-agent-core）

### 2.3 持久化

- **MVP**：PostgreSQL（推荐） 或 SQLite（本地教学简化）
- **ORM**：Spring Data JPA / jOOQ（你想强调 SQL 工程可用 jOOQ）
- **迁移**：Flyway

### 2.4 技能与配置

- **YAML**：SnakeYAML
- **Markdown**：无需渲染，直接当文本注入即可
- **文件监听（可选）**：Java NIO WatchService

### 2.5 Memory 检索

#### 关键词检索：

- PostgreSQL tsvector（推荐，工程味足）
- 或 Lucene（纯 Java、可演示倒排索引）

#### 向量检索（选修）：

- pgvector / Qdrant / Milvus（MVP 不强制）

### 2.6 Cron 调度

- Quartz（推荐：支持持久化 job store、misfire 策略成熟）
- 或 Spring Scheduling（简单，但持久化/补偿要自己做）

### 2.7 可观测性（课程加分项）

- **日志**：Logback + MDC
- **指标**：Micrometer + Prometheus（选修）
- **tracing**：OpenTelemetry（选修）

## 3) 模块拆解与实施路径（含知识点与验收）

按“先跑通主链路 → 再扩展技能/记忆/自动化”安排。

### Phase 1：主链路可跑（Gateway + Run + Streaming）【第 1~2 周】

#### 模块 A：Gateway（WebSocket 控制平面）

**A1. 功能**
- WS 连接管理
- RPC：agent.run、agent.wait、session.create/list/switch
- 事件推送：按 runId 推送流式事件

**A2. 架构**
- GatewayWebSocketHandler
- RpcRouter（method → handler）
- EventBus（runId → Flux<AgentEvent>）
- Reactor：Sinks.Many<AgentEvent>

**A3. 知识点**
- WS 协议与连接生命周期
- RPC message framing（type/id/method/payload）
- Reactor Flux、Sinks、背压/缓冲

**A4. 验收**
- 浏览器/CLI 连 WS 能发 agent.run
- 能收到 lifecycle.start、assistant.delta、lifecycle.end

#### 模块 B：Session & Run（串行 lane + 状态机）

**B1. 功能**
- runId：queued/running/done/error/canceled
- 同 session 串行：避免竞态

**B2. 架构**
- SessionLaneManager
- Map<sessionId, Scheduler> 或 Map<sessionId, SerialExecutor>
- RunService：创建 run、更新状态、查询结果
- DB 表（最小）：
    - sessions
    - runs
    - messages

**B3. 知识点**
- 并发一致性：lane queue/lock
- 状态机设计
- DB 事务与幂等（runId）

**B4. 验收**
- 同一 session 连续发 3 个 run，会严格顺序执行
- run 状态变化可查询

### Phase 2：Agentic（ReAct）+ Tools【第 3~4 周】

#### 模块 C：LLM 接入层（Java 版“pi-ai”）

**C1. 功能**
- LLMClient 统一接口：sync + streaming
- 模型/endpoint 可配置
- 输出解析：文本 delta、tool call（如果模型支持）

**C2. 架构**
- LlmClient interface
- OpenAiCompatibleLlmClient（WebClient + SSE streaming）
- UsageTracker（tokens/cost 可选）

**C3. 知识点**
- HTTP SSE 流式解析
- provider adapter 设计
- 错误封装与重试策略（超时、429）

**C4. 验收**
- LLM 流式输出能稳定转成 assistant.delta

#### 模块 D：Tool System（注册、校验、执行、回灌）

**D1. 功能**
- ToolRegistry：工具元数据 + handler
- 参数校验：JSON schema / Pydantic-like（Java 用 Jackson + 手写校验/Bean Validation）
- ToolPolicy：全局白名单、skill 白名单
- ToolDispatcher：执行后把结果写入 messages，并发 event

**D2. 内置工具（MVP）**
- read_file(path)
- write_file(path, content)
- http_get(url)
- shell(cmd)（默认禁用或强确认）
- memory_search(query)（Phase 3 接入）

**D3. 知识点**
- 工具调用协议设计（tool.call / tool.result）
- 参数校验与安全（路径白名单、命令执行风险）
- 可观测性：工具耗时、失败原因

**D4. 验收**
- 模型发起 tool call，系统执行并回灌观察，再继续生成回答

#### 模块 E：Agent Runtime（ReAct Loop Controller）

**E1. 功能**
- 控制循环：Think → Act(tool) → Observe → Think
- 最大步数、超时、取消
- 产出事件流（对 Gateway）

**E2. 架构（建议类）**
- AgentRuntime
- ReActLoopController
- ContextBuilder（system + history + skills + memory recall）
- ToolDispatcher
- 输出：Flux<AgentEvent> 或回调写入 EventBus

**E3. 知识点**
- Agentic 的“控制器”思想（框架提供零件，你控制 loop）
- 防止无限循环：max_steps、stop condition
- 轨迹记录（tool_calls、observations）

**E4. 验收**
- 给一个需要多步工具的任务，至少完成 2 轮 tool call 并最终回答

### Phase 3：Claude Skills + Memory【第 5~7 周】

#### 模块 F：Claude Skills（SKILL.md）解析与注入

**F1. 功能（兼容业界规范）**
- 扫描 .claude/skills/**/SKILL.md
- 解析 YAML frontmatter + Markdown body
- 启动只加载 metadata（name/description/flags）
- 手动触发 /skill-name args，支持 $ARGUMENTS 注入
- allowed-tools 生效：限制工具调用范围
- （选修）自动选择 skill：metadata 检索 top-k → LLM 选择

**F2. 架构**
- SkillDiscoveryService：扫描 + 建索引（metadata）
- SkillLoader：lazy load 正文
- SkillCompiler：拼接成 system instruction block（含参数替换）
- SkillSelector：手动/自动触发
- 与 runtime 集成：ContextBuilder 注入 skill block；ToolPolicy 读取 allowed-tools

**F3. 知识点**
- docs-as-config、frontmatter 解析
- lazy loading 与性能
- prompt 组合策略与安全边界（disable-model-invocation）

**F4. 验收**
- 放入一套 Claude skill 文件夹，系统能识别并 /skill 生效
- skill 的 allowed-tools 能限制工具调用

#### 模块 G：Memory（Markdown 真相源 + 检索 + flush 防丢）

**G1. 功能**
- 真相源：workspace memory/*.md
- memory_get：按文件+行号读取
- memory_search：关键词/语义检索返回 snippet+定位
- pre-compaction flush：接近 token 阈值，先写入当日 memory 再 compact

**G2. 架构**
- MemoryStore：写 md（append）
- MemoryIndexer：
    - MVP：Postgres tsvector 或 Lucene 倒排
    - 索引内容：chunk（段落/固定长度）
- MemorySearchService：查询 → top-k → 返回 snippet+引用
- PreCompactionFlushHook：在 ReActLoopController 里触发（或在 run 开始时检查 token 预算）

**G3. 知识点**
- 记忆系统：真相源 vs 派生索引
- chunking 与可解释召回
- compaction 与 flush 的工程动机

**G4. 验收**
- 多轮对话后能用 memory_search 找到早期信息
- 触发 flush 后，记忆文件有新增条目且可被检索

### Phase 4：Cron 自动化【第 8 周】

#### 模块 H：Cron（定时唤起大模型干活）

**H1. 功能**
- 增删改查 cron job（WS RPC 或 CLI）
- 到点触发：sessionKey = cron:<jobId>，创建 run
- 支持 misfire 策略（错过如何补跑）
- 输出可写入 session/history
- （选修）Delivery：发到指定 channel（先实现 console delivery 即可）

**H2. 架构**
- Quartz scheduler + JDBC JobStore
- CronJobService（管理配置）
- CronTriggerJob（到点执行：调用 AgentService.run(...)）
- DeliveryAdapter（Console / Webhook / Telegram）

**H3. 知识点**
- 调度器与持久化任务
- session 隔离命名（cron:<id>）
- 可靠性：幂等、补偿、重试

**H4. 验收**
- 每分钟定时生成一段总结，写入 session 与 DB
- 重启服务 cron 仍能继续跑（持久化）

## 4) 项目目录结构建议（Java）

```text
miniclaw/
  app/                    # Spring Boot 启动
  gateway/
    ws/                   # WebSocket handler
    rpc/                  # rpc router & message models
    events/               # EventBus (Sinks, Flux)
  runtime/
    agent/                # AgentRuntime, ReActLoopController
    session/              # SessionLaneManager
    context/              # ContextBuilder
  llm/
    api/                  # LLMClient interface
    openai/               # OpenAI-compatible adapter
  tools/
    registry/             # ToolRegistry, ToolDef
    policy/               # ToolPolicy (allowed tools)
    builtins/             # read/write/http/shell
  skills/
    discovery/            # scan skills folders
    parser/               # SKILL.md frontmatter parsing
    selector/             # /skill or auto selection
    compiler/             # compile to instruction block
  memory/
    store/                # write markdown
    index/                # FTS (postgres/lucene)
    search/               # memory_search/get
    flush/                # pre-compaction hook
  cron/
    quartz/               # scheduler config + jobs
    service/              # CRUD for cron jobs
  storage/
    entity/               # JPA entities
    repo/                 # repositories
    migration/            # flyway
  cli/ (optional)
```

## 5) 教学知识点地图（你讲课的“主线”）

- **架构**：控制平面/执行平面/扩展平面/状态平面
- **WebSocket + RPC + 事件流**：协议设计与 streaming
- **并发一致性**：session lane 串行化、run 状态机
- **Agentic（ReAct）**：为什么必须自己写 loop、如何可控停机
- **工具系统**：schema 校验、权限 gating、风险控制
- **Claude Skills**：业界规范解析、按需加载、工具白名单与安全
- **记忆工程**：md 真相源 + 索引 + flush 防丢
- **自动化**：Quartz、持久化调度、隔离 sessionKey

## 6) 关键风险与策略（非常建议你在计划里强调）

- **ReAct 无限循环**：max_steps + stop condition + timeout
- **shell 工具安全**：默认禁用/强确认/路径白名单
- **skill 自动触发风险**：先只支持手动 /skill，再做自动选择
- **memory 先用关键词检索**：FTS/tsvector 可解释、稳定；向量作为选修
- **LLM provider 不稳定**：优先 OpenAI-compatible 统一接口，降低适配成本

## 7) 里程碑与可交付成果（最适合课程节奏）

- **M1（2 周）**：WS Gateway + run/session + streaming
- **M2（2 周）**：ReAct loop + tools（能完成多步任务）
- **M3（2~3 周）**：Claude Skills + Memory（flush + search）
- **M4（1 周）**：Cron 定时任务 + delivery（可选）

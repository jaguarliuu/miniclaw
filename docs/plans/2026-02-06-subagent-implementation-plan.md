# SubAgent System Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 在 MiniClaw 中实现可生产可教学的 SubAgent 派生执行能力（spawn / 异步执行 / announce / 运维接口 / 并发隔离）。

**Architecture:** 基于现有 `AgentRuntime + SessionLaneManager + ToolDispatcher + Session/Run/Message` 增量演进。核心链路为：主 run 调用 `sessions_spawn` → 创建子 session/run → 入 `subagent lane` 异步执行 → 完成后 announce 回父 session。通过 `runKind + lane + parentRunId` 建立父子关系，默认禁止嵌套 spawn，确保安全与稳定。

**Tech Stack:** Spring Boot 3, WebFlux, Reactor, Spring Data JPA, Flyway, PostgreSQL

---

## Task SA-01: 数据库模型扩展（Session/Run/Subagent Outbox）

**Files:**
- Create: `src/main/resources/db/migration/V3__subagent_schema.sql`
- Modify: `src/main/java/com/jaguarliu/ai/storage/entity/SessionEntity.java`
- Modify: `src/main/java/com/jaguarliu/ai/storage/entity/RunEntity.java`
- Create: `src/main/java/com/jaguarliu/ai/storage/entity/SubagentOutboxEntity.java`
- Create: `src/main/java/com/jaguarliu/ai/storage/repository/SubagentOutboxRepository.java`

**Step 1: Write the failing test**
- 新建迁移测试（如果当前无迁移测试，可用启动集成测试替代）：
  - 启动后验证 `sessions` 新字段存在：`agent_id, session_kind, session_key, parent_session_id, created_by_run_id`
  - 验证 `runs` 新字段存在：`agent_id, run_kind, lane, parent_run_id, requester_session_id, deliver`
  - 验证 `subagent_outbox` 表存在

**Step 2: Run test to verify it fails**
- Run: `mvn -Dtest=*Migration* test`
- Expected: FAIL（列/表不存在）

**Step 3: Write minimal implementation**
- 编写 `V3__subagent_schema.sql`：新增字段、索引、外键（可空外键避免历史数据迁移失败）
- 更新 `SessionEntity` / `RunEntity` 映射字段
- 新增 `SubagentOutboxEntity` + Repository

**Step 4: Run test to verify it passes**
- Run: `mvn -Dtest=*Migration* test`
- Expected: PASS

**Step 5: Commit**
- `git add src/main/resources/db/migration/V3__subagent_schema.sql src/main/java/com/jaguarliu/ai/storage/entity/SessionEntity.java src/main/java/com/jaguarliu/ai/storage/entity/RunEntity.java src/main/java/com/jaguarliu/ai/storage/entity/SubagentOutboxEntity.java src/main/java/com/jaguarliu/ai/storage/repository/SubagentOutboxRepository.java`
- `git commit -m "feat(subagent): [SA-01] extend schema for parent-child runs and outbox"`

---

## Task SA-02: Agent Profile 配置与注册中心

**Files:**
- Create: `src/main/java/com/jaguarliu/ai/agents/AgentsProperties.java`
- Create: `src/main/java/com/jaguarliu/ai/agents/model/AgentProfile.java`
- Create: `src/main/java/com/jaguarliu/ai/agents/AgentRegistry.java`
- Modify: `src/main/resources/application.yml`
- Test: `src/test/java/com/jaguarliu/ai/agents/AgentRegistryTest.java`

**Step 1: Write the failing test**
- 覆盖：
  - 加载默认 profile `main`
  - profile 的 `tools.allow/deny` 生效
  - `default-agent` 缺失时回退逻辑明确

**Step 2: Run test to verify it fails**
- Run: `mvn -Dtest=AgentRegistryTest test`
- Expected: FAIL（类不存在）

**Step 3: Write minimal implementation**
- 新增配置模型 + 注册器
- 在 `application.yml` 增加示例 profiles

**Step 4: Run test to verify it passes**
- Run: `mvn -Dtest=AgentRegistryTest test`
- Expected: PASS

**Step 5: Commit**
- `git add src/main/java/com/jaguarliu/ai/agents src/main/resources/application.yml src/test/java/com/jaguarliu/ai/agents/AgentRegistryTest.java`
- `git commit -m "feat(subagent): [SA-02] add agent profiles and registry"`

---

## Task SA-03: RunContext 扩展（runKind/agentId/parent）

**Files:**
- Modify: `src/main/java/com/jaguarliu/ai/runtime/RunContext.java`
- Modify: `src/main/java/com/jaguarliu/ai/runtime/AgentRuntime.java`
- Test: `src/test/java/com/jaguarliu/ai/runtime/RunContextTest.java`

**Step 1: Write the failing test**
- 覆盖：
  - `RunContext` 新字段构造与 getter
  - `runKind=subagent` 标识可被 runtime 读取

**Step 2: Run test to verify it fails**
- Run: `mvn -Dtest=RunContextTest test`
- Expected: FAIL

**Step 3: Write minimal implementation**
- 给 `RunContext` 增加：`agentId`, `runKind`, `parentRunId`, `depth`
- `AgentRuntime.executeLoop(...)` 支持传入并沿用这些字段

**Step 4: Run test to verify it passes**
- Run: `mvn -Dtest=RunContextTest test`
- Expected: PASS

**Step 5: Commit**
- `git add src/main/java/com/jaguarliu/ai/runtime/RunContext.java src/main/java/com/jaguarliu/ai/runtime/AgentRuntime.java src/test/java/com/jaguarliu/ai/runtime/RunContextTest.java`
- `git commit -m "feat(subagent): [SA-03] extend run context for subagent metadata"`

---

## Task SA-04: SessionService / RunService 增加子会话创建能力

**Files:**
- Modify: `src/main/java/com/jaguarliu/ai/session/SessionService.java`
- Modify: `src/main/java/com/jaguarliu/ai/session/RunService.java`
- Test: `src/test/java/com/jaguarliu/ai/session/SubagentSessionRunServiceTest.java`

**Step 1: Write the failing test**
- 覆盖：
  - `createSubagentSession(parentSessionId, createdByRunId, agentId)`
  - `createSubagentRun(subSessionId, parentRunId, requesterSessionId, agentId, prompt)`
  - 字段正确落库

**Step 2: Run test to verify it fails**
- Run: `mvn -Dtest=SubagentSessionRunServiceTest test`
- Expected: FAIL

**Step 3: Write minimal implementation**
- 新增 Service API 与实现
- 保持原有 API 不破坏

**Step 4: Run test to verify it passes**
- Run: `mvn -Dtest=SubagentSessionRunServiceTest test`
- Expected: PASS

**Step 5: Commit**
- `git add src/main/java/com/jaguarliu/ai/session/SessionService.java src/main/java/com/jaguarliu/ai/session/RunService.java src/test/java/com/jaguarliu/ai/session/SubagentSessionRunServiceTest.java`
- `git commit -m "feat(subagent): [SA-04] add parent-child session/run creation APIs"`

---

## Task SA-05: LaneAwareQueueManager（main/subagent 并发配额）

**Files:**
- Create: `src/main/java/com/jaguarliu/ai/runtime/LaneAwareQueueManager.java`
- Modify: `src/main/java/com/jaguarliu/ai/runtime/SessionLaneManager.java` (保留兼容包装或迁移调用)
- Modify: `src/main/resources/application.yml`
- Test: `src/test/java/com/jaguarliu/ai/runtime/LaneAwareQueueManagerTest.java`

**Step 1: Write the failing test**
- 覆盖：
  - 同 `sessionId` 串行
  - `main` 与 `subagent` lane 分配独立并发上限
  - subagent 不影响 main 的最低可用并发

**Step 2: Run test to verify it fails**
- Run: `mvn -Dtest=LaneAwareQueueManagerTest test`
- Expected: FAIL

**Step 3: Write minimal implementation**
- 新建 Lane-aware 管理器
- 在配置中加入：`agent.lane.main-max-concurrency`, `agent.lane.subagent-max-concurrency`

**Step 4: Run test to verify it passes**
- Run: `mvn -Dtest=LaneAwareQueueManagerTest test`
- Expected: PASS

**Step 5: Commit**
- `git add src/main/java/com/jaguarliu/ai/runtime/LaneAwareQueueManager.java src/main/java/com/jaguarliu/ai/runtime/SessionLaneManager.java src/main/resources/application.yml src/test/java/com/jaguarliu/ai/runtime/LaneAwareQueueManagerTest.java`
- `git commit -m "feat(subagent): [SA-05] implement lane-aware queue with main/subagent limits"`

---

## Task SA-06: SubagentService（spawn 主流程）

**Files:**
- Create: `src/main/java/com/jaguarliu/ai/subagent/SubagentService.java`
- Create: `src/main/java/com/jaguarliu/ai/subagent/model/SubagentSpawnRequest.java`
- Create: `src/main/java/com/jaguarliu/ai/subagent/model/SubagentSpawnResult.java`
- Test: `src/test/java/com/jaguarliu/ai/subagent/SubagentServiceTest.java`

**Step 1: Write the failing test**
- 覆盖：
  - spawn 成功创建子 session/run 并入 `subagent lane`
  - 返回 `subSessionId/subRunId/sessionKey`
  - 非法 `agentId` 拒绝

**Step 2: Run test to verify it fails**
- Run: `mvn -Dtest=SubagentServiceTest test`
- Expected: FAIL

**Step 3: Write minimal implementation**
- 实现 `spawn(parentRunContext, request)`
- 依赖 `SessionService`, `RunService`, `LaneAwareQueueManager`, `AgentRegistry`

**Step 4: Run test to verify it passes**
- Run: `mvn -Dtest=SubagentServiceTest test`
- Expected: PASS

**Step 5: Commit**
- `git add src/main/java/com/jaguarliu/ai/subagent src/test/java/com/jaguarliu/ai/subagent/SubagentServiceTest.java`
- `git commit -m "feat(subagent): [SA-06] add subagent spawn service"`

---

## Task SA-07: `sessions_spawn` 工具与 no-nested 守卫

**Files:**
- Create: `src/main/java/com/jaguarliu/ai/tools/builtin/SessionsSpawnTool.java`
- Modify: `src/main/java/com/jaguarliu/ai/tools/ToolExecutionContext.java`
- Modify: `src/main/java/com/jaguarliu/ai/runtime/AgentRuntime.java`
- Test: `src/test/java/com/jaguarliu/ai/tools/builtin/SessionsSpawnToolTest.java`

**Step 1: Write the failing test**
- 覆盖：
  - main run 调用 `sessions_spawn` 成功
  - subagent run 调用 `sessions_spawn` 返回拒绝（禁止嵌套）
  - 工具 schema 与返回结构正确

**Step 2: Run test to verify it fails**
- Run: `mvn -Dtest=SessionsSpawnToolTest test`
- Expected: FAIL

**Step 3: Write minimal implementation**
- 工具调用 `SubagentService.spawn(...)`
- `ToolExecutionContext` 增加 `runKind/agentId/parentRunId`
- `AgentRuntime.setupToolExecutionContext(...)` 注入新字段

**Step 4: Run test to verify it passes**
- Run: `mvn -Dtest=SessionsSpawnToolTest test`
- Expected: PASS

**Step 5: Commit**
- `git add src/main/java/com/jaguarliu/ai/tools/builtin/SessionsSpawnTool.java src/main/java/com/jaguarliu/ai/tools/ToolExecutionContext.java src/main/java/com/jaguarliu/ai/runtime/AgentRuntime.java src/test/java/com/jaguarliu/ai/tools/builtin/SessionsSpawnToolTest.java`
- `git commit -m "feat(subagent): [SA-07] add sessions_spawn tool with no-nested guard"`

---

## Task SA-08: 子任务完成回传（Announce）

**Files:**
- Create: `src/main/java/com/jaguarliu/ai/subagent/SubagentAnnounceService.java`
- Modify: `src/main/java/com/jaguarliu/ai/gateway/events/AgentEvent.java`
- Modify: `src/main/java/com/jaguarliu/ai/gateway/rpc/handler/AgentRunHandler.java`
- Modify: `src/main/java/com/jaguarliu/ai/session/MessageService.java`
- Test: `src/test/java/com/jaguarliu/ai/subagent/SubagentAnnounceServiceTest.java`

**Step 1: Write the failing test**
- 覆盖：
  - 子 run `done/error` 后父 session 获得 announce 消息
  - 事件 `subagent.announced` 正确发布
  - 消息包含 `subRunId/subSessionId/duration/status`

**Step 2: Run test to verify it fails**
- Run: `mvn -Dtest=SubagentAnnounceServiceTest test`
- Expected: FAIL

**Step 3: Write minimal implementation**
- `AgentRunHandler` 在子 run 完成路径调用 announce service
- 扩展 event 枚举与 payload

**Step 4: Run test to verify it passes**
- Run: `mvn -Dtest=SubagentAnnounceServiceTest test`
- Expected: PASS

**Step 5: Commit**
- `git add src/main/java/com/jaguarliu/ai/subagent/SubagentAnnounceService.java src/main/java/com/jaguarliu/ai/gateway/events/AgentEvent.java src/main/java/com/jaguarliu/ai/gateway/rpc/handler/AgentRunHandler.java src/main/java/com/jaguarliu/ai/session/MessageService.java src/test/java/com/jaguarliu/ai/subagent/SubagentAnnounceServiceTest.java`
- `git commit -m "feat(subagent): [SA-08] announce subagent completion to parent session"`

---

## Task SA-09: SubAgent 运维 RPC（list/stop/send）

**Files:**
- Create: `src/main/java/com/jaguarliu/ai/subagent/SubagentOpsService.java`
- Create: `src/main/java/com/jaguarliu/ai/gateway/rpc/handler/SubagentListHandler.java`
- Create: `src/main/java/com/jaguarliu/ai/gateway/rpc/handler/SubagentStopHandler.java`
- Create: `src/main/java/com/jaguarliu/ai/gateway/rpc/handler/SubagentSendHandler.java`
- Test: `src/test/java/com/jaguarliu/ai/gateway/rpc/handler/SubagentHandlersTest.java`

**Step 1: Write the failing test**
- 覆盖：
  - `subagent.list` 返回父子关系与状态
  - `subagent.stop` 触发取消
  - `subagent.send` 追加任务并产生新子 run

**Step 2: Run test to verify it fails**
- Run: `mvn -Dtest=SubagentHandlersTest test`
- Expected: FAIL

**Step 3: Write minimal implementation**
- 实现 Ops Service 与 3 个 handler
- 注册到 `RpcRouter` 自动发现链路

**Step 4: Run test to verify it passes**
- Run: `mvn -Dtest=SubagentHandlersTest test`
- Expected: PASS

**Step 5: Commit**
- `git add src/main/java/com/jaguarliu/ai/subagent/SubagentOpsService.java src/main/java/com/jaguarliu/ai/gateway/rpc/handler/SubagentListHandler.java src/main/java/com/jaguarliu/ai/gateway/rpc/handler/SubagentStopHandler.java src/main/java/com/jaguarliu/ai/gateway/rpc/handler/SubagentSendHandler.java src/test/java/com/jaguarliu/ai/gateway/rpc/handler/SubagentHandlersTest.java`
- `git commit -m "feat(subagent): [SA-09] add subagent ops RPC handlers"`

---

## Task SA-10: Outbox 重试与恢复（可选但推荐）

**Files:**
- Create: `src/main/java/com/jaguarliu/ai/subagent/SubagentOutboxService.java`
- Modify: `src/main/java/com/jaguarliu/ai/subagent/SubagentAnnounceService.java`
- Test: `src/test/java/com/jaguarliu/ai/subagent/SubagentOutboxServiceTest.java`

**Step 1: Write the failing test**
- 覆盖：
  - announce 失败落 outbox
  - 定时/触发重试成功后标记 delivered
  - 幂等去重

**Step 2: Run test to verify it fails**
- Run: `mvn -Dtest=SubagentOutboxServiceTest test`
- Expected: FAIL

**Step 3: Write minimal implementation**
- 实现 outbox enqueue/retry/mark
- 在 announce 中接入

**Step 4: Run test to verify it passes**
- Run: `mvn -Dtest=SubagentOutboxServiceTest test`
- Expected: PASS

**Step 5: Commit**
- `git add src/main/java/com/jaguarliu/ai/subagent/SubagentOutboxService.java src/main/java/com/jaguarliu/ai/subagent/SubagentAnnounceService.java src/test/java/com/jaguarliu/ai/subagent/SubagentOutboxServiceTest.java`
- `git commit -m "feat(subagent): [SA-10] add outbox retry for subagent announce"`

---

## Task SA-11: 文档与配置示例更新

**Files:**
- Modify: `README.md`
- Modify: `Agent.md`
- Modify: `src/main/resources/application.yml`

**Step 1: Write doc checklist**
- 列出需要补充的段落：
  - SubAgent 功能说明
  - 配置示例
  - RPC / 工具调用示例

**Step 2: Implement docs updates**
- 更新 README 与 Agent.md 对应章节

**Step 3: Sanity check**
- 人工检查链接和示例命令

**Step 4: Commit**
- `git add README.md Agent.md src/main/resources/application.yml`
- `git commit -m "docs(subagent): [SA-11] document usage, config and ops"`

---

## 最终验收清单（E2E）

1. 主会话请求触发 `sessions_spawn`，即时返回子任务标识
2. 子任务异步执行期间，主会话可继续处理后续输入
3. 子任务结束后父会话收到 announce（成功与失败都可回传）
4. `subagent.list` 可见状态；`subagent.stop` 可取消
5. `subagent` 不能再 `sessions_spawn`
6. main/subagent lane 配额生效（压测下主交互无明显饥饿）

---

## 推荐执行顺序

- 必做链路：`SA-01 -> SA-02 -> SA-03 -> SA-04 -> SA-05 -> SA-06 -> SA-07 -> SA-08`
- 运维增强：`SA-09`
- 稳定性增强：`SA-10`
- 文档收口：`SA-11`


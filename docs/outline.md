# 课程大纲

## 第一阶段：认知与体验

### 第0章：课程导学
- OpenClaw 现象深度解析
- AI Agent 全景介绍
- MiniClaw 五层架构全景图
- 课程学习路线图

### 第1章：深度理解 AI Agent
- 快速部署：10 分钟启动你的第一个 AI Agent
- OpenClaw 核心能力全景
- 实战案例：代码审查、学习助手、工作助手、数据分析
- Skills、Memory、Cron 深度解析

### 第2章：MiniClaw 实战应用
- MiniClaw vs 普通 Agent
- 复杂多步任务编排
- 跨会话记忆召回
- Skills 动态加载
- HITL 人在回路
- SessionLane 并发控制

## 第二阶段：核心开发

### 第3章：开发环境与基础底座
- Docker Compose 编排基础设施
- Flyway 数据库版本控制
- Spring Boot 项目骨架

### 第4章：LLM 对接与流式输出
- OpenAI 兼容客户端实现
- SSE 流式输出原理
- Reactor Flux 响应式流
- 多模型适配

### 第5章：WebSocket 网关与 RPC 协议
- WebSocket 连接管理
- 自定义 RPC 协议
- EventBus 事件驱动
- Session 状态管理
- SessionLane 并发控制

### 第6章：前端界面开发
- Vue 3 + Vite + TypeScript
- WebSocket 客户端封装
- RPC 客户端封装
- 对话界面流式渲染

## 第三阶段：Agent 能力

### 第7章：工具系统
- Tool 定义与注册
- Function Calling 协议
- ToolDispatcher 调度器
- 内置工具实现
- HITL 人在回路确认
- 工具权限控制

### 第8章：ReAct 循环
- ReAct 论文原理
- AgentRuntime 循环控制器
- 上下文管理
- 终止条件设计
- 错误处理与重试
- 取消机制

### 第9章：Skills 系统
- SKILL.md 格式解析
- Skill 发现与索引
- Lazy Loading 按需加载
- Skill Gating 环境检查
- Embedding 语义检索
- Skill 自动选择
- 工具白名单

### 第10章：Memory 系统
- Markdown 真相源
- Chunking 分块策略
- 向量索引（pgvector）
- 混合检索降级
- Pre-compaction Flush
- 索引重建

## 第四阶段：高级特性

### 第11章：Cron 自动化
- Quartz 调度器
- CronJobService 管理
- DeliveryAdapter 输出投递
- Misfire 策略
- 幂等设计

### 第12章：MCP 协议
- MCP 核心设计理念
- Client/Server/Transport 模型
- 工具与资源暴露
- Claude Desktop 生态接入
- 权限控制与沙箱隔离

### 第13章：部署上线
- CLI 客户端开发
- Docker Compose 一键部署
- 环境变量配置
- 日志与监控
- 性能优化

## 第五阶段：实战与进阶

### 第14章：实战场景
- 代码审查 Agent
- 日报生成 Agent
- 数据分析 Agent
- 运维监控 Agent
- 学习助手 Agent
- 自定义 Skill 开发

### 第15章：架构师之路
- 五层架构设计回顾
- 框架对比分析（LangChain、LlamaIndex）
- 企业级 Agent 系统设计
- AI Agent 未来展望

---

## 预估时长

约 **35-40 小时**（12 个章节）

## 课程难度

**中高级**（需要 Java/Spring Boot 基础）

## 技术储备

### 必须掌握
- Java 基础语法（集合、泛型、Lambda）
- Spring Boot 基础（依赖注入、配置文件）
- 数据库基础（SQL、事务）
- HTTP 协议基础

### 建议了解（课程会讲解）
- WebSocket 协议
- 响应式编程（Reactor）
- Docker 基础
- 大模型 API 调用

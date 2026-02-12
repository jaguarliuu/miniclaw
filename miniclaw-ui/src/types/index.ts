/**
 * MiniClaw Types
 * 与后端 RPC 协议对齐
 */

// Session (后端返回 name 字段)
export interface Session {
  id: string
  name: string
  createdAt: string
  updatedAt: string
}

// Message
export interface Message {
  id: string
  sessionId: string
  runId: string
  role: 'user' | 'assistant'
  content: string                 // 纯文本内容（用于用户消息或简单显示）
  createdAt: string
  blocks?: StreamBlock[]          // 交错的内容块（用于 assistant 消息的详细显示）
  attachedFiles?: AttachedFile[]  // 用户消息附带的文件（仅前端展示用）- 向后兼容
  attachedContexts?: AttachedContext[]  // 用户消息附带的上下文（新字段）
}

// Run
export interface Run {
  id: string
  sessionId: string
  prompt: string
  status: 'queued' | 'running' | 'done' | 'error'
  createdAt: string
  updatedAt: string
}

// Tool Call (工具调用状态)
export interface ToolCall {
  callId: string
  toolName: string
  arguments: Record<string, unknown>
  status: 'pending' | 'confirmed' | 'executing' | 'success' | 'error' | 'rejected'
  result?: string
  requiresConfirm: boolean
}

// Stream Block (流式内容块，用于交错显示文本和工具调用)
export interface StreamBlock {
  id: string
  type: 'text' | 'tool' | 'skill' | 'subagent' | 'file'
  content?: string      // type === 'text' 时的文本内容
  toolCall?: ToolCall   // type === 'tool' 时的工具调用
  skillActivation?: SkillActivation  // type === 'skill' 时的技能激活
  subagent?: SubagentInfo            // type === 'subagent' 时的子代理信息
  file?: SessionFile                 // type === 'file' 时的文件信息
}

// Skill Activation (技能激活信息)
export interface SkillActivation {
  skillName: string
  source: 'manual' | 'auto'
}

// Session File (会话生成的文件)
export interface SessionFile {
  id: string
  sessionId: string
  runId: string
  filePath: string    // 相对路径 e.g. "report.pdf"
  fileName: string
  fileSize: number
  createdAt: string
}

// File Created Event Payload
export interface FileCreatedPayload {
  fileId: string
  path: string
  fileName: string
  size: number
}

// WebSocket Connection State
export type ConnectionState = 'disconnected' | 'connecting' | 'connected' | 'error'

// RPC Request
// 格式: {"type":"request","id":"xxx","method":"xxx","payload":{...}}
export interface RpcRequest {
  type: 'request'
  id: string
  method: string
  payload?: unknown
}

// RPC Response
// 格式: {"type":"response","id":"xxx","payload":{...}} 或 {"type":"response","id":"xxx","error":{...}}
export interface RpcResponse {
  type: 'response'
  id: string
  payload?: unknown
  error?: {
    code: string
    message: string
  }
}

// 事件名 -> payload 类型映射（把你已经定义的 payload 都用上）
export interface RpcEventPayloadMap {
  'tool.call': ToolCallPayload
  'tool.result': ToolResultPayload
  'tool.confirm_request': ToolConfirmRequestPayload
  'step.completed': StepCompletedPayload
  'skill.activated': SkillActivatedPayload
  'file.created': FileCreatedPayload

  'subagent.spawned': SubagentSpawnedPayload
  'subagent.started': SubagentStartedPayload
  'subagent.announced': SubagentAnnouncedPayload
  'subagent.failed': SubagentFailedPayload

  // 这些你暂时没定义 payload 结构，就先用 unknown 占位
  'lifecycle.start': unknown
  'lifecycle.end': unknown
  'lifecycle.error': unknown
  'assistant.delta': unknown
  'session.renamed': unknown
}

// 用 AgentEventType 约束事件名，并用映射表推断 payload 类型
export type RpcEvent<K extends AgentEventType = AgentEventType> = {
  type: 'event'
  event: K
  runId: string
  payload?: K extends keyof RpcEventPayloadMap ? RpcEventPayloadMap[K] : unknown
}

// Agent Event Types
export type AgentEventType =
  | 'lifecycle.start'
  | 'lifecycle.end'
  | 'lifecycle.error'
  | 'assistant.delta'
  | 'step.completed'
  | 'tool.call'
  | 'tool.result'
  | 'tool.confirm_request'
  | 'skill.activated'
  | 'file.created'
  | 'session.renamed'
  | 'subagent.spawned'
  | 'subagent.started'
  | 'subagent.announced'
  | 'subagent.failed'

// Tool Event Payloads
export interface ToolCallPayload {
  callId: string
  toolName: string
  arguments: Record<string, unknown>
}

export interface ToolResultPayload {
  callId: string
  success: boolean
  content: string
}

export interface ToolConfirmRequestPayload {
  callId: string
  toolName: string
  arguments: Record<string, unknown>
}

export interface StepCompletedPayload {
  step: number
  maxSteps: number
  elapsedSeconds: number
}

// Skill Activated Event Payload
export interface SkillActivatedPayload {
  skillName: string
  source: 'manual' | 'auto'
}

// Skill (技能列表项)
export interface Skill {
  name: string
  description: string
  available: boolean
  unavailableReason: string
  priority: number
  tokenCost: number
}

// SkillDetail (技能详情)
export interface SkillDetail extends Skill {
  body: string
  allowedTools: readonly string[]
  confirmBefore: readonly string[]
}

// ==================== SubAgent Types ====================

// SubAgent 状态
export type SubagentStatus = 'queued' | 'running' | 'completed' | 'failed'

// SubAgent 信息（用于前端 block 渲染）
export interface SubagentInfo {
  subRunId: string
  subSessionId: string
  sessionKey: string
  agentId: string
  task: string
  lane: string
  status: SubagentStatus
  result?: string
  error?: string
  durationMs?: number
  startedAt?: number    // timestamp ms
  streamBlocks?: StreamBlock[]
  toolCallIndex?: Record<string, ToolCall>
}

// SubAgent Event Payloads
export interface SubagentSpawnedPayload {
  subRunId: string
  subSessionId: string
  sessionKey: string
  agentId: string
  task: string
  lane: string
}

export interface SubagentStartedPayload {
  subRunId: string
}

export interface SubagentAnnouncedPayload {
  subRunId: string
  subSessionId: string
  sessionKey: string
  agentId: string
  task: string
  status: string
  result?: string
  error?: string
  durationMs: number
}

export interface SubagentFailedPayload {
  subRunId: string
  agentId: string
  task: string
  error: string
}

// ==================== Node Console Types ====================

export type ConnectorType = 'ssh' | 'k8s' | 'db'
export type AuthType = 'password' | 'key' | 'kubeconfig' | 'token'
export type SafetyPolicy = 'strict' | 'standard' | 'relaxed'

export interface NodeInfo {
  id: string
  alias: string
  displayName: string | null
  connectorType: ConnectorType
  host: string | null
  port: number | null
  username: string | null
  authType: AuthType | null
  tags: string | null
  safetyPolicy: SafetyPolicy
  lastTestedAt: string | null
  lastTestSuccess: boolean | null
  createdAt: string
  updatedAt: string
}

export interface NodeRegisterPayload {
  alias: string
  displayName?: string
  connectorType: ConnectorType
  host?: string
  port?: number
  username?: string
  authType?: AuthType
  credential: string
  tags?: string
  safetyPolicy?: SafetyPolicy
}

// ==================== Channel Types ====================

export type ChannelType = 'email' | 'webhook'

export interface ChannelInfo {
  id: string
  name: string
  type: ChannelType
  enabled: boolean
  config: EmailChannelConfig | WebhookChannelConfig
  lastTestedAt: string | null
  lastTestSuccess: boolean | null
  createdAt: string
  updatedAt: string
}

export interface EmailChannelConfig {
  host: string
  port: number
  username: string
  from: string
  tls: boolean
}

export interface WebhookChannelConfig {
  url: string
  method: string
  headers: Record<string, string>
  secret: boolean
}

export interface ChannelCreatePayload {
  name: string
  type: ChannelType
  config: EmailChannelConfig | WebhookChannelConfig
  credential?: string
}

// ==================== Audit Log Types ====================

export interface AuditLogEntry {
  id: string
  eventType: string
  runId: string | null
  sessionId: string | null
  nodeAlias: string | null
  connectorType: string | null
  toolName: string | null
  command: string | null
  safetyLevel: string | null
  safetyPolicy: string | null
  hitlRequired: boolean
  hitlDecision: string | null
  resultStatus: string
  resultSummary: string | null
  durationMs: number | null
  createdAt: string
}

export interface AuditLogPage {
  logs: AuditLogEntry[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

// ==================== Schedule Types ====================

export interface ScheduleInfo {
  id: string
  name: string
  cronExpr: string
  prompt: string
  channelId: string
  channelType: ChannelType
  emailTo: string | null
  emailCc: string | null
  enabled: boolean
  lastRunAt: string | null
  lastRunSuccess: boolean | null
  lastRunError: string | null
  createdAt: string
  updatedAt: string
}

export interface ScheduleCreatePayload {
  name: string
  cronExpr: string
  prompt: string
  channelId: string
  channelType: ChannelType
  emailTo?: string
  emailCc?: string
}

// ==================== LLM Config Types ====================

export interface LlmConfig {
  endpoint: string
  apiKey: string    // 脱敏值
  model: string
  configured: boolean
}

export interface LlmConfigInput {
  endpoint: string
  apiKey: string
  model: string
}

export interface LlmTestResult {
  success: boolean
  message: string
  latencyMs?: number
}

export interface AppStatus {
  llmConfigured: boolean
}

// ==================== Tool Config Types ====================

export interface ToolConfig {
  trustedDomains: { defaults: string[]; user: string[] }
  searchProviders: SearchProviderEntry[]
  hitl: {
    alwaysConfirmTools: string[]
    dangerousKeywords: string[]
  }
}

export interface SearchProviderEntry {
  type: string
  displayName: string
  apiKey: string      // GET 响应中为脱敏值
  enabled: boolean
  keyRequired: boolean
}

// Slash Command Autocomplete
export interface SlashCommandItem {
  type: 'tool' | 'skill'
  name: string           // e.g. "read_file", "web_search"
  description: string    // from backend
  displayName: string    // formatted: "/read_file" or "/skillname"
}

// ==================== Context Attachment Types ====================

/** 上下文类型 */
export type ContextType = 'file' | 'folder' | 'web' | 'doc' | 'code' | 'rule' | 'workspace' | 'problems'

/** 附加的上下文（统一接口，支持多种类型） */
export interface AttachedContext {
  id: string              // 前端唯一标识
  type: ContextType       // 上下文类型
  displayName: string     // 显示名称
  uploading?: boolean     // 上传中

  // File 类型字段
  filePath?: string       // workspace 相对路径（后端返回）
  filename?: string       // 原始文件名
  size?: number           // 文件大小（字节）

  // Folder 类型字段
  folderPath?: string     // 文件夹路径

  // Web 类型字段
  url?: string            // 网页 URL

  // 预留字段（未来实现）
  docId?: string          // Doc 类型的文档 ID
  codeSnippet?: string    // Code 类型的代码片段
  ruleContent?: string    // Rule 类型的规则内容
}

/** 向后兼容：AttachedFile 类型别名 */
export type AttachedFile = AttachedContext

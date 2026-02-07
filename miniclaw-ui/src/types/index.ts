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
  type: 'text' | 'tool' | 'skill' | 'subagent'
  content?: string      // type === 'text' 时的文本内容
  toolCall?: ToolCall   // type === 'tool' 时的工具调用
  skillActivation?: SkillActivation  // type === 'skill' 时的技能激活
  subagent?: SubagentInfo            // type === 'subagent' 时的子代理信息
}

// Skill Activation (技能激活信息)
export interface SkillActivation {
  skillName: string
  source: 'manual' | 'auto'
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

// RPC Event (服务端主动推送)
// 格式: {"type":"event","event":"xxx","runId":"xxx","payload":{...}}
export interface RpcEvent {
  type: 'event'
  event: string
  runId: string
  payload?: unknown
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

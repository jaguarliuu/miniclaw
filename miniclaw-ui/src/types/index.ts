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
  type: 'text' | 'tool'
  content?: string      // type === 'text' 时的文本内容
  toolCall?: ToolCall   // type === 'tool' 时的工具调用
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

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
  content: string
  createdAt: string
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

// Agent Event Types (不带 agent. 前缀)
export type AgentEventType =
  | 'lifecycle.start'
  | 'lifecycle.end'
  | 'lifecycle.error'
  | 'assistant.delta'

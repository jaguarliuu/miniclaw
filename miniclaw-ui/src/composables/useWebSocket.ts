import { ref, readonly } from 'vue'
import type { ConnectionState, RpcRequest, RpcResponse, RpcEvent, AgentEventType } from '@/types'

const WS_URL = import.meta.env.DEV
  ? 'ws://localhost:8080/ws'
  : `ws://${window.location.host}/ws`

// Global state
const connectionState = ref<ConnectionState>('disconnected')
const socket = ref<WebSocket | null>(null)
const pendingRequests = new Map<string, {
  resolve: (value: RpcResponse) => void
  reject: (reason: Error) => void
}>()

// Event handlers
// 用一个“最宽”的事件类型来存 handler（内部存储用）
type AnyRpcEvent = RpcEvent<AgentEventType>

// Event handlers
const eventHandlers = new Map<string, Set<(event: AnyRpcEvent) => void>>()

let reconnectTimer: ReturnType<typeof setTimeout> | null = null
let intentionalDisconnect = false
let requestIdCounter = 0

function generateId(): string {
  return `req-${Date.now()}-${++requestIdCounter}`
}

function connect() {
  if (socket.value?.readyState === WebSocket.OPEN) {
    return
  }

  intentionalDisconnect = false
  connectionState.value = 'connecting'

  const ws = new WebSocket(WS_URL)

  ws.onopen = () => {
    connectionState.value = 'connected'
    console.log('[WS] Connected')
  }

  ws.onclose = () => {
    connectionState.value = 'disconnected'
    socket.value = null
    console.log('[WS] Disconnected')

    // Auto reconnect after 3s, unless disconnect was intentional
    if (!intentionalDisconnect && !reconnectTimer) {
      reconnectTimer = setTimeout(() => {
        reconnectTimer = null
        connect()
      }, 3000)
    }
  }

  ws.onerror = (error) => {
    connectionState.value = 'error'
    console.error('[WS] Error:', error)
  }

  ws.onmessage = (event) => {
    try {
      const data = JSON.parse(event.data)
      console.log('[WS] Received:', data)

      // Check message type
      if (data.type === 'response' && data.id && pendingRequests.has(data.id)) {
        // It's a response to a pending request
        const { resolve } = pendingRequests.get(data.id)!
        pendingRequests.delete(data.id)
        resolve(data as RpcResponse)
        return
      }

      if (data.type === 'event' && data.event) {
        const rpcEvent = data as AnyRpcEvent
        const handlers = eventHandlers.get(rpcEvent.event)
        if (handlers) handlers.forEach((h) => h(rpcEvent))

        const wildcardHandlers = eventHandlers.get('*')
        if (wildcardHandlers) wildcardHandlers.forEach((h) => h(rpcEvent))
      }
    } catch (e) {
      console.error('[WS] Parse error:', e)
    }
  }

  socket.value = ws
}

function disconnect() {
  intentionalDisconnect = true
  if (reconnectTimer) {
    clearTimeout(reconnectTimer)
    reconnectTimer = null
  }
  socket.value?.close()
  socket.value = null
  connectionState.value = 'disconnected'
}

function waitForConnection(timeoutMs = 10000): Promise<void> {
  return new Promise((resolve, reject) => {
    if (socket.value?.readyState === WebSocket.OPEN) {
      resolve()
      return
    }

    const timeout = setTimeout(() => {
      clearInterval(check)
      reject(new Error('WebSocket connection timeout'))
    }, timeoutMs)

    const check = setInterval(() => {
      if (socket.value?.readyState === WebSocket.OPEN) {
        clearTimeout(timeout)
        clearInterval(check)
        resolve()
      }
    }, 50)
  })
}

async function request<T = unknown>(method: string, payload?: unknown): Promise<T> {
  // Wait for connection if socket is still connecting
  if (!socket.value || socket.value.readyState !== WebSocket.OPEN) {
    await waitForConnection()
  }

  const id = generateId()
  const rpcRequest: RpcRequest = {
    type: 'request',
    id,
    method,
    payload
  }

  console.log('[WS] Sending:', rpcRequest)

  return new Promise((resolve, reject) => {
    pendingRequests.set(id, {
      resolve: (response) => {
        if (response.error) {
          reject(new Error(response.error.message))
        } else {
          resolve(response.payload as T)
        }
      },
      reject
    })

    socket.value!.send(JSON.stringify(rpcRequest))

    // Timeout after 30s
    setTimeout(() => {
      if (pendingRequests.has(id)) {
        pendingRequests.delete(id)
        reject(new Error('Request timeout'))
      }
    }, 30000)
  })
}

// ✅ onEvent：事件名是 AgentEventType 时，payload 自动推断
function onEvent<K extends AgentEventType>(
    eventType: K,
    handler: (event: RpcEvent<K>) => void
): () => void

// ✅ 兜底：允许监听任意字符串（比如你以后有 * 或后端新事件）
function onEvent(
    eventType: string,
    handler: (event: AnyRpcEvent) => void
): () => void

function onEvent(
    eventType: string,
    handler: (event: AnyRpcEvent) => void
) {
  if (!eventHandlers.has(eventType)) {
    eventHandlers.set(eventType, new Set())
  }
  eventHandlers.get(eventType)!.add(handler)

  return () => {
    eventHandlers.get(eventType)?.delete(handler)
  }
}

export function useWebSocket() {
  return {
    state: readonly(connectionState),
    connect,
    disconnect,
    request,
    onEvent
  }
}

import { ref, readonly } from 'vue'
import type { ConnectionState, RpcRequest, RpcResponse, RpcEvent } from '@/types'

const WS_URL = 'ws://localhost:8080/ws'

// Global state
const connectionState = ref<ConnectionState>('disconnected')
const socket = ref<WebSocket | null>(null)
const pendingRequests = new Map<string, {
  resolve: (value: RpcResponse) => void
  reject: (reason: Error) => void
}>()

// Event handlers
const eventHandlers = new Map<string, Set<(event: RpcEvent) => void>>()

let reconnectTimer: ReturnType<typeof setTimeout> | null = null
let requestIdCounter = 0

function generateId(): string {
  return `req-${Date.now()}-${++requestIdCounter}`
}

function connect() {
  if (socket.value?.readyState === WebSocket.OPEN) {
    return
  }

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

    // Auto reconnect after 3s
    if (!reconnectTimer) {
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
        // It's an event
        const rpcEvent = data as RpcEvent
        const handlers = eventHandlers.get(rpcEvent.event)
        if (handlers) {
          handlers.forEach((handler) => handler(rpcEvent))
        }

        // Also emit to wildcard handlers
        const wildcardHandlers = eventHandlers.get('*')
        if (wildcardHandlers) {
          wildcardHandlers.forEach((handler) => handler(rpcEvent))
        }
      }
    } catch (e) {
      console.error('[WS] Parse error:', e)
    }
  }

  socket.value = ws
}

function disconnect() {
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

function onEvent(eventType: string, handler: (event: RpcEvent) => void) {
  if (!eventHandlers.has(eventType)) {
    eventHandlers.set(eventType, new Set())
  }
  eventHandlers.get(eventType)!.add(handler)

  // Return cleanup function
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

import { ref, computed } from 'vue'
import { useWebSocket } from './useWebSocket'
import type { Session, Message, Run, RpcEvent } from '@/types'

const sessions = ref<Session[]>([])
const currentSessionId = ref<string | null>(null)
const messages = ref<Message[]>([])
const currentRun = ref<Run | null>(null)
const streamingContent = ref<string>('')
const isStreaming = ref(false)

const { request, onEvent } = useWebSocket()

// Computed
const currentSession = computed(() =>
  sessions.value.find((s) => s.id === currentSessionId.value) ?? null
)

// Session API
async function loadSessions() {
  const result = await request<{ sessions: Session[] }>('session.list')
  sessions.value = result.sessions
}

async function createSession(title?: string) {
  const result = await request<Session>('session.create', { name: title })
  sessions.value.unshift(result)
  return result
}

async function selectSession(sessionId: string) {
  currentSessionId.value = sessionId
  streamingContent.value = ''
  isStreaming.value = false
  currentRun.value = null

  // Load session messages
  await loadMessages(sessionId)
}

// Message API
async function loadMessages(sessionId: string) {
  try {
    const result = await request<{ messages: Message[] }>('message.list', { sessionId })
    messages.value = result.messages
  } catch (e) {
    console.error('Failed to load messages:', e)
    messages.value = []
  }
}

// Agent API
async function sendMessage(prompt: string) {
  if (!prompt.trim()) return

  let sessionId = currentSessionId.value

  // Create session if none selected
  if (!sessionId) {
    const session = await createSession('New Conversation')
    sessionId = session.id
    currentSessionId.value = sessionId
  }

  // Add user message to UI immediately
  const userMessage: Message = {
    id: `temp-${Date.now()}`,
    sessionId,
    runId: '',
    role: 'user',
    content: prompt,
    createdAt: new Date().toISOString()
  }
  messages.value.push(userMessage)

  // Start streaming
  isStreaming.value = true
  streamingContent.value = ''

  try {
    const result = await request<{ runId: string; sessionId: string; status: string }>(
      'agent.run',
      { sessionId, prompt }
    )

    currentRun.value = {
      id: result.runId,
      sessionId: result.sessionId,
      prompt,
      status: result.status as Run['status'],
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString()
    }
  } catch (e) {
    isStreaming.value = false
    console.error('Failed to start run:', e)
  }
}

// Setup event listeners
function setupEventListeners() {
  // Handle streaming deltas
  // 后端事件: {"type":"event","event":"assistant.delta","runId":"xxx","payload":{"content":"..."}}
  onEvent('assistant.delta', (event: RpcEvent) => {
    if (event.payload && typeof event.payload === 'object' && 'content' in event.payload) {
      streamingContent.value += (event.payload as { content: string }).content
    }
  })

  // Handle lifecycle end
  onEvent('lifecycle.end', (event: RpcEvent) => {
    if (currentRun.value && event.runId === currentRun.value.id) {
      // Add assistant message
      const assistantMessage: Message = {
        id: `msg-${Date.now()}`,
        sessionId: currentRun.value.sessionId,
        runId: event.runId,
        role: 'assistant',
        content: streamingContent.value,
        createdAt: new Date().toISOString()
      }
      messages.value.push(assistantMessage)

      // Reset streaming state
      isStreaming.value = false
      streamingContent.value = ''
      currentRun.value = null
    }
  })

  // Handle errors
  // 后端 ErrorData: {"message":"..."}
  onEvent('lifecycle.error', (event: RpcEvent) => {
    if (currentRun.value && event.runId === currentRun.value.id) {
      const errorMsg = event.payload && typeof event.payload === 'object' && 'message' in event.payload
        ? (event.payload as { message: string }).message
        : 'Unknown error'

      // Add error as assistant message
      const errorMessage: Message = {
        id: `msg-${Date.now()}`,
        sessionId: currentRun.value.sessionId,
        runId: event.runId,
        role: 'assistant',
        content: `Error: ${errorMsg}`,
        createdAt: new Date().toISOString()
      }
      messages.value.push(errorMessage)

      isStreaming.value = false
      streamingContent.value = ''
      currentRun.value = null
    }
  })
}

export function useChat() {
  return {
    // State
    sessions,
    currentSession,
    currentSessionId,
    messages,
    streamingContent,
    isStreaming,

    // Actions
    loadSessions,
    createSession,
    selectSession,
    loadMessages,
    sendMessage,
    setupEventListeners
  }
}

import { ref, computed } from 'vue'
import { useWebSocket } from './useWebSocket'
import type {
  Session,
  Message,
  Run,
  RpcEvent,
  ToolCall,
  StreamBlock,
  ToolCallPayload,
  ToolResultPayload,
  ToolConfirmRequestPayload,
  SkillActivatedPayload,
  SkillActivation
} from '@/types'

const sessions = ref<Session[]>([])
const currentSessionId = ref<string | null>(null)
const messages = ref<Message[]>([])
const currentRun = ref<Run | null>(null)
const isStreaming = ref(false)

// 流式内容块（交错显示文本和工具调用）
const streamBlocks = ref<StreamBlock[]>([])

// 工具调用索引（用于快速查找和更新）- 使用普通对象避免 Map 的响应式问题
const toolCallIndex = ref<Record<string, ToolCall>>({})

// 事件监听器清理函数
let eventCleanups: (() => void)[] = []
let isSetup = false

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
  isStreaming.value = false
  currentRun.value = null
  streamBlocks.value = []
  toolCallIndex.value = {}

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
  streamBlocks.value = []
  toolCallIndex.value = {}

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

// HITL Confirmation
async function confirmToolCall(callId: string, decision: 'approve' | 'reject') {
  try {
    await request('tool.confirm', { callId, decision })

    // Update local state
    const toolCall = toolCallIndex.value[callId]
    if (toolCall) {
      toolCall.status = decision === 'approve' ? 'confirmed' : 'rejected'
      if (decision === 'reject') {
        toolCall.result = 'Rejected by user'
      }
    }
  } catch (e) {
    console.error('Failed to confirm tool call:', e)
  }
}

// Cancel Run
async function cancelRun() {
  if (!currentRun.value) {
    console.warn('No active run to cancel')
    return
  }

  const runIdToCancel = currentRun.value.id

  try {
    await request('agent.cancel', { runId: runIdToCancel })
    console.log('[Chat] Cancellation requested for run:', runIdToCancel)

    // 立即重置前端状态（不等待 lifecycle.error 事件）
    // 后端的实际取消可能需要一些时间（等待 LLM 响应完成）
    isStreaming.value = false
    streamBlocks.value = []
    toolCallIndex.value = {}
    currentRun.value = null

    // 添加一条取消消息
    const cancelMessage: Message = {
      id: `msg-cancel-${Date.now()}`,
      sessionId: currentSessionId.value!,
      runId: runIdToCancel,
      role: 'assistant',
      content: '_Cancelled by user_',
      createdAt: new Date().toISOString()
    }
    messages.value.push(cancelMessage)
  } catch (e) {
    console.error('Failed to cancel run:', e)
  }
}

/**
 * 获取或创建当前文本块
 * 如果最后一个块是文本块，返回它；否则创建新的文本块
 */
function getOrCreateTextBlock(): StreamBlock {
  const lastBlock = streamBlocks.value[streamBlocks.value.length - 1]
  if (lastBlock && lastBlock.type === 'text') {
    return lastBlock
  }

  // 创建新的文本块
  const newBlock: StreamBlock = {
    id: `text-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`,
    type: 'text',
    content: ''
  }
  streamBlocks.value.push(newBlock)
  return newBlock
}

/**
 * 创建工具调用块
 */
function createToolBlock(toolCall: ToolCall): StreamBlock {
  const block: StreamBlock = {
    id: `tool-${toolCall.callId}`,
    type: 'tool',
    toolCall
  }
  streamBlocks.value.push(block)
  toolCallIndex.value[toolCall.callId] = toolCall
  return block
}

/**
 * 创建技能激活块
 */
function createSkillBlock(activation: SkillActivation): StreamBlock {
  const block: StreamBlock = {
    id: `skill-${activation.skillName}-${Date.now()}`,
    type: 'skill',
    skillActivation: activation
  }
  streamBlocks.value.push(block)
  return block
}

/**
 * 查找工具块并更新
 */
function updateToolBlock(callId: string, updater: (toolCall: ToolCall) => void) {
  const toolCall = toolCallIndex.value[callId]
  if (toolCall) {
    updater(toolCall)
  }
}

// Setup event listeners
function setupEventListeners() {
  // 防止重复注册
  if (isSetup) {
    return
  }
  isSetup = true

  // 清理之前的监听器（以防万一）
  eventCleanups.forEach(cleanup => cleanup())
  eventCleanups = []

  // Handle streaming deltas - 追加到当前文本块
  eventCleanups.push(onEvent('assistant.delta', (event: RpcEvent) => {
    if (event.payload && typeof event.payload === 'object' && 'content' in event.payload) {
      const content = (event.payload as { content: string }).content
      const textBlock = getOrCreateTextBlock()
      textBlock.content = (textBlock.content || '') + content
    }
  }))

  // Handle tool.confirm_request (HITL tool needs approval) - 创建新的工具块
  eventCleanups.push(onEvent('tool.confirm_request', (event: RpcEvent) => {
    const payload = event.payload as ToolConfirmRequestPayload
    if (payload) {
      const toolCall: ToolCall = {
        callId: payload.callId,
        toolName: payload.toolName,
        arguments: payload.arguments,
        status: 'pending',
        requiresConfirm: true
      }
      createToolBlock(toolCall)
    }
  }))

  // Handle tool.call (tool execution started) - 创建或更新工具块
  eventCleanups.push(onEvent('tool.call', (event: RpcEvent) => {
    const payload = event.payload as ToolCallPayload
    if (payload) {
      const existingCall = toolCallIndex.value[payload.callId]
      if (existingCall) {
        // 已存在（HITL 工具已确认），更新状态
        existingCall.status = 'executing'
      } else {
        // 新工具调用（非 HITL 工具）
        const toolCall: ToolCall = {
          callId: payload.callId,
          toolName: payload.toolName,
          arguments: payload.arguments,
          status: 'executing',
          requiresConfirm: false
        }
        createToolBlock(toolCall)
      }
    }
  }))

  // Handle tool.result - 更新工具块
  eventCleanups.push(onEvent('tool.result', (event: RpcEvent) => {
    const payload = event.payload as ToolResultPayload
    if (payload) {
      updateToolBlock(payload.callId, (toolCall) => {
        toolCall.status = payload.success ? 'success' : 'error'
        toolCall.result = payload.content
      })
    }
  }))

  // Handle skill.activated - 创建技能激活块
  eventCleanups.push(onEvent('skill.activated', (event: RpcEvent) => {
    const payload = event.payload as SkillActivatedPayload
    if (payload) {
      createSkillBlock({
        skillName: payload.skillName,
        source: payload.source
      })
    }
  }))

  // Handle lifecycle end - 保存到消息
  eventCleanups.push(onEvent('lifecycle.end', (event: RpcEvent) => {
    if (currentRun.value && event.runId === currentRun.value.id) {
      // 收集所有文本内容（用于简单显示和搜索）
      const textContent = streamBlocks.value
        .filter(b => b.type === 'text')
        .map(b => b.content || '')
        .join('')

      // 复制 blocks 用于保存（深拷贝避免引用问题）
      const savedBlocks = streamBlocks.value.map(block => ({
        ...block,
        toolCall: block.toolCall ? { ...block.toolCall } : undefined,
        skillActivation: block.skillActivation ? { ...block.skillActivation } : undefined
      }))

      // Add assistant message with blocks
      const assistantMessage: Message = {
        id: `msg-${Date.now()}`,
        sessionId: currentRun.value.sessionId,
        runId: event.runId,
        role: 'assistant',
        content: textContent,
        createdAt: new Date().toISOString(),
        blocks: savedBlocks.length > 0 ? savedBlocks : undefined
      }
      messages.value.push(assistantMessage)

      // Reset streaming state
      isStreaming.value = false
      streamBlocks.value = []
      toolCallIndex.value = {}
      currentRun.value = null
    }
  }))

  // Handle errors
  eventCleanups.push(onEvent('lifecycle.error', (event: RpcEvent) => {
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
      streamBlocks.value = []
      toolCallIndex.value = {}
      currentRun.value = null
    }
  }))
}

export function useChat() {
  return {
    // State
    sessions,
    currentSession,
    currentSessionId,
    currentRun,
    messages,
    streamBlocks,
    isStreaming,

    // Actions
    loadSessions,
    createSession,
    selectSession,
    loadMessages,
    sendMessage,
    confirmToolCall,
    cancelRun,
    setupEventListeners
  }
}

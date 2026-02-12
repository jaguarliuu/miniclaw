import { ref, computed } from 'vue'
import { useWebSocket } from './useWebSocket'
import { useArtifact } from './useArtifact'
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
  SkillActivation,
  SubagentInfo,
  SubagentSpawnedPayload,
  SubagentStartedPayload,
  SubagentAnnouncedPayload,
  SubagentFailedPayload,
  AttachedFile,
  AttachedContext,
  SessionFile,
  FileCreatedPayload
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

// 子代理索引（subRunId -> SubagentInfo）
const subagentIndex = ref<Record<string, SubagentInfo>>({})

// 右侧面板当前选中的 subagent
const activeSubagentId = ref<string | null>(null)

// 排除的 MCP 服务器名称集合（按会话过滤工具）
const excludedMcpServers = ref<Set<string>>(new Set())

// Session 文件列表
const sessionFiles = ref<SessionFile[]>([])

// 事件监听器清理函数
let eventCleanups: (() => void)[] = []
let isSetup = false

const { request, onEvent } = useWebSocket()
const { artifact, openArtifact, closeArtifact, startStreaming, appendContent, finishStreaming } = useArtifact()

// Computed
const currentSession = computed(() =>
  sessions.value.find((s) => s.id === currentSessionId.value) ?? null
)

// 当前选中的 subagent（从 subagentIndex 或已保存 messages 的 blocks 中查找）
const activeSubagent = computed<SubagentInfo | null>(() => {
  const id = activeSubagentId.value
  if (!id) return null
  // 优先从当前流式 subagentIndex 中查找
  if (subagentIndex.value[id]) return subagentIndex.value[id]
  // 再从已保存消息的 blocks 中查找
  for (const msg of messages.value) {
    if (msg.blocks) {
      for (const block of msg.blocks) {
        if (block.type === 'subagent' && block.subagent?.subRunId === id) {
          return block.subagent
        }
      }
    }
  }
  return null
})

// 收集所有已知 subagent 子会话 ID（用于侧边栏过滤）
const subagentSessionIds = computed<Set<string>>(() => {
  const ids = new Set<string>()
  // 从 subagentIndex 收集
  for (const info of Object.values(subagentIndex.value)) {
    if (info.subSessionId) ids.add(info.subSessionId)
  }
  // 从已保存消息的 blocks 中收集
  for (const msg of messages.value) {
    if (msg.blocks) {
      for (const block of msg.blocks) {
        if (block.type === 'subagent' && block.subagent?.subSessionId) {
          ids.add(block.subagent.subSessionId)
        }
      }
    }
  }
  return ids
})

// 过滤掉 subagent 子会话的会话列表
const filteredSessions = computed(() =>
  sessions.value.filter(s => !subagentSessionIds.value.has(s.id))
)

function setActiveSubagent(id: string | null) {
  activeSubagentId.value = id
}

function toggleMcpServer(name: string) {
  const newSet = new Set(excludedMcpServers.value)
  if (newSet.has(name)) {
    newSet.delete(name)
  } else {
    newSet.add(name)
  }
  excludedMcpServers.value = newSet
}

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
  subagentIndex.value = {}
  excludedMcpServers.value = new Set()
  sessionFiles.value = []
  closeArtifact()

  // Load session messages
  await loadMessages(sessionId)
}

// Delete session
async function deleteSession(sessionId: string) {
  try {
    await request('session.delete', { sessionId })
    // Remove from local state
    sessions.value = sessions.value.filter(s => s.id !== sessionId)
    // If deleted current session, clear selection
    if (currentSessionId.value === sessionId) {
      currentSessionId.value = null
      messages.value = []
      streamBlocks.value = []
      toolCallIndex.value = {}
      subagentIndex.value = {}
      sessionFiles.value = []
      currentRun.value = null
      closeArtifact()
    }
    console.log('[Chat] Deleted session:', sessionId)
  } catch (e) {
    console.error('Failed to delete session:', e)
  }
}

// Message API
async function loadMessages(sessionId: string) {
  try {
    const result = await request<{ messages: Message[]; files?: SessionFile[] }>('message.list', { sessionId })

    // 保存 session files
    sessionFiles.value = result.files || []

    // 将 files 按 runId 分组
    const filesByRun: Record<string, SessionFile[]> = {}
    for (const f of sessionFiles.value) {
      if (f.runId) {
        if (!filesByRun[f.runId]) filesByRun[f.runId] = []
        filesByRun[f.runId]?.push(f)
      }
    }

    // 过滤 subagent_announce 消息，转化为带 SubagentCard 块的消息
    messages.value = result.messages.map(msg => {
      if (msg.role === 'assistant' && msg.content.startsWith('{"type":"subagent_announce"')) {
        try {
          const data = JSON.parse(msg.content)
          const info: SubagentInfo = {
            subRunId: data.subRunId,
            subSessionId: data.subSessionId,
            sessionKey: data.sessionKey || '',
            agentId: data.agentId || '',
            task: data.task || '',
            lane: '',
            status: data.status === 'completed' ? 'completed' : 'failed',
            result: data.result,
            error: data.error,
            durationMs: data.durationMs,
          }
          return {
            ...msg,
            content: '',  // 清空原始 JSON
            blocks: [{
              id: `subagent-${info.subRunId}`,
              type: 'subagent' as const,
              subagent: info
            }]
          }
        } catch { return msg }
      }
      return msg
    })

    // 对每个 assistant message，如果其 runId 有文件，追加 file blocks
    messages.value.forEach(msg => {
      if (msg.role === 'assistant' && msg.runId && filesByRun[msg.runId]) {
        const existingBlocks = msg.blocks || []
        // 如果消息原本没有 blocks，需要先将 content 包装为 text block
        // 否则 MessageItem 的 hasBlocks 分支会跳过纯文本内容的渲染
        const baseBlocks: StreamBlock[] = existingBlocks.length === 0 && msg.content
          ? [{ id: `text-${msg.id}`, type: 'text' as const, content: msg.content }]
          : existingBlocks
        const files = filesByRun[msg.runId]
        const fileBlocks: StreamBlock[] = (files || []).map(f => ({
          id: `file-${f.id}`,
          type: 'file' as const,
          file: f
        }))
        msg.blocks = [...baseBlocks, ...fileBlocks]
      }
    })
  } catch (e) {
    console.error('Failed to load messages:', e)
    messages.value = []
    sessionFiles.value = []
  }
}

/**
 * 构建上下文 Prompt
 * 将附加的上下文（File、Folder、Web 等）格式化为 prompt 前缀
 */
function buildContextPrompt(contexts: AttachedContext[]): string {
  if (!contexts || contexts.length === 0) return ''

  // 按类型分组
  const fileContexts = contexts.filter(c => c.type === 'file' && c.filePath)
  const folderContexts = contexts.filter(c => c.type === 'folder' && c.folderPath)
  const webContexts = contexts.filter(c => c.type === 'web' && c.url)

  const sections: string[] = []

  // 文件上下文
  if (fileContexts.length > 0) {
    const fileList = fileContexts.map(c => `- ${c.filePath}`).join('\n')
    sections.push(`[Attached Files]\n${fileList}`)
  }

  // 文件夹上下文
  if (folderContexts.length > 0) {
    const folderList = folderContexts.map(c => `- ${c.folderPath} (explore using glob/grep/read_file)`).join('\n')
    sections.push(`[Attached Folders]\n${folderList}`)
  }

  // Web 上下文
  if (webContexts.length > 0) {
    const webList = webContexts.map(c => `- ${c.url} (fetch using http_get or web_search)`).join('\n')
    sections.push(`[Attached Web Resources]\n${webList}`)
  }

  return sections.length > 0 ? sections.join('\n\n') + '\n\n' : ''
}

// Agent API
async function sendMessage(
  prompt: string,
  attachedContexts?: AttachedContext[],
  filePaths?: string[],
  attachedFiles?: AttachedFile[],
  dataSourceId?: string,
  dataSourceName?: string
) {
  if (!prompt.trim()) return

  let sessionId = currentSessionId.value

  // Create session if none selected
  if (!sessionId) {
    const session = await createSession('New Conversation')
    sessionId = session.id
    currentSessionId.value = sessionId
  }

  // 优先使用新的 attachedContexts，如果为空则向后兼容旧的 filePaths
  let fullPrompt = prompt
  if (attachedContexts && attachedContexts.length > 0) {
    const contextPrefix = buildContextPrompt(attachedContexts)
    fullPrompt = contextPrefix + prompt
  } else if (filePaths && filePaths.length > 0) {
    // 向后兼容旧的 filePaths 参数
    const fileList = filePaths.map(p => `- ${p}`).join('\n')
    fullPrompt = `[Attached Files]\n${fileList}\n\n${prompt}`
  }

  // Add user message to UI immediately（保存 attachedContexts 和 dataSourceId 用于展示）
  const userMessage: Message = {
    id: `temp-${Date.now()}`,
    sessionId,
    runId: '',
    role: 'user',
    content: prompt,
    createdAt: new Date().toISOString(),
    attachedContexts: attachedContexts && attachedContexts.length > 0 ? [...attachedContexts] : undefined,
    attachedFiles: attachedFiles && attachedFiles.length > 0 ? [...attachedFiles] : undefined,  // 向后兼容
    dataSourceId,
    dataSourceName
  }
  messages.value.push(userMessage)

  // Start streaming
  isStreaming.value = true
  streamBlocks.value = []
  toolCallIndex.value = {}
  subagentIndex.value = {}

  try {
    const payload: Record<string, unknown> = { sessionId, prompt: fullPrompt }

    // 添加排除的 MCP 服务器
    if (excludedMcpServers.value.size > 0) {
      payload.excludedMcpServers = Array.from(excludedMcpServers.value)
    }

    // 添加数据源 ID
    if (dataSourceId) {
      payload.dataSourceId = dataSourceId
    }

    const result = await request<{ runId: string; sessionId: string; status: string }>(
      'agent.run',
      payload
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

/**
 * 深拷贝 streamBlocks，避免引用问题
 * 在 lifecycle.end 和 cancelRun 中复用
 */
function deepCopyBlocks(blocks: StreamBlock[]): StreamBlock[] {
  return blocks.map(block => ({
    ...block,
    toolCall: block.toolCall ? { ...block.toolCall } : undefined,
    skillActivation: block.skillActivation ? { ...block.skillActivation } : undefined,
    file: block.file ? { ...block.file } : undefined,
    subagent: block.subagent ? {
      ...block.subagent,
      streamBlocks: block.subagent.streamBlocks?.map(sb => ({
        ...sb,
        toolCall: sb.toolCall ? { ...sb.toolCall } : undefined
      })),
      toolCallIndex: undefined  // 不需要保存索引
    } : undefined
  }))
}

// Cancel Run
async function cancelRun() {
  if (!currentRun.value) {
    console.warn('No active run to cancel')
    return
  }

  const runIdToCancel = currentRun.value.id
  const sessionId = currentRun.value.sessionId

  try {
    await request('agent.cancel', { runId: runIdToCancel })
    console.log('[Chat] Cancellation requested for run:', runIdToCancel)

    // 先将 currentRun 设为 null，这样后续到达的 lifecycle.error 事件会被忽略
    currentRun.value = null

    if (streamBlocks.value.length > 0) {
      // 保存已有 streamBlocks 为 assistant message（复用深拷贝逻辑）
      const textContent = streamBlocks.value
        .filter(b => b.type === 'text')
        .map(b => b.content || '')
        .join('')

      const savedBlocks = deepCopyBlocks(streamBlocks.value)

      const assistantMessage: Message = {
        id: `msg-cancel-${Date.now()}`,
        sessionId: sessionId,
        runId: runIdToCancel,
        role: 'assistant',
        content: textContent + '\n\n---\n_Cancelled by user_',
        createdAt: new Date().toISOString(),
        blocks: savedBlocks
      }
      messages.value.push(assistantMessage)
    } else {
      // streamBlocks 为空，只添加取消标记
      const cancelMessage: Message = {
        id: `msg-cancel-${Date.now()}`,
        sessionId: sessionId,
        runId: runIdToCancel,
        role: 'assistant',
        content: '_Cancelled by user_',
        createdAt: new Date().toISOString()
      }
      messages.value.push(cancelMessage)
    }

    // 重置流式状态
    isStreaming.value = false
    streamBlocks.value = []
    toolCallIndex.value = {}
    subagentIndex.value = {}
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
 * 创建子代理块
 */
function createSubagentBlock(info: SubagentInfo): StreamBlock {
  info.streamBlocks = []
  info.toolCallIndex = {}
  const block: StreamBlock = {
    id: `subagent-${info.subRunId}`,
    type: 'subagent',
    subagent: info
  }
  streamBlocks.value.push(block)
  subagentIndex.value[info.subRunId] = info
  return block
}

/**
 * 更新子代理块
 */
function updateSubagentBlock(subRunId: string, updater: (info: SubagentInfo) => void) {
  const info = subagentIndex.value[subRunId]
  if (info) {
    updater(info)
    return
  }
  // 也检查已保存的消息中的 subagent blocks
  for (const msg of messages.value) {
    if (msg.blocks) {
      for (const block of msg.blocks) {
        if (block.type === 'subagent' && block.subagent?.subRunId === subRunId) {
          updater(block.subagent)
          return
        }
      }
    }
  }
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

/**
 * 根据 runId 查找对应的 subagent（subRunId === event.runId）
 */
function findSubagentByRunId(runId: string): SubagentInfo | undefined {
  return subagentIndex.value[runId]
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

      // 检查是否属于 subagent
      const subagent = findSubagentByRunId(event.runId)
      if (subagent && subagent.streamBlocks) {
        const lastBlock = subagent.streamBlocks[subagent.streamBlocks.length - 1]
        if (lastBlock && lastBlock.type === 'text') {
          lastBlock.content = (lastBlock.content || '') + content
        } else {
          subagent.streamBlocks.push({
            id: `text-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`,
            type: 'text',
            content
          })
        }
        return
      }

      // 路由到主流
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

      // 检查是否属于 subagent
      const subagent = findSubagentByRunId(event.runId)
      if (subagent && subagent.streamBlocks) {
        subagent.streamBlocks.push({
          id: `tool-${payload.callId}`,
          type: 'tool',
          toolCall
        })
        subagent.toolCallIndex![payload.callId] = toolCall
        // 注意：confirm 仍需放入主 toolCallIndex 以便 confirmToolCall 能找到
        toolCallIndex.value[payload.callId] = toolCall
        return
      }

      createToolBlock(toolCall)
    }
  }))

  // Handle tool.call (tool execution started) - 创建或更新工具块
  eventCleanups.push(onEvent('tool.call', (event: RpcEvent) => {
    const payload = event.payload as ToolCallPayload
    if (payload) {
      // 检查是否属于 subagent
      const subagent = findSubagentByRunId(event.runId)
      if (subagent && subagent.streamBlocks) {
        const existingCall = subagent.toolCallIndex?.[payload.callId]
        if (existingCall) {
          existingCall.status = 'executing'
        } else {
          const toolCall: ToolCall = {
            callId: payload.callId,
            toolName: payload.toolName,
            arguments: payload.arguments,
            status: 'executing',
            requiresConfirm: false
          }
          subagent.streamBlocks.push({
            id: `tool-${payload.callId}`,
            type: 'tool',
            toolCall
          })
          subagent.toolCallIndex![payload.callId] = toolCall
        }
        return
      }

      // 路由到主流
      const existingCall = toolCallIndex.value[payload.callId]
      if (existingCall) {
        existingCall.status = 'executing'
      } else {
        const toolCall: ToolCall = {
          callId: payload.callId,
          toolName: payload.toolName,
          arguments: payload.arguments,
          status: 'executing',
          requiresConfirm: false
        }
        createToolBlock(toolCall)
      }

      // Detect write_file and open artifact panel
      if (payload.toolName === 'write_file' && payload.arguments) {
        const args = payload.arguments as Record<string, unknown>
        const path = (args.path || args.filePath) as string | undefined
        const content = args.content as string | undefined
        if (path && content) {
          // If already streaming, finish with final content; otherwise open directly (fallback)
          if (artifact.value?.streaming) {
            finishStreaming(content)
          } else {
            openArtifact(path, content)
          }
        }
      }
    }
  }))

  // Handle tool.result - 更新工具块
  eventCleanups.push(onEvent('tool.result', (event: RpcEvent) => {
    const payload = event.payload as ToolResultPayload
    if (payload) {
      // 检查是否属于 subagent
      const subagent = findSubagentByRunId(event.runId)
      if (subagent && subagent.toolCallIndex?.[payload.callId]) {
        const toolCall = subagent.toolCallIndex[payload.callId]!
        toolCall.status = payload.success ? 'success' : 'error'
        toolCall.result = payload.content
        return
      }

      // 路由到主流
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

  // Handle session.renamed - 更新会话名称
  eventCleanups.push(onEvent('session.renamed', (event: RpcEvent) => {
    const payload = event.payload as { sessionId: string; name: string }
    if (payload?.sessionId) {
      const session = sessions.value.find(s => s.id === payload.sessionId)
      if (session) {
        session.name = payload.name
      }
    }
  }))

  // Handle artifact.open - AI 开始写文件，打开流式预览面板
  eventCleanups.push(onEvent('artifact.open', (event: RpcEvent) => {
    if (currentRun.value && event.runId === currentRun.value.id) {
      const payload = event.payload as { path: string }
      if (payload?.path) {
        startStreaming(payload.path)
      }
    }
  }))

  // Handle artifact.delta - 文件内容增量到达
  eventCleanups.push(onEvent('artifact.delta', (event: RpcEvent) => {
    if (currentRun.value && event.runId === currentRun.value.id) {
      const payload = event.payload as { content: string }
      if (payload?.content) {
        appendContent(payload.content)
      }
    }
  }))

  // Handle file.created - 文件创建完成，添加 file block
  eventCleanups.push(onEvent('file.created', (event: RpcEvent) => {
    const payload = event.payload as FileCreatedPayload
    if (payload) {
      const fileInfo: SessionFile = {
        id: payload.fileId || `tmp-${Date.now()}`,
        sessionId: currentSessionId.value || '',
        runId: event.runId,
        filePath: payload.path,
        fileName: payload.fileName,
        fileSize: payload.size,
        createdAt: new Date().toISOString()
      }
      streamBlocks.value.push({
        id: `file-${fileInfo.id}`,
        type: 'file',
        file: fileInfo
      })
      sessionFiles.value.push(fileInfo)
    }
  }))

  // Handle subagent.spawned - 创建子代理块
  eventCleanups.push(onEvent('subagent.spawned', (event: RpcEvent) => {
    const payload = event.payload as SubagentSpawnedPayload
    if (payload) {
      createSubagentBlock({
        subRunId: payload.subRunId,
        subSessionId: payload.subSessionId,
        sessionKey: payload.sessionKey,
        agentId: payload.agentId,
        task: payload.task,
        lane: payload.lane,
        status: 'queued'
      })
    }
  }))

  // Handle subagent.started - 更新状态为 running
  eventCleanups.push(onEvent('subagent.started', (event: RpcEvent) => {
    const payload = event.payload as SubagentStartedPayload
    if (payload) {
      updateSubagentBlock(payload.subRunId, (info) => {
        info.status = 'running'
        info.startedAt = Date.now()
      })
    }
  }))

  // Handle subagent.announced - 更新为完成状态
  eventCleanups.push(onEvent('subagent.announced', (event: RpcEvent) => {
    const payload = event.payload as SubagentAnnouncedPayload
    if (payload) {
      updateSubagentBlock(payload.subRunId, (info) => {
        info.status = payload.status === 'completed' ? 'completed' : 'failed'
        info.result = payload.result
        info.error = payload.error
        info.durationMs = payload.durationMs
      })
    }
  }))

  // Handle subagent.failed - 更新为失败状态
  eventCleanups.push(onEvent('subagent.failed', (event: RpcEvent) => {
    const payload = event.payload as SubagentFailedPayload
    if (payload) {
      updateSubagentBlock(payload.subRunId, (info) => {
        info.status = 'failed'
        info.error = payload.error
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
      const savedBlocks = deepCopyBlocks(streamBlocks.value)

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
      subagentIndex.value = {}
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
      subagentIndex.value = {}
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
    subagentIndex,
    sessionFiles,

    // Panel state
    activeSubagentId,
    activeSubagent,
    setActiveSubagent,

    // MCP server filtering
    excludedMcpServers,
    toggleMcpServer,

    // Filtered sessions (excludes subagent child sessions)
    filteredSessions,

    // Actions
    loadSessions,
    createSession,
    selectSession,
    deleteSession,
    loadMessages,
    sendMessage,
    confirmToolCall,
    cancelRun,
    setupEventListeners
  }
}

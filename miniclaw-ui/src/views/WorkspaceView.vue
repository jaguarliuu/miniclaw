<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useWebSocket } from '@/composables/useWebSocket'
import { useChat } from '@/composables/useChat'
import { useLlmConfig } from '@/composables/useLlmConfig'
import { useContext } from '@/composables/useContext'
import { useMcpServers } from '@/composables/useMcpServers'
import { useDataSource } from '@/composables/useDataSource'
import { useModelSelector } from '@/composables/useModelSelector'
import type { ContextType } from '@/types'
import ConnectionStatus from '@/components/ConnectionStatus.vue'
import SessionSidebar from '@/components/SessionSidebar.vue'
import MessageList from '@/components/MessageList.vue'
import MessageInput from '@/components/MessageInput.vue'
import SubagentPanel from '@/components/SubagentPanel.vue'
import ArtifactPanel from '@/components/ArtifactPanel.vue'
import ContextInputModal from '@/components/ContextInputModal.vue'
import { useArtifact } from '@/composables/useArtifact'

const { state: connectionState } = useWebSocket()
const { checkStatus, getConfig: loadLlmConfig, multiConfig } = useLlmConfig()
const router = useRouter()
const route = useRoute()
const { artifact } = useArtifact()
const { contexts: attachedContexts, uploadFile, addContext, removeContext, clearContexts } = useContext()
const { servers: mcpServers, loadServers: loadMcpServers } = useMcpServers()
const { dataSources, loadDataSources } = useDataSource()
const { selectedModel, availableModels, activeModelLabel, selectModel } = useModelSelector()

// Context input modal 状态
const showContextModal = ref(false)
const currentContextType = ref<ContextType>('folder')

// 选中的数据源 ID
const selectedDataSourceId = ref<string | undefined>(undefined)

const {
  currentSession,
  currentSessionId,
  messages,
  streamBlocks,
  isStreaming,
  filteredSessions,
  activeSubagentId,
  activeSubagent,
  setActiveSubagent,
  excludedMcpServers,
  toggleMcpServer,
  loadSessions,
  createSession,
  selectSession,
  deleteSession,
  sendMessage,
  confirmToolCall,
  cancelRun
} = useChat()

// 只显示 enabled 且已连接（有 toolCount）的 MCP 服务器
const connectedMcpServers = computed(() =>
  mcpServers.value.filter(s => s.enabled && (s.toolCount ?? 0) > 0)
)

async function handleCreateSession() {
  const session = await createSession('New Conversation')
  await selectSession(session.id)
}

async function handleSelectSession(id: string) {
  await selectSession(id)
}

function handleDeleteSession(id: string) {
  deleteSession(id)
}

function handleSend(prompt: string, contexts: typeof attachedContexts.value) {
  // 获取选中数据源的名称
  const dataSourceName = selectedDataSourceId.value
    ? dataSources.value.find(ds => ds.id === selectedDataSourceId.value)?.name
    : undefined

  // 传递上下文信息、数据源 ID 和模型选择给 sendMessage
  sendMessage(
    prompt,
    contexts.length > 0 ? contexts : undefined,
    undefined, // filePaths (legacy)
    undefined, // attachedFiles (legacy)
    selectedDataSourceId.value,
    dataSourceName,
    selectedModel.value ?? undefined
  )
  clearContexts()
}

function handleConfirmToolCall(callId: string, decision: 'approve' | 'reject') {
  confirmToolCall(callId, decision)
}

function handleCancel() {
  cancelRun()
}

async function handleAttachFile(file: File) {
  await uploadFile(file)
}

function handleAddContext(type: ContextType) {
  // 文件类型已经在 MessageInput 中处理，这里处理其他类型
  currentContextType.value = type
  showContextModal.value = true
}

function handleContextModalConfirm(value: string) {
  const type = currentContextType.value

  // 根据类型添加上下文
  if (type === 'folder') {
    addContext('folder', value, { folderPath: value })
  } else if (type === 'web') {
    addContext('web', value, { url: value })
  } else if (type === 'doc') {
    addContext('doc', value, { docId: value })
  } else if (type === 'code') {
    addContext('code', value, { codeSnippet: value })
  } else if (type === 'rule') {
    addContext('rule', value, { ruleContent: value })
  }

  showContextModal.value = false
}

function handleContextModalCancel() {
  showContextModal.value = false
}

function handleRemoveContext(contextId: string) {
  removeContext(contextId)
}

function handleSelectSubagent(subRunId: string) {
  // Toggle: if already selected, close panel
  if (activeSubagentId.value === subRunId) {
    setActiveSubagent(null)
  } else {
    setActiveSubagent(subRunId)
  }
}

function handleSelectDataSource(dataSourceId: string | undefined) {
  selectedDataSourceId.value = dataSourceId
}

function handleSelectModel(providerId: string, modelName: string) {
  selectModel(providerId, modelName)
}

function handleOpenModelSettings() {
  router.push('/settings/llm')
}

onMounted(() => {
  // Wait for connection (managed by App.vue), then check LLM config and load sessions
  const checkConnection = setInterval(async () => {
    if (connectionState.value === 'connected') {
      clearInterval(checkConnection)

      // Check if LLM is configured — redirect to setup if not
      const status = await checkStatus()
      if (!status.llmConfigured) {
        router.replace('/setup')
        return
      }

      loadSessions()
      loadMcpServers()
      loadDataSources()
      loadLlmConfig()

      // Handle install/uninstall action from system settings
      await handleInstallAction()
    }
  }, 200)
})

// Watch for query parameter changes (install/uninstall actions from system settings)
watch(() => route.query, async (newQuery) => {
  if (newQuery.action && newQuery.prompt) {
    await handleInstallAction()
  }
})

async function handleInstallAction() {
  const { action, env, prompt } = route.query

  if (action && prompt && typeof prompt === 'string') {
    // Create a new session for the installation
    const sessionName = action === 'install'
      ? `Install ${env}`
      : `Uninstall ${env}`

    const session = await createSession(sessionName)
    await selectSession(session.id)

    // Send the installation prompt
    setTimeout(() => {
      sendMessage(prompt)
      // Clear query parameters
      router.replace({ path: '/workspace' })
    }, 500)
  }
}
</script>

<template>
  <div class="workspace">
    <SessionSidebar
      :sessions="filteredSessions"
      :current-id="currentSessionId"
      @select="handleSelectSession"
      @create="handleCreateSession"
      @delete="handleDeleteSession"
    >
      <template #footer>
        <ConnectionStatus :state="connectionState" />
      </template>
    </SessionSidebar>

    <main class="main-area">
      <header class="main-header">
        <span class="session-title">
          {{ currentSession?.name || 'MiniClaw' }}
        </span>
      </header>

      <MessageList
        :messages="messages"
        :stream-blocks="streamBlocks"
        :is-streaming="isStreaming"
        :active-subagent-id="activeSubagentId"
        :current-session-id="currentSessionId"
        @confirm="handleConfirmToolCall"
        @select-subagent="handleSelectSubagent"
      />

      <MessageInput
        :disabled="isStreaming || connectionState !== 'connected'"
        :is-running="isStreaming"
        :attached-contexts="attachedContexts"
        :mcp-servers="connectedMcpServers"
        :excluded-mcp-servers="excludedMcpServers"
        :data-sources="dataSources"
        :selected-data-source-id="selectedDataSourceId"
        :available-models="availableModels"
        :selected-model="selectedModel"
        :default-model="multiConfig?.defaultModel ?? ''"
        :active-model-label="activeModelLabel"
        @send="handleSend"
        @cancel="handleCancel"
        @attach-file="handleAttachFile"
        @add-context="handleAddContext"
        @remove-context="handleRemoveContext"
        @toggle-mcp-server="toggleMcpServer"
        @select-datasource="handleSelectDataSource"
        @select-model="handleSelectModel"
        @open-model-settings="handleOpenModelSettings"
      />
    </main>

    <!-- Context input modal -->
    <ContextInputModal
      :type="currentContextType"
      :show="showContextModal"
      @confirm="handleContextModalConfirm"
      @cancel="handleContextModalCancel"
    />

    <Transition name="panel-slide">
      <SubagentPanel
        v-if="activeSubagent && !artifact"
        :subagent="activeSubagent"
        @close="setActiveSubagent(null)"
        @confirm="handleConfirmToolCall"
      />
    </Transition>

    <Transition name="panel-slide">
      <ArtifactPanel v-if="artifact" />
    </Transition>
  </div>
</template>

<style scoped>
.workspace {
  display: flex;
  height: 100vh;
  width: 100vw;
}

.main-area {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
  background: var(--color-gray-50);
  transition: all var(--duration-normal) var(--ease-out);
}

.main-header {
  padding: 20px 48px;
  border-bottom: var(--border-light);
  background: var(--color-white);
  display: flex;
  align-items: center;
}

.session-title {
  font-family: var(--font-mono);
  font-size: 13px;
  font-weight: 500;
  letter-spacing: -0.01em;
  color: var(--color-gray-600);
}

/* Panel slide transition */
.panel-slide-enter-active,
.panel-slide-leave-active {
  transition: opacity var(--duration-normal) var(--ease-out), transform var(--duration-normal) var(--ease-out);
}

.panel-slide-enter-from,
.panel-slide-leave-to {
  opacity: 0;
  transform: translateX(40px);
}
</style>

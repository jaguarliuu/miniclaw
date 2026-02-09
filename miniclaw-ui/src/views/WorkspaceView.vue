<script setup lang="ts">
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useWebSocket } from '@/composables/useWebSocket'
import { useChat } from '@/composables/useChat'
import { useLlmConfig } from '@/composables/useLlmConfig'
import { useFileUpload } from '@/composables/useFileUpload'
import ConnectionStatus from '@/components/ConnectionStatus.vue'
import SessionSidebar from '@/components/SessionSidebar.vue'
import MessageList from '@/components/MessageList.vue'
import MessageInput from '@/components/MessageInput.vue'
import SubagentPanel from '@/components/SubagentPanel.vue'
import ArtifactPanel from '@/components/ArtifactPanel.vue'
import { useArtifact } from '@/composables/useArtifact'

const { state: connectionState } = useWebSocket()
const { checkStatus } = useLlmConfig()
const router = useRouter()
const { artifact } = useArtifact()
const { files: attachedFiles, uploadFile, removeFile, clearFiles } = useFileUpload()
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
  loadSessions,
  createSession,
  selectSession,
  deleteSession,
  sendMessage,
  confirmToolCall,
  cancelRun
} = useChat()

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

function handleSend(prompt: string, filePaths: string[]) {
  // 传递文件路径和附件信息给 sendMessage
  sendMessage(
    prompt,
    filePaths.length > 0 ? filePaths : undefined,
    attachedFiles.value.length > 0 ? attachedFiles.value : undefined
  )
  clearFiles()
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

function handleRemoveFile(fileId: string) {
  removeFile(fileId)
}

function handleSelectSubagent(subRunId: string) {
  // Toggle: if already selected, close panel
  if (activeSubagentId.value === subRunId) {
    setActiveSubagent(null)
  } else {
    setActiveSubagent(subRunId)
  }
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
    }
  }, 200)
})
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
        @confirm="handleConfirmToolCall"
        @select-subagent="handleSelectSubagent"
      />

      <MessageInput
        :disabled="isStreaming || connectionState !== 'connected'"
        :is-running="isStreaming"
        :attached-files="attachedFiles"
        @send="handleSend"
        @cancel="handleCancel"
        @attach-file="handleAttachFile"
        @remove-file="handleRemoveFile"
      />
    </main>

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

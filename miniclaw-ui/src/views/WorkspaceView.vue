<script setup lang="ts">
import { onMounted, onUnmounted } from 'vue'
import { useWebSocket } from '@/composables/useWebSocket'
import { useChat } from '@/composables/useChat'
import ConnectionStatus from '@/components/ConnectionStatus.vue'
import SessionSidebar from '@/components/SessionSidebar.vue'
import MessageList from '@/components/MessageList.vue'
import MessageInput from '@/components/MessageInput.vue'
import SubagentPanel from '@/components/SubagentPanel.vue'
import ArtifactPanel from '@/components/ArtifactPanel.vue'
import { useArtifact } from '@/composables/useArtifact'

const { state: connectionState, connect, disconnect } = useWebSocket()
const { artifact } = useArtifact()
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
  cancelRun,
  setupEventListeners
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

function handleSend(prompt: string) {
  sendMessage(prompt)
}

function handleConfirmToolCall(callId: string, decision: 'approve' | 'reject') {
  confirmToolCall(callId, decision)
}

function handleCancel() {
  cancelRun()
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
  connect()
  setupEventListeners()

  // Load sessions once connected
  const checkConnection = setInterval(() => {
    if (connectionState.value === 'connected') {
      clearInterval(checkConnection)
      loadSessions()
    }
  }, 200)
})

onUnmounted(() => {
  disconnect()
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
        @send="handleSend"
        @cancel="handleCancel"
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
  transition: all 0.2s ease;
}

.main-header {
  padding: 20px 48px;
  border-bottom: var(--border-light);
  display: flex;
  align-items: center;
}

.session-title {
  font-family: var(--font-mono);
  font-size: 13px;
  font-weight: 500;
  letter-spacing: -0.01em;
  color: var(--color-gray-dark);
}

/* Panel slide transition */
.panel-slide-enter-active,
.panel-slide-leave-active {
  transition: opacity 0.2s ease, transform 0.2s ease;
}

.panel-slide-enter-from,
.panel-slide-leave-to {
  opacity: 0;
  transform: translateX(40px);
}
</style>

<script setup lang="ts">
import { onMounted, onUnmounted } from 'vue'
import { useWebSocket } from '@/composables/useWebSocket'
import { useChat } from '@/composables/useChat'
import ConnectionStatus from '@/components/ConnectionStatus.vue'
import SessionSidebar from '@/components/SessionSidebar.vue'
import MessageList from '@/components/MessageList.vue'
import MessageInput from '@/components/MessageInput.vue'

const { state: connectionState, connect, disconnect } = useWebSocket()
const {
  sessions,
  currentSession,
  currentSessionId,
  messages,
  streamingContent,
  isStreaming,
  loadSessions,
  createSession,
  selectSession,
  sendMessage,
  setupEventListeners
} = useChat()

async function handleCreateSession() {
  const session = await createSession('New Conversation')
  await selectSession(session.id)
}

async function handleSelectSession(id: string) {
  await selectSession(id)
}

function handleSend(prompt: string) {
  sendMessage(prompt)
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
      :sessions="sessions"
      :current-id="currentSessionId"
      @select="handleSelectSession"
      @create="handleCreateSession"
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
        :streaming-content="streamingContent"
        :is-streaming="isStreaming"
      />

      <MessageInput
        :disabled="isStreaming || connectionState !== 'connected'"
        @send="handleSend"
      />
    </main>
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
</style>

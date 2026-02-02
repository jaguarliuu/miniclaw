<script setup lang="ts">
import { ref, watch, nextTick } from 'vue'
import type { Message } from '@/types'
import MessageItem from './MessageItem.vue'

const props = defineProps<{
  messages: Message[]
  streamingContent: string
  isStreaming: boolean
}>()

const containerRef = ref<HTMLElement | null>(null)

// Auto-scroll on new content
watch(
  () => [props.messages.length, props.streamingContent],
  async () => {
    await nextTick()
    if (containerRef.value) {
      containerRef.value.scrollTop = containerRef.value.scrollHeight
    }
  }
)
</script>

<template>
  <div class="message-list" ref="containerRef">
    <div class="message-container">
      <!-- Empty state -->
      <div v-if="messages.length === 0 && !isStreaming" class="empty-state">
        <p class="empty-title">Ready</p>
        <p class="empty-hint">Type a message to begin</p>
      </div>

      <!-- Messages -->
      <MessageItem
        v-for="message in messages"
        :key="message.id"
        :message="message"
      />

      <!-- Streaming indicator -->
      <article v-if="isStreaming" class="message assistant streaming">
        <div class="message-meta">
          <span class="role">Assistant</span>
          <span class="streaming-indicator"></span>
        </div>
        <div class="message-content">
          <p v-if="streamingContent">{{ streamingContent }}</p>
          <p v-else class="thinking">...</p>
        </div>
      </article>
    </div>
  </div>
</template>

<style scoped>
.message-list {
  flex: 1;
  overflow-y: auto;
  padding: 0 48px;
}

.message-container {
  max-width: 720px;
  margin: 0 auto;
  padding: 48px 0;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 200px;
  text-align: center;
}

.empty-title {
  font-family: var(--font-mono);
  font-size: 14px;
  font-weight: 500;
  letter-spacing: 0.05em;
  text-transform: uppercase;
  margin-bottom: 8px;
}

.empty-hint {
  font-size: 14px;
  color: var(--color-gray-dark);
}

/* Streaming message styles */
.message {
  padding: 24px 0;
  border-bottom: var(--border-light);
}

.message-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.role {
  font-family: var(--font-mono);
  font-size: 11px;
  font-weight: 500;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  color: var(--color-gray-dark);
}

.streaming-indicator {
  width: 6px;
  height: 6px;
  background: var(--color-black);
  animation: blink 0.6s ease-in-out infinite;
}

@keyframes blink {
  0%, 100% {
    opacity: 1;
  }
  50% {
    opacity: 0.2;
  }
}

.message-content {
  font-size: 15px;
  line-height: 1.7;
}

.message-content p {
  margin-bottom: 1em;
  white-space: pre-wrap;
}

.message-content p:last-child {
  margin-bottom: 0;
}

.thinking {
  color: var(--color-gray-dark);
  animation: fade 1s ease-in-out infinite;
}

@keyframes fade {
  0%, 100% {
    opacity: 0.3;
  }
  50% {
    opacity: 1;
  }
}
</style>

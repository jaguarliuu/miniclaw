<script setup lang="ts">
import { ref, watch, nextTick, computed } from 'vue'
import type { Message, StreamBlock } from '@/types'
import { useMarkdown } from '@/composables/useMarkdown'
import MessageItem from './MessageItem.vue'
import ToolCallCard from './ToolCallCard.vue'
import SubagentCard from './SubagentCard.vue'

const props = defineProps<{
  messages: Message[]
  streamBlocks: StreamBlock[]
  isStreaming: boolean
  activeSubagentId?: string | null
}>()

const emit = defineEmits<{
  confirm: [callId: string, decision: 'approve' | 'reject']
  'select-subagent': [subRunId: string]
}>()

const containerRef = ref<HTMLElement | null>(null)

const { render } = useMarkdown()

// 渲染文本块的 Markdown
function renderTextBlock(content: string | undefined): string {
  return render(content || '')
}

        // 检查是否有内容（用于显示 thinking 状态）
const hasContent = computed(() => {
  return props.streamBlocks.some(block =>
    (block.type === 'text' && block.content) || block.type === 'tool' || block.type === 'subagent'
  )
})

// Auto-scroll on new content
watch(
  () => [props.messages.length, props.streamBlocks.length, props.streamBlocks.map(b => b.type === 'text' ? b.content?.length : b.toolCall?.status).join(',')],
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
        :active-subagent-id="activeSubagentId"
        @confirm="(callId, decision) => emit('confirm', callId, decision)"
        @select-subagent="(subRunId) => emit('select-subagent', subRunId)"
      />

      <!-- Streaming message with interleaved blocks -->
      <article v-if="isStreaming" class="message assistant streaming">
        <div class="message-meta">
          <span class="role">Assistant</span>
          <span class="streaming-indicator">
            <span class="streaming-dot"></span>
            <span class="streaming-dot"></span>
            <span class="streaming-dot"></span>
          </span>
        </div>

        <!-- Thinking state when no content yet -->
        <div v-if="!hasContent" class="message-content">
          <p class="thinking">...</p>
        </div>

        <!-- Interleaved blocks -->
        <template v-for="block in streamBlocks" :key="block.id">
          <!-- Text block -->
          <div
            v-if="block.type === 'text' && block.content"
            class="message-content markdown-body"
            v-html="renderTextBlock(block.content)"
          ></div>

          <!-- Tool block -->
          <ToolCallCard
            v-else-if="block.type === 'tool' && block.toolCall"
            :tool-call="block.toolCall"
            @confirm="(callId, decision) => emit('confirm', callId, decision)"
          />

          <!-- SubAgent block -->
          <SubagentCard
            v-else-if="block.type === 'subagent' && block.subagent"
            :subagent="block.subagent"
            :active-subagent-id="activeSubagentId"
            @select="(subRunId) => emit('select-subagent', subRunId)"
          />
        </template>
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
  color: var(--color-gray-400);
}

/* Streaming message styles */
.message {
  padding: 20px 0;
  border-bottom: 1px solid var(--color-gray-100);
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
  color: var(--color-gray-500);
}

.streaming-indicator {
  display: inline-flex;
  align-items: center;
  gap: 3px;
}
.streaming-dot {
  width: 4px;
  height: 4px;
  border-radius: 50%;
  background: var(--color-black);
  animation: stream-bounce 1.4s ease-in-out infinite;
}
.streaming-dot:nth-child(2) { animation-delay: 0.16s; }
.streaming-dot:nth-child(3) { animation-delay: 0.32s; }
@keyframes stream-bounce {
  0%, 80%, 100% { opacity: 0.2; transform: scale(0.8); }
  40% { opacity: 1; transform: scale(1); }
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

<!-- Markdown styles (unscoped to apply to v-html content) -->
<style>
@import '@/styles/markdown.css';
</style>

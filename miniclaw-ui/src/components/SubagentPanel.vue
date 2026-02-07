<script setup lang="ts">
import { ref, watch, nextTick, computed } from 'vue'
import type { SubagentInfo } from '@/types'
import { useMarkdown } from '@/composables/useMarkdown'
import ToolCallCard from './ToolCallCard.vue'

const props = defineProps<{
  subagent: SubagentInfo
}>()

const emit = defineEmits<{
  close: []
  confirm: [callId: string, decision: 'approve' | 'reject']
}>()

const { render } = useMarkdown()
const bodyRef = ref<HTMLElement | null>(null)

const statusIcon = computed(() => {
  switch (props.subagent.status) {
    case 'queued': return '\u25CB'
    case 'running': return '\u25CF'
    case 'completed': return '\u2713'
    case 'failed': return '\u2717'
    default: return '\u2022'
  }
})

const statusLabel = computed(() => {
  switch (props.subagent.status) {
    case 'queued': return 'Queued'
    case 'running': return 'Running'
    case 'completed': return 'Completed'
    case 'failed': return 'Failed'
    default: return ''
  }
})

const durationDisplay = computed(() => {
  if (props.subagent.durationMs != null) {
    const sec = Math.floor(props.subagent.durationMs / 1000)
    return sec >= 60 ? `${Math.floor(sec / 60)}m ${sec % 60}s` : `${sec}s`
  }
  return ''
})

const hasBlocks = computed(() => {
  const blocks = props.subagent.streamBlocks
  return blocks != null && blocks.length > 0
})

function renderTextBlock(content: string): string {
  return render(content)
}

// Auto-scroll on new blocks
watch(
  () => props.subagent.streamBlocks?.length,
  async () => {
    await nextTick()
    if (bodyRef.value) {
      bodyRef.value.scrollTop = bodyRef.value.scrollHeight
    }
  }
)
</script>

<template>
  <aside class="subagent-panel">
    <div class="panel-header">
      <div class="panel-title-row">
        <span class="panel-icon" :class="subagent.status">{{ statusIcon }}</span>
        <span class="panel-agent">{{ subagent.agentId }}</span>
        <span class="panel-status">{{ statusLabel }}</span>
        <span v-if="durationDisplay" class="panel-duration">{{ durationDisplay }}</span>
        <button class="panel-close" @click="emit('close')" title="Close panel">&times;</button>
      </div>
      <div class="panel-task">{{ subagent.task }}</div>
    </div>

    <div class="panel-body" ref="bodyRef">
      <template v-if="hasBlocks">
        <template v-for="block in subagent.streamBlocks" :key="block.id">
          <ToolCallCard
            v-if="block.type === 'tool' && block.toolCall"
            :tool-call="block.toolCall"
            @confirm="(callId, decision) => emit('confirm', callId, decision)"
          />
          <div
            v-else-if="block.type === 'text' && block.content"
            class="panel-text markdown-body"
            v-html="renderTextBlock(block.content)"
          />
        </template>
      </template>
      <div v-else class="panel-empty">
        <p v-if="subagent.status === 'running' || subagent.status === 'queued'">Waiting for activity...</p>
        <p v-else>No work details recorded.</p>
      </div>
    </div>
  </aside>
</template>

<style scoped>
.subagent-panel {
  width: var(--detail-panel-width, 480px);
  height: 100%;
  display: flex;
  flex-direction: column;
  background: var(--color-white);
  border-left: var(--border);
  flex-shrink: 0;
}

.panel-header {
  padding: 16px 20px;
  border-bottom: var(--border);
}

.panel-title-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.panel-icon {
  font-size: 12px;
  line-height: 1;
}

.panel-icon.running {
  animation: pulse 1.5s ease-in-out infinite;
}

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.3; }
}

.panel-agent {
  font-family: var(--font-mono);
  font-size: 13px;
  font-weight: 600;
}

.panel-status {
  font-family: var(--font-mono);
  font-size: 11px;
  color: var(--color-gray-dark);
  text-transform: uppercase;
  letter-spacing: 0.05em;
}

.panel-duration {
  font-family: var(--font-mono);
  font-size: 11px;
  color: var(--color-gray-dark);
}

.panel-close {
  margin-left: auto;
  width: 28px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: var(--border);
  background: var(--color-white);
  font-size: 18px;
  cursor: pointer;
  transition: all 0.15s ease;
}

.panel-close:hover {
  background: var(--color-black);
  color: var(--color-white);
}

.panel-task {
  font-family: var(--font-mono);
  font-size: 12px;
  line-height: 1.5;
  padding: 8px;
  background: var(--color-gray-bg);
  border: var(--border-light);
}

.panel-body {
  flex: 1;
  overflow-y: auto;
  padding: 16px 20px;
}

.panel-text {
  font-size: 14px;
  line-height: 1.6;
  padding: 8px 0;
}

.panel-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: var(--color-gray-dark);
  font-family: var(--font-mono);
  font-size: 12px;
}
</style>

<!-- Markdown styles (unscoped to apply to v-html content) -->
<style>
@import '@/styles/markdown.css';
</style>

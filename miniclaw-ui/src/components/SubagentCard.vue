<script setup lang="ts">
import { computed, ref, onMounted, onUnmounted } from 'vue'
import type { SubagentInfo } from '@/types'
import { useSubagent } from '@/composables/useSubagent'
import { useMarkdown } from '@/composables/useMarkdown'

const props = defineProps<{
  subagent: SubagentInfo
  activeSubagentId?: string | null
}>()

const emit = defineEmits<{
  select: [subRunId: string]
}>()

const { stopSubagent } = useSubagent()
const { render } = useMarkdown()

const isResultExpanded = ref(false)
const elapsed = ref(0)
let timer: ReturnType<typeof setInterval> | null = null

const RESULT_TRUNCATE_LENGTH = 800

// 计时器（running 状态时自动更新）
onMounted(() => {
  if (props.subagent.status === 'running') {
    startTimer()
  }
})

onUnmounted(() => {
  if (timer) clearInterval(timer)
})

function startTimer() {
  if (timer) return
  elapsed.value = props.subagent.startedAt
    ? Math.floor((Date.now() - props.subagent.startedAt) / 1000)
    : 0
  timer = setInterval(() => {
    if (props.subagent.status === 'running' && props.subagent.startedAt) {
      elapsed.value = Math.floor((Date.now() - props.subagent.startedAt) / 1000)
    } else if (props.subagent.status !== 'running' && props.subagent.status !== 'queued') {
      if (timer) { clearInterval(timer); timer = null }
    }
  }, 1000)
}

const statusIcon = computed(() => {
  switch (props.subagent.status) {
    case 'queued': return '\u25CB'   // ○
    case 'running': return '\u25CF'  // ●
    case 'completed': return '\u2713' // ✓
    case 'failed': return '\u2717'    // ✗
    default: return '\u2022'          // •
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
  if (props.subagent.status === 'running' || props.subagent.status === 'queued') {
    return elapsed.value >= 60
      ? `${Math.floor(elapsed.value / 60)}m ${elapsed.value % 60}s`
      : `${elapsed.value}s`
  }
  return ''
})

const taskDisplay = computed(() => {
  const task = props.subagent.task || ''
  return task.length > 120 ? task.substring(0, 117) + '...' : task
})

const isActive = computed(() =>
  props.subagent.status === 'queued' || props.subagent.status === 'running'
)

const isResultLong = computed(() => {
  if (!props.subagent.result) return false
  return props.subagent.result.length > RESULT_TRUNCATE_LENGTH
})

const renderedResult = computed(() => {
  if (!props.subagent.result) return ''
  const text = isResultExpanded.value || !isResultLong.value
    ? props.subagent.result
    : props.subagent.result.substring(0, RESULT_TRUNCATE_LENGTH)
  return render(text)
})

// 内部工具调用步骤数量
const innerStepCount = computed(() => {
  const blocks = props.subagent.streamBlocks
  if (!blocks) return 0
  return blocks.filter(b => b.type === 'tool').length
})

// 是否可点击（有 inner blocks 或正在运行）
const isClickable = computed(() =>
  innerStepCount.value > 0 || props.subagent.status === 'running'
)

// 是否为当前选中的 subagent
const isSelected = computed(() =>
  props.activeSubagentId === props.subagent.subRunId
)

function handleClick() {
  if (isClickable.value) {
    emit('select', props.subagent.subRunId)
  }
}

async function handleStop(e: Event) {
  e.stopPropagation()
  try {
    await stopSubagent(props.subagent.subRunId)
  } catch (e) {
    console.error('Failed to stop subagent:', e)
  }
}
</script>

<template>
  <div class="subagent-card" :class="[subagent.status, { clickable: isClickable, active: isSelected }]"
       @click="handleClick">
    <div class="subagent-header">
      <span class="subagent-icon" :class="{ pulse: subagent.status === 'running' }">{{ statusIcon }}</span>
      <span class="subagent-label">SubAgent</span>
      <span class="subagent-agent">{{ subagent.agentId }}</span>
      <span v-if="innerStepCount > 0" class="step-badge">{{ innerStepCount }} steps</span>
      <span class="subagent-status">{{ statusLabel }}</span>
      <span v-if="durationDisplay" class="subagent-duration">{{ durationDisplay }}</span>
    </div>

    <div class="subagent-task">
      {{ taskDisplay }}
    </div>

    <!-- Active controls -->
    <div v-if="isActive" class="subagent-actions">
      <button class="btn-stop" @click="handleStop">Stop</button>
    </div>

    <!-- Error display -->
    <div v-if="subagent.error" class="subagent-error">
      <span class="error-label">Error:</span>
      {{ subagent.error }}
    </div>

    <!-- Result display -->
    <div v-if="subagent.result" class="subagent-result" :class="{ expanded: isResultExpanded }">
      <div class="result-header">
        <span class="result-label">Result</span>
        <button v-if="isResultLong" class="expand-btn" @click.stop="isResultExpanded = !isResultExpanded">
          {{ isResultExpanded ? 'Collapse' : 'Expand' }}
        </button>
      </div>
      <div class="result-content markdown-body" v-html="renderedResult"></div>
      <div v-if="isResultLong && !isResultExpanded" class="truncation-fade"></div>
    </div>
  </div>
</template>

<style scoped>
.subagent-card {
  margin: 12px 0;
  padding: 14px 16px;
  border: var(--border);
  border-radius: var(--radius-lg);
  background: var(--color-gray-bg);
  font-family: var(--font-mono);
  font-size: 12px;
}

.subagent-card.clickable {
  cursor: pointer;
  transition: background var(--duration-fast) var(--ease-in-out), border-color var(--duration-fast) var(--ease-in-out);
}

.subagent-card.clickable:hover {
  background: var(--color-white);
  border-color: var(--color-black);
}

.subagent-card.active {
  background: var(--color-white);
  border-color: var(--color-black);
  box-shadow: var(--shadow-sm);
}

.subagent-card.completed {
  border-color: var(--color-success);
}

.subagent-card.failed {
  border-color: var(--color-error);
}

.subagent-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.subagent-icon {
  font-size: 12px;
  line-height: 1;
}

.subagent-icon.pulse {
  animation: pulse 1.5s ease-in-out infinite;
}

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.3; }
}

.subagent-label {
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  font-size: 11px;
}

.subagent-agent {
  color: var(--color-gray-dark);
  font-size: 11px;
}

.step-badge {
  font-size: 10px;
  padding: 1px 6px;
  background: var(--color-black);
  color: var(--color-white);
  letter-spacing: 0.03em;
  border-radius: var(--radius-sm);
}

.subagent-status {
  margin-left: auto;
  font-size: 11px;
  color: var(--color-gray-dark);
}

.subagent-duration {
  font-size: 11px;
  color: var(--color-gray-dark);
}

.subagent-task {
  padding: 8px;
  background: var(--color-white);
  border: var(--border-light);
  border-radius: var(--radius-md);
  line-height: 1.5;
}

.subagent-actions {
  display: flex;
  gap: 8px;
  margin-top: 10px;
}

.btn-stop {
  padding: 4px 12px;
  border: var(--border);
  border-radius: var(--radius-md);
  background: var(--color-white);
  font-family: var(--font-mono);
  font-size: 11px;
  font-weight: 500;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  cursor: pointer;
  transition: all var(--duration-fast) var(--ease-in-out);
}

.btn-stop:hover {
  background: var(--color-gray-bg);
  border-color: var(--color-error);
  color: var(--color-error);
}

.subagent-error {
  margin-top: 8px;
  padding: 8px;
  background: #fff5f5;
  border: 1px solid var(--color-error);
  border-radius: var(--radius-md);
  color: var(--color-error);
  line-height: 1.5;
}

.error-label {
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  font-size: 11px;
}

.subagent-result {
  margin-top: 8px;
  padding: 8px;
  background: var(--color-white);
  border: var(--border-light);
  border-radius: var(--radius-md);
  max-height: 200px;
  overflow: auto;
  position: relative;
}

.subagent-result.expanded {
  max-height: none;
}

.result-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 6px;
}

.result-label {
  font-size: 11px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  color: var(--color-gray-dark);
}

.expand-btn {
  background: none;
  border: none;
  font-family: var(--font-mono);
  font-size: 11px;
  color: var(--color-gray-dark);
  cursor: pointer;
  padding: 0 4px;
  text-decoration: underline;
  text-underline-offset: 2px;
}

.expand-btn:hover {
  color: var(--color-black);
}

.result-content {
  font-size: 13px;
  line-height: 1.6;
}

.truncation-fade {
  position: sticky;
  bottom: 0;
  left: 0;
  right: 0;
  height: 32px;
  background: linear-gradient(transparent, var(--color-white));
  pointer-events: none;
}
</style>

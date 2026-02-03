<script setup lang="ts">
import { computed } from 'vue'
import type { ToolCall } from '@/types'

const props = defineProps<{
  toolCall: ToolCall
}>()

const emit = defineEmits<{
  confirm: [callId: string, decision: 'approve' | 'reject']
}>()

const statusIcon = computed(() => {
  switch (props.toolCall.status) {
    case 'pending':
      return '⏳'
    case 'confirmed':
    case 'executing':
      return '⚙️'
    case 'success':
      return '✓'
    case 'error':
      return '✗'
    case 'rejected':
      return '⊘'
    default:
      return '•'
  }
})

const statusLabel = computed(() => {
  switch (props.toolCall.status) {
    case 'pending':
      return 'Waiting for approval'
    case 'confirmed':
      return 'Approved'
    case 'executing':
      return 'Executing'
    case 'success':
      return 'Completed'
    case 'error':
      return 'Failed'
    case 'rejected':
      return 'Rejected'
    default:
      return ''
  }
})

// 工具名称的友好显示
const toolDisplayName = computed(() => {
  const names: Record<string, string> = {
    read_file: '读取文件',
    write_file: '写入文件',
    shell: '执行命令',
    http_get: 'HTTP 请求'
  }
  return names[props.toolCall.toolName] || props.toolCall.toolName
})

// 格式化参数为人类可读的形式
const formattedArgs = computed(() => {
  const args = props.toolCall.arguments
  const toolName = props.toolCall.toolName

  switch (toolName) {
    case 'read_file':
      return `📄 ${args.path || args.filePath || '未知路径'}`

    case 'write_file': {
      const path = args.path || args.filePath || '未知路径'
      const content = String(args.content || '')
      const preview = content.length > 100 ? content.substring(0, 100) + '...' : content
      return `📝 ${path}\n\n内容:\n${preview}`
    }

    case 'shell': {
      const cmd = args.command || args.cmd || ''
      return `$ ${cmd}`
    }

    case 'http_get':
      return `🌐 ${args.url || '未知 URL'}`

    default:
      // 其他工具显示简化的 JSON
      return formatJson(args)
  }
})

// 格式化结果为人类可读的形式
const formattedResult = computed(() => {
  if (!props.toolCall.result) return ''

  const result = props.toolCall.result
  const toolName = props.toolCall.toolName

  // 如果结果太长，截断显示
  if (result.length > 500) {
    return result.substring(0, 500) + '\n... (内容已截断)'
  }

  return result
})

// 简化 JSON 显示
function formatJson(obj: Record<string, unknown>): string {
  const lines: string[] = []
  for (const [key, value] of Object.entries(obj)) {
    const displayValue = typeof value === 'string'
      ? (value.length > 80 ? value.substring(0, 80) + '...' : value)
      : JSON.stringify(value)
    lines.push(`${key}: ${displayValue}`)
  }
  return lines.join('\n')
}

function handleApprove() {
  emit('confirm', props.toolCall.callId, 'approve')
}

function handleReject() {
  emit('confirm', props.toolCall.callId, 'reject')
}
</script>

<template>
  <div class="tool-card" :class="toolCall.status">
    <div class="tool-header">
      <span class="tool-icon">{{ statusIcon }}</span>
      <span class="tool-name">{{ toolDisplayName }}</span>
      <span class="tool-status">{{ statusLabel }}</span>
    </div>

    <div class="tool-args">
      <pre>{{ formattedArgs }}</pre>
    </div>

    <!-- HITL Confirmation Buttons -->
    <div v-if="toolCall.status === 'pending' && toolCall.requiresConfirm" class="tool-actions">
      <button class="btn-approve" @click="handleApprove">Approve</button>
      <button class="btn-reject" @click="handleReject">Reject</button>
    </div>

    <!-- Result -->
    <div v-if="toolCall.result" class="tool-result" :class="{ error: toolCall.status === 'error' }">
      <div class="result-label">结果:</div>
      <pre>{{ formattedResult }}</pre>
    </div>
  </div>
</template>

<style scoped>
.tool-card {
  margin: 12px 0;
  padding: 12px 16px;
  border: var(--border);
  background: var(--color-gray-bg);
  font-family: var(--font-mono);
  font-size: 12px;
}

.tool-card.success {
  border-color: #2d2;
}

.tool-card.error {
  border-color: #d22;
}

.tool-card.rejected {
  border-color: var(--color-gray-dark);
  opacity: 0.7;
}

.tool-card.pending {
  border-color: #fa0;
}

.tool-card.executing {
  border-color: var(--color-black);
}

.tool-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.tool-icon {
  font-size: 14px;
}

.tool-name {
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.05em;
}

.tool-status {
  margin-left: auto;
  color: var(--color-gray-dark);
  font-size: 11px;
}

.tool-args {
  background: var(--color-white);
  border: var(--border-light);
  padding: 8px;
  max-height: 120px;
  overflow: auto;
}

.tool-args pre {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-all;
}

.tool-actions {
  display: flex;
  gap: 8px;
  margin-top: 12px;
}

.tool-actions button {
  flex: 1;
  padding: 8px 16px;
  border: var(--border);
  background: var(--color-white);
  font-family: var(--font-mono);
  font-size: 12px;
  font-weight: 500;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  cursor: pointer;
  transition: all 0.15s ease;
}

.btn-approve {
  background: var(--color-black) !important;
  color: var(--color-white);
}

.btn-approve:hover {
  opacity: 0.8;
}

.btn-reject:hover {
  background: var(--color-gray-bg) !important;
}

.tool-result {
  margin-top: 8px;
  padding: 8px;
  background: var(--color-white);
  border: var(--border-light);
  max-height: 200px;
  overflow: auto;
}

.tool-result.error {
  border-color: #d22;
  color: #d22;
}

.tool-result .result-label {
  font-size: 11px;
  color: var(--color-gray-dark);
  margin-bottom: 4px;
  text-transform: uppercase;
  letter-spacing: 0.05em;
}

.tool-result pre {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-all;
}

/* Animation for executing status */
.tool-card.executing .tool-icon {
  animation: spin 1s linear infinite;
}

@keyframes spin {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}
</style>

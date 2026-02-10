<script setup lang="ts">
import type { AttachedContext } from '@/types'

const props = defineProps<{
  context: AttachedContext
  readonly?: boolean
}>()

const emit = defineEmits<{
  remove: [contextId: string]
}>()

/**
 * 获取上下文类型的图标
 */
function getContextIcon(type: string): string {
  const map: Record<string, string> = {
    file: '📄',
    folder: '📁',
    web: '🌐',
    doc: '📝',
    code: '💻',
    rule: '📋',
    workspace: '🗂️',
    problems: '⚠️'
  }
  return map[type] || '📎'
}

/**
 * 获取上下文类型的标签文本
 */
function getTypeLabel(type: string): string {
  const map: Record<string, string> = {
    file: 'FILE',
    folder: 'FOLDER',
    web: 'WEB',
    doc: 'DOC',
    code: 'CODE',
    rule: 'RULE',
    workspace: 'WORKSPACE',
    problems: 'PROBLEMS'
  }
  return map[type] || 'CONTEXT'
}

/**
 * 格式化文件大小
 */
function formatSize(bytes: number): string {
  if (bytes < 1024) return bytes + 'B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(0) + 'KB'
  return (bytes / (1024 * 1024)).toFixed(1) + 'MB'
}

/**
 * 获取上下文的详细信息（用于显示）
 */
function getContextDetail(context: AttachedContext): string {
  switch (context.type) {
    case 'file':
      return context.size ? formatSize(context.size) : ''
    case 'folder':
      return context.folderPath || ''
    case 'web':
      return context.url || ''
    default:
      return ''
  }
}
</script>

<template>
  <div class="context-chip" :class="{ uploading: context.uploading }">
    <span class="context-icon">{{ getContextIcon(context.type) }}</span>
    <span class="context-type">{{ getTypeLabel(context.type) }}</span>
    <span class="context-name">{{ context.displayName }}</span>
    <span v-if="getContextDetail(context)" class="context-detail">{{ getContextDetail(context) }}</span>
    <span v-if="context.uploading" class="upload-spinner"></span>
    <button v-if="!readonly" class="remove-btn" @click.stop="emit('remove', context.id)" title="Remove">
      <span>&times;</span>
    </button>
  </div>
</template>

<style scoped>
.context-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 8px;
  border: var(--border);
  border-radius: var(--radius-md);
  background: var(--color-gray-50);
  font-family: var(--font-mono);
  font-size: 11px;
  line-height: 1;
  max-width: 320px;
}

.context-chip.uploading {
  opacity: 0.6;
}

.context-icon {
  font-size: 14px;
  line-height: 1;
  flex-shrink: 0;
}

.context-type {
  font-weight: 700;
  font-size: 9px;
  letter-spacing: 0.05em;
  padding: 2px 4px;
  background: var(--color-gray-200);
  border-radius: var(--radius-sm);
  color: var(--color-gray-600);
  flex-shrink: 0;
}

.context-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--color-gray-700);
  font-weight: 500;
}

.context-detail {
  color: var(--color-gray-400);
  flex-shrink: 0;
  font-size: 10px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 120px;
}

.upload-spinner {
  width: 8px;
  height: 8px;
  border: 1.5px solid var(--color-gray-300);
  border-top-color: var(--color-gray-600);
  border-radius: 50%;
  flex-shrink: 0;
  animation: spin 0.8s linear infinite;
}

.remove-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 14px;
  height: 14px;
  border: none;
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--color-gray-400);
  cursor: pointer;
  font-size: 14px;
  line-height: 1;
  padding: 0;
  flex-shrink: 0;
  transition: all var(--duration-fast) var(--ease-in-out);
}

.remove-btn:hover {
  background: var(--color-gray-200);
  color: var(--color-gray-700);
}

@keyframes spin {
  to { transform: rotate(360deg); }
}
</style>

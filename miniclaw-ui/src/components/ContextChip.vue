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
  padding: 6px 10px;
  background: var(--color-gray-100);
  border: 1px solid var(--color-gray-200);
  border-radius: 8px;
  font-family: var(--font-ui);
  font-size: 13px;
  line-height: 1.2;
  max-width: 320px;
  transition: all 0.15s ease;
  animation: chipIn 0.2s cubic-bezier(0.34, 1.56, 0.64, 1);
}

@keyframes chipIn {
  from {
    opacity: 0;
    transform: scale(0.9);
  }
  to {
    opacity: 1;
    transform: scale(1);
  }
}

.context-chip:hover {
  background: var(--color-gray-200);
  border-color: var(--color-gray-300);
}

.context-chip.uploading {
  opacity: 0.6;
  pointer-events: none;
}

.context-icon {
  font-size: 14px;
  line-height: 1;
  flex-shrink: 0;
}

.context-type {
  font-family: var(--font-mono);
  font-weight: 600;
  font-size: 9px;
  letter-spacing: 0.08em;
  padding: 2px 5px;
  background: var(--color-white);
  border-radius: 4px;
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
  color: var(--color-gray-500);
  flex-shrink: 0;
  font-size: 11px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 120px;
}

.upload-spinner {
  width: 10px;
  height: 10px;
  border: 2px solid var(--color-gray-300);
  border-top-color: var(--color-gray-600);
  border-radius: 50%;
  flex-shrink: 0;
  animation: spin 0.7s linear infinite;
}

.remove-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  border: none;
  border-radius: 4px;
  background: transparent;
  color: var(--color-gray-500);
  cursor: pointer;
  font-size: 16px;
  line-height: 1;
  padding: 0;
  flex-shrink: 0;
  transition: all 0.12s ease;
}

.remove-btn:hover {
  background: var(--color-gray-300);
  color: var(--color-gray-700);
  transform: scale(1.1);
}

@keyframes spin {
  to { transform: rotate(360deg); }
}
</style>

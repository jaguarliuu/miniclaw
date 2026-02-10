<script setup lang="ts">
import type { AttachedFile } from '@/types'

const props = defineProps<{
  file: AttachedFile
  readonly?: boolean
}>()

const emit = defineEmits<{
  remove: [fileId: string]
}>()

function getFileIcon(filename: string): string {
  const ext = filename.split('.').pop()?.toLowerCase() || ''
  const map: Record<string, string> = {
    pdf: 'PDF', docx: 'DOC', xlsx: 'XLS', pptx: 'PPT',
    md: 'MD', txt: 'TXT', csv: 'CSV', json: 'JSON',
    yaml: 'YML', yml: 'YML', xml: 'XML', html: 'HTML'
  }
  return map[ext] || 'FILE'
}

function formatSize(bytes: number): string {
  if (bytes < 1024) return bytes + 'B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(0) + 'KB'
  return (bytes / (1024 * 1024)).toFixed(1) + 'MB'
}
</script>

<template>
  <div class="file-chip" :class="{ uploading: file.uploading }">
    <span class="file-icon">{{ getFileIcon(file.filename || file.displayName) }}</span>
    <span class="file-name">{{ file.filename || file.displayName }}</span>
    <span class="file-size">{{ formatSize(file.size || 0) }}</span>
    <span v-if="file.uploading" class="upload-spinner"></span>
    <button v-if="!readonly" class="remove-btn" @click.stop="emit('remove', file.id)" title="Remove">
      <span>&times;</span>
    </button>
  </div>
</template>

<style scoped>
.file-chip {
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
  max-width: 240px;
}

.file-chip.uploading {
  opacity: 0.6;
}

.file-icon {
  font-weight: 700;
  font-size: 9px;
  letter-spacing: 0.05em;
  padding: 2px 4px;
  background: var(--color-gray-200);
  border-radius: var(--radius-sm);
  color: var(--color-gray-600);
  flex-shrink: 0;
}

.file-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--color-gray-700);
}

.file-size {
  color: var(--color-gray-400);
  flex-shrink: 0;
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

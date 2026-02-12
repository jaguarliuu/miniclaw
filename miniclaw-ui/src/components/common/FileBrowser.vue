<script setup lang="ts">
import { ref, watch } from 'vue'
import { useWebSocket } from '@/composables/useWebSocket'

const props = defineProps<{
  visible: boolean
  mode?: 'all' | 'dir' | 'file'  // what can be selected
  title?: string
}>()

const emit = defineEmits<{
  select: [path: string]
  cancel: []
}>()

const { request } = useWebSocket()

const dialogRef = ref<HTMLDialogElement | null>(null)
const currentPath = ref('')
const parentPath = ref('')
const entries = ref<Array<{ name: string; path: string; isDirectory: boolean; size?: number }>>([])
const loading = ref(false)
const error = ref<string | null>(null)
const selectedPath = ref('')

watch(() => props.visible, (visible) => {
  if (visible) {
    dialogRef.value?.showModal()
    browse('')  // start from roots
    selectedPath.value = ''
  } else {
    dialogRef.value?.close()
  }
})

async function browse(path: string) {
  loading.value = true
  error.value = null
  try {
    const res = await request<{
      path: string
      parent?: string
      entries: Array<{ name: string; path: string; isDirectory: boolean; size?: number }>
    }>('files.browse', { path, mode: props.mode === 'dir' ? 'all' : 'all' })
    currentPath.value = res.path
    parentPath.value = res.parent || ''
    entries.value = res.entries
  } catch (e) {
    error.value = e instanceof Error ? e.message : 'Failed to browse'
  } finally {
    loading.value = false
  }
}

function handleEntryClick(entry: { name: string; path: string; isDirectory: boolean }) {
  if (entry.isDirectory) {
    browse(entry.path)
    // In dir mode, selecting a directory sets it as the selection
    if (props.mode === 'dir') {
      selectedPath.value = entry.path
    }
  } else {
    selectedPath.value = entry.path
  }
}

function handleEntryDblClick(entry: { name: string; path: string; isDirectory: boolean }) {
  if (!entry.isDirectory) {
    emit('select', entry.path)
  }
}

function handleGoUp() {
  if (parentPath.value) {
    browse(parentPath.value)
  } else {
    browse('')
  }
}

function handleSelect() {
  // In dir mode, use currentPath if nothing explicitly selected
  const path = selectedPath.value || (props.mode === 'dir' ? currentPath.value : '')
  if (path) {
    emit('select', path)
  }
}

function handleCancel() {
  emit('cancel')
}

function handleBackdropClick(e: MouseEvent) {
  if (e.target === dialogRef.value) {
    emit('cancel')
  }
}

function formatSize(size?: number): string {
  if (size == null) return ''
  if (size < 1024) return size + ' B'
  if (size < 1024 * 1024) return (size / 1024).toFixed(1) + ' KB'
  return (size / 1024 / 1024).toFixed(1) + ' MB'
}
</script>

<template>
  <dialog ref="dialogRef" class="file-browser-dialog" @click="handleBackdropClick">
    <div class="dialog-content">
      <header class="dialog-header">
        <h3>{{ title || 'Browse' }}</h3>
        <button class="close-btn" @click="handleCancel">&times;</button>
      </header>

      <!-- Current path + up button -->
      <div class="path-bar">
        <button
          class="up-btn"
          :disabled="!currentPath"
          @click="handleGoUp"
          title="Go up"
        >&larr;</button>
        <span class="current-path">{{ currentPath || 'Root' }}</span>
      </div>

      <!-- Loading / Error -->
      <div v-if="loading" class="browse-status">Loading...</div>
      <div v-else-if="error" class="browse-status browse-error">{{ error }}</div>

      <!-- File list -->
      <div v-else class="file-list">
        <div v-if="entries.length === 0" class="empty-dir">Empty directory</div>
        <div
          v-for="entry in entries"
          :key="entry.path"
          class="file-entry"
          :class="{
            'is-dir': entry.isDirectory,
            'is-selected': entry.path === selectedPath
          }"
          @click="handleEntryClick(entry)"
          @dblclick="handleEntryDblClick(entry)"
        >
          <span class="entry-icon">{{ entry.isDirectory ? '&#128193;' : '&#128196;' }}</span>
          <span class="entry-name">{{ entry.name }}</span>
          <span class="entry-size" v-if="!entry.isDirectory">{{ formatSize(entry.size) }}</span>
        </div>
      </div>

      <!-- Selection display -->
      <div class="selection-bar" v-if="selectedPath">
        <span class="selection-label">Selected:</span>
        <span class="selection-path">{{ selectedPath }}</span>
      </div>

      <!-- Actions -->
      <footer class="dialog-footer">
        <button class="btn btn-cancel" @click="handleCancel">Cancel</button>
        <button
          class="btn btn-select"
          :disabled="!selectedPath && !(mode === 'dir' && currentPath)"
          @click="handleSelect"
        >Select</button>
      </footer>
    </div>
  </dialog>
</template>

<style scoped>
.file-browser-dialog {
  padding: 0;
  border: var(--border);
  background: var(--color-white);
  max-width: 600px;
  width: 95%;
  margin: auto;
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-float);
}

.file-browser-dialog::backdrop {
  background: rgba(0, 0, 0, 0.5);
}

.dialog-content {
  padding: 20px;
  display: flex;
  flex-direction: column;
  max-height: 70vh;
}

.dialog-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.dialog-header h3 {
  font-size: 16px;
  font-weight: 600;
  margin: 0;
}

.close-btn {
  background: none;
  border: none;
  font-size: 20px;
  cursor: pointer;
  color: var(--color-gray-500);
  padding: 0 4px;
}

.path-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  background: var(--color-gray-50);
  border: var(--border);
  border-radius: var(--radius-md);
  margin-bottom: 8px;
}

.up-btn {
  padding: 2px 8px;
  background: var(--color-white);
  border: var(--border);
  border-radius: var(--radius-sm);
  cursor: pointer;
  font-size: 14px;
}

.up-btn:disabled {
  opacity: 0.3;
  cursor: not-allowed;
}

.current-path {
  font-size: 13px;
  color: var(--color-gray-600);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.browse-status {
  padding: 20px;
  text-align: center;
  color: var(--color-gray-500);
  font-size: 13px;
}

.browse-error {
  color: var(--color-error);
}

.file-list {
  flex: 1;
  overflow-y: auto;
  border: var(--border);
  border-radius: var(--radius-md);
  min-height: 200px;
  max-height: 350px;
}

.empty-dir {
  padding: 40px;
  text-align: center;
  color: var(--color-gray-400);
  font-size: 13px;
}

.file-entry {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  cursor: pointer;
  font-size: 13px;
  border-bottom: 1px solid var(--color-gray-100);
  transition: background 0.1s;
}

.file-entry:last-child {
  border-bottom: none;
}

.file-entry:hover {
  background: var(--color-gray-50);
}

.file-entry.is-selected {
  background: var(--color-blue-50, #e8f0fe);
}

.entry-icon {
  flex-shrink: 0;
  font-size: 16px;
}

.entry-name {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.is-dir .entry-name {
  font-weight: 500;
}

.entry-size {
  flex-shrink: 0;
  color: var(--color-gray-400);
  font-size: 12px;
}

.selection-bar {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 10px;
  margin-top: 8px;
  background: var(--color-gray-50);
  border-radius: var(--radius-md);
  font-size: 12px;
}

.selection-label {
  color: var(--color-gray-500);
  flex-shrink: 0;
}

.selection-path {
  color: var(--color-black);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.dialog-footer {
  display: flex;
  gap: 12px;
  justify-content: flex-end;
  margin-top: 12px;
}

.btn {
  padding: 8px 20px;
  border: var(--border);
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  border-radius: var(--radius-md);
  transition: all 0.15s;
}

.btn-cancel {
  background: var(--color-white);
  color: var(--color-black);
}

.btn-select {
  background: var(--color-black);
  color: var(--color-white);
  border-color: var(--color-black);
}

.btn-select:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}
</style>

<script setup lang="ts">
import { ref, computed, watch, nextTick } from 'vue'
import type { ContextType } from '@/types'

interface Props {
  type: ContextType
  show: boolean
}

const props = defineProps<Props>()

const emit = defineEmits<{
  confirm: [value: string]
  cancel: []
}>()

const inputValue = ref('')
const inputRef = ref<HTMLInputElement | null>(null)
const errorMessage = ref('')

// 检查是否在 Electron 环境中
const isElectron = computed(() => {
  return typeof window !== 'undefined' && (window as any).electron?.isElectron
})

// 标题和占位符根据类型变化
const modalTitle = computed(() => {
  const map: Record<string, string> = {
    folder: 'Add Folder Path',
    web: 'Add Web URL',
    doc: 'Add Document',
    code: 'Add Code Snippet',
    rule: 'Add Rule'
  }
  return map[props.type] || 'Add Context'
})

const placeholder = computed(() => {
  const map: Record<string, string> = {
    folder: 'e.g., workspace/src or /absolute/path',
    web: 'e.g., https://example.com/api',
    doc: 'Document ID or name',
    code: 'Paste code here',
    rule: 'Enter rule content'
  }
  return map[props.type] || 'Enter value'
})

const inputType = computed(() => {
  return props.type === 'web' ? 'url' : 'text'
})

// 监听 show 变化，打开时聚焦输入框
watch(() => props.show, (newShow) => {
  if (newShow) {
    inputValue.value = ''
    errorMessage.value = ''
    nextTick(() => {
      inputRef.value?.focus()
    })
  }
})

/**
 * 选择文件夹（仅在 Electron 环境中可用）
 */
async function handleSelectFolder() {
  if (!isElectron.value) {
    // Web 环境不支持图形化文件夹选择，只能手动输入
    return
  }

  try {
    const folderPath = await (window as any).electron.selectFolder()
    if (folderPath) {
      inputValue.value = folderPath
      errorMessage.value = ''
    }
  } catch (err) {
    errorMessage.value = 'Failed to select folder'
    console.error('Failed to select folder:', err)
  }
}

function validateInput(value: string): string | null {
  if (!value.trim()) {
    return 'Please enter a value'
  }

  // 特定类型的验证
  if (props.type === 'web') {
    try {
      const url = new URL(value.trim())
      if (url.protocol !== 'http:' && url.protocol !== 'https:') {
        return 'URL must start with http:// or https://'
      }
    } catch {
      return 'Please enter a valid URL (e.g., https://example.com)'
    }
  }

  if (props.type === 'folder') {
    // 简单的路径验证
    const trimmed = value.trim()
    if (trimmed.includes('..')) {
      return 'Path cannot contain ".."'
    }
  }

  return null
}

function handleConfirm() {
  const error = validateInput(inputValue.value)
  if (error) {
    errorMessage.value = error
    return
  }

  emit('confirm', inputValue.value.trim())
}

function handleCancel() {
  emit('cancel')
}

function handleKeydown(e: KeyboardEvent) {
  if (e.key === 'Enter') {
    e.preventDefault()
    handleConfirm()
  } else if (e.key === 'Escape') {
    e.preventDefault()
    handleCancel()
  }
}

function handleInput() {
  // 清除错误消息当用户开始输入
  if (errorMessage.value) {
    errorMessage.value = ''
  }
}
</script>

<template>
  <div v-if="show" class="modal-overlay" @click.self="handleCancel">
    <div class="modal-dialog">
      <div class="modal-header">
        <h3 class="modal-title">{{ modalTitle }}</h3>
        <button class="close-btn" @click="handleCancel" title="Close (Esc)">
          <span>&times;</span>
        </button>
      </div>

      <div class="modal-body">
        <!-- 文件夹类型：显示输入框，Electron 环境显示浏览按钮 -->
        <div v-if="type === 'folder'" class="folder-input-group">
          <input
            ref="inputRef"
            v-model="inputValue"
            type="text"
            :placeholder="placeholder"
            class="modal-input"
            :class="{ error: errorMessage }"
            @keydown="handleKeydown"
            @input="handleInput"
          />
          <!-- 只在 Electron 环境显示 Browse 按钮 -->
          <button
            v-if="isElectron"
            class="browse-btn"
            @click="handleSelectFolder"
            title="Browse folder"
          >
            📁 Browse
          </button>
        </div>

        <!-- 其他类型：只显示输入框 -->
        <input
          v-else
          ref="inputRef"
          v-model="inputValue"
          :type="inputType"
          :placeholder="placeholder"
          class="modal-input"
          :class="{ error: errorMessage }"
          @keydown="handleKeydown"
          @input="handleInput"
        />

        <div v-if="errorMessage" class="error-message">
          {{ errorMessage }}
        </div>

        <!-- Web 环境的提示 -->
        <div v-if="type === 'folder' && !isElectron" class="hint-message">
          💡 Tip: Enter a relative path like "workspace/src" or an absolute path
        </div>
      </div>

      <div class="modal-footer">
        <button class="btn btn-secondary" @click="handleCancel">
          Cancel
        </button>
        <button class="btn btn-primary" @click="handleConfirm">
          Add
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
  animation: fadeIn 0.15s ease-out;
}

@keyframes fadeIn {
  from {
    opacity: 0;
  }
  to {
    opacity: 1;
  }
}

.modal-dialog {
  background: var(--color-white);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-xl);
  width: 90%;
  max-width: 480px;
  animation: slideUp 0.2s ease-out;
}

@keyframes slideUp {
  from {
    transform: translateY(20px);
    opacity: 0;
  }
  to {
    transform: translateY(0);
    opacity: 1;
  }
}

.modal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20px 24px;
  border-bottom: var(--border);
}

.modal-title {
  font-family: var(--font-ui);
  font-size: 16px;
  font-weight: 600;
  color: var(--color-gray-900);
  margin: 0;
}

.close-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border: none;
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--color-gray-400);
  cursor: pointer;
  font-size: 20px;
  line-height: 1;
  padding: 0;
  transition: all var(--duration-fast) var(--ease-in-out);
}

.close-btn:hover {
  background: var(--color-gray-100);
  color: var(--color-gray-700);
}

.modal-body {
  padding: 24px;
}

.folder-input-group {
  display: flex;
  gap: 8px;
  align-items: stretch;
}

.folder-input-group .modal-input {
  flex: 1;
}

.modal-input {
  width: 100%;
  padding: 10px 12px;
  border: var(--border-strong);
  border-radius: var(--radius-md);
  font-family: var(--font-mono);
  font-size: 13px;
  line-height: 1.5;
  outline: none;
  transition: all var(--duration-fast) var(--ease-in-out);
}

.modal-input:focus {
  border-color: var(--color-gray-400);
  box-shadow: 0 0 0 3px rgba(0, 0, 0, 0.03);
}

.modal-input.error {
  border-color: #dc2626;
}

.browse-btn {
  padding: 10px 16px;
  border: var(--border-strong);
  border-radius: var(--radius-md);
  background: var(--color-white);
  color: var(--color-gray-700);
  font-family: var(--font-ui);
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all var(--duration-fast) var(--ease-in-out);
  white-space: nowrap;
  flex-shrink: 0;
}

.browse-btn:hover {
  background: var(--color-gray-50);
  border-color: var(--color-gray-400);
}

.browse-btn:active {
  background: var(--color-gray-100);
}

.error-message {
  margin-top: 8px;
  font-family: var(--font-mono);
  font-size: 11px;
  color: #dc2626;
}

.hint-message {
  margin-top: 8px;
  font-family: var(--font-mono);
  font-size: 11px;
  color: var(--color-gray-500);
  line-height: 1.4;
}

.modal-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding: 16px 24px;
  border-top: var(--border);
}

.btn {
  padding: 8px 16px;
  border: none;
  border-radius: var(--radius-md);
  font-family: var(--font-ui);
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all var(--duration-fast) var(--ease-in-out);
}

.btn-secondary {
  background: var(--color-white);
  border: var(--border-strong);
  color: var(--color-gray-700);
}

.btn-secondary:hover {
  background: var(--color-gray-50);
}

.btn-primary {
  background: var(--color-black);
  color: var(--color-white);
}

.btn-primary:hover {
  opacity: 0.85;
}
</style>

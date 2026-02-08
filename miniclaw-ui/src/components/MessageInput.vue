<script setup lang="ts">
import { ref, computed, onMounted, nextTick } from 'vue'
import type { SlashCommandItem, AttachedFile } from '@/types'
import { useSlashCommands } from '@/composables/useSlashCommands'
import FileChip from '@/components/FileChip.vue'

const props = defineProps<{
  disabled: boolean
  isRunning?: boolean
  attachedFiles?: AttachedFile[]
}>()

const emit = defineEmits<{
  send: [message: string, filePaths: string[]]
  cancel: []
  'attach-file': [file: File]
  'remove-file': [fileId: string]
}>()

const input = ref('')
const inputRef = ref<HTMLTextAreaElement | null>(null)
const fileInputRef = ref<HTMLInputElement | null>(null)

// Slash command autocomplete
const { loadCommands, filterCommands } = useSlashCommands()

const showSlashMenu = ref(false)
const slashItems = ref<SlashCommandItem[]>([])
const selectedIndex = ref(0)
const menuRef = ref<HTMLElement | null>(null)

// 是否有文件正在上传
const hasUploading = computed(() => props.attachedFiles?.some(f => f.uploading) ?? false)

// 发送按钮是否禁用：常规禁用 OR 有文件正在上传
const sendDisabled = computed(() => props.disabled || hasUploading.value || !input.value.trim())

onMounted(() => {
  loadCommands()
})

function scrollSelectedIntoView() {
  nextTick(() => {
    const menu = menuRef.value
    if (!menu) return
    const selected = menu.children[selectedIndex.value] as HTMLElement | undefined
    if (selected) {
      selected.scrollIntoView({ block: 'nearest' })
    }
  })
}

function selectSlashCommand(item: SlashCommandItem) {
  input.value = '/' + item.name + ' '
  showSlashMenu.value = false
  inputRef.value?.focus()
}

function handleSubmit() {
  if (sendDisabled.value) return

  // 收集已上传完成的文件路径
  const filePaths = (props.attachedFiles || [])
    .filter(f => !f.uploading && f.filePath)
    .map(f => f.filePath)

  emit('send', input.value.trim(), filePaths)
  input.value = ''

  // Reset textarea height
  if (inputRef.value) {
    inputRef.value.style.height = 'auto'
  }
}

function handleAttachClick() {
  fileInputRef.value?.click()
}

function handleFileChange(e: Event) {
  const target = e.target as HTMLInputElement
  const file = target.files?.[0]
  if (file) {
    emit('attach-file', file)
  }
  // Reset so same file can be selected again
  target.value = ''
}

function handleCancel() {
  emit('cancel')
}

function handleKeydown(e: KeyboardEvent) {
  // Slash menu keyboard navigation
  if (showSlashMenu.value) {
    if (e.key === 'ArrowDown') {
      e.preventDefault()
      selectedIndex.value = (selectedIndex.value + 1) % slashItems.value.length
      scrollSelectedIntoView()
      return
    }
    if (e.key === 'ArrowUp') {
      e.preventDefault()
      selectedIndex.value = (selectedIndex.value - 1 + slashItems.value.length) % slashItems.value.length
      scrollSelectedIntoView()
      return
    }
    if (e.key === 'Tab' || e.key === 'Enter') {
      e.preventDefault()
      const item = slashItems.value[selectedIndex.value]
      if (item) selectSlashCommand(item)
      return
    }
    if (e.key === 'Escape') {
      e.preventDefault()
      showSlashMenu.value = false
      return
    }
  }

  // Enter to submit (Shift+Enter for new line)
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    handleSubmit()
  }
  // Escape to cancel
  if (e.key === 'Escape' && props.isRunning) {
    handleCancel()
  }
}

function handleInput(e: Event) {
  const target = e.target as HTMLTextAreaElement
  // Auto-resize
  target.style.height = 'auto'
  target.style.height = Math.min(target.scrollHeight, 200) + 'px'

  // Slash command detection
  const val = target.value
  if (val.startsWith('/')) {
    const query = val.substring(1).split(/\s/)[0] ?? ''
    if (!val.includes(' ')) {
      slashItems.value = filterCommands(query)
      showSlashMenu.value = slashItems.value.length > 0
      selectedIndex.value = 0
      return
    }
  }
  showSlashMenu.value = false
}
</script>

<template>
  <div class="input-area">
    <div class="input-container">
      <!-- Slash command dropdown (positioned above input) -->
      <div v-if="showSlashMenu" ref="menuRef" class="slash-menu">
        <div
          v-for="(item, i) in slashItems"
          :key="item.name"
          class="slash-item"
          :class="{ selected: i === selectedIndex }"
          @mousedown.prevent="selectSlashCommand(item)"
          @mouseenter="selectedIndex = i"
        >
          <span class="slash-item-name">{{ item.displayName }}</span>
          <span class="slash-item-type">{{ item.type }}</span>
          <span class="slash-item-desc">{{ item.description }}</span>
        </div>
      </div>

      <!-- File attachment chips -->
      <div v-if="attachedFiles && attachedFiles.length > 0" class="attached-files">
        <FileChip
          v-for="file in attachedFiles"
          :key="file.id"
          :file="file"
          @remove="emit('remove-file', $event)"
        />
      </div>

      <div class="input-wrap">
        <!-- Hidden file input -->
        <input
          ref="fileInputRef"
          type="file"
          accept=".pdf,.docx,.txt,.md,.xlsx,.pptx,.csv,.json,.yaml,.yml,.xml,.html"
          style="display: none"
          @change="handleFileChange"
        />

        <!-- Attach button -->
        <button
          class="attach-btn"
          :disabled="disabled"
          @click="handleAttachClick"
          title="Attach file"
        >
          <span class="paperclip">+</span>
        </button>

        <textarea
          ref="inputRef"
          v-model="input"
          :disabled="disabled"
          placeholder="Type your message..."
          rows="1"
          @keydown="handleKeydown"
          @input="handleInput"
        ></textarea>

        <!-- Cancel button when running -->
        <button
          v-if="isRunning"
          class="cancel-btn"
          @click="handleCancel"
          title="Cancel (Esc)"
        >
          <span class="icon">x</span>
        </button>

        <!-- Send button -->
        <button
          v-else
          class="send-btn"
          :disabled="sendDisabled"
          @click="handleSubmit"
          :title="hasUploading ? 'Waiting for upload...' : 'Send'"
        >
          <span class="arrow">↑</span>
        </button>
      </div>
    </div>
    <div class="input-hint">
      <template v-if="hasUploading">
        <span class="uploading-hint">Uploading file...</span>
      </template>
      <template v-else-if="isRunning">
        <span class="running">Running...</span>
        <span class="separator">·</span>
        <span>Esc to cancel</span>
      </template>
      <template v-else>
        <span>Enter to send</span>
        <span class="separator">·</span>
        <span>Shift+Enter for new line</span>
      </template>
    </div>
  </div>
</template>

<style scoped>
.input-area {
  padding: 24px 48px 32px;
  border-top: var(--border);
  background: var(--color-white);
}

.input-container {
  max-width: 720px;
  margin: 0 auto;
  position: relative;
}

.input-wrap {
  display: flex;
  gap: 8px;
  align-items: flex-end;
  border: var(--border-strong);
  border-radius: var(--radius-lg);
  padding: 8px 8px 8px 16px;
  background: var(--color-white);
  box-shadow: var(--shadow-xs);
  transition: border-color var(--duration-fast) var(--ease-in-out), box-shadow var(--duration-fast) var(--ease-in-out);
}

.input-wrap:focus-within {
  border-color: var(--color-gray-400);
  box-shadow: 0 0 0 3px rgba(0,0,0,0.03);
}

textarea {
  flex: 1;
  padding: 6px 0;
  border: none;
  background: transparent;
  font-family: var(--font-ui);
  font-size: 14px;
  line-height: 1.5;
  resize: none;
  outline: none;
}

textarea::placeholder {
  color: var(--color-gray-400);
}

textarea:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.send-btn {
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  border-radius: var(--radius-md);
  background: var(--color-black);
  color: var(--color-white);
  cursor: pointer;
  transition: all var(--duration-fast) var(--ease-in-out);
  flex-shrink: 0;
}

.send-btn:hover:not(:disabled) {
  opacity: 0.85;
}

.send-btn:disabled {
  background: var(--color-gray-200);
  cursor: not-allowed;
}

.cancel-btn {
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: var(--border-strong);
  border-radius: var(--radius-md);
  background: var(--color-white);
  color: var(--color-black);
  cursor: pointer;
  transition: all var(--duration-fast) var(--ease-in-out);
  flex-shrink: 0;
}

.cancel-btn:hover {
  background: var(--color-gray-100);
}

.cancel-btn .icon {
  font-family: var(--font-mono);
  font-size: 14px;
  font-weight: 600;
}

.arrow {
  font-family: var(--font-mono);
  font-size: 16px;
  font-weight: 500;
}

.attached-files {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 8px;
}

.attach-btn {
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  border-radius: var(--radius-md);
  background: transparent;
  color: var(--color-gray-400);
  cursor: pointer;
  transition: all var(--duration-fast) var(--ease-in-out);
  flex-shrink: 0;
}

.attach-btn:hover:not(:disabled) {
  background: var(--color-gray-100);
  color: var(--color-gray-600);
}

.attach-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.paperclip {
  font-family: var(--font-mono);
  font-size: 18px;
  font-weight: 300;
}

.input-hint {
  max-width: 720px;
  margin: 8px auto 0;
  font-family: var(--font-mono);
  font-size: 11px;
  color: var(--color-gray-400);
  display: flex;
  gap: 8px;
  justify-content: center;
}

.separator {
  opacity: 0.5;
}

.running {
  animation: pulse 1.5s ease-in-out infinite;
}

.uploading-hint {
  animation: pulse 1.5s ease-in-out infinite;
}

@keyframes pulse {
  0%, 100% {
    opacity: 1;
  }
  50% {
    opacity: 0.4;
  }
}

/* Slash command autocomplete menu */
.slash-menu {
  position: absolute;
  bottom: calc(100% + 4px);
  left: 0;
  right: 0;
  max-height: 240px;
  overflow-y: auto;
  background: var(--color-white);
  border: var(--border-strong);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-lg);
  z-index: 100;
  padding: 4px;
}

.slash-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  cursor: pointer;
  font-family: var(--font-mono);
  font-size: 12px;
  border-radius: var(--radius-md);
}

.slash-item.selected {
  background: var(--color-gray-50);
}

.slash-item-name {
  font-weight: 600;
  min-width: 120px;
}

.slash-item-type {
  font-size: 10px;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  padding: 1px 5px;
  background: var(--color-gray-100);
  border-radius: var(--radius-sm);
  color: var(--color-gray-500);
}

.slash-item-desc {
  color: var(--color-gray-500);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>

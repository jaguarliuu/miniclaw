<script setup lang="ts">
import { ref, computed, onMounted, nextTick } from 'vue'
import type { SlashCommandItem, AttachedContext, ContextType, DataSourceInfo } from '@/types'
import type { McpServer } from '@/composables/useMcpServers'
import { useSlashCommands } from '@/composables/useSlashCommands'
import ContextChip from '@/components/ContextChip.vue'
import ContextTypeMenu from '@/components/ContextTypeMenu.vue'
import McpServerFilter from '@/components/McpServerFilter.vue'
import DataSourceSelector from '@/components/DataSourceSelector.vue'

const props = defineProps<{
  disabled: boolean
  isRunning?: boolean
  attachedContexts?: AttachedContext[]
  mcpServers?: McpServer[]
  excludedMcpServers?: Set<string>
  dataSources?: DataSourceInfo[]
  selectedDataSourceId?: string
}>()

const emit = defineEmits<{
  send: [message: string, contexts: AttachedContext[]]
  cancel: []
  'add-context': [type: ContextType]
  'attach-file': [file: File]
  'remove-context': [contextId: string]
  'toggle-mcp-server': [serverName: string]
  'select-datasource': [dataSourceId: string | undefined]
}>()

const input = ref('')
const inputRef = ref<HTMLTextAreaElement | null>(null)
const fileInputRef = ref<HTMLInputElement | null>(null)
const isExpanded = ref(false)

// Slash command autocomplete
const { loadCommands, filterCommands } = useSlashCommands()

const showSlashMenu = ref(false)
const slashItems = ref<SlashCommandItem[]>([])
const selectedIndex = ref(0)
const menuRef = ref<HTMLElement | null>(null)

// Context type menu
const showContextMenu = ref(false)
const contextMenuRef = ref<InstanceType<typeof ContextTypeMenu> | null>(null)

// MCP server filter
const showMcpFilter = ref(false)

// Data source selector
const showDataSourceSelector = ref(false)

// MCP 状态标签
const mcpStatusLabel = computed(() => {
  const servers = props.mcpServers ?? []
  if (servers.length === 0) return null
  const excluded = props.excludedMcpServers?.size ?? 0
  const active = servers.length - excluded
  return `MCP: ${active}/${servers.length}`
})

// 数据源状态标签
const dataSourceLabel = computed(() => {
  const sources = props.dataSources ?? []
  const activeSources = sources.filter(s => s.status === 'ACTIVE')
  if (activeSources.length === 0) return null

  const selectedSource = activeSources.find(s => s.id === props.selectedDataSourceId)
  if (selectedSource) {
    return selectedSource.name
  }
  return '数据源'
})

// 获取选中的数据源对象
const selectedDataSource = computed(() => {
  if (!props.selectedDataSourceId) return null
  return props.dataSources?.find(ds => ds.id === props.selectedDataSourceId)
})

// 是否显示 MCP 按钮
const showMcpButton = computed(() => {
  const servers = props.mcpServers ?? []
  return servers.length > 0
})

// 是否显示数据源按钮
const showDataSourceButton = computed(() => {
  const sources = props.dataSources ?? []
  return sources.filter(s => s.status === 'ACTIVE').length > 0
})

// 是否有上下文正在上传
const hasUploading = computed(() => props.attachedContexts?.some(c => c.uploading) ?? false)

// 发送按钮是否禁用：常规禁用 OR 有上下文正在上传
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

  // 收集所有上下文
  const contexts = props.attachedContexts || []

  emit('send', input.value.trim(), contexts)
  input.value = ''

  // Reset textarea height
  if (inputRef.value) {
    inputRef.value.style.height = 'auto'
  }
}

function handleAttachClick() {
  showContextMenu.value = !showContextMenu.value
}

function handleContextTypeSelect(type: ContextType) {
  showContextMenu.value = false

  if (type === 'file') {
    // 文件类型触发文件选择器
    fileInputRef.value?.click()
  } else {
    // 其他类型通知父组件
    emit('add-context', type)
  }
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
  // Context type menu keyboard navigation
  if (showContextMenu.value) {
    if (e.key === 'Escape') {
      e.preventDefault()
      showContextMenu.value = false
      return
    }
    // 将键盘事件转发给 ContextTypeMenu
    contextMenuRef.value?.handleKeydown(e)
    return
  }

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

// 点击外部关闭上下文菜单、MCP 过滤器和数据源选择器
function handleClickOutside(e: MouseEvent) {
  const target = e.target as HTMLElement

  // 关闭上下文菜单
  if (showContextMenu.value) {
    const menuEl = contextMenuRef.value?.$el
    const attachBtn = target.closest('.toolbar-btn')
    if (menuEl && !menuEl.contains(target) && !attachBtn) {
      showContextMenu.value = false
    }
  }

  // 关闭 MCP 过滤器
  if (showMcpFilter.value) {
    const filterEl = document.querySelector('.mcp-filter')
    const toolbarBtn = target.closest('.toolbar-btn')
    if (filterEl && !filterEl.contains(target) && !toolbarBtn) {
      showMcpFilter.value = false
    }
  }

  // 关闭数据源选择器
  if (showDataSourceSelector.value) {
    const selectorEl = document.querySelector('.datasource-selector')
    const toolbarBtn = target.closest('.toolbar-btn')
    if (selectorEl && !selectorEl.contains(target) && !toolbarBtn) {
      showDataSourceSelector.value = false
    }
  }
}

function handleRemoveDataSource() {
  emit('select-datasource', undefined)
}

const isHovered = ref(false)
const isFocused = ref(false)

function handleFocus() {
  isFocused.value = true
  isExpanded.value = true
}

function handleMouseEnter() {
  isHovered.value = true
}

function handleMouseLeave() {
  isHovered.value = false
  // 如果没有焦点且没有内容，延迟收起
  setTimeout(() => {
    if (!isHovered.value && !isFocused.value && !input.value && !props.attachedContexts?.length && !props.selectedDataSourceId) {
      isExpanded.value = false
    }
  }, 100)
}

function handleBlur() {
  isFocused.value = false
  // 延迟收起，避免点击按钮时立即收起
  setTimeout(() => {
    if (!isHovered.value && !input.value && !props.attachedContexts?.length && !props.selectedDataSourceId) {
      isExpanded.value = false
    }
  }, 200)
}

onMounted(() => {
  document.addEventListener('mousedown', handleClickOutside)
})
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

      <!-- Context type menu -->
      <ContextTypeMenu
        v-if="showContextMenu"
        ref="contextMenuRef"
        @select="handleContextTypeSelect"
      />

      <!-- MCP server filter -->
      <McpServerFilter
        v-if="showMcpFilter"
        :servers="mcpServers ?? []"
        :excluded-servers="excludedMcpServers ?? new Set()"
        @toggle="emit('toggle-mcp-server', $event)"
      />

      <!-- Data source selector -->
      <DataSourceSelector
        v-if="showDataSourceSelector"
        :data-sources="dataSources ?? []"
        :selected-data-source-id="selectedDataSourceId"
        @select="emit('select-datasource', $event)"
      />

      <!-- Context attachment chips -->
      <div v-if="attachedContexts && attachedContexts.length > 0" class="attached-contexts">
        <ContextChip
          v-for="context in attachedContexts"
          :key="context.id"
          :context="context"
          @remove="emit('remove-context', $event)"
        />
      </div>

      <div
        class="input-wrap"
        :class="{ expanded: isExpanded || input.length > 0 || (attachedContexts && attachedContexts.length > 0) || selectedDataSource }"
        @mouseenter="handleMouseEnter"
        @mouseleave="handleMouseLeave"
      >
        <!-- Hidden file input -->
        <input
          ref="fileInputRef"
          type="file"
          accept=".pdf,.docx,.txt,.md,.xlsx,.pptx,.csv,.json,.yaml,.yml,.xml,.html"
          style="display: none"
          @change="handleFileChange"
        />

        <!-- Main content area -->
        <div class="input-main">
          <!-- Chips container -->
          <div v-if="selectedDataSource || (attachedContexts && attachedContexts.length > 0)" class="chips-container">
            <!-- Selected datasource chip -->
            <div v-if="selectedDataSource" class="chip datasource-chip">
              <svg width="12" height="12" viewBox="0 0 12 12" fill="none" class="chip-icon">
                <path d="M2 3C2 2.44772 2.44772 2 3 2H9C9.55228 2 10 2.44772 10 3V4C10 4.55228 9.55228 5 9 5H3C2.44772 5 2 4.55228 2 4V3Z" fill="currentColor"/>
                <path d="M2 8C2 7.44772 2.44772 7 3 7H9C9.55228 7 10 7.44772 10 8V9C10 9.55228 9.55228 10 9 10H3C2.44772 10 2 9.55228 2 9V8Z" fill="currentColor"/>
              </svg>
              <span class="chip-label">{{ selectedDataSource.name }}</span>
              <button class="chip-remove" @click="handleRemoveDataSource" title="移除">
                <svg width="12" height="12" viewBox="0 0 12 12" fill="none">
                  <path d="M3 3L9 9M9 3L3 9" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
                </svg>
              </button>
            </div>

            <!-- Context chips -->
            <ContextChip
              v-for="context in attachedContexts"
              :key="context.id"
              :context="context"
              @remove="emit('remove-context', $event)"
            />
          </div>

          <!-- Textarea -->
          <textarea
            ref="inputRef"
            v-model="input"
            :disabled="disabled"
            :placeholder="selectedDataSource ? '询问关于 ' + selectedDataSource.name + ' 的问题...' : '输入消息...'"
            @keydown="handleKeydown"
            @input="handleInput"
            @focus="handleFocus"
            @blur="handleBlur"
          ></textarea>

          <!-- Bottom toolbar -->
          <div class="input-toolbar">
            <div class="toolbar-left">
              <button
                class="toolbar-btn"
                :class="{ active: showContextMenu }"
                :disabled="disabled"
                @click="handleAttachClick"
                title="添加上下文"
              >
                <svg width="16" height="16" viewBox="0 0 16 16" fill="none" xmlns="http://www.w3.org/2000/svg">
                  <path d="M8 3V13M3 8H13" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
                </svg>
              </button>

              <button
                v-if="showMcpButton"
                class="toolbar-btn"
                :class="{ active: showMcpFilter, highlight: (excludedMcpServers?.size ?? 0) > 0 }"
                :disabled="disabled"
                @click="showMcpFilter = !showMcpFilter"
                title="MCP 工具"
              >
                <svg width="16" height="16" viewBox="0 0 16 16" fill="none" xmlns="http://www.w3.org/2000/svg">
                  <path d="M11 5L8 2L5 5M5 11L8 14L11 11M14 8H2" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>
                </svg>
                <span class="toolbar-label">工具</span>
              </button>

              <button
                v-if="showDataSourceButton"
                class="toolbar-btn"
                :class="{ active: showDataSourceSelector, highlight: selectedDataSourceId }"
                :disabled="disabled"
                @click="showDataSourceSelector = !showDataSourceSelector"
                title="数据源"
              >
                <svg width="16" height="16" viewBox="0 0 16 16" fill="none" xmlns="http://www.w3.org/2000/svg">
                  <path d="M2 4C2 3.44772 3.34315 3 5 3H11C12.6569 3 14 3.44772 14 4V5.5C14 6.05228 12.6569 6.5 11 6.5H5C3.34315 6.5 2 6.05228 2 5.5V4Z" stroke="currentColor" stroke-width="1.2"/>
                  <path d="M2 10.5C2 9.94772 3.34315 9.5 5 9.5H11C12.6569 9.5 14 9.94772 14 10.5V12C14 12.5523 12.6569 13 11 13H5C3.34315 13 2 12.5523 2 12V10.5Z" stroke="currentColor" stroke-width="1.2"/>
                </svg>
                <span class="toolbar-label">{{ dataSourceLabel }}</span>
              </button>
            </div>

            <div class="toolbar-right">
              <!-- Send or Cancel button -->
              <button
                v-if="isRunning"
                class="action-btn cancel-btn"
                @click="handleCancel"
                title="停止 (Esc)"
              >
                <svg width="16" height="16" viewBox="0 0 16 16" fill="none">
                  <rect x="5" y="5" width="6" height="6" rx="0.5" fill="currentColor"/>
                </svg>
              </button>

              <button
                v-else
                class="action-btn send-btn"
                :disabled="sendDisabled"
                @click="handleSubmit"
                :title="hasUploading ? '等待上传...' : '发送 (Enter)'"
              >
                <svg width="16" height="16" viewBox="0 0 16 16" fill="none">
                  <path d="M8 13V3M8 3L5 6M8 3L11 6" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/>
                </svg>
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
    <div class="input-hint">
      <template v-if="hasUploading">
        <span class="uploading-hint">上传中...</span>
      </template>
      <template v-else-if="isRunning">
        <span class="running">运行中...</span>
        <span class="separator">·</span>
        <span>Esc 取消</span>
      </template>
      <template v-else>
        <span>Enter 发送</span>
        <span class="separator">·</span>
        <span>Shift+Enter 换行</span>
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
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 0;
  border: 1.5px solid var(--color-gray-200);
  border-radius: 16px;
  background: var(--color-white);
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.04);
  transition: all 0.25s cubic-bezier(0.4, 0, 0.2, 1);
  overflow: hidden;
}

.input-wrap:hover {
  border-color: var(--color-gray-300);
  box-shadow:
    0 1px 2px rgba(0, 0, 0, 0.04),
    0 4px 12px rgba(0, 0, 0, 0.04);
}

.input-wrap.expanded,
.input-wrap:focus-within {
  border-color: var(--color-gray-400);
  box-shadow:
    0 1px 2px rgba(0, 0, 0, 0.04),
    0 8px 24px rgba(0, 0, 0, 0.06),
    0 0 0 3px rgba(0, 0, 0, 0.03);
}

/* Main input area */
.input-main {
  display: flex;
  flex-direction: column;
  flex: 1;
  padding: 14px 16px;
  gap: 8px;
  min-height: 48px;
  transition: min-height 0.25s cubic-bezier(0.4, 0, 0.2, 1);
}

.input-wrap.expanded .input-main,
.input-wrap:focus-within .input-main {
  min-height: 100px;
}

/* Chips container */
.chips-container {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  animation: slideDown 0.25s cubic-bezier(0.4, 0, 0.2, 1);
  padding-bottom: 4px;
}

@keyframes slideDown {
  from {
    opacity: 0;
    transform: translateY(-8px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  background: linear-gradient(135deg, var(--color-gray-100) 0%, var(--color-gray-50) 100%);
  border: 1px solid var(--color-gray-200);
  border-radius: 10px;
  font-family: var(--font-ui);
  font-size: 13px;
  color: var(--color-gray-800);
  transition: all 0.15s ease;
  animation: chipIn 0.2s cubic-bezier(0.34, 1.56, 0.64, 1);
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.05);
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

.chip:hover {
  background: linear-gradient(135deg, var(--color-gray-200) 0%, var(--color-gray-100) 100%);
  border-color: var(--color-gray-300);
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.08);
  transform: translateY(-1px);
}

.chip-icon {
  color: var(--color-gray-600);
  flex-shrink: 0;
}

.chip-label {
  font-weight: 500;
  line-height: 1.2;
}

.chip-remove {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  padding: 0;
  border: none;
  border-radius: 6px;
  background: transparent;
  color: var(--color-gray-500);
  cursor: pointer;
  transition: all 0.12s ease;
}

.chip-remove:hover {
  background: var(--color-gray-300);
  color: var(--color-gray-800);
  transform: scale(1.15);
}

/* Textarea */
textarea {
  flex: 1;
  width: 100%;
  min-height: 28px;
  padding: 0;
  border: none;
  background: transparent;
  font-family: var(--font-ui);
  font-size: 15px;
  line-height: 1.6;
  color: var(--color-black);
  resize: none;
  outline: none;
  font-weight: 400;
}

textarea::placeholder {
  color: var(--color-gray-400);
  font-weight: 400;
}

textarea:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

/* Bottom toolbar */
.input-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px;
  border-top: 1px solid var(--color-gray-100);
  background: var(--color-gray-50);
  margin: 0;
  transition: all 0.25s cubic-bezier(0.4, 0, 0.2, 1);
}

.toolbar-left {
  display: flex;
  align-items: center;
  gap: 6px;
}

.toolbar-right {
  display: flex;
  align-items: center;
}

.toolbar-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  height: 32px;
  padding: 0 12px;
  border: none;
  border-radius: 8px;
  background: var(--color-white);
  color: var(--color-gray-600);
  font-family: var(--font-ui);
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.15s ease;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.05);
}

.toolbar-btn:hover:not(:disabled) {
  background: var(--color-white);
  color: var(--color-gray-800);
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
  transform: translateY(-1px);
}

.toolbar-btn.active {
  background: var(--color-gray-800);
  color: var(--color-white);
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.15);
}

.toolbar-btn.highlight {
  background: var(--color-gray-800);
  color: var(--color-white);
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.15);
}

.toolbar-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
  transform: none;
}

.toolbar-btn svg {
  flex-shrink: 0;
}

.toolbar-label {
  white-space: nowrap;
}

/* Action buttons (Send/Cancel) */
.action-btn {
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  border-radius: 10px;
  cursor: pointer;
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
  flex-shrink: 0;
}

.send-btn {
  background: linear-gradient(135deg, var(--color-black) 0%, var(--color-gray-700) 100%);
  color: var(--color-white);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.2);
}

.send-btn:hover:not(:disabled) {
  background: linear-gradient(135deg, var(--color-gray-700) 0%, var(--color-gray-600) 100%);
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.25);
}

.send-btn:active:not(:disabled) {
  transform: translateY(0);
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.2);
}

.send-btn:disabled {
  background: var(--color-gray-200);
  color: var(--color-gray-400);
  cursor: not-allowed;
  box-shadow: none;
  transform: none;
}

.cancel-btn {
  background: var(--color-white);
  color: var(--color-gray-600);
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
}

.cancel-btn:hover {
  background: var(--color-gray-100);
  color: var(--color-gray-700);
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.12);
  transform: translateY(-1px);
}

.attached-contexts {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 12px;
  animation: slideDown 0.25s cubic-bezier(0.4, 0, 0.2, 1);
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

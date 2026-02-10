<script setup lang="ts">
import { ref, computed, nextTick } from 'vue'
import type { ContextType } from '@/types'

interface ContextTypeOption {
  type: ContextType
  icon: string
  label: string
  description: string
  available: boolean
}

const emit = defineEmits<{
  select: [type: ContextType]
}>()

const menuRef = ref<HTMLElement | null>(null)
const selectedIndex = ref(0)

// 上下文类型选项配置
const contextTypes: ContextTypeOption[] = [
  {
    type: 'file',
    icon: '📄',
    label: 'File',
    description: 'Upload a local file',
    available: true
  },
  {
    type: 'folder',
    icon: '📁',
    label: 'Folder',
    description: 'Reference a folder path',
    available: true
  },
  {
    type: 'web',
    icon: '🌐',
    label: 'Web',
    description: 'Fetch content from a URL',
    available: true
  },
  {
    type: 'doc',
    icon: '📝',
    label: 'Doc',
    description: 'Link to internal documentation',
    available: false
  },
  {
    type: 'code',
    icon: '💻',
    label: 'Code',
    description: 'Paste a code snippet',
    available: false
  },
  {
    type: 'rule',
    icon: '📋',
    label: 'Rule',
    description: 'Define custom rules',
    available: false
  }
]

// 只显示可用的类型
const availableTypes = computed(() => contextTypes.filter(t => t.available))

function selectType(type: ContextType) {
  emit('select', type)
}

function handleKeydown(e: KeyboardEvent) {
  if (e.key === 'ArrowDown') {
    e.preventDefault()
    selectedIndex.value = (selectedIndex.value + 1) % availableTypes.value.length
    scrollSelectedIntoView()
  } else if (e.key === 'ArrowUp') {
    e.preventDefault()
    selectedIndex.value = (selectedIndex.value - 1 + availableTypes.value.length) % availableTypes.value.length
    scrollSelectedIntoView()
  } else if (e.key === 'Enter' || e.key === ' ') {
    e.preventDefault()
    const selected = availableTypes.value[selectedIndex.value]
    if (selected) {
      selectType(selected.type)
    }
  }
}

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

// 暴露给父组件，允许父组件处理键盘事件
defineExpose({
  handleKeydown
})
</script>

<template>
  <div ref="menuRef" class="context-type-menu" @keydown="handleKeydown">
    <div
      v-for="(item, i) in availableTypes"
      :key="item.type"
      class="context-type-item"
      :class="{ selected: i === selectedIndex }"
      @mousedown.prevent="selectType(item.type)"
      @mouseenter="selectedIndex = i"
    >
      <span class="type-icon">{{ item.icon }}</span>
      <div class="type-info">
        <span class="type-label">{{ item.label }}</span>
        <span class="type-desc">{{ item.description }}</span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.context-type-menu {
  position: absolute;
  bottom: calc(100% + 4px);
  left: 0;
  min-width: 280px;
  background: var(--color-white);
  border: var(--border-strong);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-lg);
  z-index: 100;
  padding: 4px;
  max-height: 320px;
  overflow-y: auto;
}

.context-type-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  cursor: pointer;
  border-radius: var(--radius-md);
  transition: all var(--duration-fast) var(--ease-in-out);
}

.context-type-item.selected {
  background: var(--color-gray-50);
}

.context-type-item:hover {
  background: var(--color-gray-50);
}

.type-icon {
  font-size: 20px;
  line-height: 1;
  flex-shrink: 0;
}

.type-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
  flex: 1;
  min-width: 0;
}

.type-label {
  font-family: var(--font-ui);
  font-size: 13px;
  font-weight: 500;
  color: var(--color-gray-900);
  line-height: 1.3;
}

.type-desc {
  font-family: var(--font-mono);
  font-size: 11px;
  color: var(--color-gray-500);
  line-height: 1.3;
}
</style>

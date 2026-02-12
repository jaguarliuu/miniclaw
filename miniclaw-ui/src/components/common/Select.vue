<script setup lang="ts" generic="T extends string | number">
import { ref, computed, onMounted, onUnmounted } from 'vue'

export interface SelectOption<T = string | number> {
  label: string
  value: T
  disabled?: boolean
}

const props = defineProps<{
  modelValue: T
  options: SelectOption<T>[]
  placeholder?: string
  disabled?: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [value: T]
}>()

const isOpen = ref(false)
const highlightedIndex = ref(-1)
const selectRef = ref<HTMLDivElement>()

const selectedOption = computed(() => {
  return props.options.find(opt => opt.value === props.modelValue)
})

const displayText = computed(() => {
  return selectedOption.value?.label || props.placeholder || 'Select...'
})

function toggleDropdown() {
  if (props.disabled) return
  isOpen.value = !isOpen.value
  if (isOpen.value) {
    highlightedIndex.value = props.options.findIndex(opt => opt.value === props.modelValue)
  }
}

function selectOption(option: SelectOption<T>) {
  if (option.disabled) return
  emit('update:modelValue', option.value)
  isOpen.value = false
}

function handleKeyDown(e: KeyboardEvent) {
  if (!isOpen.value) {
    if (e.key === 'Enter' || e.key === ' ' || e.key === 'ArrowDown') {
      e.preventDefault()
      isOpen.value = true
      highlightedIndex.value = props.options.findIndex(opt => opt.value === props.modelValue)
    }
    return
  }

  switch (e.key) {
    case 'Escape':
      e.preventDefault()
      isOpen.value = false
      break

    case 'ArrowDown':
      e.preventDefault()
      highlightedIndex.value = Math.min(highlightedIndex.value + 1, props.options.length - 1)
      break

    case 'ArrowUp':
      e.preventDefault()
      highlightedIndex.value = Math.max(highlightedIndex.value - 1, 0)
      break

    case 'Enter':
    case ' ':
      e.preventDefault()
      if (highlightedIndex.value >= 0 && highlightedIndex.value < props.options.length) {
        const option = props.options[highlightedIndex.value]
        if (option) {
          selectOption(option)
        }
      }
      break
  }
}

function handleClickOutside(e: MouseEvent) {
  if (selectRef.value && !selectRef.value.contains(e.target as Node)) {
    isOpen.value = false
  }
}

onMounted(() => {
  document.addEventListener('click', handleClickOutside)
})

onUnmounted(() => {
  document.removeEventListener('click', handleClickOutside)
})
</script>

<template>
  <div
    ref="selectRef"
    class="select-wrapper"
    :class="{ 'is-open': isOpen, 'is-disabled': disabled }"
    @keydown="handleKeyDown"
  >
    <button
      type="button"
      class="select-trigger"
      :disabled="disabled"
      @click="toggleDropdown"
      :aria-expanded="isOpen"
      :aria-haspopup="true"
      tabindex="0"
    >
      <span class="select-value">{{ displayText }}</span>
      <svg
        class="select-arrow"
        width="12"
        height="8"
        viewBox="0 0 12 8"
        fill="none"
        xmlns="http://www.w3.org/2000/svg"
      >
        <path d="M1 1.5L6 6.5L11 1.5" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>
      </svg>
    </button>

    <Transition name="dropdown">
      <div v-if="isOpen" class="select-dropdown">
        <div class="select-options">
          <div
            v-for="(option, index) in options"
            :key="option.value"
            class="select-option"
            :class="{
              'is-selected': option.value === modelValue,
              'is-highlighted': index === highlightedIndex,
              'is-disabled': option.disabled
            }"
            @click="selectOption(option)"
            @mouseenter="highlightedIndex = index"
            role="option"
            :aria-selected="option.value === modelValue"
          >
            <span class="option-label">{{ option.label }}</span>
            <svg
              v-if="option.value === modelValue"
              class="option-check"
              width="16"
              height="16"
              viewBox="0 0 16 16"
              fill="none"
            >
              <path d="M13.3333 4L6 11.3333L2.66666 8" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
            </svg>
          </div>
        </div>
      </div>
    </Transition>
  </div>
</template>

<style scoped>
.select-wrapper {
  position: relative;
  width: 100%;
}

.select-trigger {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 10px 12px;
  border: var(--border);
  border-radius: var(--radius-md);
  background: var(--color-white);
  font-size: 14px;
  font-family: inherit;
  cursor: pointer;
  transition: all var(--duration-fast);
  text-align: left;
}

.select-trigger:hover:not(:disabled) {
  border-color: var(--color-gray-400);
}

.select-trigger:focus {
  outline: none;
  border-color: var(--color-black);
  box-shadow: 0 0 0 3px rgba(0, 0, 0, 0.05);
}

.select-trigger:disabled {
  opacity: 0.5;
  cursor: not-allowed;
  background: var(--color-gray-50);
}

.select-value {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.select-arrow {
  flex-shrink: 0;
  color: var(--color-gray-600);
  transition: transform var(--duration-fast);
}

.is-open .select-arrow {
  transform: rotate(180deg);
}

.select-dropdown {
  position: absolute;
  top: calc(100% + 4px);
  left: 0;
  right: 0;
  z-index: 1000;
  background: var(--color-white);
  border: var(--border);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-lg);
  overflow: hidden;
}

.select-options {
  max-height: 240px;
  overflow-y: auto;
  padding: 4px;
}

.select-option {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 8px 10px;
  border-radius: var(--radius-sm);
  cursor: pointer;
  transition: background var(--duration-fast);
  font-size: 14px;
}

.select-option:hover:not(.is-disabled) {
  background: var(--color-gray-50);
}

.select-option.is-highlighted:not(.is-disabled) {
  background: var(--color-gray-100);
}

.select-option.is-selected {
  color: var(--color-black);
  font-weight: 500;
}

.select-option.is-disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.option-label {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.option-check {
  flex-shrink: 0;
  color: var(--color-black);
}

/* Dropdown transition */
.dropdown-enter-active,
.dropdown-leave-active {
  transition: all 0.15s ease;
}

.dropdown-enter-from {
  opacity: 0;
  transform: translateY(-4px);
}

.dropdown-leave-to {
  opacity: 0;
  transform: translateY(-4px);
}

/* Scrollbar styling */
.select-options::-webkit-scrollbar {
  width: 6px;
}

.select-options::-webkit-scrollbar-track {
  background: transparent;
}

.select-options::-webkit-scrollbar-thumb {
  background: var(--color-gray-300);
  border-radius: 3px;
}

.select-options::-webkit-scrollbar-thumb:hover {
  background: var(--color-gray-400);
}
</style>

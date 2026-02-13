<script setup lang="ts">
import { computed } from 'vue'
import type { ModelOption } from '@/types'

const props = defineProps<{
  availableModels: ModelOption[]
  selectedModel: string | null
  defaultModel: string
}>()

const emit = defineEmits<{
  select: [providerId: string, modelName: string]
  'open-settings': []
}>()

// Group models by provider
const groupedModels = computed(() => {
  const groups: { providerName: string; providerId: string; models: ModelOption[] }[] = []
  const seen = new Map<string, ModelOption[]>()

  for (const m of props.availableModels) {
    if (!seen.has(m.providerId)) {
      const models: ModelOption[] = []
      seen.set(m.providerId, models)
      groups.push({ providerName: m.providerName, providerId: m.providerId, models })
    }
    seen.get(m.providerId)!.push(m)
  }
  return groups
})

function isSelected(model: ModelOption): boolean {
  const key = `${model.providerId}:${model.modelName}`
  // Use selectedModel if set, otherwise compare with defaultModel
  const activeKey = props.selectedModel ?? props.defaultModel
  return key === activeKey
}

function handleSelect(model: ModelOption) {
  emit('select', model.providerId, model.modelName)
}
</script>

<template>
  <div class="model-selector">
    <div class="selector-header">
      <span class="selector-title">选择模型</span>
      <span class="selector-count">{{ availableModels.length }} models</span>
    </div>

    <div v-if="groupedModels.length === 0" class="no-models">
      <p>No models configured</p>
      <span class="hint">Configure providers in Settings</span>
    </div>

    <div v-else class="model-list">
      <template v-for="group in groupedModels" :key="group.providerId">
        <div class="group-header">{{ group.providerName }}</div>
        <button
          v-for="model in group.models"
          :key="`${model.providerId}:${model.modelName}`"
          class="model-item"
          :class="{ selected: isSelected(model) }"
          @click="handleSelect(model)"
        >
          <span class="model-name">{{ model.modelName }}</span>
          <span v-if="isSelected(model)" class="checkmark">&#10003;</span>
        </button>
      </template>
    </div>

    <div class="selector-footer">
      <button class="add-btn" @click="emit('open-settings')">
        <svg width="12" height="12" viewBox="0 0 12 12" fill="none">
          <path d="M6 2V10M2 6H10" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
        </svg>
        添加模型
      </button>
    </div>
  </div>
</template>

<style scoped>
.model-selector {
  position: absolute;
  top: auto;
  bottom: 100%;
  right: 0;
  margin-bottom: 8px;
  width: 280px;
  background: var(--color-white);
  border: var(--border-strong);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-lg);
  z-index: 100;
  overflow: hidden;
}

.selector-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border-bottom: var(--border);
  background: var(--color-gray-50);
}

.selector-title {
  font-family: var(--font-mono);
  font-size: 11px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  color: var(--color-gray-600);
}

.selector-count {
  font-family: var(--font-mono);
  font-size: 11px;
  color: var(--color-gray-400);
}

.no-models {
  padding: 32px 16px;
  text-align: center;
}

.no-models p {
  font-family: var(--font-ui);
  font-size: 13px;
  color: var(--color-gray-600);
  margin: 0 0 4px 0;
}

.no-models .hint {
  font-family: var(--font-mono);
  font-size: 11px;
  color: var(--color-gray-400);
}

.model-list {
  max-height: 320px;
  overflow-y: auto;
  padding: 4px;
}

.group-header {
  padding: 8px 12px 4px;
  font-family: var(--font-mono);
  font-size: 10px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  color: var(--color-gray-400);
}

.model-item {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  border: none;
  border-radius: var(--radius-md);
  background: transparent;
  cursor: pointer;
  transition: background var(--duration-fast) var(--ease-in-out);
  text-align: left;
}

.model-item:hover {
  background: var(--color-gray-50);
}

.model-item.selected {
  background: var(--color-gray-100);
}

.model-name {
  font-family: var(--font-mono);
  font-size: 13px;
  color: var(--color-black);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.checkmark {
  font-family: var(--font-mono);
  font-size: 14px;
  font-weight: 600;
  color: var(--color-black);
  flex-shrink: 0;
}

.selector-footer {
  padding: 8px 12px;
  border-top: var(--border);
  background: var(--color-gray-50);
}

.add-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  width: 100%;
  padding: 8px 12px;
  border: 1px dashed var(--color-gray-300);
  border-radius: var(--radius-md);
  background: transparent;
  font-family: var(--font-mono);
  font-size: 12px;
  color: var(--color-gray-500);
  cursor: pointer;
  transition: all 0.15s ease;
  justify-content: center;
}

.add-btn:hover {
  border-color: var(--color-gray-400);
  color: var(--color-gray-700);
  background: var(--color-white);
}
</style>

import { ref, computed } from 'vue'
import { useLlmConfig } from './useLlmConfig'
import type { ModelOption } from '@/types'

// Module-level state (shared across components)
const selectedModel = ref<string | null>(null) // "providerId:modelName" or null (use default)

export function useModelSelector() {
  const { multiConfig, getAllModelOptions } = useLlmConfig()

  const availableModels = computed<ModelOption[]>(() => getAllModelOptions())

  const activeModel = computed<ModelOption | null>(() => {
    const key = selectedModel.value ?? multiConfig.value?.defaultModel
    if (!key || !key.includes(':')) return null

    const [providerId, modelName] = key.split(':', 2)
    const models = availableModels.value
    return models.find(m => m.providerId === providerId && m.modelName === modelName) ?? null
  })

  const activeModelLabel = computed<string>(() => {
    const model = activeModel.value
    if (!model) return ''
    return model.modelName
  })

  function selectModel(providerId: string, modelName: string) {
    selectedModel.value = `${providerId}:${modelName}`
  }

  function resetToDefault() {
    selectedModel.value = null
  }

  return {
    selectedModel,
    availableModels,
    activeModel,
    activeModelLabel,
    selectModel,
    resetToDefault
  }
}

import { ref, readonly } from 'vue'
import { useWebSocket } from './useWebSocket'
import type {
  LlmConfig,
  LlmConfigInput,
  LlmTestResult,
  LlmMultiConfig,
  LlmProviderInput,
  ModelOption,
  AppStatus
} from '@/types'

const config = ref<LlmConfig | null>(null)
const multiConfig = ref<LlmMultiConfig | null>(null)
const loading = ref(false)
const error = ref<string | null>(null)

export function useLlmConfig() {
  const { request } = useWebSocket()

  async function getConfig(): Promise<LlmMultiConfig> {
    loading.value = true
    error.value = null
    try {
      const result = await request<LlmMultiConfig>('llm.config.get')
      multiConfig.value = result

      // Derive legacy config for backward compat
      if (result.providers && result.providers.length > 0) {
        const defaultParts = result.defaultModel?.split(':') ?? []
        const defaultProvider = defaultParts[0]
          ? result.providers.find(p => p.id === defaultParts[0])
          : result.providers[0]
        config.value = {
          endpoint: defaultProvider?.endpoint ?? '',
          apiKey: defaultProvider?.apiKey ?? '',
          model: defaultParts[1] ?? '',
          configured: result.configured
        }
      } else {
        config.value = {
          endpoint: '',
          apiKey: '',
          model: '',
          configured: result.configured
        }
      }

      return result
    } catch (e) {
      console.error('[LlmConfig] Failed to get config:', e)
      error.value = e instanceof Error ? e.message : 'Failed to get config'
      throw e
    } finally {
      loading.value = false
    }
  }

  async function saveConfig(input: LlmConfigInput): Promise<void> {
    error.value = null
    try {
      await request<{ success: boolean }>('llm.config.save', input)
      await getConfig()
    } catch (e) {
      console.error('[LlmConfig] Failed to save config:', e)
      error.value = e instanceof Error ? e.message : 'Failed to save config'
      throw e
    }
  }

  async function testConfig(input: LlmConfigInput): Promise<LlmTestResult> {
    error.value = null
    try {
      return await request<LlmTestResult>('llm.config.test', input)
    } catch (e) {
      console.error('[LlmConfig] Failed to test config:', e)
      error.value = e instanceof Error ? e.message : 'Failed to test config'
      throw e
    }
  }

  async function addProvider(input: LlmProviderInput): Promise<string> {
    error.value = null
    try {
      const result = await request<{ providerId: string }>('llm.provider.add', input)
      await getConfig()
      return result.providerId
    } catch (e) {
      console.error('[LlmConfig] Failed to add provider:', e)
      error.value = e instanceof Error ? e.message : 'Failed to add provider'
      throw e
    }
  }

  async function updateProvider(providerId: string, updates: Partial<LlmProviderInput>): Promise<void> {
    error.value = null
    try {
      await request<{ success: boolean }>('llm.provider.update', { providerId, ...updates })
      await getConfig()
    } catch (e) {
      console.error('[LlmConfig] Failed to update provider:', e)
      error.value = e instanceof Error ? e.message : 'Failed to update provider'
      throw e
    }
  }

  async function removeProvider(providerId: string): Promise<void> {
    error.value = null
    try {
      await request<{ success: boolean }>('llm.provider.remove', { providerId })
      await getConfig()
    } catch (e) {
      console.error('[LlmConfig] Failed to remove provider:', e)
      error.value = e instanceof Error ? e.message : 'Failed to remove provider'
      throw e
    }
  }

  async function setDefaultModel(defaultModel: string): Promise<void> {
    error.value = null
    try {
      await request<{ success: boolean }>('llm.config.setDefault', { defaultModel })
      await getConfig()
    } catch (e) {
      console.error('[LlmConfig] Failed to set default model:', e)
      error.value = e instanceof Error ? e.message : 'Failed to set default model'
      throw e
    }
  }

  function getAllModelOptions(): ModelOption[] {
    if (!multiConfig.value?.providers) return []
    const options: ModelOption[] = []
    for (const provider of multiConfig.value.providers) {
      for (const model of provider.models ?? []) {
        options.push({
          providerId: provider.id,
          providerName: provider.name,
          modelName: model
        })
      }
    }
    return options
  }

  async function checkStatus(): Promise<AppStatus> {
    try {
      return await request<AppStatus>('app.status')
    } catch (e) {
      console.error('[LlmConfig] Failed to check status:', e)
      return { llmConfigured: false }
    }
  }

  return {
    config: readonly(config),
    multiConfig: readonly(multiConfig),
    loading: readonly(loading),
    error: readonly(error),
    getConfig,
    saveConfig,
    testConfig,
    addProvider,
    updateProvider,
    removeProvider,
    setDefaultModel,
    getAllModelOptions,
    checkStatus
  }
}

import { ref, readonly } from 'vue'
import { useWebSocket } from './useWebSocket'
import type { LlmConfig, LlmConfigInput, LlmTestResult, AppStatus } from '@/types'

const config = ref<LlmConfig | null>(null)
const loading = ref(false)
const error = ref<string | null>(null)

export function useLlmConfig() {
  const { request } = useWebSocket()

  async function getConfig(): Promise<LlmConfig> {
    loading.value = true
    error.value = null
    try {
      const result = await request<LlmConfig>('llm.config.get')
      config.value = result
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
      // Refresh config after save
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
    loading: readonly(loading),
    error: readonly(error),
    getConfig,
    saveConfig,
    testConfig,
    checkStatus
  }
}

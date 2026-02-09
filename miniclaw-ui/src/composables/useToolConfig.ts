import { ref, readonly } from 'vue'
import { useWebSocket } from './useWebSocket'
import type { ToolConfig } from '@/types'

const config = ref<ToolConfig | null>(null)
const loading = ref(false)
const error = ref<string | null>(null)

export function useToolConfig() {
  const { request } = useWebSocket()

  async function getConfig(): Promise<ToolConfig> {
    loading.value = true
    error.value = null
    try {
      const result = await request<ToolConfig>('tools.config.get')
      config.value = result
      return result
    } catch (e) {
      console.error('[ToolConfig] Failed to get config:', e)
      error.value = e instanceof Error ? e.message : 'Failed to get config'
      throw e
    } finally {
      loading.value = false
    }
  }

  async function saveConfig(payload: {
    userDomains: string[]
    searchProviders: { type: string; apiKey: string; enabled: boolean }[]
    hitl?: {
      alwaysConfirmTools: string[]
      dangerousKeywords: string[]
    }
  }): Promise<void> {
    error.value = null
    try {
      await request<{ success: boolean }>('tools.config.save', payload)
      // Refresh config after save
      await getConfig()
    } catch (e) {
      console.error('[ToolConfig] Failed to save config:', e)
      error.value = e instanceof Error ? e.message : 'Failed to save config'
      throw e
    }
  }

  return {
    config: readonly(config),
    loading: readonly(loading),
    error: readonly(error),
    getConfig,
    saveConfig
  }
}

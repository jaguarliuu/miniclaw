import { ref } from 'vue'

export interface SoulConfig {
  id?: number
  agentName: string
  personality: string
  traits: string[]
  responseStyle: string
  detailLevel: string
  expertise: string[]
  forbiddenTopics: string[]
  customPrompt: string
  enabled: boolean
}

export function useSoulConfig() {
  const config = ref<SoulConfig | null>(null)
  const loading = ref(false)
  const error = ref<string | null>(null)

  async function fetchConfig() {
    loading.value = true
    error.value = null
    try {
      const response = await fetch('/api/soul/config')
      if (!response.ok) {
        throw new Error('Failed to fetch soul config')
      }
      config.value = await response.json()
    } catch (e) {
      error.value = e instanceof Error ? e.message : 'Unknown error'
      console.error('Failed to fetch soul config:', e)
    } finally {
      loading.value = false
    }
  }

  async function saveConfig(newConfig: Partial<SoulConfig>) {
    loading.value = true
    error.value = null
    try {
      const response = await fetch('/api/soul/config', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json'
        },
        body: JSON.stringify(newConfig)
      })
      if (!response.ok) {
        throw new Error('Failed to save soul config')
      }
      await fetchConfig()
    } catch (e) {
      error.value = e instanceof Error ? e.message : 'Unknown error'
      console.error('Failed to save soul config:', e)
      throw e
    } finally {
      loading.value = false
    }
  }

  return {
    config,
    loading,
    error,
    fetchConfig,
    saveConfig
  }
}

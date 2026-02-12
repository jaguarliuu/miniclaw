import { ref } from 'vue'

export interface SystemInfo {
  os: string
  osVersion: string
  architecture: string
  javaVersion: string
  javaVendor: string
  userHome: string
  userName: string
  totalMemory: number
  freeMemory: number
  maxMemory: number
  availableProcessors: number
}

export interface EnvironmentCheck {
  name: string
  command: string
  installed: boolean
  version?: string
  path?: string
}

export function useSystemInfo() {
  const systemInfo = ref<SystemInfo | null>(null)
  const environments = ref<EnvironmentCheck[]>([])
  const loading = ref(false)
  const error = ref<string | null>(null)

  async function fetchSystemInfo() {
    loading.value = true
    error.value = null
    try {
      const response = await fetch('/api/system/info')
      if (!response.ok) {
        throw new Error('Failed to fetch system info')
      }
      systemInfo.value = await response.json()
    } catch (e) {
      error.value = e instanceof Error ? e.message : 'Unknown error'
      console.error('Failed to fetch system info:', e)
    } finally {
      loading.value = false
    }
  }

  async function fetchEnvironments() {
    loading.value = true
    error.value = null
    try {
      const response = await fetch('/api/system/environment')
      if (!response.ok) {
        throw new Error('Failed to fetch environments')
      }
      const data = await response.json()
      environments.value = data.environments || []
    } catch (e) {
      error.value = e instanceof Error ? e.message : 'Unknown error'
      console.error('Failed to fetch environments:', e)
    } finally {
      loading.value = false
    }
  }

  async function refresh() {
    await Promise.all([fetchSystemInfo(), fetchEnvironments()])
  }

  return {
    systemInfo,
    environments,
    loading,
    error,
    fetchSystemInfo,
    fetchEnvironments,
    refresh
  }
}

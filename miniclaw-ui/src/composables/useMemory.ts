import { ref, readonly } from 'vue'
import { useWebSocket } from './useWebSocket'

export interface MemoryStatus {
  totalChunks: number
  chunksWithEmbedding: number
  vectorSearchEnabled: boolean
  embeddingProvider: string
  embeddingModel: string
  memoryFileCount: number
  note: string
}

const status = ref<MemoryStatus | null>(null)
const loading = ref(false)
const rebuilding = ref(false)
const error = ref<string | null>(null)

export function useMemory() {
  const { request } = useWebSocket()

  async function loadStatus() {
    loading.value = true
    error.value = null
    try {
      const result = await request<MemoryStatus>('memory.status')
      status.value = result
    } catch (e) {
      console.error('[Memory] Failed to load status:', e)
      error.value = e instanceof Error ? e.message : 'Failed to load status'
      status.value = null
    } finally {
      loading.value = false
    }
  }

  async function rebuildIndex() {
    rebuilding.value = true
    error.value = null
    try {
      const result = await request<{ message: string; totalChunks: number; chunksWithEmbedding: number }>('memory.rebuild')
      // Reload status after rebuild
      await loadStatus()
      return result.message
    } catch (e) {
      console.error('[Memory] Failed to rebuild:', e)
      error.value = e instanceof Error ? e.message : 'Failed to rebuild index'
      throw e
    } finally {
      rebuilding.value = false
    }
  }

  return {
    status: readonly(status),
    loading: readonly(loading),
    rebuilding: readonly(rebuilding),
    error: readonly(error),
    loadStatus,
    rebuildIndex
  }
}

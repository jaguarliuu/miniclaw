import { ref, readonly } from 'vue'
import { useWebSocket } from './useWebSocket'
import type { NodeInfo, NodeRegisterPayload } from '@/types'

const nodes = ref<NodeInfo[]>([])
const loading = ref(false)
const error = ref<string | null>(null)

export function useNodeConsole() {
  const { request } = useWebSocket()

  async function loadNodes() {
    loading.value = true
    error.value = null
    try {
      const result = await request<NodeInfo[]>('nodes.list')
      nodes.value = result
    } catch (e) {
      console.error('[NodeConsole] Failed to load nodes:', e)
      error.value = e instanceof Error ? e.message : 'Failed to load nodes'
      nodes.value = []
    } finally {
      loading.value = false
    }
  }

  async function registerNode(payload: NodeRegisterPayload): Promise<NodeInfo> {
    error.value = null
    try {
      const result = await request<NodeInfo>('nodes.register', payload)
      await loadNodes()
      return result
    } catch (e) {
      console.error('[NodeConsole] Failed to register node:', e)
      error.value = e instanceof Error ? e.message : 'Failed to register node'
      throw e
    }
  }

  async function updateNode(id: string, payload: Partial<NodeRegisterPayload>): Promise<NodeInfo> {
    error.value = null
    try {
      const result = await request<NodeInfo>('nodes.update', { id, ...payload })
      await loadNodes()
      return result
    } catch (e) {
      console.error('[NodeConsole] Failed to update node:', e)
      error.value = e instanceof Error ? e.message : 'Failed to update node'
      throw e
    }
  }

  async function removeNode(id: string): Promise<void> {
    error.value = null
    try {
      await request<{ success: boolean }>('nodes.remove', { id })
      await loadNodes()
    } catch (e) {
      console.error('[NodeConsole] Failed to remove node:', e)
      error.value = e instanceof Error ? e.message : 'Failed to remove node'
      throw e
    }
  }

  async function testNode(id: string): Promise<boolean> {
    error.value = null
    try {
      const result = await request<{ success: boolean }>('nodes.test', { id })
      await loadNodes()
      return result.success
    } catch (e) {
      console.error('[NodeConsole] Failed to test node:', e)
      error.value = e instanceof Error ? e.message : 'Failed to test node'
      throw e
    }
  }

  return {
    nodes: readonly(nodes),
    loading: readonly(loading),
    error: readonly(error),
    loadNodes,
    registerNode,
    updateNode,
    removeNode,
    testNode
  }
}

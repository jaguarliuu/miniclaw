import { ref } from 'vue'
import { useWebSocket } from './useWebSocket'

export interface McpServer {
  id: number
  name: string
  transportType: 'STDIO' | 'SSE' | 'HTTP'
  enabled: boolean
  toolPrefix: string
  command?: string
  args?: string[]
  url?: string
  workingDir?: string
  env?: string[]
  requiresHitl?: boolean
  hitlTools?: string[]
  requestTimeoutSeconds?: number
  toolCount?: number
  createdAt: string
  updatedAt: string
}

export function useMcpServers() {
  const { request } = useWebSocket()

  const servers = ref<McpServer[]>([])
  const loading = ref(false)
  const error = ref<string | null>(null)

  async function loadServers() {
    loading.value = true
    error.value = null

    try {
      const result = await request<{ servers: McpServer[] }>('mcp.servers.list', {})
      servers.value = result.servers || []
    } catch (err: any) {
      error.value = err.message || 'Failed to load MCP servers'
      console.error('Failed to load MCP servers:', err)
    } finally {
      loading.value = false
    }
  }

  async function testConnection(config: Partial<McpServer>): Promise<{ success: boolean; message: string }> {
    try {
      const result = await request<{ success: boolean; message: string }>('mcp.servers.test', config)
      return result
    } catch (err: any) {
      return { success: false, message: err.message || 'Connection test failed' }
    }
  }

  async function createServer(config: Partial<McpServer>): Promise<boolean> {
    try {
      await request<{ server: any }>('mcp.servers.create', config)
      return true
    } catch (err: any) {
      error.value = err.message || 'Failed to create server'
      console.error('Failed to create server:', err)
      return false
    }
  }

  async function updateServer(id: number, config: Partial<McpServer>): Promise<boolean> {
    try {
      await request<{ server: any }>('mcp.servers.update', { ...config, id })
      return true
    } catch (err: any) {
      error.value = err.message || 'Failed to update server'
      console.error('Failed to update server:', err)
      return false
    }
  }

  async function deleteServer(id: number): Promise<boolean> {
    try {
      await request<{ success: boolean }>('mcp.servers.delete', { id })
      return true
    } catch (err: any) {
      error.value = err.message || 'Failed to delete server'
      console.error('Failed to delete server:', err)
      return false
    }
  }

  return {
    servers,
    loading,
    error,
    loadServers,
    testConnection,
    createServer,
    updateServer,
    deleteServer
  }
}

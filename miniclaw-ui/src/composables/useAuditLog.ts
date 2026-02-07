import { ref, readonly } from 'vue'
import { useWebSocket } from './useWebSocket'
import type { AuditLogEntry, AuditLogPage } from '@/types'

const logs = ref<AuditLogEntry[]>([])
const loading = ref(false)
const error = ref<string | null>(null)
const page = ref(0)
const size = ref(50)
const totalElements = ref(0)
const totalPages = ref(0)

export function useAuditLog() {
  const { request } = useWebSocket()

  async function loadLogs(filters: {
    nodeAlias?: string
    eventType?: string
    safetyLevel?: string
    resultStatus?: string
    sessionId?: string
  } = {}, reqPage = 0, reqSize = 50) {
    loading.value = true
    error.value = null
    try {
      const result = await request<AuditLogPage>('audit.logs.list', {
        ...filters,
        page: reqPage,
        size: reqSize
      })
      logs.value = result.logs
      page.value = result.page
      size.value = result.size
      totalElements.value = result.totalElements
      totalPages.value = result.totalPages
    } catch (e) {
      console.error('[AuditLog] Failed to load logs:', e)
      error.value = e instanceof Error ? e.message : 'Failed to load audit logs'
      logs.value = []
    } finally {
      loading.value = false
    }
  }

  return {
    logs: readonly(logs),
    loading: readonly(loading),
    error: readonly(error),
    page: readonly(page),
    size: readonly(size),
    totalElements: readonly(totalElements),
    totalPages: readonly(totalPages),
    loadLogs
  }
}

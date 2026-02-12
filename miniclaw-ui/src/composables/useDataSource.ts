import { ref, readonly } from 'vue'
import { useWebSocket } from './useWebSocket'
import type {
  DataSourceInfo,
  DataSourceCreatePayload,
  DataSourceUpdatePayload,
  ConnectionTestResult,
  QueryResult,
  SchemaMetadata
} from '@/types'

const dataSources = ref<DataSourceInfo[]>([])
const loading = ref(false)
const error = ref<string | null>(null)

export function useDataSource() {
  const { request } = useWebSocket()

  async function loadDataSources() {
    loading.value = true
    error.value = null
    try {
      const result = await request<DataSourceInfo[]>('datasources.list')
      dataSources.value = result
    } catch (e) {
      console.error('[DataSource] Failed to load data sources:', e)
      error.value = e instanceof Error ? e.message : 'Failed to load data sources'
      dataSources.value = []
    } finally {
      loading.value = false
    }
  }

  async function createDataSource(payload: DataSourceCreatePayload): Promise<DataSourceInfo> {
    error.value = null
    try {
      const result = await request<DataSourceInfo>('datasources.create', payload)
      await loadDataSources()
      return result
    } catch (e) {
      console.error('[DataSource] Failed to create data source:', e)
      error.value = e instanceof Error ? e.message : 'Failed to create data source'
      throw e
    }
  }

  async function updateDataSource(
    id: string,
    payload: DataSourceUpdatePayload
  ): Promise<DataSourceInfo> {
    error.value = null
    try {
      const result = await request<DataSourceInfo>('datasources.update', { id, ...payload })
      await loadDataSources()
      return result
    } catch (e) {
      console.error('[DataSource] Failed to update data source:', e)
      error.value = e instanceof Error ? e.message : 'Failed to update data source'
      throw e
    }
  }

  async function deleteDataSource(id: string): Promise<void> {
    error.value = null
    try {
      await request<{ deleted: boolean }>('datasources.delete', { id })
      await loadDataSources()
    } catch (e) {
      console.error('[DataSource] Failed to delete data source:', e)
      error.value = e instanceof Error ? e.message : 'Failed to delete data source'
      throw e
    }
  }

  async function testConnection(id: string): Promise<ConnectionTestResult> {
    error.value = null
    try {
      const result = await request<ConnectionTestResult>('datasources.test', { id })
      await loadDataSources()
      return result
    } catch (e) {
      console.error('[DataSource] Failed to test connection:', e)
      error.value = e instanceof Error ? e.message : 'Failed to test connection'
      throw e
    }
  }

  async function testNewConnection(
    payload: DataSourceCreatePayload
  ): Promise<ConnectionTestResult> {
    error.value = null
    try {
      const result = await request<ConnectionTestResult>('datasources.testNew', payload)
      return result
    } catch (e) {
      console.error('[DataSource] Failed to test new connection:', e)
      error.value = e instanceof Error ? e.message : 'Failed to test new connection'
      throw e
    }
  }

  async function enableDataSource(id: string): Promise<void> {
    error.value = null
    try {
      await request<{ enabled: boolean }>('datasources.enable', { id })
      await loadDataSources()
    } catch (e) {
      console.error('[DataSource] Failed to enable data source:', e)
      error.value = e instanceof Error ? e.message : 'Failed to enable data source'
      throw e
    }
  }

  async function disableDataSource(id: string): Promise<void> {
    error.value = null
    try {
      await request<{ disabled: boolean }>('datasources.disable', { id })
      await loadDataSources()
    } catch (e) {
      console.error('[DataSource] Failed to disable data source:', e)
      error.value = e instanceof Error ? e.message : 'Failed to disable data source'
      throw e
    }
  }

  async function executeQuery(
    id: string,
    query: string,
    maxRows?: number,
    timeoutSeconds?: number
  ): Promise<QueryResult> {
    error.value = null
    try {
      const result = await request<QueryResult>('datasources.query', {
        id,
        query,
        maxRows,
        timeoutSeconds
      })
      return result
    } catch (e) {
      console.error('[DataSource] Failed to execute query:', e)
      error.value = e instanceof Error ? e.message : 'Failed to execute query'
      throw e
    }
  }

  async function getSchemaMetadata(id: string): Promise<SchemaMetadata> {
    error.value = null
    try {
      const result = await request<SchemaMetadata>('datasources.schema', { id })
      return result
    } catch (e) {
      console.error('[DataSource] Failed to get schema metadata:', e)
      error.value = e instanceof Error ? e.message : 'Failed to get schema metadata'
      throw e
    }
  }

  return {
    dataSources: readonly(dataSources),
    loading: readonly(loading),
    error: readonly(error),
    loadDataSources,
    createDataSource,
    updateDataSource,
    deleteDataSource,
    testConnection,
    testNewConnection,
    enableDataSource,
    disableDataSource,
    executeQuery,
    getSchemaMetadata
  }
}

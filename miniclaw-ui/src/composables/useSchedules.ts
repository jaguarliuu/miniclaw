import { ref, readonly } from 'vue'
import { useWebSocket } from './useWebSocket'
import type { ScheduleInfo, ScheduleCreatePayload } from '@/types'

const schedules = ref<ScheduleInfo[]>([])
const loading = ref(false)
const error = ref<string | null>(null)

export function useSchedules() {
  const { request } = useWebSocket()

  async function loadSchedules() {
    loading.value = true
    error.value = null
    try {
      const result = await request<ScheduleInfo[]>('schedule.list')
      schedules.value = result
    } catch (e) {
      console.error('[Schedules] Failed to load schedules:', e)
      error.value = e instanceof Error ? e.message : 'Failed to load schedules'
      schedules.value = []
    } finally {
      loading.value = false
    }
  }

  async function createSchedule(payload: ScheduleCreatePayload): Promise<ScheduleInfo> {
    error.value = null
    try {
      const result = await request<ScheduleInfo>('schedule.create', payload)
      await loadSchedules()
      return result
    } catch (e) {
      console.error('[Schedules] Failed to create schedule:', e)
      error.value = e instanceof Error ? e.message : 'Failed to create schedule'
      throw e
    }
  }

  async function removeSchedule(id: string): Promise<void> {
    error.value = null
    try {
      await request<{ success: boolean }>('schedule.delete', { id })
      await loadSchedules()
    } catch (e) {
      console.error('[Schedules] Failed to remove schedule:', e)
      error.value = e instanceof Error ? e.message : 'Failed to remove schedule'
      throw e
    }
  }

  async function toggleSchedule(id: string, enabled: boolean): Promise<void> {
    error.value = null
    try {
      await request<{ success: boolean }>('schedule.toggle', { id, enabled })
      await loadSchedules()
    } catch (e) {
      console.error('[Schedules] Failed to toggle schedule:', e)
      error.value = e instanceof Error ? e.message : 'Failed to toggle schedule'
      throw e
    }
  }

  async function runSchedule(id: string): Promise<void> {
    error.value = null
    try {
      await request<{ success: boolean }>('schedule.run', { id })
      await loadSchedules()
    } catch (e) {
      console.error('[Schedules] Failed to run schedule:', e)
      error.value = e instanceof Error ? e.message : 'Failed to run schedule'
      throw e
    }
  }

  return {
    schedules: readonly(schedules),
    loading: readonly(loading),
    error: readonly(error),
    loadSchedules,
    createSchedule,
    removeSchedule,
    toggleSchedule,
    runSchedule
  }
}

import { ref, readonly } from 'vue'
import { useWebSocket } from './useWebSocket'
import type { ChannelInfo, ChannelCreatePayload } from '@/types'

const channels = ref<ChannelInfo[]>([])
const loading = ref(false)
const error = ref<string | null>(null)

export function useChannels() {
  const { request } = useWebSocket()

  async function loadChannels() {
    loading.value = true
    error.value = null
    try {
      const result = await request<ChannelInfo[]>('channel.list')
      channels.value = result
    } catch (e) {
      console.error('[Channels] Failed to load channels:', e)
      error.value = e instanceof Error ? e.message : 'Failed to load channels'
      channels.value = []
    } finally {
      loading.value = false
    }
  }

  async function createChannel(payload: ChannelCreatePayload): Promise<ChannelInfo> {
    error.value = null
    try {
      const result = await request<ChannelInfo>('channel.create', payload)
      await loadChannels()
      return result
    } catch (e) {
      console.error('[Channels] Failed to create channel:', e)
      error.value = e instanceof Error ? e.message : 'Failed to create channel'
      throw e
    }
  }

  async function removeChannel(id: string): Promise<void> {
    error.value = null
    try {
      await request<{ success: boolean }>('channel.delete', { id })
      await loadChannels()
    } catch (e) {
      console.error('[Channels] Failed to remove channel:', e)
      error.value = e instanceof Error ? e.message : 'Failed to remove channel'
      throw e
    }
  }

  async function testChannel(id: string): Promise<boolean> {
    error.value = null
    try {
      const result = await request<{ success: boolean }>('channel.test', { id })
      await loadChannels()
      return result.success
    } catch (e) {
      console.error('[Channels] Failed to test channel:', e)
      error.value = e instanceof Error ? e.message : 'Failed to test channel'
      throw e
    }
  }

  return {
    channels: readonly(channels),
    loading: readonly(loading),
    error: readonly(error),
    loadChannels,
    createChannel,
    removeChannel,
    testChannel
  }
}

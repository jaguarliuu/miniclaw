import { ref, readonly } from 'vue'
import type { Skill, SlashCommandItem } from '@/types'
import { useWebSocket } from './useWebSocket'

const commands = ref<SlashCommandItem[]>([])
const loaded = ref(false)

export function useSlashCommands() {
  const { request } = useWebSocket()

  async function loadCommands() {
    if (loaded.value) return
    try {
      const [toolResult, skillResult] = await Promise.all([
        request<{ tools: { name: string; description: string; hitl: boolean }[] }>('tool.list'),
        request<{ skills: Skill[] }>('skills.list')
      ])

      const items: SlashCommandItem[] = []

      for (const t of toolResult.tools) {
        items.push({
          type: 'tool',
          name: t.name,
          description: t.description,
          displayName: '/' + t.name
        })
      }

      for (const s of skillResult.skills) {
        if (!s.available) continue
        items.push({
          type: 'skill',
          name: s.name,
          description: s.description,
          displayName: '/' + s.name
        })
      }

      commands.value = items
      loaded.value = true
    } catch (e) {
      console.error('[SlashCommands] Failed to load:', e)
    }
  }

  function filterCommands(query: string): SlashCommandItem[] {
    if (!query) return commands.value
    const q = query.toLowerCase()
    return commands.value.filter(c =>
      c.name.toLowerCase().includes(q) ||
      c.description.toLowerCase().includes(q)
    )
  }

  return { commands: readonly(commands), loaded: readonly(loaded), loadCommands, filterCommands }
}

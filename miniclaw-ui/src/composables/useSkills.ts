import { ref, readonly } from 'vue'
import type { Skill, SkillDetail } from '@/types'
import { useWebSocket } from './useWebSocket'

const skills = ref<Skill[]>([])
const loading = ref(false)
const selectedSkill = ref<string | null>(null)
const selectedSkillDetail = ref<SkillDetail | null>(null)
const detailLoading = ref(false)

export function useSkills() {
  const { request } = useWebSocket()

  async function loadSkills() {
    loading.value = true
    try {
      const result = await request<{ skills: Skill[] }>('skills.list')
      skills.value = result.skills || []
    } catch (e) {
      console.error('[Skills] Failed to load:', e)
      skills.value = []
    } finally {
      loading.value = false
    }
  }

  async function selectSkill(name: string) {
    selectedSkill.value = name
    selectedSkillDetail.value = null
    detailLoading.value = true

    try {
      const result = await request<SkillDetail>('skills.get', { name })
      selectedSkillDetail.value = result
    } catch (e) {
      console.error('[Skills] Failed to get detail:', e)
      selectedSkillDetail.value = null
    } finally {
      detailLoading.value = false
    }
  }

  function clearSelection() {
    selectedSkill.value = null
    selectedSkillDetail.value = null
  }

  return {
    skills: readonly(skills),
    loading: readonly(loading),
    selectedSkill: readonly(selectedSkill),
    selectedSkillDetail: readonly(selectedSkillDetail),
    detailLoading: readonly(detailLoading),
    loadSkills,
    selectSkill,
    clearSelection
  }
}

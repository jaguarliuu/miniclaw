<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import type { Skill, SkillDetail } from '@/types'
import { useSkills } from '@/composables/useSkills'
import SkillRow from './SkillRow.vue'
import SkillDetailPanel from './SkillDetail.vue'
import SlidePanel from '@/components/common/SlidePanel.vue'

const { skills, loading, selectedSkill, selectedSkillDetail, detailLoading, loadSkills, selectSkill, clearSelection } = useSkills()

const availableSkills = computed(() => skills.value.filter(s => s.available))
const unavailableSkills = computed(() => skills.value.filter(s => !s.available))

const panelOpen = computed(() => selectedSkill.value !== null)

function handleSelect(skill: Skill) {
  selectSkill(skill.name)
}

function handleClose() {
  clearSelection()
}

onMounted(() => {
  loadSkills()
})
</script>

<template>
  <div class="skills-section">
    <header class="section-header">
      <h2 class="section-title">Skills</h2>
      <span class="section-count">{{ skills.length }} total</span>
    </header>

    <div v-if="loading" class="loading-state">
      <span>Loading skills...</span>
    </div>

    <div v-else class="skills-list">
      <!-- Available -->
      <div v-if="availableSkills.length" class="skill-group">
        <h3 class="group-title">Available ({{ availableSkills.length }})</h3>
        <div class="group-list">
          <SkillRow
            v-for="skill in availableSkills"
            :key="skill.name"
            :skill="skill"
            :selected="selectedSkill === skill.name"
            @click="handleSelect(skill)"
          />
        </div>
      </div>

      <!-- Unavailable -->
      <div v-if="unavailableSkills.length" class="skill-group">
        <h3 class="group-title">Unavailable ({{ unavailableSkills.length }})</h3>
        <div class="group-list">
          <SkillRow
            v-for="skill in unavailableSkills"
            :key="skill.name"
            :skill="skill"
            :selected="selectedSkill === skill.name"
            @click="handleSelect(skill)"
          />
        </div>
      </div>

      <!-- Empty state -->
      <div v-if="!skills.length" class="empty-state">
        <span>No skills configured</span>
      </div>
    </div>

    <!-- Detail Panel -->
    <SlidePanel
      :open="panelOpen"
      :title="selectedSkill || ''"
      @close="handleClose"
    >
      <SkillDetailPanel :skill="selectedSkillDetail" :loading="detailLoading" />
    </SlidePanel>
  </div>
</template>

<style scoped>
.skills-section {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.section-header {
  display: flex;
  align-items: baseline;
  gap: 12px;
  padding: 20px;
  border-bottom: var(--border);
}

.section-title {
  font-family: var(--font-mono);
  font-size: 14px;
  font-weight: 500;
}

.section-count {
  font-family: var(--font-mono);
  font-size: 11px;
  color: var(--color-gray-dark);
}

.loading-state,
.empty-state {
  padding: 40px 20px;
  text-align: center;
  font-family: var(--font-mono);
  font-size: 12px;
  color: var(--color-gray-dark);
}

.skills-list {
  flex: 1;
  overflow-y: auto;
}

.skill-group {
  border-bottom: var(--border);
}

.skill-group:last-child {
  border-bottom: none;
}

.group-title {
  font-family: var(--font-mono);
  font-size: 10px;
  font-weight: 500;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  color: var(--color-gray-dark);
  padding: 12px 16px;
  background: var(--color-gray-bg);
  border-bottom: var(--border-light);
}

.group-list {
  display: flex;
  flex-direction: column;
}
</style>

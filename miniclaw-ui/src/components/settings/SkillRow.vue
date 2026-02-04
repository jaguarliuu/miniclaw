<script setup lang="ts">
import type { Skill, SkillDetail } from '@/types'
import StatusDot from '@/components/common/StatusDot.vue'
import Badge from '@/components/common/Badge.vue'

defineProps<{
  skill: Skill
  selected?: boolean
}>()

defineEmits<{
  click: []
}>()
</script>

<template>
  <button
    class="skill-row"
    :class="{ selected }"
    @click="$emit('click')"
  >
    <div class="skill-status">
      <StatusDot :status="skill.available ? 'success' : 'neutral'" />
    </div>
    <div class="skill-info">
      <span class="skill-name">{{ skill.name }}</span>
      <span class="skill-desc">{{ skill.description }}</span>
    </div>
    <div class="skill-meta">
      <Badge variant="muted">{{ skill.tokenCost }} tokens</Badge>
      <Badge v-if="skill.priority > 0" variant="outline">P{{ skill.priority }}</Badge>
    </div>
  </button>
</template>

<style scoped>
.skill-row {
  width: 100%;
  display: grid;
  grid-template-columns: 24px 1fr auto;
  gap: 12px;
  align-items: center;
  padding: 12px 16px;
  border: none;
  border-bottom: var(--border-light);
  background: transparent;
  text-align: left;
  cursor: pointer;
  transition: background 0.15s ease;
}

.skill-row:hover {
  background: var(--color-gray-bg);
}

.skill-row.selected {
  background: var(--color-black);
  color: var(--color-white);
}

.skill-status {
  display: flex;
  align-items: center;
  justify-content: center;
}

.skill-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.skill-name {
  font-family: var(--font-mono);
  font-size: 13px;
  font-weight: 500;
}

.skill-desc {
  font-size: 12px;
  color: var(--color-gray-dark);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.skill-row.selected .skill-desc {
  color: var(--color-gray-light);
}

.skill-meta {
  display: flex;
  gap: 6px;
  align-items: center;
}

.skill-row.selected .skill-meta :deep(.badge) {
  background: var(--color-white);
  color: var(--color-black);
  border-color: var(--color-white);
}
</style>

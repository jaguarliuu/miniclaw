<script setup lang="ts">
import type { SkillActivation } from '@/types'

defineProps<{
  activation: SkillActivation
}>()
</script>

<template>
  <div class="skill-activation-card">
    <div class="skill-icon">⚡</div>
    <div class="skill-info">
      <span class="skill-label">Using skill</span>
      <span class="skill-name">{{ activation.skillName }}</span>
    </div>
    <div class="skill-source">
      {{ activation.source === 'auto' ? 'Auto-triggered' : 'Manual' }}
    </div>
  </div>
</template>

<style scoped>
.skill-activation-card {
  display: flex;
  align-items: center;
  gap: 12px;
  margin: 12px 0;
  padding: 12px 16px;
  border: var(--border);
  background: linear-gradient(135deg, var(--color-gray-bg) 0%, var(--color-white) 100%);
  font-family: var(--font-mono);
  font-size: 12px;
}

.skill-icon {
  font-size: 16px;
}

.skill-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.skill-label {
  font-size: 10px;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  color: var(--color-gray-dark);
}

.skill-name {
  font-weight: 600;
  color: var(--color-black);
}

.skill-source {
  margin-left: auto;
  padding: 2px 8px;
  background: var(--color-black);
  color: var(--color-white);
  font-size: 10px;
  text-transform: uppercase;
  letter-spacing: 0.03em;
}
</style>

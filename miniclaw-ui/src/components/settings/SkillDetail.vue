<script setup lang="ts">
import type { SkillDetail } from '@/types'
import Badge from '@/components/common/Badge.vue'
import StatusDot from '@/components/common/StatusDot.vue'

defineProps<{
  skill: SkillDetail | null
  loading?: boolean
}>()
</script>

<template>
  <div v-if="loading" class="loading">
    <span class="loading-text">Loading...</span>
  </div>
  <div v-else-if="skill" class="skill-detail">
    <!-- Header -->
    <div class="detail-header">
      <div class="detail-status">
        <StatusDot :status="skill.available ? 'success' : 'neutral'" />
        <span class="status-text">{{ skill.available ? 'Available' : 'Unavailable' }}</span>
      </div>
      <div class="detail-badges">
        <Badge variant="muted">{{ skill.tokenCost }} tokens</Badge>
        <Badge v-if="skill.priority > 0" variant="outline">Priority {{ skill.priority }}</Badge>
      </div>
    </div>

    <!-- Description -->
    <section class="detail-section">
      <h3 class="section-title">Description</h3>
      <p class="section-content">{{ skill.description }}</p>
    </section>

    <!-- Unavailable Reason -->
    <section v-if="!skill.available && skill.unavailableReason" class="detail-section">
      <h3 class="section-title">Unavailable Reason</h3>
      <p class="section-content muted">{{ skill.unavailableReason }}</p>
    </section>

    <!-- Allowed Tools -->
    <section v-if="skill.allowedTools?.length" class="detail-section">
      <h3 class="section-title">Allowed Tools</h3>
      <div class="tag-list">
        <Badge v-for="tool in skill.allowedTools" :key="tool" variant="muted">
          {{ tool }}
        </Badge>
      </div>
    </section>

    <!-- Confirm Before -->
    <section v-if="skill.confirmBefore?.length" class="detail-section">
      <h3 class="section-title">Confirm Before</h3>
      <div class="tag-list">
        <Badge v-for="tool in skill.confirmBefore" :key="tool" variant="outline">
          {{ tool }}
        </Badge>
      </div>
    </section>

    <!-- Body / Prompt -->
    <section v-if="skill.body" class="detail-section">
      <h3 class="section-title">Prompt Body</h3>
      <pre class="code-block">{{ skill.body }}</pre>
    </section>
  </div>
  <div v-else class="empty">
    <span class="empty-text">Select a skill to view details</span>
  </div>
</template>

<style scoped>
.skill-detail {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.loading,
.empty {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 200px;
  color: var(--color-gray-dark);
  font-family: var(--font-mono);
  font-size: 12px;
}

.detail-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-bottom: 16px;
  border-bottom: var(--border-light);
}

.detail-status {
  display: flex;
  align-items: center;
  gap: 8px;
}

.status-text {
  font-family: var(--font-mono);
  font-size: 12px;
  text-transform: uppercase;
  letter-spacing: 0.03em;
}

.detail-badges {
  display: flex;
  gap: 6px;
}

.detail-section {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.section-title {
  font-family: var(--font-mono);
  font-size: 10px;
  font-weight: 500;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  color: var(--color-gray-dark);
}

.section-content {
  font-size: 13px;
  line-height: 1.6;
}

.section-content.muted {
  color: var(--color-gray-dark);
}

.tag-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.code-block {
  font-family: var(--font-mono);
  font-size: 12px;
  line-height: 1.5;
  padding: 16px;
  background: var(--color-gray-bg);
  border: var(--border-light);
  overflow-x: auto;
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 400px;
}
</style>

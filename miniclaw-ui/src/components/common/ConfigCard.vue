<script setup lang="ts">
defineProps<{
  title: string
  description: string
  summary?: string
  badge?: {
    text: string
    variant: 'default' | 'success' | 'warning'
  }
}>()

const emit = defineEmits<{
  click: []
}>()
</script>

<template>
  <div class="config-card" @click="emit('click')">
    <div class="card-header">
      <div class="card-info">
        <h3 class="card-title">{{ title }}</h3>
        <p class="card-description">{{ description }}</p>
      </div>
      <div class="card-action">
        <span v-if="badge" class="card-badge" :class="`badge-${badge.variant}`">
          {{ badge.text }}
        </span>
        <svg class="chevron-icon" width="20" height="20" viewBox="0 0 20 20" fill="none">
          <path d="M7.5 5L12.5 10L7.5 15" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>
        </svg>
      </div>
    </div>
    <div v-if="summary" class="card-summary">
      {{ summary }}
    </div>
  </div>
</template>

<style scoped>
.config-card {
  border: var(--border);
  border-radius: var(--radius-lg);
  padding: 20px;
  background: var(--color-white);
  cursor: pointer;
  transition: all var(--duration-fast);
}

.config-card:hover {
  border-color: var(--color-gray-400);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
  transform: translateY(-1px);
}

.card-header {
  display: flex;
  align-items: flex-start;
  gap: 14px;
}

.card-info {
  flex: 1;
  min-width: 0;
}

.card-title {
  font-family: var(--font-mono);
  font-size: 15px;
  font-weight: 600;
  margin: 0 0 4px 0;
  color: var(--color-black);
}

.card-description {
  font-size: 13px;
  color: var(--color-gray-600);
  margin: 0;
  line-height: 1.4;
}

.card-action {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 8px;
}

.card-badge {
  font-family: var(--font-mono);
  font-size: 11px;
  font-weight: 500;
  padding: 3px 8px;
  border-radius: var(--radius-sm);
}

.badge-default {
  background: var(--color-gray-100);
  color: var(--color-gray-700);
}

.badge-success {
  background: #f0fdf4;
  border: 1px solid #bbf7d0;
  color: #166534;
}

.badge-warning {
  background: #fefce8;
  border: 1px solid #fde68a;
  color: #92400e;
}

.chevron-icon {
  color: var(--color-gray-400);
  transition: transform var(--duration-fast);
}

.config-card:hover .chevron-icon {
  transform: translateX(2px);
  color: var(--color-gray-600);
}

.card-summary {
  margin-top: 12px;
  padding-top: 12px;
  border-top: var(--border-light);
  font-family: var(--font-mono);
  font-size: 12px;
  color: var(--color-gray-600);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>

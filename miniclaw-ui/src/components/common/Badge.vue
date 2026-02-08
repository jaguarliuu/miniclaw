<script setup lang="ts">
defineProps<{
  variant?: 'default' | 'outline' | 'muted'
}>()
</script>

<template>
  <span class="badge" :class="variant || 'default'">
    <slot />
  </span>
</template>

<style scoped>
.badge {
  display: inline-flex;
  align-items: center;
  padding: 2px 6px;
  font-family: var(--font-mono);
  font-size: 10px;
  font-weight: 500;
  text-transform: uppercase;
  letter-spacing: 0.03em;
  white-space: nowrap;
  border-radius: var(--radius-sm);
}

.default {
  background: var(--color-gray-100);
  color: var(--color-gray-700);
}

.outline {
  background: transparent;
  border: 1px solid var(--color-gray-300);
  color: var(--color-gray-600);
}

.muted {
  background: var(--color-gray-bg);
  color: var(--color-gray-dark);
}
</style>

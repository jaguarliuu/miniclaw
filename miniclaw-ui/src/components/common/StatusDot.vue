<script setup lang="ts">
defineProps<{
  status: 'success' | 'warning' | 'error' | 'neutral'
  pulse?: boolean
}>()
</script>

<template>
  <span
    class="status-dot"
    :class="[status, { pulse }]"
  ></span>
</template>

<style scoped>
.status-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  flex-shrink: 0;
}

.success {
  background: var(--color-success);
}

.warning {
  background: var(--color-warning);
}

.error {
  background: var(--color-error);
}

.neutral {
  background: var(--color-gray-light);
}

.pulse {
  animation: pulse 2s ease-in-out infinite;
}

@keyframes pulse {
  0%, 100% {
    opacity: 1;
  }
  50% {
    opacity: 0.4;
  }
}
</style>

<script setup lang="ts">
import { computed } from 'vue'
import type { ConnectionState } from '@/types'

const props = defineProps<{
  state: ConnectionState
}>()

const stateText = computed(() => {
  switch (props.state) {
    case 'connected':
      return 'Online'
    case 'connecting':
      return 'Connecting...'
    case 'disconnected':
      return 'Offline'
    case 'error':
      return 'Error'
  }
})
</script>

<template>
  <div class="connection-status" :class="state">
    <span class="dot"></span>
    <span class="text">{{ stateText }}</span>
  </div>
</template>

<style scoped>
.connection-status {
  display: flex;
  align-items: center;
  gap: 8px;
  font-family: var(--font-mono);
  font-size: 11px;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  color: var(--color-gray-dark);
  padding: 8px 0;
}

.dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--color-gray-light);
  transition: background 0.2s ease;
}

.connected .dot {
  background: var(--color-black);
  animation: pulse 2s ease-in-out infinite;
}

.connecting .dot {
  background: var(--color-gray-dark);
  animation: blink 0.8s ease-in-out infinite;
}

.error .dot {
  background: var(--color-black);
}

.text {
  opacity: 0;
  transition: opacity 0.2s ease;
}

.connection-status:hover .text {
  opacity: 1;
}

@keyframes pulse {
  0%, 100% {
    opacity: 1;
  }
  50% {
    opacity: 0.4;
  }
}

@keyframes blink {
  0%, 100% {
    opacity: 1;
  }
  50% {
    opacity: 0;
  }
}
</style>

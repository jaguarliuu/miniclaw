<script setup lang="ts">
import { watch, onMounted, onUnmounted } from 'vue'

const props = defineProps<{
  open: boolean
  title?: string
}>()

const emit = defineEmits<{
  close: []
}>()

function onKeydown(e: KeyboardEvent) {
  if (e.key === 'Escape' && props.open) {
    emit('close')
  }
}

onMounted(() => {
  window.addEventListener('keydown', onKeydown)
})

onUnmounted(() => {
  window.removeEventListener('keydown', onKeydown)
})

watch(() => props.open, (open) => {
  if (open) {
    document.body.style.overflow = 'hidden'
  } else {
    document.body.style.overflow = ''
  }
})
</script>

<template>
  <Teleport to="body">
    <Transition name="panel">
      <div v-if="open" class="slide-panel-overlay" @click.self="emit('close')">
        <aside class="slide-panel">
          <header class="panel-header">
            <h2 class="panel-title">{{ title }}</h2>
            <button class="close-btn" @click="emit('close')" title="Close">
              <span>x</span>
            </button>
          </header>
          <div class="panel-content">
            <slot />
          </div>
        </aside>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.slide-panel-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.2);
  z-index: 100;
  display: flex;
  justify-content: flex-end;
}

.slide-panel {
  width: var(--detail-panel-width);
  max-width: 100%;
  height: 100%;
  background: var(--color-white);
  border-left: var(--border);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  border-bottom: var(--border);
  flex-shrink: 0;
}

.panel-title {
  font-family: var(--font-mono);
  font-size: 13px;
  font-weight: 500;
  letter-spacing: -0.01em;
}

.close-btn {
  width: 24px;
  height: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: var(--border);
  background: var(--color-white);
  font-family: var(--font-mono);
  font-size: 14px;
  cursor: pointer;
  transition: all 0.15s ease;
}

.close-btn:hover {
  background: var(--color-black);
  color: var(--color-white);
}

.panel-content {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
}

/* Transitions */
.panel-enter-active,
.panel-leave-active {
  transition: opacity 0.2s ease;
}

.panel-enter-active .slide-panel,
.panel-leave-active .slide-panel {
  transition: transform 0.2s ease;
}

.panel-enter-from,
.panel-leave-to {
  opacity: 0;
}

.panel-enter-from .slide-panel,
.panel-leave-to .slide-panel {
  transform: translateX(100%);
}
</style>

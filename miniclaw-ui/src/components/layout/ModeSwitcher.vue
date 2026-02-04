<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()

const currentMode = computed(() => {
  return route.path.startsWith('/settings') ? 'settings' : 'workspace'
})

function switchTo(mode: 'workspace' | 'settings') {
  if (mode === 'workspace') {
    router.push('/')
  } else {
    router.push('/settings')
  }
}
</script>

<template>
  <div class="mode-switcher">
    <button
      class="mode-btn"
      :class="{ active: currentMode === 'workspace' }"
      @click="switchTo('workspace')"
      title="Workspace"
    >
      <span class="icon">&#9671;</span>
    </button>
    <button
      class="mode-btn"
      :class="{ active: currentMode === 'settings' }"
      @click="switchTo('settings')"
      title="Settings"
    >
      <span class="icon">&#9881;</span>
    </button>
  </div>
</template>

<style scoped>
.mode-switcher {
  display: flex;
  gap: 4px;
}

.mode-btn {
  width: 28px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: var(--border);
  background: var(--color-white);
  font-size: 14px;
  cursor: pointer;
  transition: all 0.15s ease;
}

.mode-btn:hover {
  background: var(--color-gray-bg);
}

.mode-btn.active {
  background: var(--color-black);
  color: var(--color-white);
}

.icon {
  line-height: 1;
}
</style>

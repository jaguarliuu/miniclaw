<script setup lang="ts">
import { useRoute, useRouter } from 'vue-router'
import { computed } from 'vue'

const route = useRoute()
const router = useRouter()

const sections = [
  { id: 'skills', label: '/skills' },
  { id: 'memory', label: '/memory' },
  { id: 'nodes', label: '/nodes' },
  { id: 'audit', label: '/audit' },
  { id: 'tasks', label: '/tasks' }
]

const currentSection = computed(() => {
  return route.params.section as string || 'skills'
})

function navigateTo(sectionId: string) {
  router.push(`/settings/${sectionId}`)
}
</script>

<template>
  <nav class="settings-sidebar">
    <header class="sidebar-title">Settings</header>
    <ul class="nav-list">
      <li v-for="section in sections" :key="section.id">
        <button
          class="nav-item"
          :class="{ active: currentSection === section.id }"
          @click="navigateTo(section.id)"
        >
          {{ section.label }}
        </button>
      </li>
    </ul>
  </nav>
</template>

<style scoped>
.settings-sidebar {
  width: var(--settings-nav-width);
  height: 100%;
  border-right: var(--border);
  background: var(--color-white);
  display: flex;
  flex-direction: column;
}

.sidebar-title {
  padding: 20px;
  font-family: var(--font-mono);
  font-size: 11px;
  font-weight: 500;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  color: var(--color-gray-dark);
  border-bottom: var(--border-light);
}

.nav-list {
  list-style: none;
  padding: 8px;
}

.nav-item {
  width: 100%;
  padding: 10px 12px;
  border: none;
  background: transparent;
  font-family: var(--font-mono);
  font-size: 13px;
  text-align: left;
  cursor: pointer;
  transition: all 0.15s ease;
  color: var(--color-gray-dark);
}

.nav-item:hover {
  background: var(--color-gray-bg);
  color: var(--color-black);
}

.nav-item.active {
  background: var(--color-black);
  color: var(--color-white);
}
</style>

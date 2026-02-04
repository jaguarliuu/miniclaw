<script setup lang="ts">
import { computed, onMounted, onUnmounted } from 'vue'
import { useRoute } from 'vue-router'
import { useWebSocket } from '@/composables/useWebSocket'
import ModeSwitcher from '@/components/layout/ModeSwitcher.vue'
import SettingsSidebar from '@/components/settings/SettingsSidebar.vue'
import SkillsSection from '@/components/settings/SkillsSection.vue'
import PlaceholderSection from '@/components/settings/PlaceholderSection.vue'
import ConnectionStatus from '@/components/ConnectionStatus.vue'

const route = useRoute()
const { state: connectionState, connect, disconnect } = useWebSocket()

const currentSection = computed(() => {
  return route.params.section as string || 'skills'
})

onMounted(() => {
  connect()
})

onUnmounted(() => {
  // Don't disconnect - keep connection alive for workspace
})
</script>

<template>
  <div class="settings-view">
    <!-- Header -->
    <header class="settings-header">
      <div class="header-left">
        <ModeSwitcher />
        <h1 class="logo">MiniClaw</h1>
      </div>
    </header>

    <!-- Main content -->
    <div class="settings-body">
      <SettingsSidebar />

      <main class="settings-content">
        <SkillsSection v-if="currentSection === 'skills'" />
        <PlaceholderSection v-else-if="currentSection === 'memory'" title="Memory" message="Memory management coming soon" />
        <PlaceholderSection v-else-if="currentSection === 'tasks'" title="Tasks" message="Task management coming soon" />
        <PlaceholderSection v-else title="Not Found" message="Section not found" />
      </main>
    </div>

    <!-- Footer -->
    <footer class="settings-footer">
      <ConnectionStatus :state="connectionState" />
    </footer>
  </div>
</template>

<style scoped>
.settings-view {
  height: 100%;
  display: flex;
  flex-direction: column;
  background: var(--color-white);
}

.settings-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20px;
  border-bottom: var(--border);
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.logo {
  font-family: var(--font-mono);
  font-size: 14px;
  font-weight: 500;
  letter-spacing: -0.02em;
}

.settings-body {
  flex: 1;
  display: flex;
  overflow: hidden;
}

.settings-content {
  flex: 1;
  overflow: hidden;
}

.settings-footer {
  padding: 12px 20px;
  border-top: var(--border-light);
}
</style>

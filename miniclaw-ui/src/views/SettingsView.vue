<script setup lang="ts">
import { computed, onMounted, onUnmounted } from 'vue'
import { useRoute } from 'vue-router'
import { useWebSocket } from '@/composables/useWebSocket'
import ModeSwitcher from '@/components/layout/ModeSwitcher.vue'
import SettingsSidebar from '@/components/settings/SettingsSidebar.vue'
import LlmSection from '@/components/settings/LlmSection.vue'
import SkillsSection from '@/components/settings/SkillsSection.vue'
import MemorySection from '@/components/settings/MemorySection.vue'
import NodesSection from '@/components/settings/NodesSection.vue'
import ChannelsSection from '@/components/settings/ChannelsSection.vue'
import AuditLogSection from '@/components/settings/AuditLogSection.vue'
import SchedulesSection from '@/components/settings/SchedulesSection.vue'
import PlaceholderSection from '@/components/settings/PlaceholderSection.vue'
import ConnectionStatus from '@/components/ConnectionStatus.vue'

const route = useRoute()
const { state: connectionState, connect, disconnect } = useWebSocket()

const currentSection = computed(() => {
  return route.params.section as string || 'llm'
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
        <LlmSection v-if="currentSection === 'llm'" />
        <SkillsSection v-else-if="currentSection === 'skills'" />
        <MemorySection v-else-if="currentSection === 'memory'" />
        <NodesSection v-else-if="currentSection === 'nodes'" />
        <ChannelsSection v-else-if="currentSection === 'channels'" />
        <AuditLogSection v-else-if="currentSection === 'audit'" />
        <SchedulesSection v-else-if="currentSection === 'tasks'" />
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

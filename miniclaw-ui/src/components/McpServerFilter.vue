<script setup lang="ts">
import type { McpServer } from '@/composables/useMcpServers'

defineProps<{
  servers: McpServer[]
  excludedServers: Set<string>
}>()

const emit = defineEmits<{
  toggle: [serverName: string]
}>()
</script>

<template>
  <div class="mcp-filter">
    <div class="mcp-filter-header">
      <span class="title">MCP Servers</span>
      <span class="subtitle">Toggle tools per session</span>
    </div>
    <div class="mcp-filter-list">
      <div
        v-for="server in servers"
        :key="server.name"
        class="mcp-filter-item"
        @click="emit('toggle', server.name)"
      >
        <div class="item-info">
          <span class="item-name">{{ server.name }}</span>
          <span class="item-tools">{{ server.toolCount ?? 0 }} tools</span>
        </div>
        <button
          class="toggle-btn"
          :class="{ excluded: excludedServers.has(server.name) }"
          :title="excludedServers.has(server.name) ? 'Excluded — click to include' : 'Included — click to exclude'"
        >
          <span class="toggle-track">
            <span class="toggle-thumb" />
          </span>
        </button>
      </div>
      <div v-if="servers.length === 0" class="empty-state">
        No connected MCP servers
      </div>
    </div>
  </div>
</template>

<style scoped>
.mcp-filter {
  position: absolute;
  bottom: calc(100% + 4px);
  left: 0;
  min-width: 240px;
  max-width: 320px;
  background: var(--color-white);
  border: var(--border-strong);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-lg);
  z-index: 100;
  overflow: hidden;
}

.mcp-filter-header {
  padding: 10px 14px;
  border-bottom: var(--border-light);
  display: flex;
  align-items: baseline;
  gap: 8px;
}

.title {
  font-family: var(--font-mono);
  font-size: 12px;
  font-weight: 600;
  color: var(--color-gray-700);
}

.subtitle {
  font-family: var(--font-mono);
  font-size: 10px;
  color: var(--color-gray-400);
}

.mcp-filter-list {
  max-height: 200px;
  overflow-y: auto;
  padding: 4px;
}

.mcp-filter-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 8px 10px;
  border-radius: var(--radius-md);
  cursor: pointer;
  transition: background var(--duration-fast) var(--ease-in-out);
}

.mcp-filter-item:hover {
  background: var(--color-gray-50);
}

.item-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.item-name {
  font-family: var(--font-mono);
  font-size: 12px;
  font-weight: 500;
  color: var(--color-gray-700);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.item-tools {
  font-family: var(--font-mono);
  font-size: 10px;
  color: var(--color-gray-400);
}

.toggle-btn {
  flex-shrink: 0;
  background: none;
  border: none;
  padding: 0;
  cursor: pointer;
}

.toggle-track {
  display: block;
  width: 28px;
  height: 16px;
  border-radius: 8px;
  background: var(--color-gray-700);
  position: relative;
  transition: background var(--duration-fast) var(--ease-in-out);
}

.excluded .toggle-track {
  background: var(--color-gray-300);
}

.toggle-thumb {
  display: block;
  width: 12px;
  height: 12px;
  border-radius: 50%;
  background: var(--color-white);
  position: absolute;
  top: 2px;
  left: 14px;
  transition: left var(--duration-fast) var(--ease-in-out);
}

.excluded .toggle-thumb {
  left: 2px;
}

.empty-state {
  padding: 16px 14px;
  font-family: var(--font-mono);
  font-size: 11px;
  color: var(--color-gray-400);
  text-align: center;
}
</style>

<script setup lang="ts">
import type { Session } from '@/types'
import ModeSwitcher from '@/components/layout/ModeSwitcher.vue'

defineProps<{
  sessions: Session[]
  currentId: string | null
}>()

const emit = defineEmits<{
  select: [id: string]
  create: []
}>()

function formatDate(dateStr: string): string {
  const date = new Date(dateStr)
  const now = new Date()
  const diff = now.getTime() - date.getTime()

  // Today
  if (diff < 86400000 && date.getDate() === now.getDate()) {
    return date.toLocaleTimeString('en-US', { hour: '2-digit', minute: '2-digit', hour12: false })
  }

  // This week
  if (diff < 604800000) {
    return date.toLocaleDateString('en-US', { weekday: 'short' })
  }

  // Older
  return date.toLocaleDateString('en-US', { month: 'short', day: 'numeric' })
}
</script>

<template>
  <aside class="sidebar">
    <header class="sidebar-header">
      <div class="header-left">
        <ModeSwitcher />
        <h1 class="logo">MiniClaw</h1>
      </div>
      <button class="new-btn" @click="emit('create')" title="New session">+</button>
    </header>

    <nav class="session-list">
      <div v-if="sessions.length === 0" class="empty-state">
        No sessions yet
      </div>

      <button
        v-for="session in sessions"
        :key="session.id"
        class="session-item"
        :class="{ active: session.id === currentId }"
        @click="emit('select', session.id)"
      >
        <span class="session-title">{{ session.name || 'Untitled' }}</span>
        <span class="session-date">{{ formatDate(session.createdAt) }}</span>
      </button>
    </nav>

    <footer class="sidebar-footer">
      <slot name="footer"></slot>
    </footer>
  </aside>
</template>

<style scoped>
.sidebar {
  width: 260px;
  height: 100%;
  display: flex;
  flex-direction: column;
  border-right: var(--border);
  background: var(--color-white);
}

.sidebar-header {
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

.new-btn {
  width: 28px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: var(--border);
  background: var(--color-white);
  font-family: var(--font-mono);
  font-size: 18px;
  font-weight: 400;
  cursor: pointer;
  transition: all 0.15s ease;
}

.new-btn:hover {
  background: var(--color-black);
  color: var(--color-white);
}

.session-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}

.empty-state {
  padding: 20px 12px;
  font-size: 13px;
  color: var(--color-gray-dark);
}

.session-item {
  width: 100%;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 4px;
  padding: 12px;
  border: none;
  background: transparent;
  text-align: left;
  cursor: pointer;
  transition: background 0.15s ease;
}

.session-item:hover {
  background: var(--color-gray-bg);
}

.session-item.active {
  background: var(--color-black);
  color: var(--color-white);
}

.session-title {
  font-size: 13px;
  font-weight: 500;
  line-height: 1.3;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.session-date {
  font-family: var(--font-mono);
  font-size: 11px;
  color: var(--color-gray-dark);
}

.session-item.active .session-date {
  color: var(--color-gray-light);
}

.sidebar-footer {
  padding: 12px 20px;
  border-top: var(--border-light);
}
</style>

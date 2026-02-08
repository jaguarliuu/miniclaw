<script setup lang="ts">
import type { Session } from '@/types'
import ModeSwitcher from '@/components/layout/ModeSwitcher.vue'
import { useConfirm } from '@/composables/useConfirm'

const { confirm } = useConfirm()

defineProps<{
  sessions: Session[]
  currentId: string | null
}>()

const emit = defineEmits<{
  select: [id: string]
  create: []
  delete: [id: string]
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

async function handleDelete(e: Event, sessionId: string) {
  e.stopPropagation()
  const confirmed = await confirm({
    title: 'Delete Session',
    message: 'This will permanently delete the session and all its messages. This action cannot be undone.',
    confirmText: 'Delete',
    cancelText: 'Cancel',
    danger: true
  })
  if (confirmed) {
    emit('delete', sessionId)
  }
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

      <div
        v-for="session in sessions"
        :key="session.id"
        class="session-item"
        :class="{ active: session.id === currentId }"
        @click="emit('select', session.id)"
      >
        <div class="session-content">
          <span class="session-title">{{ session.name || 'Untitled' }}</span>
          <span class="session-date">{{ formatDate(session.createdAt) }}</span>
        </div>
        <button class="delete-btn" @click="(e) => handleDelete(e, session.id)" title="Delete session">×</button>
      </div>
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
  padding: 16px 20px;
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
  border: 1px solid var(--color-gray-200);
  border-radius: var(--radius-md);
  background: var(--color-gray-100);
  font-family: var(--font-mono);
  font-size: 18px;
  font-weight: 400;
  cursor: pointer;
  transition: all var(--duration-fast) var(--ease-in-out);
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
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 12px;
  border: none;
  border-radius: var(--radius-md);
  background: transparent;
  text-align: left;
  cursor: pointer;
  margin-bottom: 2px;
  transition: background var(--duration-fast) var(--ease-in-out);
}

.session-item:hover {
  background: var(--color-gray-50);
}

.session-item.active {
  background: var(--color-black);
  color: var(--color-white);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-sm);
}

.session-content {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
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
  color: var(--color-gray-400);
}

.session-item.active .session-date {
  color: var(--color-gray-light);
}

.delete-btn {
  opacity: 0;
  width: 20px;
  height: 20px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  background: transparent;
  font-size: 16px;
  font-weight: 500;
  color: var(--color-gray-dark);
  cursor: pointer;
  transition: all var(--duration-fast) var(--ease-in-out);
  flex-shrink: 0;
}

.session-item:hover .delete-btn {
  opacity: 1;
}

.delete-btn:hover {
  color: var(--color-error);
}

.session-item.active .delete-btn {
  color: var(--color-gray-light);
}

.session-item.active .delete-btn:hover {
  color: #ffaaaa;
}

.sidebar-footer {
  padding: 12px 20px;
  border-top: var(--border-light);
}
</style>

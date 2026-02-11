<script setup lang="ts">
import { onMounted, onUnmounted } from 'vue'
import { RouterView } from 'vue-router'
import ConfirmDialog from '@/components/common/ConfirmDialog.vue'
import { useConfirm } from '@/composables/useConfirm'
import { useWebSocket } from '@/composables/useWebSocket'
import { useChat } from '@/composables/useChat'
import { useNotification } from '@/composables/useNotification'

const { state, handleConfirm, handleCancel } = useConfirm()
const { connect, disconnect } = useWebSocket()
const { setupEventListeners } = useChat()

onMounted(() => {
  connect()
  setupEventListeners()
  useNotification()
})

onUnmounted(() => {
  disconnect()
})
</script>

<template>
  <RouterView />

  <!-- Global Confirm Dialog -->
  <ConfirmDialog
    :visible="state.visible"
    :title="state.title"
    :message="state.message"
    :confirm-text="state.confirmText"
    :cancel-text="state.cancelText"
    :danger="state.danger"
    @confirm="handleConfirm"
    @cancel="handleCancel"
  />
</template>

<style>
@import url('https://fonts.googleapis.com/css2?family=IBM+Plex+Sans:wght@400;500;600&family=JetBrains+Mono:wght@400;500&display=swap');

:root {
  --color-black: #111111;
  --color-white: #ffffff;

  /* Gray scale */
  --color-gray-50:  #f7f7f7;
  --color-gray-100: #f0f0f0;
  --color-gray-200: #e2e2e2;
  --color-gray-300: #c0c0c0;
  --color-gray-400: #999999;
  --color-gray-500: #777777;
  --color-gray-600: #555555;
  --color-gray-700: #444444;
  --color-gray-800: #2a2a2a;
  --color-gray-900: #1a1a1a;

  /* Backward compatibility aliases */
  --color-gray-dark: var(--color-gray-500);
  --color-gray-light: var(--color-gray-200);
  --color-gray-bg: var(--color-gray-50);

  /* Status colors (muted) */
  --color-success: #2a9d5c;
  --color-warning: #c98a0c;
  --color-error: #d44040;
  --color-info: #4a7fd4;

  /* Color scales for badges and status */
  --color-blue-50: #eff6ff;
  --color-blue-600: #2563eb;
  --color-green-50: #f0fdf4;
  --color-green-500: #22c55e;
  --color-green-600: #16a34a;
  --color-purple-50: #faf5ff;
  --color-purple-600: #9333ea;
  --color-red-50: #fef2f2;
  --color-red-200: #fecaca;
  --color-red-500: #ef4444;
  --color-red-600: #dc2626;
  --color-red-700: #b91c1c;

  --font-ui: 'IBM Plex Sans', -apple-system, sans-serif;
  --font-mono: 'JetBrains Mono', monospace;

  /* Borders */
  --border: 1px solid var(--color-gray-200);
  --border-light: 1px solid var(--color-gray-100);
  --border-strong: 1px solid var(--color-gray-300);

  /* Radius scale */
  --radius-sm: 4px;
  --radius-md: 6px;
  --radius-lg: 8px;
  --radius-xl: 12px;
  --radius-full: 9999px;

  /* Shadow scale */
  --shadow-xs:    0 1px 2px rgba(0,0,0,0.04);
  --shadow-sm:    0 1px 3px rgba(0,0,0,0.06), 0 1px 2px rgba(0,0,0,0.04);
  --shadow-md:    0 4px 12px rgba(0,0,0,0.06), 0 1px 3px rgba(0,0,0,0.04);
  --shadow-lg:    0 8px 24px rgba(0,0,0,0.08), 0 2px 6px rgba(0,0,0,0.04);
  --shadow-float: 0 12px 32px rgba(0,0,0,0.10), 0 2px 8px rgba(0,0,0,0.04);

  /* Transitions */
  --ease-out: cubic-bezier(0.16, 1, 0.3, 1);
  --ease-in-out: cubic-bezier(0.4, 0, 0.2, 1);
  --duration-fast: 0.15s;
  --duration-normal: 0.2s;

  /* Layout */
  --sidebar-width: 260px;
  --settings-nav-width: 180px;
  --detail-panel-width: 480px;
}

* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

html, body, #app {
  height: 100%;
  width: 100%;
  overflow: hidden;
}

body {
  font-family: var(--font-ui);
  font-size: 14px;
  line-height: 1.5;
  color: var(--color-black);
  background: var(--color-white);
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
}

::selection {
  background: var(--color-black);
  color: var(--color-white);
}

::-webkit-scrollbar {
  width: 6px;
  height: 6px;
}

::-webkit-scrollbar-track {
  background: transparent;
}

::-webkit-scrollbar-thumb {
  background: var(--color-gray-300);
  border-radius: 3px;
}

::-webkit-scrollbar-thumb:hover {
  background: var(--color-gray-400);
}
</style>

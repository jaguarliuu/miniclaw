<script setup lang="ts">
import { RouterView } from 'vue-router'
import ConfirmDialog from '@/components/common/ConfirmDialog.vue'
import { useConfirm } from '@/composables/useConfirm'

const { state, handleConfirm, handleCancel } = useConfirm()
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
  --color-black: #000000;
  --color-white: #ffffff;
  --color-gray-dark: #666666;
  --color-gray-light: #e0e0e0;
  --color-gray-bg: #f5f5f5;

  /* Status colors */
  --color-success: #22c55e;
  --color-warning: #eab308;
  --color-error: #ef4444;

  --font-ui: 'IBM Plex Sans', -apple-system, sans-serif;
  --font-mono: 'JetBrains Mono', monospace;

  --border: 1px solid var(--color-black);
  --border-light: 1px solid var(--color-gray-light);

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
  background: var(--color-gray-light);
  border-radius: 3px;
}

::-webkit-scrollbar-thumb:hover {
  background: var(--color-gray-dark);
}
</style>

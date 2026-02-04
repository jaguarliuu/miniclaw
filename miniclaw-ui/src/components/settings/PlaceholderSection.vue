<script setup lang="ts">
defineProps<{
  title: string
  message?: string
}>()
</script>

<template>
  <div class="placeholder-section">
    <header class="section-header">
      <h2 class="section-title">{{ title }}</h2>
    </header>
    <div class="placeholder-content">
      <span class="placeholder-text">{{ message || 'Coming soon' }}</span>
    </div>
  </div>
</template>

<style scoped>
.placeholder-section {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.section-header {
  padding: 20px;
  border-bottom: var(--border);
}

.section-title {
  font-family: var(--font-mono);
  font-size: 14px;
  font-weight: 500;
}

.placeholder-content {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
}

.placeholder-text {
  font-family: var(--font-mono);
  font-size: 12px;
  color: var(--color-gray-dark);
  text-transform: uppercase;
  letter-spacing: 0.05em;
}
</style>

<script setup lang="ts">
import { onMounted, computed } from 'vue'
import { useMemory } from '@/composables/useMemory'

const { status, loading, rebuilding, error, loadStatus, rebuildIndex } = useMemory()

const vectorStatus = computed(() => {
  if (!status.value) return 'Unknown'
  return status.value.vectorSearchEnabled ? 'Enabled' : 'Disabled (FTS only)'
})

const embeddingInfo = computed(() => {
  if (!status.value) return 'N/A'
  if (status.value.embeddingProvider === 'none') return 'None'
  return `${status.value.embeddingProvider} / ${status.value.embeddingModel}`
})

async function handleRebuild() {
  try {
    await rebuildIndex()
  } catch (e) {
    // Error already handled in composable
  }
}

onMounted(() => {
  loadStatus()
})
</script>

<template>
  <div class="memory-section">
    <header class="section-header">
      <h2 class="section-title">/memory</h2>
      <p class="section-subtitle">Global, cross-session memory system</p>
    </header>

    <div v-if="loading && !status" class="loading-state">
      Loading memory status...
    </div>

    <div v-else-if="error && !status" class="error-state">
      <p>{{ error }}</p>
      <button class="retry-btn" @click="loadStatus">Retry</button>
    </div>

    <div v-else-if="status" class="status-panel">
      <!-- Key Point Banner -->
      <div class="key-point-banner">
        <span class="banner-icon">💡</span>
        <span class="banner-text">{{ status.note }}</span>
      </div>

      <!-- Status Grid -->
      <div class="status-grid">
        <div class="status-card">
          <div class="card-label">Total Chunks</div>
          <div class="card-value">{{ status.totalChunks }}</div>
        </div>

        <div class="status-card">
          <div class="card-label">With Embedding</div>
          <div class="card-value">{{ status.chunksWithEmbedding }}</div>
        </div>

        <div class="status-card">
          <div class="card-label">Memory Files</div>
          <div class="card-value">{{ status.memoryFileCount }}</div>
        </div>

        <div class="status-card">
          <div class="card-label">Vector Search</div>
          <div class="card-value" :class="{ enabled: status.vectorSearchEnabled }">
            {{ vectorStatus }}
          </div>
        </div>
      </div>

      <!-- Embedding Provider -->
      <div class="info-row">
        <span class="info-label">Embedding Provider:</span>
        <span class="info-value">{{ embeddingInfo }}</span>
      </div>

      <!-- Actions -->
      <div class="actions">
        <button
          class="rebuild-btn"
          :disabled="rebuilding"
          @click="handleRebuild"
        >
          {{ rebuilding ? 'Rebuilding...' : 'Rebuild Index' }}
        </button>
        <span class="action-hint">
          Rebuild index from Markdown source files
        </span>
      </div>

      <!-- Error display -->
      <div v-if="error" class="error-banner">
        {{ error }}
      </div>
    </div>
  </div>
</template>

<style scoped>
.memory-section {
  height: 100%;
  overflow-y: auto;
  padding: 24px 32px;
}

.section-header {
  margin-bottom: 24px;
}

.section-title {
  font-family: var(--font-mono);
  font-size: 20px;
  font-weight: 500;
  margin-bottom: 4px;
}

.section-subtitle {
  font-size: 14px;
  color: var(--color-gray-dark);
}

.loading-state,
.error-state {
  padding: 40px;
  text-align: center;
  color: var(--color-gray-dark);
}

.retry-btn {
  margin-top: 12px;
  padding: 8px 16px;
  border: var(--border);
  background: var(--color-white);
  cursor: pointer;
  font-family: var(--font-mono);
  font-size: 13px;
}

.retry-btn:hover {
  background: var(--color-gray-bg);
}

.status-panel {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.key-point-banner {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 16px;
  background: var(--color-gray-bg);
  border-left: 3px solid var(--color-black);
  font-size: 14px;
}

.banner-icon {
  font-size: 16px;
}

.banner-text {
  color: var(--color-gray-dark);
}

.status-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
  gap: 16px;
}

.status-card {
  padding: 16px;
  border: var(--border);
  background: var(--color-white);
}

.card-label {
  font-size: 11px;
  font-weight: 500;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  color: var(--color-gray-dark);
  margin-bottom: 8px;
}

.card-value {
  font-family: var(--font-mono);
  font-size: 20px;
  font-weight: 500;
}

.card-value.enabled {
  color: #22c55e;
}

.info-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 0;
  border-top: var(--border-light);
}

.info-label {
  font-size: 13px;
  color: var(--color-gray-dark);
}

.info-value {
  font-family: var(--font-mono);
  font-size: 13px;
}

.actions {
  display: flex;
  align-items: center;
  gap: 12px;
  padding-top: 8px;
}

.rebuild-btn {
  padding: 10px 20px;
  border: none;
  background: var(--color-black);
  color: var(--color-white);
  font-family: var(--font-mono);
  font-size: 13px;
  cursor: pointer;
  transition: opacity 0.15s ease;
}

.rebuild-btn:hover:not(:disabled) {
  opacity: 0.9;
}

.rebuild-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.action-hint {
  font-size: 12px;
  color: var(--color-gray-dark);
}

.error-banner {
  padding: 12px 16px;
  background: #fef2f2;
  border: 1px solid #fecaca;
  color: #dc2626;
  font-size: 13px;
}
</style>

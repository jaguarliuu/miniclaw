<script setup lang="ts">
import { computed } from 'vue'
import type { DataSourceInfo } from '@/types'

const props = defineProps<{
  dataSources: readonly DataSourceInfo[]
  selectedDataSourceId?: string
}>()

const emit = defineEmits<{
  select: [dataSourceId: string | undefined]
}>()

// 只显示已启用的数据源
const enabledDataSources = computed(() =>
  props.dataSources.filter(ds => ds.status === 'ACTIVE')
)

function handleSelect(dataSourceId: string) {
  // 如果点击的是当前已选中的，则取消选择
  if (props.selectedDataSourceId === dataSourceId) {
    emit('select', undefined)
  } else {
    emit('select', dataSourceId)
  }
}

function getStatusBadge(status: string) {
  switch (status) {
    case 'ACTIVE':
      return '●'
    case 'INACTIVE':
      return '○'
    case 'ERROR':
      return '✕'
    default:
      return '○'
  }
}

function getDataSourceTypeLabel(type: string) {
  const labels: Record<string, string> = {
    MYSQL: 'MySQL',
    POSTGRESQL: 'PostgreSQL',
    ORACLE: 'Oracle',
    GAUSS: 'GaussDB',
    CSV: 'CSV',
    XLSX: 'Excel'
  }
  return labels[type] || type
}
</script>

<template>
  <div class="datasource-selector">
    <div class="selector-header">
      <span class="selector-title">Data Sources</span>
      <span class="selector-count">{{ enabledDataSources.length }} available</span>
    </div>

    <div v-if="enabledDataSources.length === 0" class="no-sources">
      <p>No active data sources</p>
      <span class="hint">Configure data sources in Settings</span>
    </div>

    <div v-else class="source-list">
      <button
        v-for="ds in enabledDataSources"
        :key="ds.id"
        class="source-item"
        :class="{ selected: selectedDataSourceId === ds.id }"
        @click="handleSelect(ds.id)"
      >
        <div class="source-info">
          <span class="source-status" :class="ds.status.toLowerCase()">
            {{ getStatusBadge(ds.status) }}
          </span>
          <span class="source-name">{{ ds.name }}</span>
          <span class="source-type">{{ getDataSourceTypeLabel(ds.type) }}</span>
        </div>
        <span v-if="selectedDataSourceId === ds.id" class="checkmark">✓</span>
      </button>
    </div>
  </div>
</template>

<style scoped>
.datasource-selector {
  position: absolute;
  top: auto;
  bottom: 100%;
  left: 0;
  margin-bottom: 8px;
  width: 320px;
  background: var(--color-white);
  border: var(--border-strong);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-lg);
  z-index: 100;
  overflow: hidden;
}

.selector-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border-bottom: var(--border);
  background: var(--color-gray-50);
}

.selector-title {
  font-family: var(--font-mono);
  font-size: 11px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  color: var(--color-gray-600);
}

.selector-count {
  font-family: var(--font-mono);
  font-size: 11px;
  color: var(--color-gray-400);
}

.no-sources {
  padding: 32px 16px;
  text-align: center;
}

.no-sources p {
  font-family: var(--font-ui);
  font-size: 13px;
  color: var(--color-gray-600);
  margin: 0 0 4px 0;
}

.no-sources .hint {
  font-family: var(--font-mono);
  font-size: 11px;
  color: var(--color-gray-400);
}

.source-list {
  max-height: 280px;
  overflow-y: auto;
  padding: 4px;
}

.source-item {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px;
  border: none;
  border-radius: var(--radius-md);
  background: transparent;
  cursor: pointer;
  transition: background var(--duration-fast) var(--ease-in-out);
  text-align: left;
}

.source-item:hover {
  background: var(--color-gray-50);
}

.source-item.selected {
  background: var(--color-gray-100);
}

.source-info {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 1;
}

.source-status {
  font-size: 10px;
  line-height: 1;
}

.source-status.active {
  color: #22c55e;
}

.source-status.inactive {
  color: var(--color-gray-400);
}

.source-status.error {
  color: #ef4444;
}

.source-name {
  font-family: var(--font-ui);
  font-size: 13px;
  font-weight: 500;
  color: var(--color-black);
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.source-type {
  font-family: var(--font-mono);
  font-size: 10px;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  padding: 2px 6px;
  background: var(--color-gray-100);
  border-radius: var(--radius-sm);
  color: var(--color-gray-500);
}

.checkmark {
  font-family: var(--font-mono);
  font-size: 14px;
  font-weight: 600;
  color: var(--color-black);
}
</style>

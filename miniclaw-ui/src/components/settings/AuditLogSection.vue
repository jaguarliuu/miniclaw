<script setup lang="ts">
import { onMounted, ref, computed } from 'vue'
import { useAuditLog } from '@/composables/useAuditLog'

const { logs, loading, error, page, totalElements, totalPages, loadLogs } = useAuditLog()

// Filter state
const filterNodeAlias = ref('')
const filterEventType = ref('')
const filterSafetyLevel = ref('')
const filterResultStatus = ref('')

// Expanded row
const expandedId = ref<string | null>(null)

const eventTypeOptions = ['', 'command.execute', 'command.reject', 'node.register', 'node.remove', 'node.test']
const safetyLevelOptions = ['', 'read_only', 'side_effect', 'destructive']
const resultStatusOptions = ['', 'success', 'error', 'rejected', 'blocked']

function currentFilters() {
  const filters: Record<string, string> = {}
  if (filterNodeAlias.value) filters.nodeAlias = filterNodeAlias.value
  if (filterEventType.value) filters.eventType = filterEventType.value
  if (filterSafetyLevel.value) filters.safetyLevel = filterSafetyLevel.value
  if (filterResultStatus.value) filters.resultStatus = filterResultStatus.value
  return filters
}

function applyFilters() {
  loadLogs(currentFilters(), 0)
}

function refresh() {
  loadLogs(currentFilters(), page.value)
}

function prevPage() {
  if (page.value > 0) {
    loadLogs(currentFilters(), page.value - 1)
  }
}

function nextPage() {
  if (page.value < totalPages.value - 1) {
    loadLogs(currentFilters(), page.value + 1)
  }
}

function toggleExpand(id: string) {
  expandedId.value = expandedId.value === id ? null : id
}

function formatTime(ts: string): string {
  if (!ts) return '-'
  const d = new Date(ts)
  if (isNaN(d.getTime())) return ts
  return d.toLocaleString()
}

function truncateCommand(cmd: string | null, max = 60): string {
  if (!cmd) return '-'
  if (cmd.length <= max) return cmd
  return cmd.substring(0, max) + '...'
}

function formatDuration(ms: number | null): string {
  if (ms == null) return '-'
  if (ms < 1000) return ms + 'ms'
  return (ms / 1000).toFixed(1) + 's'
}

const hasPrev = computed(() => page.value > 0)
const hasNext = computed(() => page.value < totalPages.value - 1)

onMounted(() => {
  loadLogs()
})
</script>

<template>
  <div class="audit-section">
    <header class="section-header">
      <h2 class="section-title">/audit</h2>
      <p class="section-subtitle">Command execution audit trail</p>
    </header>

    <!-- Filters -->
    <div class="filter-bar">
      <input
        v-model="filterNodeAlias"
        class="filter-input"
        type="text"
        placeholder="Node alias"
        @keyup.enter="applyFilters"
      />
      <select v-model="filterEventType" class="filter-select" @change="applyFilters">
        <option value="">All events</option>
        <option v-for="opt in eventTypeOptions.slice(1)" :key="opt" :value="opt">{{ opt }}</option>
      </select>
      <select v-model="filterSafetyLevel" class="filter-select" @change="applyFilters">
        <option value="">All levels</option>
        <option v-for="opt in safetyLevelOptions.slice(1)" :key="opt" :value="opt">{{ opt }}</option>
      </select>
      <select v-model="filterResultStatus" class="filter-select" @change="applyFilters">
        <option value="">All status</option>
        <option v-for="opt in resultStatusOptions.slice(1)" :key="opt" :value="opt">{{ opt }}</option>
      </select>
      <button class="filter-btn" @click="applyFilters">Search</button>
      <button class="filter-btn refresh-btn" @click="refresh">Refresh</button>
    </div>

    <!-- Loading -->
    <div v-if="loading && logs.length === 0" class="loading-state">
      Loading audit logs...
    </div>

    <!-- Error -->
    <div v-else-if="error && logs.length === 0" class="error-state">
      <p>{{ error }}</p>
      <button class="retry-btn" @click="refresh">Retry</button>
    </div>

    <!-- Empty -->
    <div v-else-if="!loading && logs.length === 0" class="empty-state">
      No audit logs found.
    </div>

    <!-- Table -->
    <div v-else class="table-container">
      <table class="audit-table">
        <thead>
          <tr>
            <th>Time</th>
            <th>Event</th>
            <th>Node</th>
            <th>Command</th>
            <th>Safety</th>
            <th>HITL</th>
            <th>Result</th>
            <th>Duration</th>
          </tr>
        </thead>
        <tbody>
          <template v-for="log in logs" :key="log.id">
            <tr class="log-row" @click="toggleExpand(log.id)">
              <td class="col-time">{{ formatTime(log.createdAt) }}</td>
              <td>
                <span class="badge badge-event">{{ log.eventType }}</span>
              </td>
              <td class="col-node">{{ log.nodeAlias || '-' }}</td>
              <td class="col-command" :title="log.command || ''">{{ truncateCommand(log.command) }}</td>
              <td>
                <span
                  v-if="log.safetyLevel"
                  class="badge"
                  :class="{
                    'badge-safe': log.safetyLevel === 'read_only',
                    'badge-warn': log.safetyLevel === 'side_effect',
                    'badge-danger': log.safetyLevel === 'destructive'
                  }"
                >{{ log.safetyLevel }}</span>
                <span v-else>-</span>
              </td>
              <td>
                <span v-if="log.hitlRequired" class="badge badge-hitl">
                  {{ log.hitlDecision || 'required' }}
                </span>
                <span v-else>-</span>
              </td>
              <td>
                <span
                  class="badge"
                  :class="{
                    'badge-safe': log.resultStatus === 'success',
                    'badge-danger': log.resultStatus === 'error' || log.resultStatus === 'blocked',
                    'badge-warn': log.resultStatus === 'rejected'
                  }"
                >{{ log.resultStatus }}</span>
              </td>
              <td class="col-duration">{{ formatDuration(log.durationMs) }}</td>
            </tr>
            <tr v-if="expandedId === log.id" class="expanded-row">
              <td colspan="8">
                <div class="expanded-content">
                  <div v-if="log.command" class="detail-row">
                    <span class="detail-label">Command:</span>
                    <code class="detail-value">{{ log.command }}</code>
                  </div>
                  <div v-if="log.resultSummary" class="detail-row">
                    <span class="detail-label">Output:</span>
                    <pre class="detail-pre">{{ log.resultSummary }}</pre>
                  </div>
                  <div class="detail-row">
                    <span class="detail-label">Tool:</span>
                    <span class="detail-value">{{ log.toolName || '-' }}</span>
                  </div>
                  <div class="detail-row">
                    <span class="detail-label">Policy:</span>
                    <span class="detail-value">{{ log.safetyPolicy || '-' }}</span>
                  </div>
                  <div v-if="log.runId" class="detail-row">
                    <span class="detail-label">Run ID:</span>
                    <span class="detail-value mono">{{ log.runId }}</span>
                  </div>
                  <div v-if="log.sessionId" class="detail-row">
                    <span class="detail-label">Session ID:</span>
                    <span class="detail-value mono">{{ log.sessionId }}</span>
                  </div>
                  <div v-if="log.connectorType" class="detail-row">
                    <span class="detail-label">Connector:</span>
                    <span class="detail-value">{{ log.connectorType }}</span>
                  </div>
                </div>
              </td>
            </tr>
          </template>
        </tbody>
      </table>

      <!-- Pagination -->
      <div class="pagination">
        <button class="page-btn" :disabled="!hasPrev" @click="prevPage">Previous</button>
        <span class="page-info">
          Page {{ page + 1 }} of {{ totalPages }} ({{ totalElements }} total)
        </span>
        <button class="page-btn" :disabled="!hasNext" @click="nextPage">Next</button>
      </div>

      <!-- Error banner -->
      <div v-if="error" class="error-banner">
        {{ error }}
      </div>
    </div>
  </div>
</template>

<style scoped>
.audit-section {
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

/* Filters */
.filter-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}

.filter-input {
  padding: 6px 10px;
  border: var(--border);
  background: var(--color-white);
  font-family: var(--font-mono);
  font-size: 12px;
  width: 140px;
}

.filter-select {
  padding: 6px 10px;
  border: var(--border);
  background: var(--color-white);
  font-family: var(--font-mono);
  font-size: 12px;
}

.filter-btn {
  padding: 6px 14px;
  border: var(--border);
  background: var(--color-white);
  font-family: var(--font-mono);
  font-size: 12px;
  cursor: pointer;
  transition: background 0.15s ease;
}

.filter-btn:hover {
  background: var(--color-gray-bg);
}

.refresh-btn {
  background: var(--color-black);
  color: var(--color-white);
  border: none;
}

.refresh-btn:hover {
  opacity: 0.9;
}

/* States */
.loading-state,
.error-state,
.empty-state {
  padding: 40px;
  text-align: center;
  color: var(--color-gray-dark);
}

.retry-btn {
  margin-top: 12px;
  padding: 8px 16px;
  border: var(--border);
  border-radius: var(--radius-md);
  background: var(--color-white);
  cursor: pointer;
  font-family: var(--font-mono);
  font-size: 13px;
}

.retry-btn:hover {
  background: var(--color-gray-bg);
}

/* Table */
.table-container {
  overflow-x: auto;
}

.audit-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 12px;
  font-family: var(--font-mono);
}

.audit-table th {
  text-align: left;
  padding: 8px 10px;
  font-size: 11px;
  font-weight: 500;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  color: var(--color-gray-dark);
  border-bottom: var(--border);
  white-space: nowrap;
}

.audit-table td {
  padding: 8px 10px;
  border-bottom: var(--border-light);
  vertical-align: top;
}

.log-row {
  cursor: pointer;
  transition: background 0.1s ease;
}

.log-row:hover {
  background: var(--color-gray-bg);
}

.col-time {
  white-space: nowrap;
  font-size: 11px;
  color: var(--color-gray-dark);
}

.col-node {
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.col-command {
  max-width: 200px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.col-duration {
  white-space: nowrap;
  text-align: right;
}

/* Badges */
.badge {
  display: inline-block;
  padding: 2px 6px;
  border-radius: var(--radius-sm);
  font-size: 11px;
  font-weight: 500;
  white-space: nowrap;
}

.badge-event {
  background: var(--color-gray-bg);
  color: var(--color-black);
}

.badge-safe {
  background: #dcfce7;
  color: #166534;
}

.badge-warn {
  background: #fff7ed;
  color: #c2410c;
}

.badge-danger {
  background: #fef2f2;
  color: #dc2626;
}

.badge-hitl {
  background: #eff6ff;
  color: #1d4ed8;
}

/* Expanded row */
.expanded-row td {
  background: var(--color-gray-bg);
  padding: 0;
}

.expanded-content {
  padding: 12px 16px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.detail-row {
  display: flex;
  gap: 8px;
  font-size: 12px;
}

.detail-label {
  color: var(--color-gray-dark);
  min-width: 80px;
  flex-shrink: 0;
}

.detail-value {
  word-break: break-all;
}

.detail-value.mono {
  font-family: var(--font-mono);
}

.detail-pre {
  margin: 0;
  padding: 8px;
  background: var(--color-white);
  border: var(--border-light);
  border-radius: var(--radius-md);
  font-size: 11px;
  font-family: var(--font-mono);
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 200px;
  overflow-y: auto;
  flex: 1;
}

/* Pagination */
.pagination {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 16px;
  margin-top: 16px;
  padding: 8px 0;
}

.page-btn {
  padding: 6px 14px;
  border: var(--border);
  border-radius: var(--radius-md);
  background: var(--color-white);
  font-family: var(--font-mono);
  font-size: 12px;
  cursor: pointer;
  transition: background 0.15s ease;
}

.page-btn:hover:not(:disabled) {
  background: var(--color-gray-bg);
}

.page-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.page-info {
  font-size: 12px;
  color: var(--color-gray-dark);
}

.error-banner {
  margin-top: 12px;
  padding: 12px 16px;
  background: #fef2f2;
  border: 1px solid #fecaca;
  border-radius: var(--radius-md);
  color: #dc2626;
  font-size: 13px;
}
</style>

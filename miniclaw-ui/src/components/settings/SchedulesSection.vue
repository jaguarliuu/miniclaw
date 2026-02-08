<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useSchedules } from '@/composables/useSchedules'
import { useChannels } from '@/composables/useChannels'
import type { ChannelType, ChannelInfo, ScheduleInfo } from '@/types'

const { schedules, loading, error, loadSchedules, createSchedule, removeSchedule, toggleSchedule, runSchedule } = useSchedules()
const { channels, loadChannels } = useChannels()

// Form state
const showForm = ref(false)
const formName = ref('')
const formCron = ref('')
const formPrompt = ref('')
const formChannelId = ref('')
const formEmailTo = ref('')
const formEmailCc = ref('')
const formError = ref<string | null>(null)
const submitting = ref(false)

// UI state
const runningTasks = ref<Set<string>>(new Set())
const confirmDeleteId = ref<string | null>(null)

// Cron presets
const cronPresets = [
  { label: 'Every hour', cron: '0 * * * *' },
  { label: 'Daily 9:00', cron: '0 9 * * *' },
  { label: 'Daily 18:00', cron: '0 18 * * *' },
  { label: 'Mon 9:00', cron: '0 9 * * 1' },
  { label: '1st of month', cron: '0 9 1 * *' }
]

// Selected channel info
const selectedChannel = computed<ChannelInfo | undefined>(() => {
  return channels.value.find(c => c.id === formChannelId.value)
})

const isEmailChannel = computed(() => {
  return selectedChannel.value?.type === 'email'
})

function resetForm() {
  formName.value = ''
  formCron.value = ''
  formPrompt.value = ''
  formChannelId.value = ''
  formEmailTo.value = ''
  formEmailCc.value = ''
  formError.value = null
}

function openForm() {
  resetForm()
  showForm.value = true
}

function closeForm() {
  showForm.value = false
  resetForm()
}

function applyPreset(cron: string) {
  formCron.value = cron
}

function getCronDescription(cron: string): string {
  if (!cron) return ''
  const parts = cron.split(/\s+/)
  if (parts.length !== 5) return 'Invalid cron expression'

  const [min, hour, dom, mon, dow] = parts

  // Simple descriptions for common patterns
  if (min === '0' && hour === '*' && dom === '*' && mon === '*' && dow === '*') return 'Every hour at :00'
  if (min === '0' && hour !== '*' && dom === '*' && mon === '*' && dow === '*') return `Daily at ${hour}:00`
  if (min !== '*' && hour !== '*' && dom === '*' && mon === '*' && dow === '*') return `Daily at ${hour}:${min!.padStart(2, '0')}`
  if (min === '0' && hour !== '*' && dom === '*' && mon === '*' && dow !== '*') {
    const days = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat']
    const dayName = days[parseInt(dow!)] || `day ${dow}`
    return `Every ${dayName} at ${hour}:00`
  }
  if (min === '0' && hour !== '*' && dom !== '*' && mon === '*' && dow === '*') return `Monthly on day ${dom} at ${hour}:00`
  return `${min} ${hour} ${dom} ${mon} ${dow}`
}

function getChannelName(channelId: string): string {
  const ch = channels.value.find(c => c.id === channelId)
  return ch ? ch.name : channelId
}

function getStatusText(task: ScheduleInfo): string {
  if (task.lastRunSuccess === null) return 'Never run'
  return task.lastRunSuccess ? 'Success' : 'Failed'
}

function getStatusClass(task: ScheduleInfo): string {
  if (task.lastRunSuccess === null) return 'status-unknown'
  return task.lastRunSuccess ? 'status-ok' : 'status-fail'
}

function formatTime(dateStr: string | null): string {
  if (!dateStr) return '-'
  const d = new Date(dateStr)
  return d.toLocaleString()
}

async function handleSubmit() {
  if (!formName.value.trim()) {
    formError.value = 'Name is required'
    return
  }
  if (!formCron.value.trim()) {
    formError.value = 'Cron expression is required'
    return
  }
  if (!formPrompt.value.trim()) {
    formError.value = 'Prompt is required'
    return
  }
  if (!formChannelId.value) {
    formError.value = 'Channel is required'
    return
  }
  if (isEmailChannel.value && !formEmailTo.value.trim()) {
    formError.value = 'Email To is required for email channels'
    return
  }

  submitting.value = true
  formError.value = null
  try {
    const channelType = selectedChannel.value?.type as ChannelType
    await createSchedule({
      name: formName.value.trim(),
      cronExpr: formCron.value.trim(),
      prompt: formPrompt.value.trim(),
      channelId: formChannelId.value,
      channelType,
      emailTo: isEmailChannel.value ? formEmailTo.value.trim() : undefined,
      emailCc: isEmailChannel.value && formEmailCc.value.trim() ? formEmailCc.value.trim() : undefined
    })
    closeForm()
  } catch (e) {
    formError.value = e instanceof Error ? e.message : 'Failed to create schedule'
  } finally {
    submitting.value = false
  }
}

async function handleToggle(task: ScheduleInfo) {
  try {
    await toggleSchedule(task.id, !task.enabled)
  } catch {
    // Error handled in composable
  }
}

async function handleRun(taskId: string) {
  runningTasks.value.add(taskId)
  try {
    await runSchedule(taskId)
  } catch {
    // Error handled in composable
  } finally {
    runningTasks.value.delete(taskId)
  }
}

async function handleDelete(taskId: string) {
  try {
    await removeSchedule(taskId)
  } catch {
    // Error handled in composable
  } finally {
    confirmDeleteId.value = null
  }
}

onMounted(() => {
  loadSchedules()
  loadChannels()
})
</script>

<template>
  <div class="schedules-section">
    <header class="section-header">
      <div class="header-top">
        <div>
          <h2 class="section-title">/tasks</h2>
          <p class="section-subtitle">Scheduled tasks with cron triggers and channel notifications</p>
        </div>
        <button class="add-btn" @click="openForm">+ Add Task</button>
      </div>
    </header>

    <!-- Loading -->
    <div v-if="loading && schedules.length === 0" class="loading-state">
      Loading scheduled tasks...
    </div>

    <!-- Error -->
    <div v-if="error && schedules.length === 0" class="error-state">
      <p>{{ error }}</p>
      <button class="retry-btn" @click="loadSchedules">Retry</button>
    </div>

    <!-- Add Form -->
    <div v-if="showForm" class="form-panel">
      <h3 class="form-title">Create Scheduled Task</h3>

      <div class="form-group">
        <label class="form-label">Name *</label>
        <input v-model="formName" class="form-input" placeholder="e.g. Daily Server Check" />
      </div>

      <div class="form-group">
        <label class="form-label">Cron Expression *</label>
        <input v-model="formCron" class="form-input" placeholder="0 9 * * *" spellcheck="false" />
        <div class="cron-presets">
          <button
            v-for="preset in cronPresets"
            :key="preset.cron"
            class="preset-btn"
            :class="{ active: formCron === preset.cron }"
            @click="applyPreset(preset.cron)"
          >
            {{ preset.label }}
          </button>
        </div>
        <div v-if="formCron" class="cron-description">{{ getCronDescription(formCron) }}</div>
      </div>

      <div class="form-group">
        <label class="form-label">Prompt *</label>
        <textarea
          v-model="formPrompt"
          class="form-textarea"
          rows="4"
          placeholder="Agent 执行的指令，如：巡检所有服务器的CPU、内存、磁盘使用情况，生成巡检报告"
          spellcheck="false"
        />
      </div>

      <div class="form-group">
        <label class="form-label">Channel *</label>
        <select v-model="formChannelId" class="form-input">
          <option value="" disabled>Select a channel...</option>
          <option v-for="ch in channels" :key="ch.id" :value="ch.id">
            {{ ch.name }} ({{ ch.type }})
          </option>
        </select>
      </div>

      <!-- Email fields -->
      <template v-if="isEmailChannel">
        <div class="form-grid">
          <div class="form-group">
            <label class="form-label">To *</label>
            <input v-model="formEmailTo" class="form-input" placeholder="admin@company.com" />
          </div>
          <div class="form-group">
            <label class="form-label">Cc</label>
            <input v-model="formEmailCc" class="form-input" placeholder="ops@company.com" />
          </div>
        </div>
      </template>

      <div v-if="formError" class="form-error">{{ formError }}</div>

      <div class="form-actions">
        <button class="cancel-btn" @click="closeForm">Cancel</button>
        <button class="submit-btn" :disabled="submitting" @click="handleSubmit">
          {{ submitting ? 'Creating...' : 'Create' }}
        </button>
      </div>
    </div>

    <!-- Task List -->
    <div v-if="schedules.length > 0" class="task-list">
      <div v-for="task in schedules" :key="task.id" class="task-card" :class="{ disabled: !task.enabled }">
        <div class="task-header">
          <div class="task-info">
            <span class="task-name">{{ task.name }}</span>
            <span class="type-badge" :class="'badge-' + task.channelType">
              {{ task.channelType }}
            </span>
            <span class="status-dot" :class="getStatusClass(task)" :title="getStatusText(task)" />
          </div>
          <div class="task-actions">
            <button
              class="run-btn"
              :disabled="runningTasks.has(task.id)"
              @click="handleRun(task.id)"
            >
              {{ runningTasks.has(task.id) ? 'Running...' : 'Run Now' }}
            </button>
            <button
              class="toggle-btn"
              :class="{ 'toggle-on': task.enabled, 'toggle-off': !task.enabled }"
              @click="handleToggle(task)"
            >
              {{ task.enabled ? 'Enabled' : 'Disabled' }}
            </button>
            <button
              v-if="confirmDeleteId !== task.id"
              class="delete-btn"
              @click="confirmDeleteId = task.id"
            >
              Delete
            </button>
            <template v-else>
              <button class="confirm-delete-btn" @click="handleDelete(task.id)">Confirm</button>
              <button class="cancel-delete-btn" @click="confirmDeleteId = null">Cancel</button>
            </template>
          </div>
        </div>
        <div class="task-details">
          <span class="task-detail">
            <span class="detail-label">cron:</span> {{ task.cronExpr }}
            <span class="cron-hint">{{ getCronDescription(task.cronExpr) }}</span>
          </span>
          <span class="task-detail">
            <span class="detail-label">channel:</span> {{ getChannelName(task.channelId) }}
          </span>
          <span v-if="task.lastRunAt" class="task-detail">
            <span class="detail-label">last run:</span> {{ formatTime(task.lastRunAt) }}
          </span>
        </div>
        <div class="task-prompt">
          <span class="detail-label">prompt:</span> {{ task.prompt.length > 120 ? task.prompt.substring(0, 120) + '...' : task.prompt }}
        </div>
        <div v-if="task.lastRunError" class="task-error">
          {{ task.lastRunError }}
        </div>
      </div>
    </div>

    <!-- Empty State -->
    <div v-if="!loading && !showForm && schedules.length === 0 && !error" class="empty-state">
      <p>No scheduled tasks configured yet.</p>
      <p class="empty-hint">Create scheduled tasks to automate Agent workflows with cron triggers.</p>
    </div>

    <!-- Global Error -->
    <div v-if="error && schedules.length > 0" class="error-banner">{{ error }}</div>
  </div>
</template>

<style scoped>
.schedules-section {
  height: 100%;
  overflow-y: auto;
  padding: 24px 32px;
}

.section-header {
  margin-bottom: 24px;
}

.header-top {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
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

.add-btn {
  padding: 8px 16px;
  border: none;
  background: var(--color-black);
  color: var(--color-white);
  font-family: var(--font-mono);
  font-size: 13px;
  cursor: pointer;
  transition: opacity 0.15s ease;
}

.add-btn:hover {
  opacity: 0.9;
}

/* Form */
.form-panel {
  margin-bottom: 24px;
  padding: 20px;
  border: var(--border);
  background: var(--color-gray-bg);
}

.form-title {
  font-family: var(--font-mono);
  font-size: 14px;
  font-weight: 500;
  margin-bottom: 16px;
}

.form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  margin-bottom: 12px;
}

.form-group {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-bottom: 12px;
}

.form-label {
  font-size: 11px;
  font-weight: 500;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  color: var(--color-gray-dark);
}

.form-input {
  padding: 8px 10px;
  border: var(--border);
  background: var(--color-white);
  font-family: var(--font-mono);
  font-size: 13px;
}

.form-textarea {
  width: 100%;
  padding: 8px 10px;
  border: var(--border);
  background: var(--color-white);
  font-family: var(--font-mono);
  font-size: 13px;
  resize: vertical;
}

.cron-presets {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
  margin-top: 6px;
}

.preset-btn {
  padding: 3px 8px;
  border: var(--border);
  background: var(--color-white);
  font-family: var(--font-mono);
  font-size: 11px;
  cursor: pointer;
  transition: all 0.15s ease;
}

.preset-btn:hover {
  background: var(--color-gray-bg);
}

.preset-btn.active {
  background: var(--color-black);
  color: var(--color-white);
  border-color: var(--color-black);
}

.cron-description {
  font-size: 12px;
  color: var(--color-gray-dark);
  font-family: var(--font-mono);
  margin-top: 4px;
}

.form-error {
  padding: 8px 12px;
  margin-bottom: 12px;
  background: #fef2f2;
  border: 1px solid #fecaca;
  color: #dc2626;
  font-size: 13px;
}

.form-actions {
  display: flex;
  gap: 8px;
  justify-content: flex-end;
}

.cancel-btn {
  padding: 8px 16px;
  border: var(--border);
  background: var(--color-white);
  font-family: var(--font-mono);
  font-size: 13px;
  cursor: pointer;
}

.cancel-btn:hover {
  background: var(--color-gray-bg);
}

.submit-btn {
  padding: 8px 16px;
  border: none;
  background: var(--color-black);
  color: var(--color-white);
  font-family: var(--font-mono);
  font-size: 13px;
  cursor: pointer;
}

.submit-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

/* Task List */
.task-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.task-card {
  padding: 14px 16px;
  border: var(--border);
  background: var(--color-white);
  transition: border-color 0.15s ease;
}

.task-card:hover {
  border-color: var(--color-black);
}

.task-card.disabled {
  opacity: 0.6;
}

.task-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 6px;
}

.task-info {
  display: flex;
  align-items: center;
  gap: 8px;
}

.task-name {
  font-family: var(--font-mono);
  font-size: 14px;
  font-weight: 500;
}

.type-badge {
  padding: 2px 6px;
  font-family: var(--font-mono);
  font-size: 11px;
  font-weight: 500;
  text-transform: uppercase;
  letter-spacing: 0.03em;
}

.badge-email {
  background: #dbeafe;
  color: #1d4ed8;
}

.badge-webhook {
  background: #fef3c7;
  color: #b45309;
}

.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
}

.status-ok {
  background: #22c55e;
}

.status-fail {
  background: #ef4444;
}

.status-unknown {
  background: #d1d5db;
}

.task-actions {
  display: flex;
  gap: 6px;
}

.run-btn, .toggle-btn, .delete-btn, .confirm-delete-btn, .cancel-delete-btn {
  padding: 4px 10px;
  border: var(--border);
  background: var(--color-white);
  font-family: var(--font-mono);
  font-size: 12px;
  cursor: pointer;
}

.run-btn:hover, .delete-btn:hover, .cancel-delete-btn:hover {
  background: var(--color-gray-bg);
}

.run-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.toggle-on {
  background: #dcfce7;
  color: #15803d;
  border-color: #bbf7d0;
}

.toggle-on:hover {
  background: #bbf7d0;
}

.toggle-off {
  background: #f3f4f6;
  color: #6b7280;
}

.toggle-off:hover {
  background: #e5e7eb;
}

.confirm-delete-btn {
  background: #dc2626;
  color: var(--color-white);
  border-color: #dc2626;
}

.confirm-delete-btn:hover {
  background: #b91c1c;
}

.task-details {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: center;
  font-size: 12px;
  color: var(--color-gray-dark);
  margin-bottom: 4px;
}

.task-detail {
  font-family: var(--font-mono);
}

.detail-label {
  color: var(--color-gray-dark);
  font-weight: 500;
}

.cron-hint {
  color: #9ca3af;
  margin-left: 4px;
}

.task-prompt {
  font-family: var(--font-mono);
  font-size: 12px;
  color: var(--color-gray-dark);
  margin-top: 4px;
  line-height: 1.4;
}

.task-error {
  margin-top: 6px;
  padding: 6px 10px;
  background: #fef2f2;
  border: 1px solid #fecaca;
  color: #dc2626;
  font-family: var(--font-mono);
  font-size: 12px;
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
  background: var(--color-white);
  cursor: pointer;
  font-family: var(--font-mono);
  font-size: 13px;
}

.retry-btn:hover {
  background: var(--color-gray-bg);
}

.empty-hint {
  font-size: 13px;
  margin-top: 8px;
}

.error-banner {
  margin-top: 16px;
  padding: 12px 16px;
  background: #fef2f2;
  border: 1px solid #fecaca;
  color: #dc2626;
  font-size: 13px;
}
</style>

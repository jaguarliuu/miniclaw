<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useChannels } from '@/composables/useChannels'
import type { ChannelType, ChannelInfo } from '@/types'

const { channels, loading, error, loadChannels, createChannel, removeChannel, testChannel } = useChannels()

// Form state
const showForm = ref(false)
const formName = ref('')
const formType = ref<ChannelType>('email')
const formError = ref<string | null>(null)
const submitting = ref(false)

// Email config
const emailHost = ref('')
const emailPort = ref(587)
const emailUsername = ref('')
const emailFrom = ref('')
const emailTls = ref(true)
const emailPassword = ref('')
const passwordVisible = ref(false)

// Webhook config
const webhookUrl = ref('')
const webhookMethod = ref('POST')
const webhookHeaders = ref('{"Content-Type": "application/json"}')
const webhookSecret = ref('')
const secretVisible = ref(false)

// Testing state per channel
const testingChannels = ref<Set<string>>(new Set())

// Delete confirmation
const confirmDeleteId = ref<string | null>(null)

function resetForm() {
  formName.value = ''
  formType.value = 'email'
  formError.value = null
  emailHost.value = ''
  emailPort.value = 587
  emailUsername.value = ''
  emailFrom.value = ''
  emailTls.value = true
  emailPassword.value = ''
  passwordVisible.value = false
  webhookUrl.value = ''
  webhookMethod.value = 'POST'
  webhookHeaders.value = '{"Content-Type": "application/json"}'
  webhookSecret.value = ''
  secretVisible.value = false
}

function openForm() {
  resetForm()
  showForm.value = true
}

function closeForm() {
  showForm.value = false
  resetForm()
}

async function handleSubmit() {
  if (!formName.value.trim()) {
    formError.value = 'Name is required'
    return
  }

  if (formType.value === 'email') {
    if (!emailHost.value.trim()) {
      formError.value = 'SMTP Host is required'
      return
    }
    if (!emailUsername.value.trim()) {
      formError.value = 'Username is required'
      return
    }
    if (!emailFrom.value.trim()) {
      formError.value = 'From address is required'
      return
    }
  } else {
    if (!webhookUrl.value.trim()) {
      formError.value = 'Webhook URL is required'
      return
    }
  }

  submitting.value = true
  formError.value = null
  try {
    if (formType.value === 'email') {
      await createChannel({
        name: formName.value.trim(),
        type: 'email',
        config: {
          host: emailHost.value.trim(),
          port: emailPort.value,
          username: emailUsername.value.trim(),
          from: emailFrom.value.trim(),
          tls: emailTls.value
        },
        credential: emailPassword.value || undefined
      })
    } else {
      let headers: Record<string, string> = {}
      try {
        headers = JSON.parse(webhookHeaders.value)
      } catch {
        formError.value = 'Invalid JSON in headers'
        submitting.value = false
        return
      }
      await createChannel({
        name: formName.value.trim(),
        type: 'webhook',
        config: {
          url: webhookUrl.value.trim(),
          method: webhookMethod.value,
          headers,
          secret: !!webhookSecret.value
        },
        credential: webhookSecret.value || undefined
      })
    }
    closeForm()
  } catch (e) {
    formError.value = e instanceof Error ? e.message : 'Failed to create channel'
  } finally {
    submitting.value = false
  }
}

async function handleTest(channelId: string) {
  testingChannels.value.add(channelId)
  try {
    await testChannel(channelId)
  } catch {
    // Error handled in composable
  } finally {
    testingChannels.value.delete(channelId)
  }
}

async function handleDelete(channelId: string) {
  try {
    await removeChannel(channelId)
  } catch {
    // Error handled in composable
  } finally {
    confirmDeleteId.value = null
  }
}

function getTypeBadgeClass(type: string): string {
  switch (type) {
    case 'email': return 'badge-email'
    case 'webhook': return 'badge-webhook'
    default: return ''
  }
}

function getStatusClass(channel: ChannelInfo): string {
  if (channel.lastTestSuccess === null) return 'status-unknown'
  return channel.lastTestSuccess ? 'status-ok' : 'status-fail'
}

function getChannelDetail(channel: ChannelInfo): string {
  if (channel.type === 'email') {
    const config = channel.config as { host?: string; username?: string }
    return `${config.host || ''} (${config.username || ''})`
  } else {
    const config = channel.config as { url?: string; method?: string }
    return `${config.method || 'POST'} ${config.url || ''}`
  }
}

onMounted(() => {
  loadChannels()
})
</script>

<template>
  <div class="channels-section">
    <header class="section-header">
      <div class="header-top">
        <div>
          <h2 class="section-title">/channels</h2>
          <p class="section-subtitle">Email & Webhook notification channels</p>
        </div>
        <button class="add-btn" @click="openForm">+ Add Channel</button>
      </div>
    </header>

    <!-- Loading -->
    <div v-if="loading && channels.length === 0" class="loading-state">
      Loading channels...
    </div>

    <!-- Error -->
    <div v-if="error && channels.length === 0" class="error-state">
      <p>{{ error }}</p>
      <button class="retry-btn" @click="loadChannels">Retry</button>
    </div>

    <!-- Add Form -->
    <div v-if="showForm" class="form-panel">
      <h3 class="form-title">Create Channel</h3>

      <div class="form-grid">
        <div class="form-group">
          <label class="form-label">Name *</label>
          <input v-model="formName" class="form-input" placeholder="e.g. work-email" />
        </div>

        <div class="form-group">
          <label class="form-label">Type *</label>
          <select v-model="formType" class="form-input">
            <option value="email">Email (SMTP)</option>
            <option value="webhook">Webhook</option>
          </select>
        </div>
      </div>

      <!-- Email Config -->
      <template v-if="formType === 'email'">
        <div class="form-grid">
          <div class="form-group">
            <label class="form-label">SMTP Host *</label>
            <input v-model="emailHost" class="form-input" placeholder="smtp.gmail.com" />
          </div>
          <div class="form-group">
            <label class="form-label">Port</label>
            <input v-model.number="emailPort" type="number" class="form-input" placeholder="587" />
          </div>
          <div class="form-group">
            <label class="form-label">Username *</label>
            <input v-model="emailUsername" class="form-input" placeholder="user@gmail.com" />
          </div>
          <div class="form-group">
            <label class="form-label">From *</label>
            <input v-model="emailFrom" class="form-input" placeholder="user@gmail.com" />
          </div>
        </div>

        <div class="form-grid">
          <div class="form-group">
            <label class="form-label">TLS</label>
            <select v-model="emailTls" class="form-input">
              <option :value="true">Enabled</option>
              <option :value="false">Disabled</option>
            </select>
          </div>
        </div>

        <div class="form-group credential-group">
          <div class="credential-label-row">
            <label class="form-label">SMTP Password</label>
            <button
              type="button"
              class="visibility-toggle"
              @click="passwordVisible = !passwordVisible"
              :title="passwordVisible ? 'Hide password' : 'Show password'"
            >
              <svg v-if="passwordVisible" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/>
                <circle cx="12" cy="12" r="3"/>
              </svg>
              <svg v-else width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94"/>
                <path d="M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19"/>
                <line x1="1" y1="1" x2="23" y2="23"/>
                <path d="M14.12 14.12a3 3 0 1 1-4.24-4.24"/>
              </svg>
            </button>
          </div>
          <input
            v-model="emailPassword"
            :type="passwordVisible ? 'text' : 'password'"
            class="form-input"
            placeholder="Enter SMTP password or app password..."
            autocomplete="off"
          />
        </div>
      </template>

      <!-- Webhook Config -->
      <template v-if="formType === 'webhook'">
        <div class="form-grid">
          <div class="form-group" style="grid-column: 1 / -1">
            <label class="form-label">URL *</label>
            <input v-model="webhookUrl" class="form-input" placeholder="https://hooks.slack.com/services/..." />
          </div>
          <div class="form-group">
            <label class="form-label">Method</label>
            <select v-model="webhookMethod" class="form-input">
              <option value="POST">POST</option>
              <option value="PUT">PUT</option>
            </select>
          </div>
        </div>

        <div class="form-group credential-group">
          <label class="form-label">Headers (JSON)</label>
          <textarea
            v-model="webhookHeaders"
            class="form-textarea"
            rows="2"
            placeholder='{"Content-Type": "application/json"}'
            spellcheck="false"
          />
        </div>

        <div class="form-group credential-group">
          <div class="credential-label-row">
            <label class="form-label">Signing Secret (optional)</label>
            <button
              type="button"
              class="visibility-toggle"
              @click="secretVisible = !secretVisible"
              :title="secretVisible ? 'Hide secret' : 'Show secret'"
            >
              <svg v-if="secretVisible" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/>
                <circle cx="12" cy="12" r="3"/>
              </svg>
              <svg v-else width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94"/>
                <path d="M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19"/>
                <line x1="1" y1="1" x2="23" y2="23"/>
                <path d="M14.12 14.12a3 3 0 1 1-4.24-4.24"/>
              </svg>
            </button>
          </div>
          <input
            v-model="webhookSecret"
            :type="secretVisible ? 'text' : 'password'"
            class="form-input"
            placeholder="Enter signing secret..."
            autocomplete="off"
          />
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

    <!-- Channel List -->
    <div v-if="channels.length > 0" class="channel-list">
      <div v-for="channel in channels" :key="channel.id" class="channel-card">
        <div class="channel-header">
          <div class="channel-info">
            <span class="channel-name">{{ channel.name }}</span>
            <span class="type-badge" :class="getTypeBadgeClass(channel.type)">
              {{ channel.type }}
            </span>
            <span class="status-dot" :class="getStatusClass(channel)" :title="
              channel.lastTestSuccess === null ? 'Not tested' :
              channel.lastTestSuccess ? 'Connected' : 'Connection failed'
            " />
          </div>
          <div class="channel-actions">
            <button
              class="test-btn"
              :disabled="testingChannels.has(channel.id)"
              @click="handleTest(channel.id)"
            >
              {{ testingChannels.has(channel.id) ? 'Testing...' : 'Test' }}
            </button>
            <button
              v-if="confirmDeleteId !== channel.id"
              class="delete-btn"
              @click="confirmDeleteId = channel.id"
            >
              Delete
            </button>
            <template v-else>
              <button class="confirm-delete-btn" @click="handleDelete(channel.id)">Confirm</button>
              <button class="cancel-delete-btn" @click="confirmDeleteId = null">Cancel</button>
            </template>
          </div>
        </div>
        <div class="channel-details">
          <span class="channel-detail">{{ getChannelDetail(channel) }}</span>
        </div>
      </div>
    </div>

    <!-- Empty State -->
    <div v-if="!loading && !showForm && channels.length === 0 && !error" class="empty-state">
      <p>No channels configured yet.</p>
      <p class="empty-hint">Add Email or Webhook channels to enable notifications and Agent integrations.</p>
    </div>

    <!-- Global Error -->
    <div v-if="error && channels.length > 0" class="error-banner">{{ error }}</div>
  </div>
</template>

<style scoped>
.channels-section {
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
  border-radius: var(--radius-lg);
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
}

.credential-group {
  margin-bottom: 12px;
}

.credential-label-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 4px;
}

.visibility-toggle {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border: none;
  background: transparent;
  color: var(--color-gray-dark);
  cursor: pointer;
  border-radius: var(--radius-md);
  transition: background 0.15s ease, color 0.15s ease;
}

.visibility-toggle:hover {
  background: var(--color-gray-bg);
  color: var(--color-black);
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
  border-radius: var(--radius-md);
  background: var(--color-white);
  font-family: var(--font-mono);
  font-size: 13px;
}

.form-textarea {
  width: 100%;
  padding: 8px 10px;
  border: var(--border);
  border-radius: var(--radius-md);
  background: var(--color-white);
  font-family: var(--font-mono);
  font-size: 13px;
  resize: vertical;
}

.form-error {
  padding: 8px 12px;
  margin-bottom: 12px;
  background: #fef2f2;
  border: 1px solid #fecaca;
  border-radius: var(--radius-md);
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
  border-radius: var(--radius-md);
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
  border-radius: var(--radius-md);
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

/* Channel List */
.channel-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.channel-card {
  padding: 14px 16px;
  border: var(--border);
  border-radius: var(--radius-lg);
  background: var(--color-white);
  transition: border-color 0.15s ease;
}

.channel-card:hover {
  border-color: var(--color-black);
}

.channel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 6px;
}

.channel-info {
  display: flex;
  align-items: center;
  gap: 8px;
}

.channel-name {
  font-family: var(--font-mono);
  font-size: 14px;
  font-weight: 500;
}

.type-badge {
  padding: 2px 6px;
  border-radius: var(--radius-sm);
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

.channel-actions {
  display: flex;
  gap: 6px;
}

.test-btn, .delete-btn, .confirm-delete-btn, .cancel-delete-btn {
  padding: 4px 10px;
  border: var(--border);
  border-radius: var(--radius-md);
  background: var(--color-white);
  font-family: var(--font-mono);
  font-size: 12px;
  cursor: pointer;
}

.test-btn:hover, .delete-btn:hover {
  background: var(--color-gray-bg);
}

.test-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.confirm-delete-btn {
  background: #dc2626;
  color: var(--color-white);
  border-color: #dc2626;
}

.confirm-delete-btn:hover {
  background: #b91c1c;
}

.cancel-delete-btn:hover {
  background: var(--color-gray-bg);
}

.channel-details {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  font-size: 12px;
  color: var(--color-gray-dark);
}

.channel-detail {
  font-family: var(--font-mono);
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

.empty-hint {
  font-size: 13px;
  margin-top: 8px;
}

.error-banner {
  margin-top: 16px;
  padding: 12px 16px;
  background: #fef2f2;
  border: 1px solid #fecaca;
  border-radius: var(--radius-md);
  color: #dc2626;
  font-size: 13px;
}
</style>

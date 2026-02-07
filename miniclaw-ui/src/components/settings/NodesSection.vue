<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useNodeConsole } from '@/composables/useNodeConsole'
import type { ConnectorType, AuthType, SafetyPolicy, NodeInfo } from '@/types'

const { nodes, loading, error, loadNodes, registerNode, removeNode, testNode } = useNodeConsole()

// Form state
const showForm = ref(false)
const formAlias = ref('')
const formDisplayName = ref('')
const formConnectorType = ref<ConnectorType>('ssh')
const formHost = ref('')
const formPort = ref<number | undefined>(undefined)
const formUsername = ref('')
const formAuthType = ref<AuthType>('password')
const formCredential = ref('')
const formTags = ref('')
const formSafetyPolicy = ref<SafetyPolicy>('strict')
const formError = ref<string | null>(null)
const submitting = ref(false)
const credentialVisible = ref(false)

// Testing state per node
const testingNodes = ref<Set<string>>(new Set())

// Delete confirmation
const confirmDeleteId = ref<string | null>(null)

function resetForm() {
  formAlias.value = ''
  formDisplayName.value = ''
  formConnectorType.value = 'ssh'
  formHost.value = ''
  formPort.value = undefined
  formUsername.value = ''
  formAuthType.value = 'password'
  formCredential.value = ''
  formTags.value = ''
  formSafetyPolicy.value = 'strict'
  formError.value = null
  credentialVisible.value = false
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
  if (!formAlias.value.trim()) {
    formError.value = 'Alias is required'
    return
  }
  if (!formCredential.value.trim()) {
    formError.value = 'Credential is required'
    return
  }

  submitting.value = true
  formError.value = null
  try {
    await registerNode({
      alias: formAlias.value.trim(),
      displayName: formDisplayName.value.trim() || undefined,
      connectorType: formConnectorType.value,
      host: formHost.value.trim() || undefined,
      port: formPort.value || undefined,
      username: formUsername.value.trim() || undefined,
      authType: formAuthType.value,
      credential: formCredential.value,
      tags: formTags.value.trim() || undefined,
      safetyPolicy: formSafetyPolicy.value
    })
    closeForm()
  } catch (e) {
    formError.value = e instanceof Error ? e.message : 'Failed to register node'
  } finally {
    submitting.value = false
  }
}

async function handleTest(nodeId: string) {
  testingNodes.value.add(nodeId)
  try {
    await testNode(nodeId)
  } catch {
    // Error handled in composable
  } finally {
    testingNodes.value.delete(nodeId)
  }
}

async function handleDelete(nodeId: string) {
  try {
    await removeNode(nodeId)
  } catch {
    // Error handled in composable
  } finally {
    confirmDeleteId.value = null
  }
}

function getTypeBadgeClass(type: string): string {
  switch (type) {
    case 'ssh': return 'badge-ssh'
    case 'k8s': return 'badge-k8s'
    case 'db': return 'badge-db'
    default: return ''
  }
}

function getStatusClass(node: NodeInfo): string {
  if (node.lastTestSuccess === null) return 'status-unknown'
  return node.lastTestSuccess ? 'status-ok' : 'status-fail'
}

function getAuthTypeOptions(type: ConnectorType): { value: AuthType; label: string }[] {
  switch (type) {
    case 'ssh': return [
      { value: 'password', label: 'Password' },
      { value: 'key', label: 'SSH Key' }
    ]
    case 'k8s': return [
      { value: 'kubeconfig', label: 'Kubeconfig' },
      { value: 'token', label: 'Token' }
    ]
    case 'db': return [
      { value: 'password', label: 'Password' },
      { value: 'token', label: 'Token' }
    ]
  }
}

function getCredentialPlaceholder(): string {
  switch (formAuthType.value) {
    case 'password': return 'Enter password...'
    case 'key': return 'Paste SSH private key...'
    case 'kubeconfig': return 'Paste kubeconfig YAML...'
    case 'token': return 'Enter token...'
  }
}

onMounted(() => {
  loadNodes()
})
</script>

<template>
  <div class="nodes-section">
    <header class="section-header">
      <div class="header-top">
        <div>
          <h2 class="section-title">/nodes</h2>
          <p class="section-subtitle">Remote machine management for AIOps</p>
        </div>
        <button class="add-btn" @click="openForm">+ Add Node</button>
      </div>
    </header>

    <!-- Loading -->
    <div v-if="loading && nodes.length === 0" class="loading-state">
      Loading nodes...
    </div>

    <!-- Error -->
    <div v-if="error && nodes.length === 0" class="error-state">
      <p>{{ error }}</p>
      <button class="retry-btn" @click="loadNodes">Retry</button>
    </div>

    <!-- Add Form -->
    <div v-if="showForm" class="form-panel">
      <h3 class="form-title">Register Node</h3>

      <div class="form-grid">
        <div class="form-group">
          <label class="form-label">Alias *</label>
          <input v-model="formAlias" class="form-input" placeholder="e.g. prod-web-1" />
        </div>

        <div class="form-group">
          <label class="form-label">Display Name</label>
          <input v-model="formDisplayName" class="form-input" placeholder="e.g. Production Web Server 1" />
        </div>

        <div class="form-group">
          <label class="form-label">Type *</label>
          <select v-model="formConnectorType" class="form-input">
            <option value="ssh">SSH</option>
            <option value="k8s">Kubernetes</option>
            <option value="db">Database</option>
          </select>
        </div>

        <div class="form-group" v-if="formConnectorType !== 'k8s'">
          <label class="form-label">Host</label>
          <input v-model="formHost" class="form-input" placeholder="e.g. 192.168.1.100" />
        </div>

        <div class="form-group" v-if="formConnectorType === 'ssh'">
          <label class="form-label">Port</label>
          <input v-model.number="formPort" type="number" class="form-input" placeholder="22" />
        </div>

        <div class="form-group" v-if="formConnectorType === 'ssh'">
          <label class="form-label">Username</label>
          <input v-model="formUsername" class="form-input" placeholder="root" />
        </div>

        <div class="form-group">
          <label class="form-label">Auth Type</label>
          <select v-model="formAuthType" class="form-input">
            <option v-for="opt in getAuthTypeOptions(formConnectorType)" :key="opt.value" :value="opt.value">
              {{ opt.label }}
            </option>
          </select>
        </div>

        <div class="form-group">
          <label class="form-label">Tags</label>
          <input v-model="formTags" class="form-input" placeholder="prod,web,us-east" />
        </div>

        <div class="form-group">
          <label class="form-label">Safety Policy</label>
          <select v-model="formSafetyPolicy" class="form-input">
            <option value="strict">Strict (all commands need confirm)</option>
            <option value="standard">Standard (read-only auto, side-effects confirm)</option>
            <option value="relaxed">Relaxed (only destructive blocked)</option>
          </select>
        </div>
      </div>

      <div class="form-group credential-group">
        <div class="credential-label-row">
          <label class="form-label">Credential *</label>
          <button
            type="button"
            class="visibility-toggle"
            @click="credentialVisible = !credentialVisible"
            :title="credentialVisible ? 'Hide credential' : 'Show credential'"
          >
            <svg v-if="credentialVisible" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
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
        <!-- Single-line credentials (password, token) use input -->
        <input
          v-if="formAuthType === 'password' || formAuthType === 'token'"
          v-model="formCredential"
          :type="credentialVisible ? 'text' : 'password'"
          class="form-input"
          :placeholder="getCredentialPlaceholder()"
          autocomplete="off"
        />
        <!-- Multi-line credentials (SSH key, kubeconfig) use textarea with masking -->
        <textarea
          v-else
          v-model="formCredential"
          class="form-textarea"
          :class="{ 'credential-masked': !credentialVisible }"
          :placeholder="getCredentialPlaceholder()"
          rows="4"
          autocomplete="off"
          spellcheck="false"
        />
      </div>

      <div v-if="formError" class="form-error">{{ formError }}</div>

      <div class="form-actions">
        <button class="cancel-btn" @click="closeForm">Cancel</button>
        <button class="submit-btn" :disabled="submitting" @click="handleSubmit">
          {{ submitting ? 'Registering...' : 'Register' }}
        </button>
      </div>
    </div>

    <!-- Node List -->
    <div v-if="nodes.length > 0" class="node-list">
      <div v-for="node in nodes" :key="node.id" class="node-card">
        <div class="node-header">
          <div class="node-info">
            <span class="node-alias">{{ node.alias }}</span>
            <span class="type-badge" :class="getTypeBadgeClass(node.connectorType)">
              {{ node.connectorType }}
            </span>
            <span class="status-dot" :class="getStatusClass(node)" :title="
              node.lastTestSuccess === null ? 'Not tested' :
              node.lastTestSuccess ? 'Connected' : 'Connection failed'
            " />
          </div>
          <div class="node-actions">
            <button
              class="test-btn"
              :disabled="testingNodes.has(node.id)"
              @click="handleTest(node.id)"
            >
              {{ testingNodes.has(node.id) ? 'Testing...' : 'Test' }}
            </button>
            <button
              v-if="confirmDeleteId !== node.id"
              class="delete-btn"
              @click="confirmDeleteId = node.id"
            >
              Delete
            </button>
            <template v-else>
              <button class="confirm-delete-btn" @click="handleDelete(node.id)">Confirm</button>
              <button class="cancel-delete-btn" @click="confirmDeleteId = null">Cancel</button>
            </template>
          </div>
        </div>
        <div class="node-details">
          <span v-if="node.displayName" class="node-detail">{{ node.displayName }}</span>
          <span v-if="node.host" class="node-detail">{{ node.host }}{{ node.port ? ':' + node.port : '' }}</span>
          <span v-if="node.username" class="node-detail">@{{ node.username }}</span>
          <span v-if="node.tags" class="node-tags">
            <span v-for="tag in node.tags.split(',')" :key="tag" class="tag">{{ tag.trim() }}</span>
          </span>
          <span class="node-policy">policy: {{ node.safetyPolicy }}</span>
        </div>
      </div>
    </div>

    <!-- Empty State -->
    <div v-if="!loading && !showForm && nodes.length === 0 && !error" class="empty-state">
      <p>No nodes registered yet.</p>
      <p class="empty-hint">Add SSH, Kubernetes, or Database nodes to enable remote operations.</p>
    </div>

    <!-- Global Error -->
    <div v-if="error && nodes.length > 0" class="error-banner">{{ error }}</div>
  </div>
</template>

<style scoped>
.nodes-section {
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
  border-radius: 4px;
  transition: background 0.15s ease, color 0.15s ease;
}

.visibility-toggle:hover {
  background: var(--color-gray-bg);
  color: var(--color-black);
}

.credential-masked {
  -webkit-text-security: disc;
  color: var(--color-gray-dark);
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

/* Node List */
.node-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.node-card {
  padding: 14px 16px;
  border: var(--border);
  background: var(--color-white);
  transition: border-color 0.15s ease;
}

.node-card:hover {
  border-color: var(--color-black);
}

.node-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 6px;
}

.node-info {
  display: flex;
  align-items: center;
  gap: 8px;
}

.node-alias {
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

.badge-ssh {
  background: #dbeafe;
  color: #1d4ed8;
}

.badge-k8s {
  background: #dcfce7;
  color: #15803d;
}

.badge-db {
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

.node-actions {
  display: flex;
  gap: 6px;
}

.test-btn, .delete-btn, .confirm-delete-btn, .cancel-delete-btn {
  padding: 4px 10px;
  border: var(--border);
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

.node-details {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  font-size: 12px;
  color: var(--color-gray-dark);
}

.node-detail {
  font-family: var(--font-mono);
}

.node-tags {
  display: flex;
  gap: 4px;
}

.tag {
  padding: 1px 6px;
  background: var(--color-gray-bg);
  font-family: var(--font-mono);
  font-size: 11px;
}

.node-policy {
  font-family: var(--font-mono);
  font-size: 11px;
  color: var(--color-gray-dark);
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

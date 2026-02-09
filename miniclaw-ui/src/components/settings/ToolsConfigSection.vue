<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useToolConfig } from '@/composables/useToolConfig'

const { config, loading, error, getConfig, saveConfig } = useToolConfig()

// Domain form state
const newDomain = ref('')
const editUserDomains = ref<string[]>([])

// Provider form state
const editProviders = ref<{ type: string; apiKey: string; enabled: boolean; displayName: string; keyRequired: boolean }[]>([])
const apiKeyVisible = ref<Record<string, boolean>>({})

// HITL form state
const editAlwaysConfirmTools = ref<string[]>([])
const editDangerousKeywords = ref<string[]>([])
const newConfirmTool = ref('')
const newDangerousKeyword = ref('')

// Save state
const saving = ref(false)
const saveError = ref<string | null>(null)
const saveSuccess = ref(false)

function addDomain() {
  const domain = newDomain.value.trim().toLowerCase()
  if (!domain) return
  if (editUserDomains.value.includes(domain)) return
  editUserDomains.value.push(domain)
  newDomain.value = ''
}

function removeDomain(domain: string) {
  editUserDomains.value = editUserDomains.value.filter(d => d !== domain)
}

function handleDomainKeydown(e: KeyboardEvent) {
  if (e.key === 'Enter') {
    e.preventDefault()
    addDomain()
  }
}

function addConfirmTool() {
  const tool = newConfirmTool.value.trim()
  if (!tool) return
  if (editAlwaysConfirmTools.value.some(t => t.toLowerCase() === tool.toLowerCase())) return
  editAlwaysConfirmTools.value.push(tool)
  newConfirmTool.value = ''
}

function removeConfirmTool(tool: string) {
  editAlwaysConfirmTools.value = editAlwaysConfirmTools.value.filter(t => t !== tool)
}

function handleConfirmToolKeydown(e: KeyboardEvent) {
  if (e.key === 'Enter') {
    e.preventDefault()
    addConfirmTool()
  }
}

function addDangerousKeyword() {
  const keyword = newDangerousKeyword.value.trim()
  if (!keyword) return
  if (editDangerousKeywords.value.some(k => k.toLowerCase() === keyword.toLowerCase())) return
  editDangerousKeywords.value.push(keyword)
  newDangerousKeyword.value = ''
}

function removeDangerousKeyword(keyword: string) {
  editDangerousKeywords.value = editDangerousKeywords.value.filter(k => k !== keyword)
}

function handleDangerousKeywordKeydown(e: KeyboardEvent) {
  if (e.key === 'Enter') {
    e.preventDefault()
    addDangerousKeyword()
  }
}

async function handleSave() {
  saving.value = true
  saveError.value = null
  saveSuccess.value = false
  try {
    await saveConfig({
      userDomains: editUserDomains.value,
      searchProviders: editProviders.value.map(p => ({
        type: p.type,
        apiKey: p.apiKey,
        enabled: p.enabled
      })),
      hitl: {
        alwaysConfirmTools: editAlwaysConfirmTools.value,
        dangerousKeywords: editDangerousKeywords.value
      }
    })
    saveSuccess.value = true
    // Clear api key fields after save (will show masked values on refresh)
    editProviders.value.forEach(p => { p.apiKey = '' })
    setTimeout(() => { saveSuccess.value = false }, 3000)
  } catch (e) {
    saveError.value = e instanceof Error ? e.message : 'Failed to save'
  } finally {
    saving.value = false
  }
}

function syncFormFromConfig() {
  if (!config.value) return
  editUserDomains.value = [...config.value.trustedDomains.user]
  editProviders.value = config.value.searchProviders.map(p => ({
    type: p.type,
    displayName: p.displayName,
    keyRequired: p.keyRequired,
    apiKey: '',
    enabled: p.enabled
  }))
  editAlwaysConfirmTools.value = config.value.hitl?.alwaysConfirmTools ? [...config.value.hitl.alwaysConfirmTools] : []
  editDangerousKeywords.value = config.value.hitl?.dangerousKeywords ? [...config.value.hitl.dangerousKeywords] : []
}

onMounted(async () => {
  await getConfig()
  syncFormFromConfig()
})
</script>

<template>
  <div class="tools-section">
    <header class="section-header">
      <div>
        <h2 class="section-title">/tools</h2>
        <p class="section-subtitle">Built-in tool configuration</p>
      </div>
    </header>

    <!-- Loading -->
    <div v-if="loading && !config" class="loading-state">Loading configuration...</div>

    <!-- Error -->
    <div v-if="error && !config" class="error-state">
      <p>{{ error }}</p>
      <button class="retry-btn" @click="getConfig">Retry</button>
    </div>

    <template v-if="config">
      <!-- Section: HTTP Trusted Domains -->
      <div class="config-block">
        <h3 class="block-title">HTTP Trusted Domains</h3>
        <p class="block-desc">Only these domains are allowed for http_get requests.</p>

        <!-- Default domains (readonly) -->
        <div class="domain-group">
          <label class="form-label">DEFAULT DOMAINS</label>
          <div class="pill-list">
            <span v-for="d in config.trustedDomains.defaults" :key="d" class="pill pill-default">
              {{ d }}
            </span>
          </div>
        </div>

        <!-- User domains (editable) -->
        <div class="domain-group">
          <label class="form-label">YOUR DOMAINS</label>
          <div class="pill-list" v-if="editUserDomains.length > 0">
            <span v-for="d in editUserDomains" :key="d" class="pill pill-user">
              {{ d }}
              <button class="pill-remove" @click="removeDomain(d)">&times;</button>
            </span>
          </div>
          <div v-else class="empty-hint">No custom domains added</div>
          <div class="domain-add-row">
            <input
              v-model="newDomain"
              class="form-input domain-input"
              placeholder="example.com"
              spellcheck="false"
              @keydown="handleDomainKeydown"
            />
            <button class="add-btn" @click="addDomain" :disabled="!newDomain.trim()">+ Add</button>
          </div>
        </div>
      </div>

      <!-- Section: Web Search Providers -->
      <div class="config-block">
        <h3 class="block-title">Web Search Providers</h3>
        <p class="block-desc">Configure search engines for the web_search tool. DuckDuckGo is always available as fallback.</p>

        <!-- Default provider (always on) -->
        <div class="provider-card provider-default">
          <div class="provider-header">
            <div class="provider-info">
              <span class="provider-name">DuckDuckGo (Default)</span>
              <span class="provider-hint">Free, no API key needed</span>
            </div>
            <span class="always-on-badge">Always On</span>
          </div>
        </div>

        <!-- Configurable providers -->
        <div v-for="provider in editProviders" :key="provider.type" class="provider-card">
          <div class="provider-header">
            <div class="provider-info">
              <span class="provider-name">{{ provider.displayName }}</span>
              <span class="provider-hint" v-if="provider.keyRequired">Requires API key</span>
              <span class="provider-hint" v-else>API key optional</span>
            </div>
            <label class="toggle-switch">
              <input type="checkbox" v-model="provider.enabled" />
              <span class="toggle-slider"></span>
            </label>
          </div>
          <div v-if="provider.keyRequired || provider.type === 'github'" class="provider-body">
            <label class="form-label">API KEY</label>
            <div class="input-with-toggle">
              <input
                v-model="provider.apiKey"
                :type="apiKeyVisible[provider.type] ? 'text' : 'password'"
                class="form-input"
                :placeholder="config.searchProviders.find(p => p.type === provider.type)?.apiKey || 'Enter API key'"
                autocomplete="off"
                spellcheck="false"
              />
              <button
                type="button"
                class="visibility-toggle"
                @click="apiKeyVisible[provider.type] = !apiKeyVisible[provider.type]"
              >
                {{ apiKeyVisible[provider.type] ? 'Hide' : 'Show' }}
              </button>
            </div>
            <span class="form-hint">Leave empty to keep current key</span>
          </div>
        </div>
      </div>

      <!-- Section: Command Safety -->
      <div class="config-block">
        <h3 class="block-title">Command Safety</h3>
        <p class="block-desc">Configure which tools and commands require human confirmation before execution.</p>

        <!-- Always Confirm Tools -->
        <div class="domain-group">
          <label class="form-label">ALWAYS CONFIRM TOOLS</label>
          <p class="field-desc">These tools will always require human confirmation before execution.</p>
          <div class="pill-list" v-if="editAlwaysConfirmTools.length > 0">
            <span v-for="t in editAlwaysConfirmTools" :key="t" class="pill pill-warn">
              {{ t }}
              <button class="pill-remove" @click="removeConfirmTool(t)">&times;</button>
            </span>
          </div>
          <div v-else class="empty-hint">No tools configured — using default rules only</div>
          <div class="domain-add-row">
            <input
              v-model="newConfirmTool"
              class="form-input domain-input"
              placeholder="shell"
              spellcheck="false"
              @keydown="handleConfirmToolKeydown"
            />
            <button class="add-btn" @click="addConfirmTool" :disabled="!newConfirmTool.trim()">+ Add</button>
          </div>
        </div>

        <!-- Dangerous Keywords -->
        <div class="domain-group">
          <label class="form-label">DANGEROUS KEYWORDS</label>
          <p class="field-desc">Commands containing any of these keywords will require confirmation (case-insensitive).</p>
          <div class="pill-list" v-if="editDangerousKeywords.length > 0">
            <span v-for="k in editDangerousKeywords" :key="k" class="pill pill-warn">
              {{ k }}
              <button class="pill-remove" @click="removeDangerousKeyword(k)">&times;</button>
            </span>
          </div>
          <div v-else class="empty-hint">No custom keywords — using built-in patterns only</div>
          <div class="domain-add-row">
            <input
              v-model="newDangerousKeyword"
              class="form-input domain-input"
              placeholder="docker rm"
              spellcheck="false"
              @keydown="handleDangerousKeywordKeydown"
            />
            <button class="add-btn" @click="addDangerousKeyword" :disabled="!newDangerousKeyword.trim()">+ Add</button>
          </div>
        </div>
      </div>

      <!-- Save Success -->
      <div v-if="saveSuccess" class="save-success">Configuration saved successfully</div>

      <!-- Save Error -->
      <div v-if="saveError" class="save-error">{{ saveError }}</div>

      <!-- Save Button -->
      <div class="form-actions">
        <button
          class="save-btn"
          :disabled="saving"
          @click="handleSave"
        >
          {{ saving ? 'Saving...' : 'Save' }}
        </button>
      </div>
    </template>
  </div>
</template>

<style scoped>
.tools-section {
  height: 100%;
  overflow-y: auto;
  padding: 24px 32px;
}

.section-header {
  margin-bottom: 20px;
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

/* Config Blocks */
.config-block {
  margin-bottom: 28px;
  max-width: 600px;
}

.block-title {
  font-family: var(--font-mono);
  font-size: 14px;
  font-weight: 500;
  margin-bottom: 4px;
}

.block-desc {
  font-size: 13px;
  color: var(--color-gray-dark);
  margin-bottom: 16px;
}

.field-desc {
  font-size: 12px;
  color: var(--color-gray-dark);
  margin-bottom: 8px;
  margin-top: -4px;
}

/* Domain Groups */
.domain-group {
  margin-bottom: 16px;
}

.form-label {
  display: block;
  font-size: 11px;
  font-weight: 500;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  color: var(--color-gray-dark);
  margin-bottom: 6px;
}

.pill-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 8px;
}

.pill {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
  border-radius: var(--radius-sm);
  font-family: var(--font-mono);
  font-size: 12px;
}

.pill-default {
  background: var(--color-gray-bg);
  border: var(--border-light);
  color: var(--color-gray-dark);
}

.pill-user {
  background: #f0fdf4;
  border: 1px solid #bbf7d0;
  color: #166534;
}

.pill-warn {
  background: #fefce8;
  border: 1px solid #fde68a;
  color: #92400e;
}

.pill-remove {
  background: none;
  border: none;
  cursor: pointer;
  font-size: 14px;
  line-height: 1;
  color: inherit;
  padding: 0 2px;
  opacity: 0.6;
}

.pill-remove:hover {
  opacity: 1;
}

.empty-hint {
  font-size: 12px;
  color: var(--color-gray-dark);
  margin-bottom: 8px;
  font-style: italic;
}

.domain-add-row {
  display: flex;
  gap: 8px;
}

.domain-input {
  flex: 1;
  max-width: 300px;
}

.add-btn {
  padding: 8px 14px;
  border: var(--border);
  border-radius: var(--radius-md);
  background: var(--color-white);
  font-family: var(--font-mono);
  font-size: 12px;
  cursor: pointer;
  white-space: nowrap;
}

.add-btn:hover:not(:disabled) {
  background: var(--color-gray-bg);
  border-color: var(--color-black);
}

.add-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

/* Form Input */
.form-input {
  padding: 8px 10px;
  border: var(--border);
  border-radius: var(--radius-md);
  background: var(--color-white);
  font-family: var(--font-mono);
  font-size: 13px;
  width: 100%;
  box-sizing: border-box;
}

.form-input:focus {
  outline: none;
  border-color: var(--color-black);
  box-shadow: 0 0 0 3px rgba(0,0,0,0.03);
}

.form-hint {
  display: block;
  font-size: 11px;
  color: var(--color-gray-dark);
  margin-top: 4px;
}

.input-with-toggle {
  display: flex;
  gap: 0;
}

.input-with-toggle .form-input {
  flex: 1;
  border-right: none;
}

.visibility-toggle {
  padding: 8px 12px;
  border: var(--border);
  border-radius: 0 var(--radius-md) var(--radius-md) 0;
  background: var(--color-gray-bg);
  font-family: var(--font-mono);
  font-size: 11px;
  cursor: pointer;
  white-space: nowrap;
  color: var(--color-gray-dark);
}

.visibility-toggle:hover {
  background: var(--color-white);
  color: var(--color-black);
}

/* Provider Cards */
.provider-card {
  border: var(--border);
  border-radius: var(--radius-lg);
  margin-bottom: -1px;
  padding: 14px 16px;
}

.provider-card:first-of-type {
  border-top-left-radius: 0;
  border-top-right-radius: 0;
}

.provider-default {
  background: var(--color-gray-bg);
}

.provider-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.provider-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.provider-name {
  font-family: var(--font-mono);
  font-size: 13px;
  font-weight: 500;
}

.provider-hint {
  font-size: 11px;
  color: var(--color-gray-dark);
}

.always-on-badge {
  font-family: var(--font-mono);
  font-size: 11px;
  color: #166534;
  background: #f0fdf4;
  border: 1px solid #bbf7d0;
  border-radius: var(--radius-sm);
  padding: 2px 8px;
}

.provider-body {
  margin-top: 12px;
  padding-top: 12px;
  border-top: var(--border-light);
}

/* Toggle Switch */
.toggle-switch {
  position: relative;
  display: inline-block;
  width: 36px;
  height: 20px;
  cursor: pointer;
}

.toggle-switch input {
  opacity: 0;
  width: 0;
  height: 0;
}

.toggle-slider {
  position: absolute;
  top: 0; left: 0; right: 0; bottom: 0;
  background: #e5e7eb;
  transition: 0.2s;
  border-radius: 10px;
}

.toggle-slider::before {
  content: '';
  position: absolute;
  height: 16px;
  width: 16px;
  left: 2px;
  bottom: 2px;
  background: white;
  transition: 0.2s;
  border-radius: 50%;
}

.toggle-switch input:checked + .toggle-slider {
  background: var(--color-black);
}

.toggle-switch input:checked + .toggle-slider::before {
  transform: translateX(16px);
}

/* Save states */
.save-success {
  padding: 10px 14px;
  background: #f0fdf4;
  border: 1px solid #bbf7d0;
  border-radius: var(--radius-md);
  color: #166534;
  font-size: 13px;
  max-width: 600px;
  margin-bottom: 12px;
}

.save-error {
  padding: 10px 14px;
  background: #fef2f2;
  border: 1px solid #fecaca;
  border-radius: var(--radius-md);
  color: #dc2626;
  font-size: 13px;
  max-width: 600px;
  margin-bottom: 12px;
}

/* Actions */
.form-actions {
  max-width: 600px;
}

.save-btn {
  padding: 8px 16px;
  border: none;
  border-radius: var(--radius-md);
  background: var(--color-black);
  color: var(--color-white);
  font-family: var(--font-mono);
  font-size: 13px;
  cursor: pointer;
}

.save-btn:hover:not(:disabled) {
  opacity: 0.9;
}

.save-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

/* States */
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
  border-radius: var(--radius-md);
  background: var(--color-white);
  cursor: pointer;
  font-family: var(--font-mono);
  font-size: 13px;
}

.retry-btn:hover {
  background: var(--color-gray-bg);
}
</style>

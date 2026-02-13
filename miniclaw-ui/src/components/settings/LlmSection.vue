<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useLlmConfig } from '@/composables/useLlmConfig'
import { providerPresets } from '@/data/providerPresets'
import type { LlmProviderConfig, LlmProviderInput } from '@/types'

const {
  multiConfig,
  loading,
  error,
  getConfig,
  testConfig,
  addProvider,
  updateProvider,
  removeProvider,
  setDefaultModel
} = useLlmConfig()

// Add form state
const showAddForm = ref(false)
const addForm = ref<LlmProviderInput>({
  id: '',
  name: '',
  endpoint: '',
  apiKey: '',
  models: []
})
const newModelInput = ref('')
const adding = ref(false)
const addError = ref<string | null>(null)

// Edit state
const editingProviderId = ref<string | null>(null)
const editForm = ref<LlmProviderInput>({
  name: '',
  endpoint: '',
  apiKey: '',
  models: []
})
const editModelInput = ref('')
const editing = ref(false)
const editError = ref<string | null>(null)

// Test state
const testing = ref(false)
const testResult = ref<{ success: boolean; message: string; latencyMs?: number } | null>(null)

// Delete confirm
const confirmDeleteId = ref<string | null>(null)

// Default model selector
const defaultModelValue = computed({
  get: () => multiConfig.value?.defaultModel ?? '',
  set: (val: string) => {
    if (val) setDefaultModel(val)
  }
})

// All model options for default selector
const allModelOptions = computed(() => {
  if (!multiConfig.value?.providers) return []
  const options: { value: string; label: string }[] = []
  for (const p of multiConfig.value.providers) {
    for (const m of p.models ?? []) {
      options.push({ value: `${p.id}:${m}`, label: `${p.name} / ${m}` })
    }
  }
  return options
})

function applyPreset(presetId: string) {
  const preset = providerPresets.find(p => p.id === presetId)
  if (preset) {
    addForm.value = {
      id: preset.id,
      name: preset.name,
      endpoint: preset.endpoint,
      apiKey: '',
      models: [...preset.models]
    }
    showAddForm.value = true
    testResult.value = null
    addError.value = null
  }
}

function addModelTag(list: string[], input: { value: string }) {
  const val = input.value.trim()
  if (val && !list.includes(val)) {
    list.push(val)
  }
  input.value = ''
}

function removeModelTag(list: string[], index: number) {
  list.splice(index, 1)
}

async function handleTest() {
  const form = editingProviderId.value ? editForm.value : addForm.value
  if (!form.endpoint.trim() || !form.apiKey.trim() || form.models.length === 0) return
  testing.value = true
  testResult.value = null
  try {
    testResult.value = await testConfig({
      endpoint: form.endpoint.trim(),
      apiKey: form.apiKey.trim(),
      model: form.models[0] ?? ''
    })
  } catch {
    testResult.value = { success: false, message: 'Request failed' }
  } finally {
    testing.value = false
  }
}

async function handleAdd() {
  const form = addForm.value
  if (!form.endpoint.trim() || !form.apiKey.trim()) return
  adding.value = true
  addError.value = null
  try {
    await addProvider({
      id: form.id || undefined,
      name: form.name.trim() || 'Provider',
      endpoint: form.endpoint.trim(),
      apiKey: form.apiKey.trim(),
      models: form.models
    })
    showAddForm.value = false
    addForm.value = { id: '', name: '', endpoint: '', apiKey: '', models: [] }
  } catch (e) {
    addError.value = e instanceof Error ? e.message : 'Failed to add'
  } finally {
    adding.value = false
  }
}

function startEdit(provider: { id: string; name: string; endpoint: string; apiKey: string; models?: readonly string[] }) {
  editingProviderId.value = provider.id
  editForm.value = {
    name: provider.name,
    endpoint: provider.endpoint,
    apiKey: '', // Leave empty, placeholder shows masked key
    models: [...(provider.models ?? [])]
  }
  editError.value = null
  testResult.value = null
}

function cancelEdit() {
  editingProviderId.value = null
  editError.value = null
  testResult.value = null
}

async function handleUpdate() {
  if (!editingProviderId.value) return
  editing.value = true
  editError.value = null
  try {
    const updates: Partial<LlmProviderInput> = {
      name: editForm.value.name.trim(),
      endpoint: editForm.value.endpoint.trim(),
      models: editForm.value.models
    }
    if (editForm.value.apiKey.trim()) {
      updates.apiKey = editForm.value.apiKey.trim()
    }
    await updateProvider(editingProviderId.value, updates)
    editingProviderId.value = null
  } catch (e) {
    editError.value = e instanceof Error ? e.message : 'Failed to update'
  } finally {
    editing.value = false
  }
}

async function handleDelete(providerId: string) {
  if (confirmDeleteId.value !== providerId) {
    confirmDeleteId.value = providerId
    return
  }
  try {
    await removeProvider(providerId)
  } catch (e) {
    console.error('Failed to delete provider:', e)
  }
  confirmDeleteId.value = null
}

function truncateEndpoint(endpoint: string): string {
  if (endpoint.length <= 40) return endpoint
  return endpoint.substring(0, 37) + '...'
}

onMounted(async () => {
  await getConfig()
})
</script>

<template>
  <div class="llm-section">
    <header class="section-header">
      <div>
        <h2 class="section-title">/llm</h2>
        <p class="section-subtitle">LLM provider configuration</p>
      </div>
    </header>

    <!-- Loading -->
    <div v-if="loading && !multiConfig" class="loading-state">Loading configuration...</div>

    <!-- Error -->
    <div v-if="error && !multiConfig" class="error-state">
      <p>{{ error }}</p>
      <button class="retry-btn" @click="getConfig">Retry</button>
    </div>

    <!-- Config Status -->
    <div v-if="multiConfig" class="status-bar" :class="multiConfig.configured ? 'status-configured' : 'status-unconfigured'">
      <span class="status-dot">&#9679;</span>
      <span v-if="multiConfig.configured">
        {{ multiConfig.providers.length }} provider(s) configured
      </span>
      <span v-else>LLM not configured</span>
    </div>

    <!-- Default Model Selector -->
    <div v-if="allModelOptions.length > 0" class="default-model">
      <label class="form-label">DEFAULT MODEL</label>
      <select v-model="defaultModelValue" class="form-select">
        <option v-for="opt in allModelOptions" :key="opt.value" :value="opt.value">
          {{ opt.label }}
        </option>
      </select>
    </div>

    <!-- Provider List -->
    <div v-if="multiConfig && multiConfig.providers.length > 0" class="provider-list">
      <div
        v-for="provider in multiConfig.providers"
        :key="provider.id"
        class="provider-card"
      >
        <!-- View mode -->
        <template v-if="editingProviderId !== provider.id">
          <div class="provider-header">
            <div class="provider-info">
              <span class="provider-name">{{ provider.name }}</span>
              <span class="provider-endpoint">{{ truncateEndpoint(provider.endpoint) }}</span>
            </div>
            <div class="provider-meta">
              <span class="model-count">{{ provider.models?.length ?? 0 }} models</span>
              <button class="icon-btn" title="Edit" @click="startEdit(provider)">
                <svg width="14" height="14" viewBox="0 0 14 14" fill="none"><path d="M10 2L12 4L5 11H3V9L10 2Z" stroke="currentColor" stroke-width="1.2" stroke-linejoin="round"/></svg>
              </button>
              <button
                class="icon-btn danger"
                :title="confirmDeleteId === provider.id ? 'Click again to confirm' : 'Delete'"
                @click="handleDelete(provider.id)"
              >
                <svg width="14" height="14" viewBox="0 0 14 14" fill="none"><path d="M3 4H11M5 4V3H9V4M4 4V12H10V4" stroke="currentColor" stroke-width="1.2" stroke-linejoin="round"/></svg>
              </button>
            </div>
          </div>
          <div class="provider-models">
            <span v-for="m in (provider.models ?? [])" :key="m" class="model-tag">{{ m }}</span>
          </div>
        </template>

        <!-- Edit mode -->
        <template v-else>
          <div class="edit-form">
            <div class="form-group">
              <label class="form-label">NAME</label>
              <input v-model="editForm.name" class="form-input" placeholder="Provider name" spellcheck="false" />
            </div>
            <div class="form-group">
              <label class="form-label">ENDPOINT</label>
              <input v-model="editForm.endpoint" class="form-input" placeholder="https://api.example.com" spellcheck="false" />
            </div>
            <div class="form-group">
              <label class="form-label">API KEY</label>
              <input v-model="editForm.apiKey" type="password" class="form-input" :placeholder="provider.apiKey || 'Leave empty to keep current'" autocomplete="off" spellcheck="false" />
            </div>
            <div class="form-group">
              <label class="form-label">MODELS</label>
              <div class="model-tags-input">
                <span v-for="(m, i) in editForm.models" :key="i" class="model-tag editable">
                  {{ m }}
                  <button class="tag-remove" @click="removeModelTag(editForm.models, i)">&times;</button>
                </span>
                <input
                  v-model="editModelInput"
                  class="tag-input"
                  placeholder="Add model..."
                  @keydown.enter.prevent="addModelTag(editForm.models, { value: editModelInput }); editModelInput = ''"
                />
              </div>
            </div>

            <!-- Test Result -->
            <div v-if="testResult" class="test-result" :class="testResult.success ? 'test-success' : 'test-fail'">
              <span class="test-icon">&#9679;</span>
              <span>{{ testResult.message }}</span>
              <span v-if="testResult.latencyMs" class="test-latency">{{ testResult.latencyMs }}ms</span>
            </div>

            <div v-if="editError" class="save-error">{{ editError }}</div>

            <div class="form-actions">
              <button class="test-btn" :disabled="!editForm.endpoint.trim() || !editForm.apiKey.trim() || editForm.models.length === 0 || testing" @click="handleTest">
                {{ testing ? 'Testing...' : 'Test' }}
              </button>
              <button class="cancel-btn" @click="cancelEdit">Cancel</button>
              <button class="save-btn" :disabled="!editForm.endpoint.trim() || editing" @click="handleUpdate">
                {{ editing ? 'Saving...' : 'Save' }}
              </button>
            </div>
          </div>
        </template>
      </div>
    </div>

    <!-- Add Provider -->
    <div class="add-provider">
      <div v-if="!showAddForm" class="presets">
        <span class="presets-label">添加 Provider：</span>
        <button
          v-for="preset in providerPresets"
          :key="preset.id"
          class="preset-btn"
          @click="applyPreset(preset.id)"
        >
          {{ preset.name }}
        </button>
      </div>

      <div v-else class="add-form">
        <div class="add-form-header">
          <span class="add-form-title">Add Provider</span>
          <button class="icon-btn" @click="showAddForm = false">
            <svg width="14" height="14" viewBox="0 0 14 14" fill="none"><path d="M3 3L11 11M11 3L3 11" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/></svg>
          </button>
        </div>

        <div class="form-group">
          <label class="form-label">NAME</label>
          <input v-model="addForm.name" class="form-input" placeholder="Provider name" spellcheck="false" />
        </div>
        <div class="form-group">
          <label class="form-label">ENDPOINT</label>
          <input v-model="addForm.endpoint" class="form-input" placeholder="https://api.example.com" spellcheck="false" />
        </div>
        <div class="form-group">
          <label class="form-label">API KEY</label>
          <input v-model="addForm.apiKey" type="password" class="form-input" placeholder="sk-xxxxxxxxxxxxxxx" autocomplete="off" spellcheck="false" />
        </div>
        <div class="form-group">
          <label class="form-label">MODELS</label>
          <div class="model-tags-input">
            <span v-for="(m, i) in addForm.models" :key="i" class="model-tag editable">
              {{ m }}
              <button class="tag-remove" @click="removeModelTag(addForm.models, i)">&times;</button>
            </span>
            <input
              v-model="newModelInput"
              class="tag-input"
              placeholder="Add model name, press Enter"
              @keydown.enter.prevent="addModelTag(addForm.models, { value: newModelInput }); newModelInput = ''"
            />
          </div>
        </div>

        <!-- Test Result -->
        <div v-if="testResult" class="test-result" :class="testResult.success ? 'test-success' : 'test-fail'">
          <span class="test-icon">&#9679;</span>
          <span>{{ testResult.message }}</span>
          <span v-if="testResult.latencyMs" class="test-latency">{{ testResult.latencyMs }}ms</span>
        </div>

        <div v-if="addError" class="save-error">{{ addError }}</div>

        <div class="form-actions">
          <button class="test-btn" :disabled="!addForm.endpoint.trim() || !addForm.apiKey.trim() || addForm.models.length === 0 || testing" @click="handleTest">
            {{ testing ? 'Testing...' : 'Test Connection' }}
          </button>
          <button class="save-btn" :disabled="!addForm.endpoint.trim() || !addForm.apiKey.trim() || adding" @click="handleAdd">
            {{ adding ? 'Adding...' : 'Add Provider' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.llm-section {
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

/* Status Bar */
.status-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
  margin-bottom: 20px;
  font-family: var(--font-mono);
  font-size: 13px;
}

.status-configured {
  background: #f0fdf4;
  border: 1px solid #bbf7d0;
  border-radius: var(--radius-md);
  color: #166534;
}

.status-configured .status-dot { color: #22c55e; }

.status-unconfigured {
  background: #fefce8;
  border: 1px solid #fef08a;
  border-radius: var(--radius-md);
  color: #854d0e;
}

.status-unconfigured .status-dot { color: #eab308; }

/* Default Model */
.default-model {
  margin-bottom: 20px;
  max-width: 400px;
}

.form-select {
  width: 100%;
  padding: 8px 10px;
  border: var(--border);
  border-radius: var(--radius-md);
  background: var(--color-white);
  font-family: var(--font-mono);
  font-size: 13px;
  cursor: pointer;
}

.form-select:focus {
  outline: none;
  border-color: var(--color-black);
  box-shadow: 0 0 0 3px rgba(0,0,0,0.03);
}

/* Provider List */
.provider-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-bottom: 24px;
}

.provider-card {
  border: var(--border);
  border-radius: var(--radius-md);
  padding: 16px;
  background: var(--color-white);
}

.provider-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.provider-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.provider-name {
  font-family: var(--font-mono);
  font-size: 14px;
  font-weight: 600;
  color: var(--color-black);
}

.provider-endpoint {
  font-family: var(--font-mono);
  font-size: 11px;
  color: var(--color-gray-dark);
}

.provider-meta {
  display: flex;
  align-items: center;
  gap: 8px;
}

.model-count {
  font-family: var(--font-mono);
  font-size: 11px;
  color: var(--color-gray-dark);
}

.icon-btn {
  width: 28px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  border-radius: var(--radius-md);
  background: transparent;
  color: var(--color-gray-dark);
  cursor: pointer;
  transition: all 0.15s ease;
}

.icon-btn:hover {
  background: var(--color-gray-bg);
  color: var(--color-black);
}

.icon-btn.danger:hover {
  background: #fef2f2;
  color: #dc2626;
}

.provider-models {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.model-tag {
  font-family: var(--font-mono);
  font-size: 11px;
  padding: 2px 8px;
  background: var(--color-gray-bg);
  border: var(--border);
  border-radius: var(--radius-sm);
  color: var(--color-gray-dark);
}

.model-tag.editable {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.tag-remove {
  border: none;
  background: none;
  padding: 0;
  font-size: 14px;
  line-height: 1;
  color: var(--color-gray-dark);
  cursor: pointer;
  opacity: 0.6;
}

.tag-remove:hover {
  opacity: 1;
  color: #dc2626;
}

/* Model Tags Input */
.model-tags-input {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  padding: 6px 10px;
  border: var(--border);
  border-radius: var(--radius-md);
  background: var(--color-white);
  min-height: 36px;
  align-items: center;
}

.model-tags-input:focus-within {
  border-color: var(--color-black);
  box-shadow: 0 0 0 3px rgba(0,0,0,0.03);
}

.tag-input {
  flex: 1;
  min-width: 120px;
  border: none;
  outline: none;
  font-family: var(--font-mono);
  font-size: 12px;
  padding: 2px 0;
  background: transparent;
}

/* Edit Form */
.edit-form {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

/* Add Provider */
.add-provider {
  margin-top: 8px;
}

.presets {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.presets-label {
  font-size: 12px;
  color: var(--color-gray-dark);
}

.preset-btn {
  padding: 4px 10px;
  border: var(--border);
  border-radius: var(--radius-md);
  background: var(--color-white);
  font-family: var(--font-mono);
  font-size: 12px;
  cursor: pointer;
  transition: all 0.15s ease;
}

.preset-btn:hover {
  border-color: var(--color-black);
  background: var(--color-gray-bg);
}

.add-form {
  border: var(--border);
  border-radius: var(--radius-md);
  padding: 16px;
  background: var(--color-white);
  display: flex;
  flex-direction: column;
  gap: 12px;
  max-width: 520px;
}

.add-form-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.add-form-title {
  font-family: var(--font-mono);
  font-size: 14px;
  font-weight: 600;
}

/* Form */
.form-group {
  display: flex;
  flex-direction: column;
  gap: 6px;
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
  width: 100%;
  box-sizing: border-box;
}

.form-input:focus {
  outline: none;
  border-color: var(--color-black);
  box-shadow: 0 0 0 3px rgba(0,0,0,0.03);
}

/* Test Result */
.test-result {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
  border-radius: var(--radius-md);
  font-size: 13px;
  font-family: var(--font-mono);
}

.test-success {
  background: #f0fdf4;
  border: 1px solid #bbf7d0;
  color: #166534;
}

.test-success .test-icon { color: #22c55e; }

.test-fail {
  background: #fef2f2;
  border: 1px solid #fecaca;
  color: #991b1b;
}

.test-fail .test-icon { color: #ef4444; }

.test-latency {
  margin-left: auto;
  opacity: 0.7;
}

/* Save states */
.save-error {
  padding: 10px 14px;
  background: #fef2f2;
  border: 1px solid #fecaca;
  border-radius: var(--radius-md);
  color: #dc2626;
  font-size: 13px;
}

/* Actions */
.form-actions {
  display: flex;
  gap: 12px;
  margin-top: 4px;
}

.test-btn,
.cancel-btn {
  padding: 8px 16px;
  border: var(--border);
  border-radius: var(--radius-md);
  background: var(--color-white);
  font-family: var(--font-mono);
  font-size: 13px;
  cursor: pointer;
}

.test-btn:hover:not(:disabled),
.cancel-btn:hover:not(:disabled) {
  background: var(--color-gray-bg);
}

.test-btn:disabled,
.cancel-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
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

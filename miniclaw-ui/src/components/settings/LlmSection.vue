<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useLlmConfig } from '@/composables/useLlmConfig'

const { config, loading, error, getConfig, saveConfig, testConfig } = useLlmConfig()

// Provider presets
const providers = [
  { id: 'deepseek', label: 'DeepSeek', endpoint: 'https://api.deepseek.com', model: 'deepseek-chat' },
  { id: 'openai', label: 'OpenAI', endpoint: 'https://api.openai.com', model: 'gpt-4o' },
  { id: 'ollama', label: 'Ollama', endpoint: 'http://localhost:11434', model: 'qwen2.5:7b' },
  { id: 'qwen', label: '通义千问', endpoint: 'https://dashscope.aliyuncs.com/compatible-mode', model: 'qwen-plus' },
  { id: 'glm', label: 'GLM', endpoint: 'https://open.bigmodel.cn/api/paas/v4', model: 'glm-4-flash' },
  { id: 'custom', label: '自定义', endpoint: '', model: '' }
]

// Form state
const endpoint = ref('')
const apiKey = ref('')
const model = ref('')
const apiKeyVisible = ref(false)

// Test state
const testing = ref(false)
const testResult = ref<{ success: boolean; message: string; latencyMs?: number } | null>(null)

// Save state
const saving = ref(false)
const saveError = ref<string | null>(null)
const saveSuccess = ref(false)

function applyPreset(providerId: string) {
  const provider = providers.find(p => p.id === providerId)
  if (provider) {
    endpoint.value = provider.endpoint
    model.value = provider.model
  }
  testResult.value = null
  saveError.value = null
  saveSuccess.value = false
}

async function handleTest() {
  if (!endpoint.value.trim() || !apiKey.value.trim() || !model.value.trim()) return
  testing.value = true
  testResult.value = null
  try {
    testResult.value = await testConfig({
      endpoint: endpoint.value.trim(),
      apiKey: apiKey.value.trim(),
      model: model.value.trim()
    })
  } catch {
    testResult.value = { success: false, message: 'Request failed' }
  } finally {
    testing.value = false
  }
}

async function handleSave() {
  if (!endpoint.value.trim() || !apiKey.value.trim() || !model.value.trim()) return
  saving.value = true
  saveError.value = null
  saveSuccess.value = false
  try {
    await saveConfig({
      endpoint: endpoint.value.trim(),
      apiKey: apiKey.value.trim(),
      model: model.value.trim()
    })
    saveSuccess.value = true
    // Reset apiKey field to show masked value
    apiKey.value = ''
    setTimeout(() => { saveSuccess.value = false }, 3000)
  } catch (e) {
    saveError.value = e instanceof Error ? e.message : 'Failed to save'
  } finally {
    saving.value = false
  }
}

onMounted(async () => {
  await getConfig()
  if (config.value) {
    endpoint.value = config.value.endpoint
    model.value = config.value.model
    // apiKey remains empty (masked on server)
  }
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
    <div v-if="loading && !config" class="loading-state">Loading configuration...</div>

    <!-- Error -->
    <div v-if="error && !config" class="error-state">
      <p>{{ error }}</p>
      <button class="retry-btn" @click="getConfig">Retry</button>
    </div>

    <!-- Config Status -->
    <div v-if="config" class="status-bar" :class="config.configured ? 'status-configured' : 'status-unconfigured'">
      <span class="status-dot">●</span>
      <span v-if="config.configured">
        LLM configured — {{ config.model }} @ {{ config.endpoint }}
      </span>
      <span v-else>LLM not configured</span>
    </div>

    <!-- Provider Presets -->
    <div class="presets">
      <span class="presets-label">快速填充：</span>
      <button
        v-for="provider in providers"
        :key="provider.id"
        class="preset-btn"
        @click="applyPreset(provider.id)"
      >
        {{ provider.label }}
      </button>
    </div>

    <!-- Config Form -->
    <div class="config-form">
      <div class="form-group">
        <label class="form-label">API Endpoint</label>
        <input
          v-model="endpoint"
          class="form-input"
          placeholder="https://api.deepseek.com"
          spellcheck="false"
        />
      </div>

      <div class="form-group">
        <label class="form-label">API Key</label>
        <div class="input-with-toggle">
          <input
            v-model="apiKey"
            :type="apiKeyVisible ? 'text' : 'password'"
            class="form-input"
            :placeholder="config?.apiKey || 'sk-xxxxxxxxxxxxxxx'"
            autocomplete="off"
            spellcheck="false"
          />
          <button
            type="button"
            class="visibility-toggle"
            @click="apiKeyVisible = !apiKeyVisible"
          >
            {{ apiKeyVisible ? '隐藏' : '显示' }}
          </button>
        </div>
        <span class="form-hint">留空则保持当前 Key 不变</span>
      </div>

      <div class="form-group">
        <label class="form-label">Model</label>
        <input
          v-model="model"
          class="form-input"
          placeholder="deepseek-chat"
          spellcheck="false"
        />
      </div>

      <!-- Test Result -->
      <div v-if="testResult" class="test-result" :class="testResult.success ? 'test-success' : 'test-fail'">
        <span class="test-icon">●</span>
        <span>{{ testResult.message }}</span>
        <span v-if="testResult.latencyMs" class="test-latency">{{ testResult.latencyMs }}ms</span>
      </div>

      <!-- Save Success -->
      <div v-if="saveSuccess" class="save-success">Configuration saved successfully</div>

      <!-- Save Error -->
      <div v-if="saveError" class="save-error">{{ saveError }}</div>

      <!-- Actions -->
      <div class="form-actions">
        <button
          class="test-btn"
          :disabled="!endpoint.trim() || !apiKey.trim() || !model.trim() || testing"
          @click="handleTest"
        >
          {{ testing ? 'Testing...' : 'Test Connection' }}
        </button>
        <button
          class="save-btn"
          :disabled="!endpoint.trim() || !model.trim() || saving"
          @click="handleSave"
        >
          {{ saving ? 'Saving...' : 'Save' }}
        </button>
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
  color: #166534;
}

.status-configured .status-dot { color: #22c55e; }

.status-unconfigured {
  background: #fefce8;
  border: 1px solid #fef08a;
  color: #854d0e;
}

.status-unconfigured .status-dot { color: #eab308; }

/* Presets */
.presets {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 20px;
  flex-wrap: wrap;
}

.presets-label {
  font-size: 12px;
  color: var(--color-gray-dark);
}

.preset-btn {
  padding: 4px 10px;
  border: var(--border);
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

/* Config Form */
.config-form {
  display: flex;
  flex-direction: column;
  gap: 16px;
  max-width: 520px;
}

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
  background: var(--color-white);
  font-family: var(--font-mono);
  font-size: 13px;
  width: 100%;
  box-sizing: border-box;
}

.form-input:focus {
  outline: none;
  border-color: var(--color-black);
}

.form-hint {
  font-size: 11px;
  color: var(--color-gray-dark);
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

/* Test Result */
.test-result {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
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
.save-success {
  padding: 10px 14px;
  background: #f0fdf4;
  border: 1px solid #bbf7d0;
  color: #166534;
  font-size: 13px;
}

.save-error {
  padding: 10px 14px;
  background: #fef2f2;
  border: 1px solid #fecaca;
  color: #dc2626;
  font-size: 13px;
}

/* Actions */
.form-actions {
  display: flex;
  gap: 12px;
  margin-top: 4px;
}

.test-btn {
  padding: 8px 16px;
  border: var(--border);
  background: var(--color-white);
  font-family: var(--font-mono);
  font-size: 13px;
  cursor: pointer;
}

.test-btn:hover:not(:disabled) {
  background: var(--color-gray-bg);
}

.test-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.save-btn {
  padding: 8px 16px;
  border: none;
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
  background: var(--color-white);
  cursor: pointer;
  font-family: var(--font-mono);
  font-size: 13px;
}

.retry-btn:hover {
  background: var(--color-gray-bg);
}
</style>

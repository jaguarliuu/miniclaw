<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useWebSocket } from '@/composables/useWebSocket'
import { useLlmConfig } from '@/composables/useLlmConfig'

const router = useRouter()
const { connect } = useWebSocket()
const { saveConfig, testConfig } = useLlmConfig()

onMounted(() => {
  connect()
})

// Wizard step
const step = ref<'provider' | 'config'>('provider')

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
const selectedProvider = ref('')
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

const isFormValid = computed(() => {
  return endpoint.value.trim() !== '' && apiKey.value.trim() !== '' && model.value.trim() !== ''
})

function selectProvider(providerId: string) {
  selectedProvider.value = providerId
  const provider = providers.find(p => p.id === providerId)
  if (provider) {
    endpoint.value = provider.endpoint
    model.value = provider.model
  }
  apiKey.value = ''
  testResult.value = null
  saveError.value = null
  step.value = 'config'
}

function goBack() {
  step.value = 'provider'
  testResult.value = null
  saveError.value = null
}

async function handleTest() {
  if (!isFormValid.value) return
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
  if (!isFormValid.value) return
  saving.value = true
  saveError.value = null
  try {
    await saveConfig({
      endpoint: endpoint.value.trim(),
      apiKey: apiKey.value.trim(),
      model: model.value.trim()
    })
    router.push('/')
  } catch (e) {
    saveError.value = e instanceof Error ? e.message : 'Failed to save'
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div class="setup-wizard">
    <div class="wizard-container">
      <!-- Step 1: Provider Selection -->
      <div v-if="step === 'provider'" class="wizard-step">
        <h1 class="wizard-title">配置大模型</h1>
        <p class="wizard-subtitle">选择你的 AI 提供商</p>

        <div class="provider-grid">
          <button
            v-for="provider in providers"
            :key="provider.id"
            class="provider-card"
            @click="selectProvider(provider.id)"
          >
            <span class="provider-name">{{ provider.label }}</span>
            <span v-if="provider.endpoint" class="provider-hint">{{ provider.endpoint }}</span>
            <span v-else class="provider-hint">自定义 OpenAI 兼容端点</span>
          </button>
        </div>
      </div>

      <!-- Step 2: Config Form -->
      <div v-if="step === 'config'" class="wizard-step">
        <div class="step-header">
          <button class="back-btn" @click="goBack">&larr; 返回</button>
          <h1 class="wizard-title">
            {{ providers.find(p => p.id === selectedProvider)?.label || '配置' }}
          </h1>
        </div>

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
                placeholder="sk-xxxxxxxxxxxxxxx"
                autocomplete="off"
                spellcheck="false"
              />
              <button
                type="button"
                class="visibility-toggle"
                @click="apiKeyVisible = !apiKeyVisible"
                :title="apiKeyVisible ? '隐藏' : '显示'"
              >
                {{ apiKeyVisible ? '隐藏' : '显示' }}
              </button>
            </div>
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
            <span class="test-icon">{{ testResult.success ? '●' : '●' }}</span>
            <span>{{ testResult.message }}</span>
            <span v-if="testResult.latencyMs" class="test-latency">{{ testResult.latencyMs }}ms</span>
          </div>

          <!-- Save Error -->
          <div v-if="saveError" class="save-error">{{ saveError }}</div>

          <!-- Actions -->
          <div class="form-actions">
            <button
              class="test-btn"
              :disabled="!isFormValid || testing"
              @click="handleTest"
            >
              {{ testing ? '测试中...' : '测试连接' }}
            </button>
            <button
              class="save-btn"
              :disabled="!isFormValid || saving"
              @click="handleSave"
            >
              {{ saving ? '保存中...' : '保存并开始' }}
            </button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.setup-wizard {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--color-white);
}

.wizard-container {
  width: 100%;
  max-width: 520px;
  padding: 40px;
}

.wizard-step {
  animation: fadeIn 0.2s ease;
}

@keyframes fadeIn {
  from { opacity: 0; transform: translateY(8px); }
  to { opacity: 1; transform: translateY(0); }
}

.wizard-title {
  font-family: var(--font-mono);
  font-size: 20px;
  font-weight: 500;
  margin-bottom: 8px;
}

.wizard-subtitle {
  font-size: 14px;
  color: var(--color-gray-dark);
  margin-bottom: 32px;
}

/* Provider Grid */
.provider-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

.provider-card {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 6px;
  padding: 16px;
  border: var(--border);
  border-radius: var(--radius-lg);
  background: var(--color-white);
  cursor: pointer;
  transition: all 0.15s ease;
  text-align: left;
}

.provider-card:hover {
  border-color: var(--color-black);
  background: var(--color-gray-bg);
  box-shadow: var(--shadow-xs);
}

.provider-name {
  font-family: var(--font-mono);
  font-size: 14px;
  font-weight: 500;
}

.provider-hint {
  font-size: 11px;
  color: var(--color-gray-dark);
  font-family: var(--font-mono);
  word-break: break-all;
}

/* Step Header */
.step-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 24px;
}

.back-btn {
  padding: 4px 8px;
  border: var(--border);
  border-radius: var(--radius-md);
  background: var(--color-white);
  font-family: var(--font-mono);
  font-size: 13px;
  cursor: pointer;
}

.back-btn:hover {
  background: var(--color-gray-bg);
}

.step-header .wizard-title {
  margin-bottom: 0;
}

/* Config Form */
.config-form {
  display: flex;
  flex-direction: column;
  gap: 16px;
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
  padding: 10px 12px;
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

.test-success .test-icon {
  color: #22c55e;
}

.test-fail {
  background: #fef2f2;
  border: 1px solid #fecaca;
  color: #991b1b;
}

.test-fail .test-icon {
  color: #ef4444;
}

.test-latency {
  margin-left: auto;
  color: inherit;
  opacity: 0.7;
}

/* Save Error */
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
  margin-top: 8px;
}

.test-btn {
  padding: 10px 20px;
  border: var(--border);
  border-radius: var(--radius-md);
  background: var(--color-white);
  font-family: var(--font-mono);
  font-size: 13px;
  cursor: pointer;
  transition: all 0.15s ease;
}

.test-btn:hover:not(:disabled) {
  background: var(--color-gray-bg);
}

.test-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.save-btn {
  flex: 1;
  padding: 10px 20px;
  border: none;
  border-radius: var(--radius-md);
  background: var(--color-black);
  color: var(--color-white);
  font-family: var(--font-mono);
  font-size: 13px;
  cursor: pointer;
  transition: opacity 0.15s ease;
}

.save-btn:hover:not(:disabled) {
  opacity: 0.9;
}

.save-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
</style>

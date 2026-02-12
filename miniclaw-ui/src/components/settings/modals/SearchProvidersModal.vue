<script setup lang="ts">
import { ref, watch } from 'vue'

interface SearchProvider {
  type: string
  displayName: string
  keyRequired: boolean
  apiKey: string
  enabled: boolean
  apiKeyUrl?: string
}

const props = defineProps<{
  providers: SearchProvider[]
}>()

const emit = defineEmits<{
  close: []
  save: [providers: { type: string; apiKey: string; enabled: boolean }[]]
}>()

const editProviders = ref<SearchProvider[]>(
  props.providers.map(p => ({ ...p, apiKey: '' }))
)
const apiKeyVisible = ref<Record<string, boolean>>({})

function handleSave() {
  emit('save', editProviders.value.map(p => ({
    type: p.type,
    apiKey: p.apiKey,
    enabled: p.enabled
  })))
}

watch(() => props.providers, (newVal) => {
  editProviders.value = newVal.map(p => ({ ...p, apiKey: '' }))
}, { deep: true })
</script>

<template>
  <div class="modal-overlay" @click.self="emit('close')">
    <div class="modal-content">
      <div class="modal-header">
        <div>
          <h3 class="modal-title">Web Search Providers</h3>
          <p class="modal-subtitle">Configure search engines for the web_search tool</p>
        </div>
        <button class="btn-close" @click="emit('close')">✕</button>
      </div>

      <div class="modal-body">
        <div class="provider-card provider-default">
          <div class="provider-header">
            <div class="provider-info">
              <span class="provider-name">DuckDuckGo (Default)</span>
              <span class="provider-hint">Free, no API key needed</span>
            </div>
            <span class="always-on-badge">Always On</span>
          </div>
        </div>

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
            <div class="api-key-header">
              <label class="form-label">API KEY</label>
              <a
                v-if="provider.apiKeyUrl"
                :href="provider.apiKeyUrl"
                target="_blank"
                rel="noopener noreferrer"
                class="get-key-link"
              >
                Get API Key →
              </a>
            </div>
            <div class="input-with-toggle">
              <input
                v-model="provider.apiKey"
                :type="apiKeyVisible[provider.type] ? 'text' : 'password'"
                class="form-input"
                :placeholder="providers.find(p => p.type === provider.type)?.apiKey || 'Enter API key'"
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

      <div class="modal-footer">
        <button class="btn-secondary" @click="emit('close')">Cancel</button>
        <button class="btn-primary" @click="handleSave">Save Changes</button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}

.modal-content {
  background: var(--color-white);
  border-radius: var(--radius-lg);
  width: 90%;
  max-width: 600px;
  max-height: 90vh;
  display: flex;
  flex-direction: column;
  box-shadow: var(--shadow-lg);
}

.modal-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  padding: 20px 24px;
  border-bottom: var(--border);
  gap: 20px;
}

.modal-title {
  font-family: var(--font-mono);
  font-size: 18px;
  font-weight: 600;
  margin: 0 0 4px 0;
}

.modal-subtitle {
  font-size: 13px;
  color: var(--color-gray-600);
  margin: 0;
}

.btn-close {
  background: none;
  border: none;
  font-size: 20px;
  cursor: pointer;
  color: var(--color-gray-400);
  padding: 4px;
  line-height: 1;
  flex-shrink: 0;
}

.btn-close:hover {
  color: var(--color-black);
}

.modal-body {
  flex: 1;
  overflow-y: auto;
  padding: 24px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.provider-card {
  border: var(--border);
  border-radius: var(--radius-lg);
  padding: 14px 16px;
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

.api-key-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 6px;
}

.form-label {
  display: block;
  font-size: 11px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  color: var(--color-gray-700);
  margin: 0;
}

.get-key-link {
  font-family: var(--font-mono);
  font-size: 11px;
  color: var(--color-blue-600);
  text-decoration: none;
  transition: all var(--duration-fast);
  display: flex;
  align-items: center;
  gap: 2px;
}

.get-key-link:hover {
  color: var(--color-blue-700);
  text-decoration: underline;
}

.input-with-toggle {
  display: flex;
  gap: 0;
}

.input-with-toggle .form-input {
  flex: 1;
  border-right: none;
  border-top-right-radius: 0;
  border-bottom-right-radius: 0;
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

.form-hint {
  display: block;
  font-size: 11px;
  color: var(--color-gray-dark);
  margin-top: 4px;
}

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
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
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

.modal-footer {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  padding: 16px 24px;
  border-top: var(--border);
  gap: 12px;
}

.btn-secondary {
  padding: 10px 16px;
  background: var(--color-white);
  border: var(--border);
  border-radius: var(--radius-md);
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all var(--duration-fast);
}

.btn-secondary:hover {
  background: var(--color-gray-100);
}

.btn-primary {
  padding: 10px 16px;
  background: var(--color-black);
  color: var(--color-white);
  border: none;
  border-radius: var(--radius-md);
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all var(--duration-fast);
}

.btn-primary:hover {
  background: var(--color-gray-800);
}
</style>

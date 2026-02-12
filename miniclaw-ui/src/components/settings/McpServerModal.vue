<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { useMcpServers, type McpServer } from '@/composables/useMcpServers'
import Select from '@/components/common/Select.vue'
import type { SelectOption } from '@/components/common/Select.vue'

const props = defineProps<{
  mode: 'create' | 'edit'
  server: McpServer | null
}>()

const emit = defineEmits(['close', 'success'])

const { testConnection, createServer, updateServer } = useMcpServers()

const activeTab = ref<'basic' | 'connection' | 'advanced'>('basic')

const transportOptions: SelectOption<string>[] = [
  { label: 'STDIO (Local Process)', value: 'STDIO' },
  { label: 'SSE (Server-Sent Events)', value: 'SSE' },
  { label: 'HTTP (REST API)', value: 'HTTP' }
]

const config = ref<Partial<McpServer>>({
  name: props.server?.name || '',
  transportType: props.server?.transportType || 'STDIO',
  command: props.server?.command || '',
  args: props.server?.args || [],
  url: props.server?.url || '',
  workingDir: props.server?.workingDir || '',
  env: props.server?.env || [],
  enabled: props.server?.enabled !== false,
  toolPrefix: props.server?.toolPrefix || '',
  requiresHitl: props.server?.requiresHitl || false,
  hitlTools: props.server?.hitlTools || [],
  requestTimeoutSeconds: props.server?.requestTimeoutSeconds || 30
})

const testing = ref(false)
const saving = ref(false)
const testResult = ref<{ success: boolean; message: string } | null>(null)

const canTest = computed(() => {
  if (!config.value.name) return false
  if (config.value.transportType === 'STDIO') {
    return !!config.value.command
  }
  return !!config.value.url
})

const canSave = computed(() => {
  return testResult.value?.success === true
})

async function handleTestConnection() {
  testing.value = true
  testResult.value = null

  try {
    testResult.value = await testConnection(config.value)
  } catch (err) {
    testResult.value = { success: false, message: 'Connection test failed' }
  } finally {
    testing.value = false
  }
}

async function handleSave() {
  saving.value = true

  try {
    let success = false
    if (props.mode === 'create') {
      success = await createServer(config.value)
    } else if (props.server) {
      success = await updateServer(props.server.id, config.value)
    }

    if (success) {
      emit('success')
    }
  } finally {
    saving.value = false
  }
}

function addArg() {
  if (!config.value.args) config.value.args = []
  config.value.args.push('')
}

function removeArg(index: number) {
  config.value.args?.splice(index, 1)
}

function addEnv() {
  if (!config.value.env) config.value.env = []
  config.value.env.push('')
}

function removeEnv(index: number) {
  config.value.env?.splice(index, 1)
}

// Reset test result when config changes
watch(config, () => {
  testResult.value = null
}, { deep: true })
</script>

<template>
  <div class="modal-overlay" @click.self="emit('close')">
    <div class="modal-content">
      <div class="modal-header">
        <h3>{{ mode === 'create' ? 'Add' : 'Edit' }} MCP Server</h3>
        <button class="btn-close" @click="emit('close')">✕</button>
      </div>

      <div class="tabs">
        <button
          class="tab"
          :class="{ active: activeTab === 'basic' }"
          @click="activeTab = 'basic'"
        >
          Basic Info
        </button>
        <button
          class="tab"
          :class="{ active: activeTab === 'connection' }"
          @click="activeTab = 'connection'"
        >
          Connection
        </button>
        <button
          class="tab"
          :class="{ active: activeTab === 'advanced' }"
          @click="activeTab = 'advanced'"
        >
          Advanced
        </button>
      </div>

      <div class="modal-body">
        <!-- Tab 1: Basic Info -->
        <div v-show="activeTab === 'basic'" class="tab-content">
          <div class="form-group">
            <label class="form-label">Name <span class="required">*</span></label>
            <input
              v-model="config.name"
              class="form-input"
              placeholder="my-server"
            />
            <p class="form-help">Unique identifier for this MCP server</p>
          </div>

          <div class="form-group">
            <label class="form-label">Transport Type <span class="required">*</span></label>
            <Select
              v-model="config.transportType"
              :options="transportOptions"
            />
            <p class="form-help">
              How to connect to the MCP server
            </p>
          </div>

          <div class="form-group">
            <label class="form-label">Tool Prefix</label>
            <input
              v-model="config.toolPrefix"
              class="form-input"
              placeholder="fs_"
            />
            <p class="form-help">Prefix for all tools to avoid naming conflicts (e.g., 'fs_' → 'fs_read_file')</p>
          </div>
        </div>

        <!-- Tab 2: Connection Settings -->
        <div v-show="activeTab === 'connection'" class="tab-content">
          <div v-if="config.transportType === 'STDIO'">
            <div class="form-group">
              <label class="form-label">Command <span class="required">*</span></label>
              <input
                v-model="config.command"
                class="form-input"
                placeholder="npx"
              />
              <p class="form-help">Executable command (e.g., npx, python, node)</p>
            </div>

            <div class="form-group">
              <label class="form-label">Arguments</label>
              <div v-for="(arg, index) in config.args" :key="index" class="array-input">
                <input
                  v-model="config.args![index]"
                  class="form-input"
                  placeholder="Argument"
                />
                <button class="btn-remove" @click="removeArg(index)">✕</button>
              </div>
              <button class="btn-add" @click="addArg">+ Add Argument</button>
              <p class="form-help">Command-line arguments for the server</p>
            </div>

            <div class="form-group">
              <label class="form-label">Working Directory</label>
              <input
                v-model="config.workingDir"
                class="form-input"
                placeholder="/path/to/working/dir"
              />
            </div>

            <div class="form-group">
              <label class="form-label">Environment Variables</label>
              <div v-for="(env, index) in config.env" :key="index" class="array-input">
                <input
                  v-model="config.env![index]"
                  class="form-input"
                  placeholder="KEY=value"
                />
                <button class="btn-remove" @click="removeEnv(index)">✕</button>
              </div>
              <button class="btn-add" @click="addEnv">+ Add Variable</button>
              <p class="form-help">Environment variables in KEY=value format</p>
            </div>
          </div>

          <div v-else>
            <div class="form-group">
              <label class="form-label">Server URL <span class="required">*</span></label>
              <input
                v-model="config.url"
                class="form-input"
                placeholder="http://localhost:3000/sse"
              />
              <p class="form-help">
                {{ config.transportType === 'SSE' ? 'SSE endpoint URL' : 'HTTP endpoint URL' }}
              </p>
            </div>
          </div>
        </div>

        <!-- Tab 3: Advanced -->
        <div v-show="activeTab === 'advanced'" class="tab-content">
          <div class="form-group">
            <label class="form-label">Request Timeout (seconds)</label>
            <input
              v-model.number="config.requestTimeoutSeconds"
              type="number"
              min="5"
              max="300"
              class="form-input"
            />
            <p class="form-help">Maximum time to wait for responses (5-300 seconds)</p>
          </div>

          <div class="form-group">
            <label class="form-checkbox">
              <input v-model="config.requiresHitl" type="checkbox" />
              <span>Require confirmation for all tools</span>
            </label>
            <p class="form-help">If enabled, all tools from this server will require user confirmation</p>
          </div>

          <div class="form-group">
            <label class="form-checkbox">
              <input v-model="config.enabled" type="checkbox" />
              <span>Enable on save</span>
            </label>
            <p class="form-help">If enabled, server will connect immediately</p>
          </div>
        </div>
      </div>

      <div class="modal-footer">
        <div class="footer-left">
          <button
            class="btn-secondary"
            :disabled="!canTest || testing"
            @click="handleTestConnection"
          >
            {{ testing ? 'Testing...' : 'Test Connection' }}
          </button>

          <div v-if="testResult" class="test-result" :class="{ success: testResult.success, error: !testResult.success }">
            <span class="result-icon">{{ testResult.success ? '✓' : '✗' }}</span>
            {{ testResult.message }}
          </div>
        </div>

        <div class="footer-right">
          <button class="btn-secondary" @click="emit('close')">
            Cancel
          </button>
          <button
            class="btn-primary"
            :disabled="!canSave || saving"
            @click="handleSave"
          >
            {{ saving ? 'Saving...' : 'Save' }}
          </button>
        </div>
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
  align-items: center;
  justify-content: space-between;
  padding: 20px 24px;
  border-bottom: var(--border);
}

.modal-header h3 {
  font-size: 18px;
  font-weight: 600;
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
}

.btn-close:hover {
  color: var(--color-black);
}

.tabs {
  display: flex;
  border-bottom: var(--border);
  padding: 0 24px;
}

.tab {
  padding: 12px 16px;
  border: none;
  background: none;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  color: var(--color-gray-600);
  border-bottom: 2px solid transparent;
  transition: all var(--duration-fast);
}

.tab:hover {
  color: var(--color-black);
}

.tab.active {
  color: var(--color-black);
  border-bottom-color: var(--color-black);
}

.modal-body {
  flex: 1;
  overflow-y: auto;
  padding: 24px;
}

.tab-content {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.form-group {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.form-label {
  font-size: 14px;
  font-weight: 500;
  color: var(--color-gray-700);
}

.required {
  color: var(--color-red-500);
}

.form-input {
  padding: 10px 12px;
  border: var(--border);
  border-radius: var(--radius-md);
  font-size: 14px;
  font-family: inherit;
  transition: border-color var(--duration-fast);
}

.form-input:focus {
  outline: none;
  border-color: var(--color-black);
}

.form-help {
  font-size: 12px;
  color: var(--color-gray-500);
  margin: 0;
}

.array-input {
  display: flex;
  gap: 8px;
}

.array-input .form-input {
  flex: 1;
}

.btn-remove {
  padding: 10px 12px;
  background: var(--color-red-50);
  border: 1px solid var(--color-red-200);
  border-radius: var(--radius-md);
  color: var(--color-red-600);
  cursor: pointer;
  font-size: 14px;
  transition: all var(--duration-fast);
}

.btn-remove:hover {
  background: var(--color-red-100);
}

.btn-add {
  padding: 8px 12px;
  background: var(--color-gray-100);
  border: var(--border);
  border-radius: var(--radius-md);
  font-size: 13px;
  cursor: pointer;
  transition: all var(--duration-fast);
  align-self: flex-start;
}

.btn-add:hover {
  background: var(--color-gray-200);
}

.form-checkbox {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  font-size: 14px;
}

.form-checkbox input[type="checkbox"] {
  cursor: pointer;
}

.modal-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 24px;
  border-top: var(--border);
}

.footer-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.footer-right {
  display: flex;
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

.btn-secondary:hover:not(:disabled) {
  background: var(--color-gray-100);
}

.btn-secondary:disabled {
  opacity: 0.5;
  cursor: not-allowed;
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

.btn-primary:hover:not(:disabled) {
  background: var(--color-gray-800);
}

.btn-primary:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.test-result {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 12px;
  border-radius: var(--radius-md);
  font-size: 13px;
}

.test-result.success {
  background: var(--color-green-50);
  color: var(--color-green-700);
}

.test-result.error {
  background: var(--color-red-50);
  color: var(--color-red-700);
}

.result-icon {
  font-weight: bold;
}
</style>

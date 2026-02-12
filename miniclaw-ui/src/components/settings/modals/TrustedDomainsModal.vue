<script setup lang="ts">
import { ref, watch } from 'vue'

const props = defineProps<{
  defaultDomains: string[]
  userDomains: string[]
}>()

const emit = defineEmits<{
  close: []
  save: [domains: string[]]
}>()

const editUserDomains = ref<string[]>([...props.userDomains])
const newDomain = ref('')

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

function handleSave() {
  emit('save', editUserDomains.value)
}

watch(() => props.userDomains, (newVal) => {
  editUserDomains.value = [...newVal]
}, { deep: true })
</script>

<template>
  <div class="modal-overlay" @click.self="emit('close')">
    <div class="modal-content">
      <div class="modal-header">
        <div>
          <h3 class="modal-title">HTTP Trusted Domains</h3>
          <p class="modal-subtitle">Only these domains are allowed for http_get requests</p>
        </div>
        <button class="btn-close" @click="emit('close')">✕</button>
      </div>

      <div class="modal-body">
        <div class="domain-group">
          <label class="form-label">DEFAULT DOMAINS</label>
          <p class="form-help">These domains are always allowed</p>
          <div class="pill-list">
            <span v-for="d in defaultDomains" :key="d" class="pill pill-default">
              {{ d }}
            </span>
          </div>
        </div>

        <div class="domain-group">
          <label class="form-label">YOUR DOMAINS</label>
          <p class="form-help">Add custom domains to allow</p>
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
  gap: 24px;
}

.domain-group {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.form-label {
  display: block;
  font-size: 11px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  color: var(--color-gray-700);
}

.form-help {
  font-size: 12px;
  color: var(--color-gray-600);
  margin: 0;
}

.pill-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
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
  font-style: italic;
}

.domain-add-row {
  display: flex;
  gap: 8px;
}

.domain-input {
  flex: 1;
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

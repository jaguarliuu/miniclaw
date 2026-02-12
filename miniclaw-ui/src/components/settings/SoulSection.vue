<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useSoulConfig, type SoulConfig } from '@/composables/useSoulConfig'

const { config, loading, error, fetchConfig, saveConfig } = useSoulConfig()

const editConfig = ref<SoulConfig>({
  agentName: '',
  personality: '',
  traits: [],
  responseStyle: 'balanced',
  detailLevel: 'balanced',
  expertise: [],
  forbiddenTopics: [],
  customPrompt: '',
  enabled: true
})

const newTrait = ref('')
const newExpertise = ref('')
const newForbiddenTopic = ref('')

const saving = ref(false)
const saveSuccess = ref(false)
const saveError = ref<string | null>(null)

const responseStyleOptions = [
  { value: 'formal', label: 'Formal' },
  { value: 'balanced', label: 'Balanced' },
  { value: 'casual', label: 'Casual' }
]

const detailLevelOptions = [
  { value: 'concise', label: 'Concise' },
  { value: 'balanced', label: 'Balanced' },
  { value: 'detailed', label: 'Detailed' }
]

function syncFormFromConfig() {
  if (!config.value) return
  editConfig.value = {
    ...config.value,
    traits: [...(config.value.traits || [])],
    expertise: [...(config.value.expertise || [])],
    forbiddenTopics: [...(config.value.forbiddenTopics || [])]
  }
}

function addTrait() {
  const trait = newTrait.value.trim()
  if (!trait) return
  if (editConfig.value.traits.includes(trait)) return
  editConfig.value.traits.push(trait)
  newTrait.value = ''
}

function removeTrait(trait: string) {
  editConfig.value.traits = editConfig.value.traits.filter(t => t !== trait)
}

function addExpertise() {
  const expertise = newExpertise.value.trim()
  if (!expertise) return
  if (editConfig.value.expertise.includes(expertise)) return
  editConfig.value.expertise.push(expertise)
  newExpertise.value = ''
}

function removeExpertise(expertise: string) {
  editConfig.value.expertise = editConfig.value.expertise.filter(e => e !== expertise)
}

function addForbiddenTopic() {
  const topic = newForbiddenTopic.value.trim()
  if (!topic) return
  if (editConfig.value.forbiddenTopics.includes(topic)) return
  editConfig.value.forbiddenTopics.push(topic)
  newForbiddenTopic.value = ''
}

function removeForbiddenTopic(topic: string) {
  editConfig.value.forbiddenTopics = editConfig.value.forbiddenTopics.filter(t => t !== topic)
}

async function handleSave() {
  saving.value = true
  saveError.value = null
  saveSuccess.value = false

  try {
    await saveConfig(editConfig.value)
    saveSuccess.value = true
    setTimeout(() => {
      saveSuccess.value = false
    }, 3000)
  } catch (e) {
    saveError.value = e instanceof Error ? e.message : 'Failed to save'
  } finally {
    saving.value = false
  }
}

onMounted(async () => {
  await fetchConfig()
  syncFormFromConfig()
})
</script>

<template>
  <div class="soul-section">
    <header class="section-header">
      <div>
        <h2 class="section-title">Soul</h2>
        <p class="section-subtitle">Configure your agent's personality and response style</p>
      </div>
    </header>

    <div v-if="loading && !config" class="loading-state">Loading configuration...</div>

    <div v-if="error && !config" class="error-state">
      <p>{{ error }}</p>
      <button class="retry-btn" @click="fetchConfig">Retry</button>
    </div>

    <template v-if="config">
      <div class="config-blocks">
        <!-- Identity -->
        <div class="config-block">
          <h3 class="block-title">Identity</h3>
          <p class="block-desc">Define who your agent is</p>

          <div class="form-group">
            <label class="form-label">Agent Name</label>
            <input
              v-model="editConfig.agentName"
              class="form-input"
              placeholder="e.g., MiniClaw"
            />
            <p class="form-help">The name your agent will identify as</p>
          </div>

          <div class="form-group">
            <label class="form-label">Personality</label>
            <textarea
              v-model="editConfig.personality"
              class="form-textarea"
              rows="4"
              placeholder="Describe your agent's personality in detail..."
            />
            <p class="form-help">A comprehensive description of your agent's character and approach</p>
          </div>
        </div>

        <!-- Traits -->
        <div class="config-block">
          <h3 class="block-title">Key Traits</h3>
          <p class="block-desc">Defining characteristics of your agent</p>

          <div class="pill-list" v-if="editConfig.traits.length > 0">
            <span v-for="trait in editConfig.traits" :key="trait" class="pill pill-trait">
              {{ trait }}
              <button class="pill-remove" @click="removeTrait(trait)">&times;</button>
            </span>
          </div>
          <div v-else class="empty-hint">No traits added</div>

          <div class="add-row">
            <input
              v-model="newTrait"
              class="form-input"
              placeholder="e.g., Professional, Friendly, Humorous"
              @keydown.enter.prevent="addTrait"
            />
            <button class="add-btn" @click="addTrait" :disabled="!newTrait.trim()">+ Add</button>
          </div>
        </div>

        <!-- Response Style -->
        <div class="config-block">
          <h3 class="block-title">Response Style</h3>
          <p class="block-desc">How your agent communicates</p>

          <div class="form-group">
            <label class="form-label">Tone</label>
            <div class="radio-group">
              <label
                v-for="option in responseStyleOptions"
                :key="option.value"
                class="radio-option"
              >
                <input
                  type="radio"
                  :value="option.value"
                  v-model="editConfig.responseStyle"
                />
                <span>{{ option.label }}</span>
              </label>
            </div>
          </div>

          <div class="form-group">
            <label class="form-label">Detail Level</label>
            <div class="radio-group">
              <label
                v-for="option in detailLevelOptions"
                :key="option.value"
                class="radio-option"
              >
                <input
                  type="radio"
                  :value="option.value"
                  v-model="editConfig.detailLevel"
                />
                <span>{{ option.label }}</span>
              </label>
            </div>
          </div>
        </div>

        <!-- Expertise -->
        <div class="config-block">
          <h3 class="block-title">Areas of Expertise</h3>
          <p class="block-desc">Domains where your agent excels</p>

          <div class="pill-list" v-if="editConfig.expertise.length > 0">
            <span v-for="area in editConfig.expertise" :key="area" class="pill pill-expertise">
              {{ area }}
              <button class="pill-remove" @click="removeExpertise(area)">&times;</button>
            </span>
          </div>
          <div v-else class="empty-hint">No expertise areas added</div>

          <div class="add-row">
            <input
              v-model="newExpertise"
              class="form-input"
              placeholder="e.g., Programming, AI, System Architecture"
              @keydown.enter.prevent="addExpertise"
            />
            <button class="add-btn" @click="addExpertise" :disabled="!newExpertise.trim()">+ Add</button>
          </div>
        </div>

        <!-- Forbidden Topics -->
        <div class="config-block">
          <h3 class="block-title">Forbidden Topics</h3>
          <p class="block-desc">Topics your agent should avoid</p>

          <div class="pill-list" v-if="editConfig.forbiddenTopics.length > 0">
            <span v-for="topic in editConfig.forbiddenTopics" :key="topic" class="pill pill-forbidden">
              {{ topic }}
              <button class="pill-remove" @click="removeForbiddenTopic(topic)">&times;</button>
            </span>
          </div>
          <div v-else class="empty-hint">No forbidden topics</div>

          <div class="add-row">
            <input
              v-model="newForbiddenTopic"
              class="form-input"
              placeholder="Topics to avoid discussing"
              @keydown.enter.prevent="addForbiddenTopic"
            />
            <button class="add-btn" @click="addForbiddenTopic" :disabled="!newForbiddenTopic.trim()">+ Add</button>
          </div>
        </div>

        <!-- Custom Prompt -->
        <div class="config-block">
          <h3 class="block-title">Custom Prompt</h3>
          <p class="block-desc">Additional instructions for your agent</p>

          <textarea
            v-model="editConfig.customPrompt"
            class="form-textarea"
            rows="6"
            placeholder="Add any custom instructions or guidelines..."
          />
        </div>
      </div>

      <!-- Save Success -->
      <div v-if="saveSuccess" class="save-success">Soul configuration saved successfully</div>

      <!-- Save Error -->
      <div v-if="saveError" class="save-error">{{ saveError }}</div>

      <!-- Save Button -->
      <div class="form-actions">
        <button
          class="save-btn"
          :disabled="saving"
          @click="handleSave"
        >
          {{ saving ? 'Saving...' : 'Save Configuration' }}
        </button>
      </div>
    </template>
  </div>
</template>

<style scoped>
.soul-section {
  height: 100%;
  overflow-y: auto;
  padding: 24px 32px;
}

.section-header {
  margin-bottom: 24px;
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

.config-blocks {
  max-width: 800px;
}

.config-block {
  margin-bottom: 28px;
  padding-bottom: 28px;
  border-bottom: var(--border-light);
}

.config-block:last-child {
  border-bottom: none;
}

.block-title {
  font-family: var(--font-mono);
  font-size: 14px;
  font-weight: 600;
  margin-bottom: 4px;
}

.block-desc {
  font-size: 13px;
  color: var(--color-gray-dark);
  margin-bottom: 16px;
}

.form-group {
  margin-bottom: 16px;
}

.form-label {
  display: block;
  font-size: 13px;
  font-weight: 500;
  color: var(--color-gray-700);
  margin-bottom: 6px;
}

.form-input,
.form-textarea {
  width: 100%;
  padding: 10px 12px;
  border: var(--border);
  border-radius: var(--radius-md);
  background: var(--color-white);
  font-family: inherit;
  font-size: 14px;
  box-sizing: border-box;
}

.form-input:focus,
.form-textarea:focus {
  outline: none;
  border-color: var(--color-black);
  box-shadow: 0 0 0 3px rgba(0, 0, 0, 0.03);
}

.form-textarea {
  resize: vertical;
  line-height: 1.5;
}

.form-help {
  font-size: 12px;
  color: var(--color-gray-600);
  margin: 4px 0 0 0;
}

.radio-group {
  display: flex;
  gap: 16px;
  flex-wrap: wrap;
}

.radio-option {
  display: flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
  font-size: 14px;
}

.radio-option input[type="radio"] {
  cursor: pointer;
}

.pill-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 12px;
}

.pill {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  border-radius: var(--radius-sm);
  font-family: var(--font-mono);
  font-size: 13px;
}

.pill-trait {
  background: #eff6ff;
  border: 1px solid #bfdbfe;
  color: #1e40af;
}

.pill-expertise {
  background: #f0fdf4;
  border: 1px solid #bbf7d0;
  color: #166534;
}

.pill-forbidden {
  background: #fef2f2;
  border: 1px solid #fecaca;
  color: #dc2626;
}

.pill-remove {
  background: none;
  border: none;
  cursor: pointer;
  font-size: 16px;
  line-height: 1;
  color: inherit;
  padding: 0;
  opacity: 0.7;
}

.pill-remove:hover {
  opacity: 1;
}

.empty-hint {
  font-size: 13px;
  color: var(--color-gray-500);
  font-style: italic;
  margin-bottom: 12px;
}

.add-row {
  display: flex;
  gap: 8px;
}

.add-btn {
  padding: 10px 14px;
  border: var(--border);
  border-radius: var(--radius-md);
  background: var(--color-white);
  font-family: var(--font-mono);
  font-size: 13px;
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

.save-success {
  padding: 10px 14px;
  background: #f0fdf4;
  border: 1px solid #bbf7d0;
  border-radius: var(--radius-md);
  color: #166534;
  font-size: 13px;
  margin-bottom: 16px;
}

.save-error {
  padding: 10px 14px;
  background: #fef2f2;
  border: 1px solid #fecaca;
  border-radius: var(--radius-md);
  color: #dc2626;
  font-size: 13px;
  margin-bottom: 16px;
}

.form-actions {
  margin-top: 24px;
}

.save-btn {
  padding: 10px 20px;
  border: none;
  border-radius: var(--radius-md);
  background: var(--color-black);
  color: var(--color-white);
  font-family: var(--font-mono);
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all var(--duration-fast);
}

.save-btn:hover:not(:disabled) {
  background: var(--color-gray-800);
}

.save-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
</style>

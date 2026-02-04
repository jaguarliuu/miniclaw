<script setup lang="ts">
import { ref } from 'vue'

const props = defineProps<{
  disabled: boolean
  isRunning?: boolean
}>()

const emit = defineEmits<{
  send: [message: string]
  cancel: []
}>()

const input = ref('')
const inputRef = ref<HTMLTextAreaElement | null>(null)

function handleSubmit() {
  if (!input.value.trim() || props.disabled) return

  emit('send', input.value.trim())
  input.value = ''

  // Reset textarea height
  if (inputRef.value) {
    inputRef.value.style.height = 'auto'
  }
}

function handleCancel() {
  emit('cancel')
}

function handleKeydown(e: KeyboardEvent) {
  // Enter to submit (Shift+Enter for new line)
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    handleSubmit()
  }
  // Escape to cancel
  if (e.key === 'Escape' && props.isRunning) {
    handleCancel()
  }
}

function handleInput(e: Event) {
  const target = e.target as HTMLTextAreaElement
  // Auto-resize
  target.style.height = 'auto'
  target.style.height = Math.min(target.scrollHeight, 200) + 'px'
}
</script>

<template>
  <div class="input-area">
    <div class="input-container">
      <textarea
        ref="inputRef"
        v-model="input"
        :disabled="disabled"
        placeholder="Type your message..."
        rows="1"
        @keydown="handleKeydown"
        @input="handleInput"
      ></textarea>

      <!-- Cancel button when running -->
      <button
        v-if="isRunning"
        class="cancel-btn"
        @click="handleCancel"
        title="Cancel (Esc)"
      >
        <span class="icon">x</span>
      </button>

      <!-- Send button -->
      <button
        v-else
        class="send-btn"
        :disabled="disabled || !input.trim()"
        @click="handleSubmit"
      >
        <span class="arrow">→</span>
      </button>
    </div>
    <div class="input-hint">
      <template v-if="isRunning">
        <span class="running">Running...</span>
        <span class="separator">·</span>
        <span>Esc to cancel</span>
      </template>
      <template v-else>
        <span>Enter to send</span>
        <span class="separator">·</span>
        <span>Shift+Enter for new line</span>
      </template>
    </div>
  </div>
</template>

<style scoped>
.input-area {
  padding: 24px 48px 32px;
  border-top: var(--border);
  background: var(--color-white);
}

.input-container {
  max-width: 720px;
  margin: 0 auto;
  display: flex;
  gap: 12px;
  align-items: flex-end;
}

textarea {
  flex: 1;
  padding: 14px 0;
  border: none;
  border-bottom: 2px solid var(--color-black);
  background: transparent;
  font-family: var(--font-ui);
  font-size: 15px;
  line-height: 1.5;
  resize: none;
  outline: none;
  transition: border-color 0.2s ease;
}

textarea::placeholder {
  color: var(--color-gray-dark);
}

textarea:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

textarea:focus {
  border-bottom-color: var(--color-black);
}

.send-btn {
  width: 44px;
  height: 44px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: var(--border);
  background: var(--color-black);
  color: var(--color-white);
  cursor: pointer;
  transition: all 0.15s ease;
  flex-shrink: 0;
}

.send-btn:hover:not(:disabled) {
  background: var(--color-white);
  color: var(--color-black);
}

.send-btn:disabled {
  background: var(--color-gray-light);
  border-color: var(--color-gray-light);
  cursor: not-allowed;
}

.cancel-btn {
  width: 44px;
  height: 44px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 2px solid var(--color-black);
  background: var(--color-white);
  color: var(--color-black);
  cursor: pointer;
  transition: all 0.15s ease;
  flex-shrink: 0;
}

.cancel-btn:hover {
  background: var(--color-black);
  color: var(--color-white);
}

.cancel-btn .icon {
  font-family: var(--font-mono);
  font-size: 16px;
  font-weight: 600;
}

.arrow {
  font-family: var(--font-mono);
  font-size: 18px;
  font-weight: 500;
}

.input-hint {
  max-width: 720px;
  margin: 8px auto 0;
  font-family: var(--font-mono);
  font-size: 11px;
  color: var(--color-gray-dark);
  display: flex;
  gap: 8px;
}

.separator {
  opacity: 0.5;
}

.running {
  animation: pulse 1.5s ease-in-out infinite;
}

@keyframes pulse {
  0%, 100% {
    opacity: 1;
  }
  50% {
    opacity: 0.4;
  }
}
</style>

<script setup lang="ts">
import { ref, watch } from 'vue'

const props = defineProps<{
  visible: boolean
  title?: string
  message: string
  confirmText?: string
  cancelText?: string
  danger?: boolean
}>()

const emit = defineEmits<{
  confirm: []
  cancel: []
}>()

const dialogRef = ref<HTMLDialogElement | null>(null)

watch(() => props.visible, (visible) => {
  if (visible) {
    dialogRef.value?.showModal()
  } else {
    dialogRef.value?.close()
  }
})

function handleConfirm() {
  emit('confirm')
}

function handleCancel() {
  emit('cancel')
}

function handleBackdropClick(e: MouseEvent) {
  if (e.target === dialogRef.value) {
    emit('cancel')
  }
}

function handleKeydown(e: KeyboardEvent) {
  if (e.key === 'Escape') {
    emit('cancel')
  }
}
</script>

<template>
  <dialog
    ref="dialogRef"
    class="confirm-dialog"
    @click="handleBackdropClick"
    @keydown="handleKeydown"
  >
    <div class="dialog-content">
      <header v-if="title" class="dialog-header">
        <h3>{{ title }}</h3>
      </header>

      <div class="dialog-body">
        <p>{{ message }}</p>
      </div>

      <footer class="dialog-footer">
        <button class="btn btn-cancel" @click="handleCancel">
          {{ cancelText || 'Cancel' }}
        </button>
        <button
          class="btn btn-confirm"
          :class="{ danger }"
          @click="handleConfirm"
        >
          {{ confirmText || 'Confirm' }}
        </button>
      </footer>
    </div>
  </dialog>
</template>

<style scoped>
.confirm-dialog {
  padding: 0;
  border: var(--border);
  background: var(--color-white);
  max-width: 400px;
  width: 90%;
  margin: auto;
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-float);
}

.confirm-dialog::backdrop {
  background: rgba(0, 0, 0, 0.5);
}

.dialog-content {
  padding: 24px;
}

.dialog-header {
  margin-bottom: 16px;
}

.dialog-header h3 {
  font-family: var(--font-mono);
  font-size: 13px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  margin: 0;
}

.dialog-body {
  margin-bottom: 24px;
}

.dialog-body p {
  font-size: 14px;
  line-height: 1.5;
  margin: 0;
  color: var(--color-gray-dark);
}

.dialog-footer {
  display: flex;
  gap: 12px;
  justify-content: flex-end;
}

.btn {
  padding: 10px 20px;
  border: var(--border);
  font-family: var(--font-mono);
  font-size: 12px;
  font-weight: 500;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  cursor: pointer;
  border-radius: var(--radius-md);
  transition: all 0.15s ease;
}

.btn-cancel {
  background: var(--color-white);
  color: var(--color-black);
}

.btn-cancel:hover {
  background: var(--color-gray-bg);
}

.btn-confirm {
  background: var(--color-black);
  color: var(--color-white);
}

.btn-confirm:hover {
  opacity: 0.8;
}

.btn-confirm.danger {
  background: var(--color-error);
  border-color: var(--color-error);
}

.btn-confirm.danger:hover {
  background: #b33;
  border-color: #b33;
}
</style>

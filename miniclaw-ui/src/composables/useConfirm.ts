import { ref, reactive } from 'vue'

export interface ConfirmOptions {
  title?: string
  message: string
  confirmText?: string
  cancelText?: string
  danger?: boolean
}

interface ConfirmState {
  visible: boolean
  title?: string
  message: string
  confirmText?: string
  cancelText?: string
  danger?: boolean
  resolve: ((value: boolean) => void) | null
}

const state = reactive<ConfirmState>({
  visible: false,
  title: undefined,
  message: '',
  confirmText: undefined,
  cancelText: undefined,
  danger: false,
  resolve: null
})

export function useConfirm() {
  function confirm(options: ConfirmOptions): Promise<boolean> {
    return new Promise((resolve) => {
      state.visible = true
      state.title = options.title
      state.message = options.message
      state.confirmText = options.confirmText
      state.cancelText = options.cancelText
      state.danger = options.danger ?? false
      state.resolve = resolve
    })
  }

  function handleConfirm() {
    state.visible = false
    state.resolve?.(true)
    state.resolve = null
  }

  function handleCancel() {
    state.visible = false
    state.resolve?.(false)
    state.resolve = null
  }

  return {
    state,
    confirm,
    handleConfirm,
    handleCancel
  }
}

import { ref } from 'vue'
import type { AttachedContext, ContextType } from '@/types'

const ALLOWED_EXTENSIONS = ['.pdf', '.docx', '.txt', '.md', '.xlsx', '.pptx', '.csv', '.json', '.yaml', '.yml', '.xml', '.html']
const MAX_SIZE_MB = 20
const MAX_SIZE_BYTES = MAX_SIZE_MB * 1024 * 1024

const contexts = ref<AttachedContext[]>([])
const error = ref<string | null>(null)

export function useContext() {

  /**
   * 添加非文件类型的上下文（Folder、Web 等）
   */
  function addContext(type: ContextType, displayName: string, data: Record<string, unknown>): AttachedContext {
    const id = `context-${Date.now()}-${Math.random().toString(36).substr(2, 6)}`

    const context: AttachedContext = {
      id,
      type,
      displayName,
      ...data
    }

    contexts.value.push(context)
    return context
  }

  /**
   * 上传文件到 workspace 并添加到上下文列表。
   * 返回后文件已落盘，Agent 可通过 read_file 读取。
   */
  async function uploadFile(file: File): Promise<AttachedContext | null> {
    error.value = null

    // 客户端校验
    const ext = '.' + file.name.split('.').pop()?.toLowerCase()
    if (!ALLOWED_EXTENSIONS.includes(ext)) {
      error.value = `File type not allowed: ${ext}. Allowed: ${ALLOWED_EXTENSIONS.join(', ')}`
      return null
    }
    if (file.size > MAX_SIZE_BYTES) {
      error.value = `File too large: ${(file.size / 1024 / 1024).toFixed(1)}MB. Max: ${MAX_SIZE_MB}MB`
      return null
    }

    // 创建占位条目（uploading 状态）
    const tempId = `file-${Date.now()}-${Math.random().toString(36).substr(2, 6)}`
    const placeholder: AttachedContext = {
      id: tempId,
      type: 'file',
      displayName: file.name,
      filePath: '',
      filename: file.name,
      size: file.size,
      uploading: true
    }
    contexts.value.push(placeholder)

    try {
      const formData = new FormData()
      formData.append('file', file)

      const response = await fetch('/api/files', {
        method: 'POST',
        body: formData
      })

      if (!response.ok) {
        const body = await response.json().catch(() => ({ error: response.statusText }))
        throw new Error(body.error || `Upload failed: ${response.status}`)
      }

      const result: { filePath: string; filename: string; size: number } = await response.json()

      // 更新占位条目
      const entry = contexts.value.find(f => f.id === tempId)
      if (entry) {
        entry.filePath = result.filePath
        entry.filename = result.filename
        entry.size = result.size
        entry.uploading = false
      }

      return entry || null
    } catch (e) {
      error.value = e instanceof Error ? e.message : 'Upload failed'
      // 移除失败的占位条目
      contexts.value = contexts.value.filter(f => f.id !== tempId)
      return null
    }
  }

  /** 是否有上下文正在上传 */
  function isUploading(): boolean {
    return contexts.value.some(f => f.uploading)
  }

  function removeContext(contextId: string) {
    contexts.value = contexts.value.filter(f => f.id !== contextId)
  }

  function clearContexts() {
    contexts.value = []
    error.value = null
  }

  // 向后兼容的别名
  const files = contexts
  const removeFile = removeContext
  const clearFiles = clearContexts

  return {
    // 新接口
    contexts,
    addContext,
    uploadFile,
    isUploading,
    removeContext,
    clearContexts,

    // 向后兼容的别名
    files,
    removeFile,
    clearFiles,

    error
  }
}

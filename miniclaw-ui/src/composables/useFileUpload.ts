import { ref } from 'vue'
import type { AttachedFile } from '@/types'

const ALLOWED_EXTENSIONS = ['.pdf', '.docx', '.txt', '.md', '.xlsx', '.pptx', '.csv', '.json', '.yaml', '.yml', '.xml', '.html']
const MAX_SIZE_MB = 20
const MAX_SIZE_BYTES = MAX_SIZE_MB * 1024 * 1024

const files = ref<AttachedFile[]>([])
const error = ref<string | null>(null)

export function useFileUpload() {

  /**
   * 上传文件到 workspace 并添加到附件列表。
   * 返回后文件已落盘，Agent 可通过 read_file 读取。
   */
  async function uploadFile(file: File): Promise<AttachedFile | null> {
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
    const placeholder: AttachedFile = {
      id: tempId,
      filePath: '',
      filename: file.name,
      size: file.size,
      uploading: true
    }
    files.value.push(placeholder)

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
      const entry = files.value.find(f => f.id === tempId)
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
      files.value = files.value.filter(f => f.id !== tempId)
      return null
    }
  }

  /** 是否有文件正在上传 */
  function isUploading(): boolean {
    return files.value.some(f => f.uploading)
  }

  function removeFile(fileId: string) {
    files.value = files.value.filter(f => f.id !== fileId)
  }

  function clearFiles() {
    files.value = []
    error.value = null
  }

  return {
    files,
    error,
    uploadFile,
    isUploading,
    removeFile,
    clearFiles
  }
}

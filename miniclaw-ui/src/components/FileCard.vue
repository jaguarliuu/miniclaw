<script setup lang="ts">
import { computed } from 'vue'
import type { SessionFile } from '@/types'
import { useArtifact } from '@/composables/useArtifact'

const props = defineProps<{
  file: SessionFile
  sessionId?: string
}>()

const { openArtifact } = useArtifact()

// 文件扩展名
const fileExt = computed(() => {
  const parts = props.file.fileName.split('.')
  return parts.length > 1 ? (parts[parts.length - 1]?.toLowerCase() || '') : ''
})

// 文件图标
const fileIcon = computed(() => {
  const ext = fileExt.value
  if (['pdf'].includes(ext)) return 'PDF'
  if (['doc', 'docx'].includes(ext)) return 'DOC'
  if (['xls', 'xlsx'].includes(ext)) return 'XLS'
  if (['ppt', 'pptx'].includes(ext)) return 'PPT'
  if (['html', 'htm'].includes(ext)) return 'HTM'
  if (['md'].includes(ext)) return 'MD'
  if (['json'].includes(ext)) return 'JSON'
  if (['js', 'ts', 'py', 'java', 'sql', 'css'].includes(ext)) return ext.toUpperCase()
  if (['png', 'jpg', 'jpeg', 'gif', 'svg'].includes(ext)) return 'IMG'
  return 'FILE'
})

// 格式化文件大小
const formattedSize = computed(() => {
  const size = props.file.fileSize
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
  return `${(size / (1024 * 1024)).toFixed(1)} MB`
})

// 编码文件路径（保留路径分隔符）
function encodePath(p: string): string {
  return p.split('/').map(encodeURIComponent).join('/')
}

// 下载 URL
const downloadUrl = computed(() => {
  const sid = props.sessionId || props.file.sessionId
  const prefix = sid ? `${sid}/` : ''
  return `/api/workspace/${prefix}${encodePath(props.file.filePath)}?download`
})

// 是否可预览（文本类文件）
const isPreviewable = computed(() => {
  const previewableExts = ['html', 'htm', 'js', 'ts', 'css', 'json', 'md', 'txt', 'py', 'java', 'sql', 'svg', 'xml', 'yaml', 'yml']
  return previewableExts.includes(fileExt.value)
})

// 打开预览
async function handleOpen() {
  if (!isPreviewable.value) return
  try {
    const sid = props.sessionId || props.file.sessionId
    const prefix = sid ? `${sid}/` : ''
    const url = `/api/workspace/${prefix}${encodePath(props.file.filePath)}`
    const res = await fetch(url)
    if (res.ok) {
      const content = await res.text()
      openArtifact(props.file.filePath, content)
    }
  } catch (e) {
    console.error('Failed to open file for preview:', e)
  }
}
</script>

<template>
  <div class="file-card">
    <div class="file-icon">{{ fileIcon }}</div>
    <div class="file-info">
      <span class="file-name">{{ file.fileName }}</span>
      <span class="file-meta">{{ formattedSize }}</span>
    </div>
    <div class="file-actions">
      <button v-if="isPreviewable" class="action-btn" title="Preview" @click="handleOpen">
        <span class="action-icon">&#x2197;</span>
      </button>
      <a :href="downloadUrl" :download="file.fileName" class="action-btn" title="Download">
        <span class="action-icon">&#x2193;</span>
      </a>
    </div>
  </div>
</template>

<style scoped>
.file-card {
  display: flex;
  align-items: center;
  gap: 10px;
  margin: 8px 0;
  padding: 10px 14px;
  border: var(--border);
  border-radius: var(--radius-lg);
  background: var(--color-white);
  font-family: var(--font-mono);
  font-size: 12px;
}

.file-icon {
  width: 36px;
  height: 36px;
  border-radius: var(--radius-md);
  background: var(--color-gray-50);
  border: var(--border-light);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 9px;
  font-weight: 700;
  letter-spacing: 0.05em;
  color: var(--color-gray-600);
  flex-shrink: 0;
}

.file-info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.file-name {
  font-weight: 600;
  color: var(--color-black);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.file-meta {
  font-size: 11px;
  color: var(--color-gray-dark);
}

.file-actions {
  display: flex;
  gap: 4px;
  flex-shrink: 0;
}

.action-btn {
  width: 28px;
  height: 28px;
  border: var(--border-light);
  border-radius: var(--radius-md);
  background: var(--color-white);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: all var(--duration-fast) var(--ease-in-out);
  text-decoration: none;
  color: var(--color-gray-600);
  font-size: 14px;
  padding: 0;
}

.action-btn:hover {
  background: var(--color-gray-50);
  border-color: var(--color-gray-dark);
  color: var(--color-black);
}

.action-icon {
  line-height: 1;
}
</style>

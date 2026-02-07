import { ref, readonly } from 'vue'

export interface ArtifactState {
  path: string
  content: string
  language: string
  previewable: boolean
  viewMode: 'code' | 'preview'
  streaming: boolean
}

const artifact = ref<ArtifactState | null>(null)
const panelWidth = ref(560)

function extToLanguage(ext: string): string {
  switch (ext) {
    case 'html':
    case 'htm':
      return 'html'
    case 'js':
    case 'mjs':
      return 'javascript'
    case 'ts':
      return 'typescript'
    case 'css':
      return 'css'
    case 'json':
      return 'json'
    case 'md':
      return 'markdown'
    case 'mermaid':
    case 'mmd':
      return 'mermaid'
    case 'svg':
      return 'svg'
    case 'py':
      return 'python'
    case 'java':
      return 'java'
    case 'sql':
      return 'sql'
    default:
      return 'text'
  }
}

export function useArtifact() {
  function openArtifact(path: string, content: string) {
    const ext = path.split('.').pop()?.toLowerCase() || ''
    const language = extToLanguage(ext)
    const previewable = ['html', 'svg', 'markdown', 'mermaid'].includes(language)
    artifact.value = {
      path,
      content,
      language,
      previewable,
      viewMode: previewable ? 'preview' : 'code',
      streaming: false
    }
  }

  function startStreaming(path: string) {
    const ext = path.split('.').pop()?.toLowerCase() || ''
    const language = extToLanguage(ext)
    const previewable = ['html', 'svg', 'markdown', 'mermaid'].includes(language)
    artifact.value = {
      path,
      content: '',
      language,
      previewable,
      viewMode: 'code',
      streaming: true
    }
  }

  function appendContent(delta: string) {
    if (artifact.value && artifact.value.streaming) {
      artifact.value.content += delta
    }
  }

  function finishStreaming(finalContent?: string) {
    if (artifact.value) {
      if (finalContent != null) {
        artifact.value.content = finalContent
      }
      artifact.value.streaming = false
      if (artifact.value.previewable) {
        artifact.value.viewMode = 'preview'
      }
    }
  }

  function closeArtifact() {
    artifact.value = null
  }

  function setViewMode(mode: 'code' | 'preview') {
    if (artifact.value) {
      artifact.value.viewMode = mode
    }
  }

  function copyContent(): Promise<void> {
    return navigator.clipboard.writeText(artifact.value?.content || '')
  }

  function downloadContent() {
    if (!artifact.value) return
    const blob = new Blob([artifact.value.content], { type: 'text/plain;charset=utf-8' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    // Extract filename from path
    const parts = artifact.value.path.replace(/\\/g, '/').split('/')
    a.download = parts[parts.length - 1] || 'download'
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    URL.revokeObjectURL(url)
  }

  function setPanelWidth(w: number) {
    panelWidth.value = Math.max(320, Math.min(w, window.innerWidth * 0.75))
  }

  return {
    artifact: readonly(artifact),
    panelWidth: readonly(panelWidth),
    openArtifact,
    startStreaming,
    appendContent,
    finishStreaming,
    closeArtifact,
    setViewMode,
    copyContent,
    downloadContent,
    setPanelWidth
  }
}

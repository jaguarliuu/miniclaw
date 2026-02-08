<script setup lang="ts">
import { ref, computed, watch, nextTick } from 'vue'
import { useArtifact } from '@/composables/useArtifact'
import { useMarkdown } from '@/composables/useMarkdown'
import { highlightCode } from '@/composables/useMarkdown'

const { artifact, panelWidth, setViewMode, copyContent, downloadContent, closeArtifact, setPanelWidth } = useArtifact()
const { render } = useMarkdown()

const copyLabel = ref('Copy')
const codeViewRef = ref<HTMLElement | null>(null)

const fileName = computed(() => {
  if (!artifact.value) return ''
  const parts = artifact.value.path.replace(/\\/g, '/').split('/')
  return parts[parts.length - 1] || artifact.value.path
})

const renderedMarkdown = computed(() => {
  if (!artifact.value || artifact.value.language !== 'markdown') return ''
  return render(artifact.value.content)
})

const highlightedCode = computed(() => {
  if (!artifact.value) return ''
  return highlightCode(artifact.value.content, artifact.value.language)
})

const mermaidSrcdoc = computed(() => {
  if (!artifact.value || artifact.value.language !== 'mermaid') return ''
  const escaped = artifact.value.content
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
  return `<!DOCTYPE html>
<html>
<head>
<style>body{margin:0;display:flex;justify-content:center;padding:16px;background:#fff;}</style>
</head>
<body>
<pre class="mermaid">${escaped}</pre>
<script src="https://cdn.jsdelivr.net/npm/mermaid/dist/mermaid.min.js"><\/script>
<script>mermaid.initialize({startOnLoad:true});<\/script>
</body>
</html>`
})

async function handleCopy() {
  await copyContent()
  copyLabel.value = 'Copied!'
  setTimeout(() => {
    copyLabel.value = 'Copy'
  }, 1500)
}

// Auto-scroll code view to bottom during streaming
watch(
  () => artifact.value?.content,
  () => {
    if (artifact.value?.streaming && codeViewRef.value) {
      nextTick(() => {
        if (codeViewRef.value) {
          codeViewRef.value.scrollTop = codeViewRef.value.scrollHeight
        }
      })
    }
  }
)

// Drag-resize
function startResize(e: MouseEvent) {
  e.preventDefault()
  const startX = e.clientX
  const startWidth = panelWidth.value
  document.body.style.cursor = 'col-resize'
  document.body.style.userSelect = 'none'

  function onMove(ev: MouseEvent) {
    setPanelWidth(startWidth + (startX - ev.clientX))
  }
  function onUp() {
    document.body.style.cursor = ''
    document.body.style.userSelect = ''
    document.removeEventListener('mousemove', onMove)
    document.removeEventListener('mouseup', onUp)
  }
  document.addEventListener('mousemove', onMove)
  document.addEventListener('mouseup', onUp)
}
</script>

<template>
  <aside v-if="artifact" class="artifact-panel" :style="{ width: panelWidth + 'px' }">
    <div class="resize-handle" @mousedown="startResize" />

    <div class="panel-header">
      <div class="panel-title-row">
        <span class="panel-path" :title="artifact.path">{{ fileName }}</span>
        <span v-if="artifact.streaming" class="streaming-indicator">
          <span class="streaming-dot" />Generating...
        </span>
        <span class="lang-badge">{{ artifact.language }}</span>
        <div class="panel-actions">
          <div v-if="artifact.previewable" class="view-toggle">
            <button
              class="toggle-btn"
              :class="{ active: artifact.viewMode === 'code' }"
              @click="setViewMode('code')"
            >Code</button>
            <button
              class="toggle-btn"
              :class="{ active: artifact.viewMode === 'preview' }"
              @click="setViewMode('preview')"
            >Preview</button>
          </div>
          <button class="action-btn" @click="handleCopy" :title="copyLabel">{{ copyLabel }}</button>
          <button class="action-btn" @click="downloadContent" title="Download">Download</button>
          <button class="panel-close" @click="closeArtifact" title="Close panel">&times;</button>
        </div>
      </div>
    </div>

    <div class="panel-body">
      <!-- Code view -->
      <pre v-if="artifact.viewMode === 'code'" ref="codeViewRef" class="code-view hljs"><code v-html="highlightedCode"></code></pre>

      <!-- HTML / SVG preview -->
      <iframe
        v-else-if="artifact.language === 'html' || artifact.language === 'svg'"
        sandbox="allow-scripts"
        :srcdoc="artifact.content"
        class="preview-iframe"
      />

      <!-- Mermaid preview -->
      <iframe
        v-else-if="artifact.language === 'mermaid'"
        sandbox="allow-scripts"
        :srcdoc="mermaidSrcdoc"
        class="preview-iframe"
      />

      <!-- Markdown preview -->
      <div
        v-else-if="artifact.language === 'markdown'"
        class="preview-markdown markdown-body"
        v-html="renderedMarkdown"
      />
    </div>
  </aside>
</template>

<style scoped>
.artifact-panel {
  height: 100%;
  display: flex;
  flex-direction: column;
  background: var(--color-white);
  border-left: var(--border);
  flex-shrink: 0;
  position: relative;
}

.resize-handle {
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  width: 4px;
  cursor: col-resize;
  z-index: 10;
}

.resize-handle:hover {
  background: var(--color-gray-dark);
  opacity: 0.3;
}

.panel-header {
  padding: 12px 16px;
  border-bottom: var(--border);
}

.panel-title-row {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.panel-path {
  font-family: var(--font-mono);
  font-size: 13px;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 160px;
}

.streaming-indicator {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-family: var(--font-mono);
  font-size: 11px;
  color: var(--color-gray-dark);
}

.streaming-dot {
  display: inline-block;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--color-gray-dark);
  animation: pulse 1.2s ease-in-out infinite;
}

@keyframes pulse {
  0%, 100% { opacity: 0.3; }
  50% { opacity: 1; }
}

.lang-badge {
  font-family: var(--font-mono);
  font-size: 10px;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  padding: 2px 6px;
  background: var(--color-gray-bg);
  border: var(--border-light);
  color: var(--color-gray-dark);
}

.panel-actions {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-left: auto;
}

.view-toggle {
  display: flex;
  border: var(--border);
}

.toggle-btn {
  font-family: var(--font-mono);
  font-size: 11px;
  padding: 4px 10px;
  border: none;
  background: var(--color-white);
  cursor: pointer;
  transition: all 0.15s ease;
}

.toggle-btn + .toggle-btn {
  border-left: var(--border);
}

.toggle-btn.active {
  background: var(--color-black);
  color: var(--color-white);
}

.toggle-btn:not(.active):hover {
  background: var(--color-gray-bg);
}

.action-btn {
  font-family: var(--font-mono);
  font-size: 11px;
  padding: 4px 10px;
  border: var(--border);
  background: var(--color-white);
  cursor: pointer;
  transition: all 0.15s ease;
}

.action-btn:hover {
  background: var(--color-black);
  color: var(--color-white);
}

.panel-close {
  width: 28px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: var(--border);
  background: var(--color-white);
  font-size: 18px;
  cursor: pointer;
  transition: all 0.15s ease;
}

.panel-close:hover {
  background: var(--color-black);
  color: var(--color-white);
}

.panel-body {
  flex: 1;
  overflow: auto;
  position: relative;
}

.code-view {
  margin: 0;
  padding: 16px;
  font-family: var(--font-mono);
  font-size: 13px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-all;
  background: var(--color-gray-bg);
  min-height: 100%;
  overflow: auto;
}

.code-view code {
  font-family: inherit;
}

.preview-iframe {
  width: 100%;
  height: 100%;
  border: none;
  position: absolute;
  top: 0;
  left: 0;
}

.preview-markdown {
  padding: 20px;
  font-size: 14px;
  line-height: 1.6;
}
</style>

<!-- Markdown styles (unscoped to apply to v-html content) -->
<style>
@import '@/styles/markdown.css';
</style>

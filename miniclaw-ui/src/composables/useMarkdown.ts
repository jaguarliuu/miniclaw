import { computed } from 'vue'
import { marked } from 'marked'
import hljs from 'highlight.js'

/**
 * Markdown 渲染器（使用 marked 库 + highlight.js 代码高亮）
 * 支持 GFM（GitHub Flavored Markdown）
 */

// 配置 marked
marked.setOptions({
  gfm: true,          // GitHub Flavored Markdown
  breaks: true,       // 换行符转为 <br>
})

// 自定义渲染器
const renderer = new marked.Renderer()

// 代码块：使用 highlight.js 进行语法高亮
renderer.code = ({ text, lang }) => {
  const language = lang || ''
  let highlighted: string

  if (language && hljs.getLanguage(language)) {
    highlighted = hljs.highlight(text, { language }).value
  } else if (language) {
    // 未知语言，尝试自动检测
    highlighted = hljs.highlightAuto(text).value
  } else {
    highlighted = escapeHtml(text)
  }

  return `<pre class="code-block hljs${language ? ` language-${language}` : ''}"><code>${highlighted}</code></pre>`
}

// 行内代码
renderer.codespan = ({ text }) => {
  return `<code class="inline-code">${text}</code>`
}

// 链接在新标签页打开
renderer.link = ({ href, title, text }) => {
  const titleAttr = title ? ` title="${title}"` : ''
  return `<a href="${href}"${titleAttr} target="_blank" rel="noopener noreferrer">${text}</a>`
}

marked.use({ renderer })

function escapeHtml(text: string): string {
  const map: Record<string, string> = {
    '&': '&amp;',
    '<': '&lt;',
    '>': '&gt;',
    '"': '&quot;',
    "'": '&#039;',
  }
  return text.replace(/[&<>"']/g, (char) => map[char] || char)
}

/**
 * 对已有的纯文本代码进行高亮（用于 ArtifactPanel 等非 Markdown 场景）
 */
export function highlightCode(code: string, language: string): string {
  if (language && hljs.getLanguage(language)) {
    return hljs.highlight(code, { language }).value
  }
  return escapeHtml(code)
}

export function useMarkdown() {
  function render(text: string): string {
    if (!text) return ''

    try {
      // marked.parse 返回 string | Promise<string>，同步模式下返回 string
      const result = marked.parse(text)
      return typeof result === 'string' ? result : ''
    } catch (e) {
      console.error('Markdown parse error:', e)
      return escapeHtml(text)
    }
  }

  return { render }
}

/**
 * 创建一个响应式的 markdown 渲染 computed
 */
export function useMarkdownContent(getText: () => string) {
  const { render } = useMarkdown()
  return computed(() => render(getText()))
}

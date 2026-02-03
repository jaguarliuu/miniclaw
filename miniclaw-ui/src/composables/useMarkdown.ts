import { computed } from 'vue'
import { marked } from 'marked'

/**
 * Markdown 渲染器（使用 marked 库）
 * 支持 GFM（GitHub Flavored Markdown）
 */

// 配置 marked
marked.setOptions({
  gfm: true,          // GitHub Flavored Markdown
  breaks: true,       // 换行符转为 <br>
})

// 自定义渲染器
const renderer = new marked.Renderer()

// 代码块添加自定义类名
renderer.code = ({ text, lang }) => {
  const language = lang || ''
  const escapedCode = escapeHtml(text)
  return `<pre class="code-block${language ? ` language-${language}` : ''}"><code>${escapedCode}</code></pre>`
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

const ESCAPE_MAP = {
  '&': '&amp;',
  '<': '&lt;',
  '>': '&gt;',
  '"': '&quot;',
  "'": '&#39;'
}

function escapeHtml(text) {
  return String(text).replace(/[&<>"']/g, (char) => ESCAPE_MAP[char])
}

function renderInline(text) {
  return escapeHtml(text)
    .replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
    .replace(/(^|[^*])\*([^*\n]+)\*(?!\*)/g, '$1<em>$2</em>')
    .replace(/`([^`]+)`/g, '<code>$1</code>')
}

/**
 * 简易 Markdown 渲染：支持标题、无序/有序列表、引用、分隔线与行内加粗/斜体/代码。
 * 仅用于渲染后端生成的报告正文，避免引入额外依赖。
 */
export function renderMarkdown(source) {
  const text = String(source || '').replace(/\r\n?/g, '\n').trim()
  if (!text) return ''

  const lines = text.split('\n')
  const html = []
  let listTag = null

  const closeList = () => {
    if (listTag) {
      html.push(`</${listTag}>`)
      listTag = null
    }
  }

  const openList = (tag) => {
    if (listTag !== tag) {
      closeList()
      html.push(`<${tag}>`)
      listTag = tag
    }
  }

  lines.forEach((raw) => {
    const line = raw.trim()
    if (!line) {
      closeList()
      return
    }

    if (/^(-{3,}|\*{3,}|_{3,})$/.test(line)) {
      closeList()
      html.push('<hr />')
      return
    }

    const heading = line.match(/^(#{1,6})\s+(.*)$/)
    if (heading) {
      closeList()
      const level = Math.min(6, heading[1].length + 1)
      html.push(`<h${level}>${renderInline(heading[2])}</h${level}>`)
      return
    }

    const quote = line.match(/^>\s?(.*)$/)
    if (quote) {
      closeList()
      html.push(`<blockquote>${renderInline(quote[1])}</blockquote>`)
      return
    }

    if (/^[-*+]\s+/.test(line)) {
      openList('ul')
      html.push(`<li>${renderInline(line.replace(/^[-*+]\s+/, ''))}</li>`)
      return
    }

    if (/^\d+[.)]\s+/.test(line)) {
      openList('ol')
      html.push(`<li>${renderInline(line.replace(/^\d+[.)]\s+/, ''))}</li>`)
      return
    }

    closeList()
    html.push(`<p>${renderInline(line)}</p>`)
  })

  closeList()
  return html.join('')
}

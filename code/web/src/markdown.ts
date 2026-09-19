// Markdown rendered for the viewer. The document is a remote file shown in the same page as the
// bridge, so raw HTML stays text (`html: false`) and markdown-it's own link check keeps
// `javascript:` and friends out of href. The CSP and the asset loader stop anything that still
// tries to load.
import MarkdownIt from 'markdown-it'

const md = new MarkdownIt({ html: false })

// Each top-level block carries the first source line it came from, so `?line=` can find it.
md.core.ruler.push('source_line', (state) => {
  for (const token of state.tokens) {
    if (token.map && token.nesting === 1 && token.level === 0) token.attrSet('data-line', String(token.map[0] + 1))
  }
})

export function renderMarkdown(source: string): HTMLElement {
  const body = document.createElement('article')
  body.className = 'markdown'
  body.innerHTML = md.render(source)
  body.addEventListener('click', (event) => {
    const link = (event.target as Element).closest('a')
    if (!link) return
    // Relative links and #fragments resolve to the page's own origin. There is nothing there to
    // go to, and handing that URL to a browser would open a dead page, so they do nothing.
    // Everything else is left to the WebView, whose client passes it to another app.
    if (new URL(link.href, location.href).origin === location.origin) event.preventDefault()
  })
  return body
}

/** The last block starting at or before [line], for scrolling a link's `?line=` into view. */
export function blockAtLine(body: HTMLElement, line: number): HTMLElement | null {
  let found: HTMLElement | null = null
  for (const block of body.querySelectorAll<HTMLElement>(':scope > [data-line]')) {
    if (Number(block.dataset.line) > line) break
    found = block
  }
  return found
}

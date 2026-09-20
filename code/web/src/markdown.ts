// Markdown rendered for the viewer, and the surface that shows it. The document is a remote file
// shown in the same page as the bridge, so raw HTML stays text (`html: false`) and markdown-it's
// own link check keeps `javascript:` and friends out of href. The CSP and the asset loader stop
// anything that still tries to load.
import MarkdownIt from 'markdown-it'
import { TOPBAR_SPACE } from './chrome/topbar'
import { type Hold, holdBlock } from './zoom'

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
function blockAtLine(body: HTMLElement, line: number): HTMLElement | null {
  let found: HTMLElement | null = null
  for (const block of body.querySelectorAll<HTMLElement>(':scope > [data-line]')) {
    if (Number(block.dataset.line) > line) break
    found = block
  }
  return found
}

/**
 * The viewer layer of a markdown file: the one place where a layer really is its own DOM, since
 * the editor beside it is CodeMirror on the source. Both speak in source lines, so switching
 * between them keeps the same part of the document on screen.
 */
export interface MarkdownSurface {
  /** Hides the rendered document while the editor has the screen, keeping where it was scrolled to. */
  visible(on: boolean): void
  /** Renders [source] again, for coming back from an editor that changed it. */
  update(source: string): void
  /** The source line of the block at the top of the screen. */
  topLine(): number
  scrollToLine(line: number): void
  /** What a pinch holds on to, as the code layers' `holdLine` is for CodeMirror. */
  hold: Hold
  remove(): void
}

export function showMarkdown(parent: HTMLElement, source: string): MarkdownSurface {
  const scroller = document.createElement('div')
  scroller.className = 'markdown-scroller'
  let body = renderMarkdown(source)
  let rendered = source
  scroller.append(body)
  parent.append(scroller)

  // Reads `body` when the gesture starts, so a re-render does not leave a stale one behind.
  const hold: Hold = (clientY) => holdBlock(scroller, body)(clientY)

  return {
    hold,
    visible: (on) => { scroller.style.display = on ? '' : 'none' },
    update(text) {
      if (text === rendered) return
      const next = renderMarkdown(text)
      body.replaceWith(next)
      body = next
      rendered = text
    },
    topLine() {
      const top = scroller.getBoundingClientRect().top + TOPBAR_SPACE
      let line = 1
      for (const block of body.querySelectorAll<HTMLElement>(':scope > [data-line]')) {
        line = Number(block.dataset.line)
        if (block.getBoundingClientRect().bottom > top) break
      }
      return line
    },
    scrollToLine(line) {
      // The scroller is positioned (see index.html), so offsetTop is measured from it.
      const block = blockAtLine(body, line)
      if (block) scroller.scrollTop = block.offsetTop - TOPBAR_SPACE
    },
    remove: () => scroller.remove(),
  }
}

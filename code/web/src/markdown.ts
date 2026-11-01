// Markdown rendered for the viewer, and the surface that shows it. The document is a remote file
// shown in the same page as the bridge, so raw HTML stays text (`html: false`) and markdown-it's
// own link check keeps `javascript:` and friends out of href. The CSP and the asset loader stop
// anything that still tries to load.
import MarkdownIt from 'markdown-it'
import { type Heading, anchorLine, decodeFragment } from './anchors'
import { TOPBAR_SPACE } from './chrome/topbar'
import { type ImageEnv, images, missingImageHtml, topBlockIndex } from './images'
import { type Hold, holdBlock } from './zoom'

const md = new MarkdownIt({ html: false }).use(images)

// Each top-level block carries the first source line it came from, so `?line=` can find it.
md.core.ruler.push('source_line', (state) => {
  for (const token of state.tokens) {
    if (token.map && token.nesting === 1 && token.level === 0) token.attrSet('data-line', String(token.map[0] + 1))
  }
})

/**
 * What a tap in the document does, which only `main.ts` knows how to: which file is open, the jump
 * list, the full screen image.
 */
export interface MarkdownHooks {
  /** Any link but a bare `#fragment`, as the document wrote it, for Kotlin to resolve against the file. */
  open(href: string): void
  /** A heading of this document, by its source line. */
  heading(line: number): void
  /** An image that is shown, outside a link, by the address it was loaded from. */
  image(src: string, alt: string): void
}

let hooks: MarkdownHooks | null = null

export function setMarkdownHooks(next: MarkdownHooks): void {
  hooks = next
}

/** [source] rendered; [docId] is the open file it is, which its images are asked for by. */
export function renderMarkdown(source: string, docId?: number): HTMLElement {
  const body = document.createElement('article')
  body.className = 'markdown'
  body.innerHTML = md.render(source, { docId } satisfies ImageEnv)
  // An image Kotlin did not answer with one becomes the alt box. `error` does not bubble, so it is
  // caught on the way down.
  body.addEventListener('error', (event) => {
    const image = event.target
    if (!(image instanceof HTMLImageElement)) return
    image.insertAdjacentHTML('afterend', missingImageHtml(image.alt, image.dataset.src ?? ''))
    image.remove()
  }, true)
  body.addEventListener('click', (event) => {
    const target = event.target as Element
    const link = target.closest('a')
    if (!link) {
      // A loaded image, not the alt box, which has nothing bigger to show.
      if (target instanceof HTMLImageElement && target.complete && target.naturalWidth > 0) hooks?.image(target.src, target.alt)
      return
    }
    // Nothing goes to the WebView, which would only hand it to another app: a link is resolved
    // against the file it is in (docs/skiffcode.spec/markdown-links.md), which the page does not
    // know the path of. The `href` as written, not `link.href`, which is resolved against the page.
    event.preventDefault()
    const href = link.getAttribute('href')
    if (!href) return
    if (href.startsWith('#')) {
      // Not found is the top, rather than nothing: the link was followed, to a heading that is gone.
      const fragment = decodeFragment(href.slice(1))
      hooks?.heading((fragment === null ? null : headingLine(body, fragment)) ?? 1)
    } else {
      hooks?.open(href)
    }
  })
  return body
}

/** The source line of the heading [fragment] names in [body], or null for none. */
function headingLine(body: HTMLElement, fragment: string): number | null {
  const headings: Heading[] = []
  for (const element of body.querySelectorAll<HTMLElement>('h1, h2, h3, h4, h5, h6')) {
    // A heading in a list or a quote has no line of its own; the block it is in has.
    let block: HTMLElement = element
    while (block.parentElement && block.parentElement !== body) block = block.parentElement
    headings.push({ text: element.textContent ?? '', line: Number(block.dataset.line) || 1 })
  }
  return anchorLine(headings, fragment)
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
  /** The source line of the heading a link's `#fragment` names, decoded, or null for none. */
  headingLine(fragment: string): number | null
  scrollToLine(line: number): void
  /** What a pinch holds on to, as the code layers' `holdLine` is for CodeMirror. */
  hold: Hold
  remove(): void
}

export function showMarkdown(parent: HTMLElement, source: string, docId?: number): MarkdownSurface {
  const scroller = document.createElement('div')
  scroller.className = 'markdown-scroller'
  let body = renderMarkdown(source, docId)
  let rendered = source
  scroller.append(body)
  parent.append(scroller)

  // Reads `body` when the gesture starts, so a re-render does not leave a stale one behind.
  const hold: Hold = (clientY) => holdBlock(scroller, body)(clientY)

  // Where the screen is meant to be: a block and how far below the top it starts. An image comes in
  // after the text around it, and one above this block would push it down the screen — `?line=`,
  // `#heading`, a jump or the outline would land and then slide away. One below it may be what the
  // document was too short to scroll to without. So when an image loads, or fails and becomes the alt
  // box, the screen goes back to the block. The block is the one the surface was sent to, or, once
  // the user has scrolled, the one they left at the top.
  let anchor: { block: Element; offset: number } | null = null
  // The scrollTop this surface last set itself, whose scroll event is not the user moving.
  let ours: number | null = null
  const top = () => scroller.getBoundingClientRect().top + TOPBAR_SPACE
  const remember = () => {
    if (scroller.style.display === 'none') return
    const blocks = [...body.children]
    const index = topBlockIndex(blocks.map((block) => block.getBoundingClientRect().bottom), top())
    anchor = index === null ? null : { block: blocks[index], offset: blocks[index].getBoundingClientRect().top - top() }
  }
  const scrolled = () => {
    if (ours !== null && Math.abs(scroller.scrollTop - ours) < 1) return
    ours = null
    remember()
  }
  const settle = (event: Event) => {
    if (!(event.target instanceof HTMLImageElement)) return
    // After the alt box has taken a failed image's place, which happens in the same dispatch.
    requestAnimationFrame(() => {
      if (!anchor || !anchor.block.isConnected || scroller.style.display === 'none') return
      const moved = anchor.block.getBoundingClientRect().top - top() - anchor.offset
      if (Math.abs(moved) < 1) return
      scroller.scrollTop += moved
      ours = scroller.scrollTop
    })
  }
  scroller.addEventListener('scroll', scrolled, { passive: true })
  scroller.addEventListener('load', settle, true)
  scroller.addEventListener('error', settle, true)
  requestAnimationFrame(() => {
    if (!anchor) remember()
  })

  return {
    hold,
    visible: (on) => { scroller.style.display = on ? '' : 'none' },
    update(text) {
      if (text === rendered) return
      const next = renderMarkdown(text, docId)
      body.replaceWith(next)
      body = next
      rendered = text
      anchor = null
      ours = null
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
    headingLine: (fragment) => headingLine(body, fragment),
    scrollToLine(line) {
      // The scroller is positioned (see index.html), so offsetTop is measured from it.
      const block = blockAtLine(body, line)
      if (!block) return
      scroller.scrollTop = block.offsetTop - TOPBAR_SPACE
      ours = scroller.scrollTop
      // The block it was sent to, even when the document is still too short to bring it to the top.
      anchor = { block, offset: 0 }
    },
    remove: () => scroller.remove(),
  }
}

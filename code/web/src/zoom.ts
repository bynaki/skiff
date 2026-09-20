// Pinch zoom by font size: the CSS variable --code-font-size changes, and what is under the fingers
// stays where it was on screen.
import { EditorView } from '@codemirror/view'

export const MIN_FONT_SIZE = 8
export const MAX_FONT_SIZE = 40

/**
 * A line held in place while the font size changes. `fraction` is where the finger midpoint sits
 * inside the line box, `glyph` is where CodeMirror's caret rectangle starts inside it (both as a
 * share of the line height), and `lineHeight` is the box height at `size`. Line boxes scale with
 * the font, so all three carry over to any other size.
 */
export interface Anchor {
  pos: number
  fraction: number
  glyph: number
  lineHeight: number
  size: number
}

/**
 * What a pinch holds on to: given the finger midpoint when the gesture starts, it returns the
 * function that applies a new size and puts the same spot back under the midpoint.
 */
export type Hold = (clientY: number) => (size: number, clientY: number) => void

/** What ② original size returns to, until settings.toml gives the user's own. */
export const DEFAULT_FONT_SIZE = 14

// One size for every document, so opening another file keeps the zoom.
let fontSize = DEFAULT_FONT_SIZE
let zoomedAt = -Infinity

export function currentFontSize(): number {
  return fontSize
}

/** When the size last changed, as performance.now(), so a scroll that follows can be told apart. */
export function lastZoomTime(): number {
  return zoomedAt
}

export function setFontSize(size: number): void {
  fontSize = Math.min(MAX_FONT_SIZE, Math.max(MIN_FONT_SIZE, size))
  zoomedAt = performance.now()
  document.documentElement.style.setProperty('--code-font-size', `${fontSize}px`)
}

export function anchorAt(view: EditorView, clientY: number): Anchor {
  const block = view.lineBlockAtHeight(clientY - view.documentTop)
  const blockTop = view.documentTop + block.top
  const caret = view.coordsAtPos(block.from)
  return {
    pos: block.from,
    fraction: Math.min(1, Math.max(0, (clientY - blockTop) / block.height)),
    glyph: caret ? (caret.top - blockTop) / block.height : 0,
    lineHeight: block.height,
    size: fontSize,
  }
}

/**
 * Sets the font size and keeps [anchor] under [clientY]. The scroll goes through CodeMirror's own
 * scroll target, which it applies after re-measuring line heights. Writing scrollTop from a
 * measure request instead fights the editor's top-of-viewport scroll anchoring and never settles.
 */
export function zoomTo(view: EditorView, size: number, anchor: Anchor, clientY: number): void {
  setFontSize(size)
  const lineHeight = anchor.lineHeight * (fontSize / anchor.size)
  const scrollerTop = view.scrollDOM.getBoundingClientRect().top
  view.dispatch({
    effects: EditorView.scrollIntoView(anchor.pos, {
      y: 'start',
      yMargin: clientY - scrollerTop - (anchor.fraction - anchor.glyph) * lineHeight,
    }),
  })
}

export function holdLine(view: EditorView): Hold {
  return (startY) => {
    const anchor = anchorAt(view, startY)
    return (size, clientY) => zoomTo(view, size, anchor, clientY)
  }
}

/**
 * For plain scrolling content such as rendered markdown: holds the child block under the fingers
 * at the same share of its height. Blocks reflow as the font changes, so a share of the block is
 * as close as it gets.
 */
export function holdBlock(scroller: HTMLElement, content: HTMLElement): Hold {
  return (startY) => {
    const block = [...content.children].find((child) => child.getBoundingClientRect().bottom >= startY) ?? content
    const start = block.getBoundingClientRect()
    const fraction = start.height > 0 ? (startY - start.top) / start.height : 0
    return (size, clientY) => {
      setFontSize(size)
      const now = block.getBoundingClientRect()
      scroller.scrollTop += now.top + fraction * now.height - clientY
    }
  }
}

/**
 * Listens for the pinch on the document, not on the scroller under the fingers: while a focused
 * editor holds the caret, Chrome hands the second finger's `touchstart` to `<html>` instead of the
 * element it landed on, so a listener further in never sees the gesture begin (the moves that
 * follow do arrive). One pane shows one surface at a time, so [hold] decides what is being zoomed.
 * Returns the function that takes the listeners off again.
 */
export function installPinchZoom(hold: Hold): () => void {
  let apply: ((size: number, clientY: number) => void) | null = null
  let gesture: { distance: number; size: number } | null = null
  let frame = 0

  const distance = (touches: TouchList) =>
    Math.hypot(touches[0].clientX - touches[1].clientX, touches[0].clientY - touches[1].clientY)
  const midY = (touches: TouchList) => (touches[0].clientY + touches[1].clientY) / 2

  const start = (event: TouchEvent) => {
    if (event.touches.length !== 2) return
    gesture = { distance: distance(event.touches), size: fontSize }
    apply = hold(midY(event.touches))
  }

  const move = (event: TouchEvent) => {
    if (!gesture || !apply || event.touches.length !== 2) return
    event.preventDefault()
    const size = gesture.size * (distance(event.touches) / gesture.distance)
    const y = midY(event.touches)
    const zoom = apply
    cancelAnimationFrame(frame)
    frame = requestAnimationFrame(() => zoom(size, y))
  }

  const end = (event: TouchEvent) => {
    if (event.touches.length < 2) gesture = null
  }

  document.addEventListener('touchstart', start, { passive: true })
  document.addEventListener('touchmove', move, { passive: false })
  document.addEventListener('touchend', end, { passive: true })
  document.addEventListener('touchcancel', end, { passive: true })

  return () => {
    document.removeEventListener('touchstart', start)
    document.removeEventListener('touchmove', move)
    document.removeEventListener('touchend', end)
    document.removeEventListener('touchcancel', end)
    cancelAnimationFrame(frame)
  }
}

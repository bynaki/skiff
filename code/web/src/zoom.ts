// Pinch zoom by font size: the CSS variable --code-font-size changes, and what is under the fingers
// stays where it was on screen. A code view is told the size through a theme as well, for the
// reason [codeFontSize] gives.
import { Compartment, type Extension } from '@codemirror/state'
import { EditorView } from '@codemirror/view'
import { read, write } from './storage'

export const MIN_FONT_SIZE = 8
export const MAX_FONT_SIZE = 40

/**
 * What one Zoom In or Zoom Out is worth. A pinch is how the size is usually found; the commands
 * are for arriving at one exactly, so the step is small enough to aim with and large enough to see.
 */
export const ZOOM_STEP = 2

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

/** Where the size the user is reading at is kept (plan.md "상태 저장"). */
const KEY = 'zoom.size'

/**
 * How long after the last change the size is written down. A pinch changes it on every frame, and
 * what is worth keeping is where the fingers left it.
 */
const WRITE_DELAY = 400

/**
 * The size this page opens at: the one it was left at, if that is still a size. It is the page's
 * own storage and not `settings.toml`, which holds the size the user *chose* to read at — this is
 * the one they pinched to, and it has to survive the activity being rebuilt by a dark mode or a
 * font scale change, which is where it used to go back to [DEFAULT_FONT_SIZE].
 */
export function storedFontSize(): number {
  const size = Number(read(KEY))
  if (!Number.isFinite(size) || size === 0) return DEFAULT_FONT_SIZE
  return Math.min(MAX_FONT_SIZE, Math.max(MIN_FONT_SIZE, size))
}

// One size for every document, so opening another file keeps the zoom.
let fontSize = storedFontSize()
let zoomedAt = -Infinity
let writing = 0

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
  // Once the fingers have settled, not sixty times a second on the way there.
  clearTimeout(writing)
  writing = setTimeout(() => write(KEY, String(fontSize)), WRITE_DELAY)
}

/**
 * The font size a code view reads, as a theme rather than through `--code-font-size`.
 *
 * CodeMirror re-reads line heights only when the theme facet changed or when `.cm-content`'s box
 * changed height. Its base theme gives that box `min-height: 100%` inside a flex scroller, so a
 * document shorter than the screen keeps the same box height at every font size and neither test
 * fires: the line boxes grow in the DOM while the height map, and with it the gutter's line
 * spacing, stays at the size the view was built with. Changing the size through a compartment is
 * what tells the view to measure again. Found on the tablet with a file of eight lines.
 */
const codeFont = new Compartment()

function fontTheme(size: number): Extension {
  return EditorView.theme({ '&': { fontSize: `${size}px` } })
}

/** What a code view starts at; [zoomTo] moves it from there. */
export function codeFontSize(): Extension {
  return codeFont.of(fontTheme(fontSize))
}

/**
 * Brings [view] to the size the rest of the screen is at. A pane whose markdown surface was
 * zoomed while the code view was hidden has a view still configured for the old size, and a
 * hidden view measures nothing; reconfiguring settles both, since a theme change is what makes
 * CodeMirror read line heights again.
 */
export function applyFontSize(view: EditorView): void {
  view.dispatch({ effects: codeFont.reconfigure(fontTheme(fontSize)) })
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
    effects: [
      codeFont.reconfigure(fontTheme(fontSize)),
      EditorView.scrollIntoView(anchor.pos, {
        y: 'start',
        yMargin: clientY - scrollerTop - (anchor.fraction - anchor.glyph) * lineHeight,
      }),
    ],
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

// The nav bar (docs/skiffcode.spec/nav-bar.md): a thin bar at the right edge with the whole file on
// it — the git gutter's changes in its left lane, the language server's errors and warnings in its
// right — and a box for the part of the file on the screen. It takes no touches: a tap or a drag
// there is the document's, and the system's back gesture at that edge is left alone.
//
// Where a mark sits comes from the height map, not the line number, so a wrapped line, the diff
// layer's deleted lines and a fold take the room on the bar they take in the scroller, and the marks
// line up with the box, which is the scroll position.
import { forEachDiagnostic, setDiagnosticsEffect } from '@codemirror/lint'
import type { Extension } from '@codemirror/state'
import { EditorView, ViewPlugin, type ViewUpdate } from '@codemirror/view'
import { type GitMark, gitMarksIn, setBaseline } from './gitGutter'

/** How wide the bar is, in px. */
export const NAV_BAR_WIDTH = 6

/**
 * How far the scroller reaches past the right edge while the bar is on, in px, so the browser's own
 * scrollbar is drawn where the editor clips it (2026-10-21 user's decision: the bar says where the
 * screen is). CSS cannot just turn it off: Android's WebView has no `scrollbar-width` (CSS.supports
 * says false and the rule is dropped) and draws its overlay scrollbar whatever `::-webkit-scrollbar`
 * says — both measured on the tablet, Chrome 155. The thumb sat about 10px in from the edge there.
 * The horizontal one stays, a little longer than the screen.
 */
const SCROLLBAR_TUCK = 24

/** The room the code views keep at the right while the bar is on, in px: the bar and the tucked-away scrollbar. */
export const NAV_BAR_ROOM = NAV_BAR_WIDTH + SCROLLBAR_TUCK

/** The shortest a mark is drawn, in px, so one line in a long file still shows. */
const MIN_MARK = 2
/** The shortest the box is drawn, in px. */
const MIN_BOX = 8

export type NavMark = GitMark | 'error' | 'warning'
export type Lane = 'git' | 'diagnostic'

/** A mark where the scroller has it: from [top] to [bottom], in px from the top of what scrolls. */
export interface Placed {
  top: number
  bottom: number
  mark: NavMark
}

/** What is drawn: a run of [height] px from [y] down, in px from the top of the bar. */
export interface Run {
  lane: Lane
  y: number
  height: number
  mark: NavMark
}

/**
 * Which mark wins a pixel row two of them share: in the git lane a deletion, which is the smallest
 * and is otherwise lost under the lines either side; in the other, an error over a warning.
 */
const RANK: Record<NavMark, number> = { added: 0, changed: 1, deleted: 2, warning: 0, error: 1 }

function laneOf(mark: NavMark): Lane {
  return mark === 'error' || mark === 'warning' ? 'diagnostic' : 'git'
}

/**
 * [marks] as the bar draws them, [barHeight] px standing for [scrollHeight]. Each mark is at least
 * [MIN_MARK] tall, centred on where it is; marks that meet in one lane on one pixel row become one
 * run, the higher [RANK] taking the row. Runs come in order, lane by lane.
 */
export function barRuns(marks: readonly Placed[], scrollHeight: number, barHeight: number): Run[] {
  const rows = Math.floor(barHeight)
  if (rows <= 0 || scrollHeight <= 0) return []
  const scale = barHeight / scrollHeight
  const lanes: Record<Lane, Array<NavMark | null>> = { git: new Array(rows).fill(null), diagnostic: new Array(rows).fill(null) }
  for (const { top, bottom, mark } of marks) {
    let from = top * scale
    let to = Math.max(bottom, top) * scale
    if (to - from < MIN_MARK) {
      const middle = (from + to) / 2
      from = middle - MIN_MARK / 2
      to = middle + MIN_MARK / 2
    }
    const length = Math.min(rows, Math.max(MIN_MARK, Math.round(to - from)))
    const first = Math.max(0, Math.min(rows - length, Math.round(from)))
    const last = first + length
    const row = lanes[laneOf(mark)]
    for (let y = first; y < last; y++) {
      const there = row[y]
      if (there === null || RANK[mark] > RANK[there]) row[y] = mark
    }
  }
  const runs: Run[] = []
  for (const lane of ['git', 'diagnostic'] as const) {
    const row = lanes[lane]
    for (let y = 0; y < rows;) {
      const mark = row[y]
      let end = y + 1
      while (end < rows && row[end] === mark) end++
      if (mark !== null) runs.push({ lane, y, height: end - y, mark })
      y = end
    }
  }
  return runs
}

/**
 * The box for what is on the screen, in px from the top of the bar: the scroller's [scrollTop] and
 * [clientHeight] out of its [scrollHeight], at least [MIN_BOX] tall and never past either end.
 */
export function viewBox(scrollTop: number, clientHeight: number, scrollHeight: number, barHeight: number): { y: number; height: number } {
  if (scrollHeight <= 0 || barHeight <= 0) return { y: 0, height: 0 }
  const scale = barHeight / scrollHeight
  const height = Math.min(barHeight, Math.max(MIN_BOX, Math.min(clientHeight, scrollHeight) * scale))
  const y = Math.max(0, Math.min(barHeight - height, scrollTop * scale))
  return { y, height }
}

/** Whether a diagnostic of [severity] goes on the bar: errors and warnings, not info and hints. */
export function shownSeverity(severity: string): 'error' | 'warning' | null {
  return severity === 'error' || severity === 'warning' ? severity : null
}

/**
 * The theme variable each mark is drawn in. Written as `var(…)` like every other use, which is how
 * `theme.test.ts` finds the colors the page reads; a canvas takes no `var`, so [draw] looks it up.
 */
const COLORS: Record<NavMark, string> = {
  added: 'var(--diff-added)',
  changed: 'var(--diff-changed)',
  deleted: 'var(--diff-removed)',
  error: 'var(--syntax-invalid)',
  warning: 'var(--syntax-warning)',
}

/** A color written as above, as the variable's own name: `--diff-added` for the first. */
function variableIn(color: string): string {
  return color.slice('var('.length, -1)
}

/** What the measure pass read for the draw: the runs, already scaled to the bar. */
interface Measured {
  runs: Run[]
  barHeight: number
}

class NavBar {
  private readonly bar: HTMLElement
  private readonly canvas: HTMLCanvasElement
  private readonly box: HTMLElement
  private readonly onScroll = () => this.placeBox()

  constructor(private readonly view: EditorView) {
    this.bar = document.createElement('div')
    this.bar.className = 'skiff-navbar'
    this.bar.setAttribute('aria-hidden', 'true')
    this.canvas = document.createElement('canvas')
    this.box = document.createElement('div')
    this.box.className = 'skiff-navbar-box'
    this.bar.append(this.canvas, this.box)
    // On the editor's root, not in the scroller: it stays where it is while the document moves, and
    // goes with the view when the markdown viewer hides it.
    view.dom.appendChild(this.bar)
    view.scrollDOM.addEventListener('scroll', this.onScroll, { passive: true })
    this.schedule()
  }

  update(update: ViewUpdate): void {
    const marksMoved = update.docChanged || update.geometryChanged || update.viewportChanged ||
      update.transactions.some((tr) => tr.reconfigured || tr.effects.some((e) => e.is(setBaseline) || e.is(setDiagnosticsEffect)))
    if (marksMoved) this.schedule()
  }

  destroy(): void {
    this.view.scrollDOM.removeEventListener('scroll', this.onScroll)
    this.bar.remove()
  }

  /** One read and one draw per frame however many updates ask, which is what the key is for. */
  private schedule(): void {
    this.view.requestMeasure<Measured>({ key: this, read: () => this.read(), write: (measured) => this.draw(measured) })
  }

  private read(): Measured {
    const { view } = this
    const scroller = view.scrollDOM
    const barHeight = this.bar.clientHeight
    // Where the document starts within what scrolls: the menu's room above the first line.
    const offset = view.documentTop - scroller.getBoundingClientRect().top + scroller.scrollTop
    const placed: Placed[] = []
    const lineAt = (pos: number) => view.lineBlockAt(pos)
    for (const [pos, mark] of gitMarksIn(view.state)) {
      const line = lineAt(pos)
      // A deletion is where the lines met: the top of the one after them.
      placed.push(mark === 'deleted'
        ? { top: offset + line.top, bottom: offset + line.top, mark }
        : { top: offset + line.top, bottom: offset + line.bottom, mark })
    }
    forEachDiagnostic(view.state, (diagnostic, from, to) => {
      const mark = shownSeverity(diagnostic.severity)
      if (mark) placed.push({ top: offset + lineAt(from).top, bottom: offset + lineAt(to).bottom, mark })
    })
    return { runs: barRuns(placed, scroller.scrollHeight, barHeight), barHeight }
  }

  private draw({ runs, barHeight }: Measured): void {
    const ratio = window.devicePixelRatio || 1
    const { canvas } = this
    const width = Math.round(NAV_BAR_WIDTH * ratio)
    const height = Math.round(barHeight * ratio)
    if (canvas.width !== width || canvas.height !== height) {
      canvas.width = width
      canvas.height = height
    }
    const context = canvas.getContext('2d')
    if (context) {
      context.clearRect(0, 0, width, height)
      // Read now rather than kept: a theme change reaches the views as a reconfiguration, which draws again.
      const style = getComputedStyle(this.view.dom)
      const half = width / 2
      for (const run of runs) {
        context.fillStyle = style.getPropertyValue(variableIn(COLORS[run.mark])).trim() || 'transparent'
        context.fillRect(run.lane === 'git' ? 0 : half, run.y * ratio, half, run.height * ratio)
      }
    }
    this.placeBox()
  }

  /** On every scroll, so only the box moves; the marks stay as they were drawn. */
  private placeBox(): void {
    const scroller = this.view.scrollDOM
    const { y, height } = viewBox(scroller.scrollTop, scroller.clientHeight, scroller.scrollHeight, this.bar.clientHeight)
    this.box.style.transform = `translateY(${y}px)`
    this.box.style.height = `${height}px`
  }
}

const navBarPlugin = ViewPlugin.fromClass(NavBar)

export function navBar(): Extension {
  return [
    navBarPlugin,
    EditorView.theme({
      // The browser's scrollbar, pushed past the edge and clipped there; see [SCROLLBAR_TUCK]. A
      // tooltip is `position: fixed`, which the clip does not reach. `clip`, not `hidden`: a box that
      // hides its overflow can still be scrolled, and bringing the cursor into view scrolled the
      // editor itself 24px sideways (tablet, `wrap = false`), the scrollbar back on the screen with it.
      '&': { overflow: 'clip' },
      '.cm-scroller': { marginRight: `-${SCROLLBAR_TUCK}px` },
      // Fixed to the screen, between the menu and the banner, clear of the command button (16px in).
      '.skiff-navbar': {
        position: 'fixed',
        top: 'var(--topbar-space)',
        right: '0',
        bottom: 'var(--banner-space, 0px)',
        width: `${NAV_BAR_WIDTH}px`,
        pointerEvents: 'none',
        zIndex: '4',
      },
      '.skiff-navbar canvas': { position: 'absolute', inset: '0', width: '100%', height: '100%' },
      '.skiff-navbar-box': {
        position: 'absolute',
        top: '0',
        left: '0',
        right: '0',
        backgroundColor: 'var(--ui-muted)',
        opacity: '0.3',
      },
    }),
  ]
}

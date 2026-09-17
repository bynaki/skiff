// M0 spike: a read-only CodeMirror 6 viewer over a 2 MB file from Kotlin, pinch zoom, and frame
// statistics printed to the HUD and to logcat (through console.log).
import { EditorState } from '@codemirror/state'
import { EditorView, lineNumbers } from '@codemirror/view'
import { defaultHighlightStyle, syntaxHighlighting } from '@codemirror/language'
import { javascript } from '@codemirror/lang-javascript'
import { rpc } from './bridge'
import { anchorAt, currentFontSize, installPinchZoom, zoomTo } from './zoom'

const hud = document.getElementById('hud')!
const hudLines = new Map<string, string>()
function report(key: string, value: string) {
  hudLines.set(key, value)
  hud.textContent = [...hudLines].map(([k, v]) => `${k}: ${v}`).join('\n')
  console.log(`${key}: ${value}`)
}

const nextFrame = () => new Promise<number>((resolve) => requestAnimationFrame(resolve))

/** Frame intervals while something moves; a session ends after 300 ms without scroll or zoom. */
class FrameMonitor {
  private intervals: number[] = []
  private last = 0
  private idleTimer = 0
  private running = false

  constructor(private readonly onSession: (summary: string) => void) {}

  poke() {
    clearTimeout(this.idleTimer)
    this.idleTimer = window.setTimeout(() => this.finish(), 300)
    if (this.running) return
    this.running = true
    this.intervals = []
    this.last = 0
    const loop = (now: number) => {
      if (!this.running) return
      if (this.last) this.intervals.push(now - this.last)
      this.last = now
      requestAnimationFrame(loop)
    }
    requestAnimationFrame(loop)
  }

  private finish() {
    this.running = false
    const sorted = [...this.intervals].sort((a, b) => a - b)
    if (sorted.length < 5) return
    const total = sorted.reduce((a, b) => a + b, 0)
    const at = (q: number) => sorted[Math.min(sorted.length - 1, Math.floor(sorted.length * q))].toFixed(1)
    this.onSession(
      `${sorted.length} frames, ${(1000 * sorted.length / total).toFixed(0)} fps, ` +
      `p50 ${at(0.5)} p95 ${at(0.95)} max ${sorted[sorted.length - 1].toFixed(1)} ms, ` +
      `>20ms ${sorted.filter((t) => t > 20).length}, >33ms ${sorted.filter((t) => t > 33).length}`,
    )
  }
}

async function main() {
  const t0 = performance.now()
  const params = new URLSearchParams(location.search)
  const bytes = Number(params.get('bytes')) || 2 * 1024 * 1024
  const { text } = await rpc<{ text: string }>('sampleText', { bytes })
  const t1 = performance.now()

  document.documentElement.style.setProperty('--code-font-size', `${currentFontSize()}px`)
  const view = new EditorView({
    parent: document.getElementById('editor')!,
    state: EditorState.create({
      doc: text,
      extensions: [
        lineNumbers(),
        EditorState.readOnly.of(true),
        EditorView.editable.of(false),
        javascript({ typescript: true }),
        syntaxHighlighting(defaultHighlightStyle),
        EditorView.theme({
          '&': { height: '100%', fontSize: 'var(--code-font-size)' },
          '.cm-scroller': { fontFamily: 'monospace', lineHeight: '1.5', touchAction: 'pan-x pan-y' },
        }),
      ],
    }),
  })
  const t2 = performance.now()
  await nextFrame()
  await nextFrame()
  const t3 = performance.now()
  report('file', `${(text.length / 1024 / 1024).toFixed(2)} M chars, ${view.state.doc.lines} lines`)
  report('load', `bridge ${(t1 - t0).toFixed(0)} ms, view ${(t2 - t1).toFixed(0)} ms, first paint ${(t3 - t2).toFixed(0)} ms`)

  const monitor = new FrameMonitor((summary) => report('last motion', summary))
  view.scrollDOM.addEventListener('scroll', () => monitor.poke(), { passive: true })
  report('font', `${currentFontSize()} px`)
  installPinchZoom(view, (size) => {
    monitor.poke()
    report('font', `${size.toFixed(1)} px`)
  })

  if (params.has('selftest')) await selftest(view)
}

function spread(times: number[]): string {
  const sorted = [...times].sort((a, b) => a - b)
  const at = (q: number) => sorted[Math.min(sorted.length - 1, Math.floor(sorted.length * q))].toFixed(0)
  return `p50 ${at(0.5)} p90 ${at(0.9)} max ${at(1)} ms`
}

async function selftest(view: EditorView) {
  const scroller = view.scrollDOM
  await new Promise((resolve) => setTimeout(resolve, 1000))

  // Scripted scroll: 150 px per frame for 180 frames, driven from the main thread.
  const start = performance.now()
  for (let i = 0; i < 180; i++) {
    scroller.scrollTop += 150
    await nextFrame()
  }
  report('selftest scroll', `180 frames in ${(performance.now() - start).toFixed(0)} ms`)
  await new Promise((resolve) => setTimeout(resolve, 500))

  // Jump to the middle of the file.
  const jump = performance.now()
  scroller.scrollTop = scroller.scrollHeight / 2
  await nextFrame()
  await nextFrame()
  report('selftest jump', `${(performance.now() - jump).toFixed(0)} ms to middle, line ${view.state.doc.lineAt(view.lineBlockAtHeight(scroller.scrollTop).from).number}`)

  // Zoom around the vertical centre: up to 40 px, down to 8 px, back to 14 px.
  const rect = scroller.getBoundingClientRect()
  const clientY = rect.top + rect.height / 2
  const anchor = anchorAt(view, clientY)
  const sizes: number[] = []
  for (let s = 14; s < 40; s *= 1.1) sizes.push(s)
  for (let s = 40; s > 8; s /= 1.1) sizes.push(s)
  for (let s = 8; s < 14; s *= 1.1) sizes.push(s)
  sizes.push(14)
  let maxDrift = 0
  const dispatchTimes: number[] = []
  const measureTimes: number[] = []
  for (const size of sizes) {
    const before = performance.now()
    zoomTo(view, size, anchor, clientY)
    dispatchTimes.push(performance.now() - before)
    // CodeMirror's measure is queued before this callback, so the time since the frame started is its cost.
    const frameStart = await nextFrame()
    measureTimes.push(performance.now() - frameStart)
    await nextFrame()
    const block = view.lineBlockAt(anchor.pos)
    const actual = view.documentTop + block.top + anchor.fraction * block.height
    maxDrift = Math.max(maxDrift, Math.abs(actual - clientY))
  }
  report('selftest zoom', `${sizes.length} steps, max drift ${maxDrift.toFixed(1)} px, dispatch ${spread(dispatchTimes)}, measure ${spread(measureTimes)}`)
  report('font', `${currentFontSize()} px`)
  console.log('selftest done')
}

main().catch((error) => report('error', String(error)))

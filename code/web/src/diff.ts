// Read-only unified diff on @codemirror/merge: the buffer is the new text, deleted lines come from
// the original as block widgets. A sign gutter puts "+" beside inserted lines and one "-" per
// deleted line beside each deletion widget; backgrounds take their opacity from --diff-alpha.
import type { Extension } from '@codemirror/state'
import { EditorView, GutterMarker, gutter } from '@codemirror/view'
import { Change, type Chunk, type DiffConfig, diff, getChunks, getOriginalDoc, unifiedMergeView } from '@codemirror/merge'

class SignMarker extends GutterMarker {
  constructor(private readonly sign: string, private readonly count = 1) {
    super()
  }

  override eq(other: GutterMarker): boolean {
    return other instanceof SignMarker && other.sign === this.sign && other.count === this.count
  }

  override toDOM(): Node {
    const dom = document.createElement('div')
    for (let i = 0; i < this.count; i++) dom.appendChild(document.createElement('div')).textContent = this.sign
    return dom
  }
}

const plus = new SignMarker('+')

/** The chunk whose B side starts at or before [pos], by binary search over chunks sorted on fromB. */
function chunkAt(chunks: readonly Chunk[], pos: number): Chunk | null {
  let lo = 0
  let hi = chunks.length - 1
  let found: Chunk | null = null
  while (lo <= hi) {
    const mid = (lo + hi) >> 1
    if (chunks[mid].fromB <= pos) {
      found = chunks[mid]
      lo = mid + 1
    } else {
      hi = mid - 1
    }
  }
  return found
}

const signGutter = gutter({
  class: 'cm-diffSigns',
  lineMarker(view, line) {
    const chunk = chunkAt(getChunks(view.state)?.chunks ?? [], line.from)
    return chunk && chunk.fromB < chunk.toB && line.from <= chunk.endB ? plus : null
  },
  widgetMarker(view, _widget, block) {
    const chunk = chunkAt(getChunks(view.state)?.chunks ?? [], block.from)
    if (!chunk || chunk.fromB !== block.from || chunk.fromA >= chunk.toA) return null
    const original = getOriginalDoc(view.state)
    return new SignMarker('-', original.lineAt(chunk.endA).number - original.lineAt(chunk.fromA).number + 1)
  },
  lineMarkerChange: (update) => update.docChanged || update.viewportChanged,
})

/**
 * `char`: the package's own character diff with its size limit lifted, so a 2 MB file is not given
 * up on as one chunk. `line`: lines first, the way git does, then characters only inside the changed
 * line ranges.
 */
export type DiffMode = 'char' | 'line'

const refineLimit: DiffConfig = { scanLimit: 500 }

function lineDiff(a: string, b: string): readonly Change[] {
  const ids = new Map<string, number>()
  const encode = (text: string) => {
    const starts: number[] = []
    const codes: string[] = []
    let pos = 0
    for (const line of text.split('\n')) {
      starts.push(pos)
      pos += line.length + 1
      let id = ids.get(line)
      if (id === undefined) ids.set(line, (id = ids.size))
      // Skip the surrogate range, which the diff refuses to split inside.
      codes.push(String.fromCharCode(id < 0xd800 ? id : id + 0x800))
    }
    starts.push(pos)
    return { code: codes.join(''), starts }
  }
  const ea = encode(a)
  const eb = encode(b)
  if (ids.size > 0xf7ff) return diff(a, b, refineLimit)

  const changes: Change[] = []
  for (const c of diff(ea.code, eb.code)) {
    const fromA = ea.starts[c.fromA]
    const toA = Math.min(ea.starts[c.toA], a.length)
    const fromB = eb.starts[c.fromB]
    const toB = Math.min(eb.starts[c.toB], b.length)
    if (fromA === toA || fromB === toB) {
      changes.push(new Change(fromA, toA, fromB, toB))
      continue
    }
    for (const inner of diff(a.slice(fromA, toA), b.slice(fromB, toB), refineLimit)) {
      changes.push(new Change(inner.fromA + fromA, inner.toA + fromA, inner.fromB + fromB, inner.toB + fromB))
    }
  }
  return changes
}

export function unifiedDiff(original: string, mode: DiffMode): Extension {
  return [
    unifiedMergeView({
      original,
      diffConfig: mode === 'char' ? { scanLimit: 1e9, timeout: 5000 } : { override: lineDiff },
      mergeControls: false,
      highlightChanges: true,
      gutter: false,
      syntaxHighlightDeletions: true,
    }),
    signGutter,
    EditorView.theme({
      '.cm-changedLine, &.cm-merge-b .cm-changedLine': { background: 'rgba(46, 160, 67, var(--diff-alpha))' },
      '.cm-deletedChunk': { background: 'rgba(248, 81, 73, var(--diff-alpha))', paddingLeft: '0' },
      '.cm-insertedLine, .cm-changedText': { background: 'rgba(46, 160, 67, calc(var(--diff-alpha) * 2))', textDecoration: 'none' },
      '.cm-deletedChunk .cm-deletedText': { background: 'rgba(248, 81, 73, calc(var(--diff-alpha) * 2))' },
      '.cm-deletedLine del': { textDecoration: 'none' },
      '.cm-diffSigns .cm-gutterElement': { width: '1.2em', textAlign: 'center' },
    }),
  ]
}

/**
 * Spike data: the same file with a change every 40 lines of blocks. Each touched block gets one
 * modified line, two deleted lines and three inserted ones.
 */
export function sampleEdits(text: string): string {
  const lines = text.split('\n')
  const out: string[] = []
  for (let i = 0; i < lines.length; i++) {
    const block = Math.floor(i / 8)
    if (block % 40 !== 5) {
      out.push(lines[i])
      continue
    }
    switch (i % 8) {
      case 1: out.push(lines[i].replace('offset = ', 'offset: number = ')); break
      case 3: case 4: break
      case 5:
        out.push(lines[i])
        out.push('  // 새 줄: 빈 줄은 건너뛴다')
        out.push('  if (lines.length === 0) return []')
        out.push(`  console.debug('chunk', ${block}, lines.length)`)
        break
      default: out.push(lines[i])
    }
  }
  return out.join('\n')
}

/**
 * Works around a bug in @codemirror/view 6.43.12 (still in main as of 2026-09-17). When a block
 * widget sits on a line boundary, HeightMapBranch.forEachLine recurses with `mid.to + 1` and
 * `mid.from - 1` without clamping them to the requested range, so viewportLineBlocks grows to
 * thousands of off-screen lines. Every gutter then renders an element for each of them, which made
 * a zoom step five times slower in the unified diff. This reaches into private internals and is
 * spike code only.
 */
export function patchViewportLineBlocks(view: EditorView): void {
  type Oracle = unknown
  interface Block { from: number; to: number }
  interface HeightNode {
    left?: HeightNode
    right: HeightNode
    break: number
    height: number
    length: number
    lineAt(value: number, type: number, oracle: Oracle, top: number, offset: number): Block
    forEachLine(from: number, to: number, oracle: Oracle, top: number, offset: number, f: (b: Block) => void): void
  }
  const heightMap = (view as unknown as { viewState: { heightMap: HeightNode } }).viewState.heightMap
  let node: HeightNode | undefined = heightMap
  while (node && !node.left) node = undefined
  if (!node) return
  const QUERY_BY_POS = 0
  Object.getPrototypeOf(node).forEachLine = function (
    this: HeightNode, from: number, to: number, oracle: Oracle, top: number, offset: number, f: (b: Block) => void,
  ) {
    const left = this.left!
    const rightTop = top + left.height
    const rightOffset = offset + left.length + this.break
    if (this.break) {
      if (from < rightOffset) left.forEachLine(from, to, oracle, top, offset, f)
      if (to >= rightOffset) this.right.forEachLine(from, to, oracle, rightTop, rightOffset, f)
    } else {
      const mid = this.lineAt(rightOffset, QUERY_BY_POS, oracle, top, offset)
      if (from < mid.from) left.forEachLine(from, Math.min(to, mid.from - 1), oracle, top, offset, f)
      if (mid.to >= from && mid.from <= to) f(mid)
      if (to > mid.to) this.right.forEachLine(Math.max(from, mid.to + 1), to, oracle, rightTop, rightOffset, f)
    }
  }
  view.requestMeasure()
}

// The diff layer (docs/skiffcode.spec.md "레이어"): a read-only unified diff on @codemirror/merge.
// The buffer is the new text and deleted lines come from the original as block widgets. A sign gutter
// puts "+" beside inserted lines and one "-" per deleted line beside each deletion widget; line
// backgrounds take their opacity from --diff-alpha, which `settings.toml`'s `diff_alpha` sets.
//
// The widgets sit on line boundaries, which trips a bug in @codemirror/view 6.43.12's
// HeightMapBranch.forEachLine: `patches/` clamps it (AGENTS.md).
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

const refineLimit: DiffConfig = { scanLimit: 500 }

function lineDiff(a: string, b: string): readonly Change[] {
  const ids = new Map<string, number>()
  const encode = (text: string) => {
    const starts: number[] = []
    const codes: string[] = []
    // A line keeps its newline, so the last one without one is a different line from the same text
    // with one, as it is to git, and the line starts end at the text's own length. Split at the
    // newlines instead, the position after the last line was one past the end, and a change at the
    // end of the file reached outside it.
    for (let pos = 0; pos < text.length;) {
      starts.push(pos)
      const end = text.indexOf('\n', pos)
      const line = text.slice(pos, (pos = end < 0 ? text.length : end + 1))
      let id = ids.get(line)
      if (id === undefined) ids.set(line, (id = ids.size))
      // Skip the surrogate range, which the diff refuses to split inside.
      codes.push(String.fromCharCode(id < 0xd800 ? id : id + 0x800))
    }
    starts.push(text.length)
    return { code: codes.join(''), starts }
  }
  const ea = encode(a)
  const eb = encode(b)
  if (ids.size > 0xf7ff) return diff(a, b, refineLimit)

  const changes: Change[] = []
  for (const c of diff(ea.code, eb.code)) {
    const fromA = ea.starts[c.fromA]
    const toA = ea.starts[c.toA]
    const fromB = eb.starts[c.fromB]
    const toB = eb.starts[c.toB]
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

/**
 * Lines first, as git compares, then characters only inside the changed line ranges. The package's
 * own character diff gives a 2 MB file up as one chunk, and with its limit lifted takes seconds and
 * is still less exact (M0). The git gutter's chunks are built with this too.
 */
export const lineDiffConfig: DiffConfig = { override: lineDiff }

/**
 * One for every diff layer: each call of `EditorView.theme` adds a stylesheet of its own. Only whole
 * lines are tinted: the words that changed are not marked (`highlightChanges: false`), and the `ins`
 * the package wraps every inserted range in takes no background, or the tint doubles over the text.
 */
const diffTheme = EditorView.theme({
  '.cm-changedLine, &.cm-merge-b .cm-changedLine': { background: 'color-mix(in srgb, var(--diff-added) calc(var(--diff-alpha, 0.3) * 100%), transparent)' },
  '.cm-deletedChunk': { background: 'color-mix(in srgb, var(--diff-removed) calc(var(--diff-alpha, 0.3) * 100%), transparent)', paddingLeft: '0' },
  '.cm-deletedLine del': { textDecoration: 'none' },
  '.cm-diffSigns .cm-gutterElement': { width: '1.2em', textAlign: 'center' },
})

/** The buffer against [original], read only. */
export function unifiedDiff(original: string): Extension {
  return [
    unifiedMergeView({
      original,
      diffConfig: lineDiffConfig,
      mergeControls: false,
      highlightChanges: false,
      gutter: false,
      syntaxHighlightDeletions: true,
    }),
    signGutter,
    diffTheme,
  ]
}

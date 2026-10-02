// The git gutter (docs/skiffcode.spec.md "git과 LSP"): a thin bar beside the line numbers wherever
// the buffer differs from the file as HEAD has it — lines added, lines changed, and a mark where
// lines were deleted. Every layer shows it, so it sits outside the layer compartment.
//
// The baseline comes from Kotlin (`baseline`) and is asked for again whenever HEAD may have moved;
// the buffer is compared with it here, so typing moves the bars without asking anyone.
import { Chunk } from '@codemirror/merge'
import { type EditorState, type Extension, RangeSet, RangeSetBuilder, StateEffect, StateField, Text } from '@codemirror/state'
import { EditorView, GutterMarker, gutter } from '@codemirror/view'
import { lineDiffConfig } from './diff'

export type GitMark = 'added' | 'changed' | 'deleted'

/** HEAD's copy of the file, or null for no gutter: no project, no git, or a file HEAD does not have. */
export const setBaseline = StateEffect.define<string | null>()

interface Baseline {
  /** As Kotlin sent it, to tell a baseline asked for again from one that changed. */
  text: string
  doc: Text
  chunks: readonly Chunk[]
}

/**
 * The chunks between the baseline and the buffer. Built whole when a baseline arrives — about a
 * quarter of a second for 2 MB, measured in M0 — and after that only around each edit, which is
 * what [Chunk.updateB] is for.
 */
const baselineField = StateField.define<Baseline | null>({
  create: () => null,
  update(value, tr) {
    for (const effect of tr.effects) {
      if (!effect.is(setBaseline)) continue
      if (effect.value === null) return null
      if (value?.text === effect.value) continue
      const doc = Text.of(effect.value.split('\n'))
      return { text: effect.value, doc, chunks: Chunk.build(doc, tr.newDoc, lineDiffConfig) }
    }
    if (!value || !tr.docChanged) return value
    return { ...value, chunks: Chunk.updateB(value.chunks, value.doc, tr.newDoc, tr.changes, lineDiffConfig) }
  },
})

/**
 * Which lines are marked, as the position each line starts at, in order. A chunk with lines on the
 * buffer's side marks each of them, as added when HEAD had none there and as changed otherwise; one
 * with none — lines that were only deleted — marks the line that follows, which a chunk always
 * starts on. Two chunks never meet on a line: ones that touch are one chunk.
 *
 * An empty file is one empty line to CodeMirror and no lines to git, so a chunk against an empty
 * [base] is lines added, and one in an empty [doc] is lines deleted. A chunk with no lines on either
 * side is nothing: `diff` itself hands out an empty change now and then, and it would otherwise read
 * as a deletion.
 */
export function gitMarks(chunks: readonly Chunk[], base: Text, doc: Text): Array<[number, GitMark]> {
  const marks: Array<[number, GitMark]> = []
  for (const chunk of chunks) {
    if (chunk.fromA === chunk.toA && chunk.fromB === chunk.toB) continue
    if (chunk.fromB === chunk.toB || doc.length === 0) {
      marks.push([chunk.fromB, 'deleted'])
      continue
    }
    const mark = chunk.fromA === chunk.toA || base.length === 0 ? 'added' : 'changed'
    for (let pos = chunk.fromB; pos <= chunk.endB;) {
      const line = doc.lineAt(pos)
      marks.push([line.from, mark])
      pos = line.to + 1
    }
  }
  return marks
}

class GitMarker extends GutterMarker {
  constructor(readonly mark: GitMark) {
    super()
    this.elementClass = `cm-git-${mark}`
  }

  override eq(other: GutterMarker): boolean {
    return other instanceof GitMarker && other.mark === this.mark
  }
}

const markers: Record<GitMark, GitMarker> = {
  added: new GitMarker('added'),
  changed: new GitMarker('changed'),
  deleted: new GitMarker('deleted'),
}

const markerField = StateField.define<RangeSet<GutterMarker>>({
  create: () => RangeSet.empty,
  update(value, tr) {
    const baseline = tr.state.field(baselineField)
    if (baseline === tr.startState.field(baselineField)) return value
    if (!baseline) return RangeSet.empty
    const builder = new RangeSetBuilder<GutterMarker>()
    for (const [pos, mark] of gitMarks(baseline.chunks, baseline.doc, tr.state.doc)) builder.add(pos, pos, markers[mark])
    return builder.finish()
  },
})

export function gitGutter(): Extension {
  return [
    baselineField,
    markerField,
    gutter({ class: 'cm-gitGutter', markers: (view) => view.state.field(markerField) }),
    EditorView.theme({
      '.cm-gitGutter': { width: '4px' },
      '.cm-gitGutter .cm-gutterElement': { padding: '0' },
      '.cm-git-added': { backgroundColor: 'var(--diff-added)' },
      '.cm-git-changed': { backgroundColor: 'var(--diff-changed)' },
      // A wedge pointing in at the boundary between the lines either side of where the deleted ones were.
      '.cm-git-deleted': { position: 'relative' },
      '.cm-git-deleted::after': {
        content: '""',
        position: 'absolute',
        top: '-4px',
        left: '0',
        borderLeft: '6px solid var(--diff-removed)',
        borderTop: '4px solid transparent',
        borderBottom: '4px solid transparent',
      },
    }),
  ]
}

/** The marks [state] shows, for the tests: what the gutter draws, without a view to draw it. */
export function gitMarksIn(state: EditorState): Array<[number, GitMark]> {
  const baseline = state.field(baselineField, false)
  return baseline ? gitMarks(baseline.chunks, baseline.doc, state.doc) : []
}

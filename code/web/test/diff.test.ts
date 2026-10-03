// The diff layer: where ③ goes, and what the layer finds between the buffer and what it compares with.
import { describe, expect, test } from 'vitest'
import { EditorState } from '@codemirror/state'
import { getChunks } from '@codemirror/merge'
import { lineDiffConfig, unifiedDiff } from '../src/diff'
import { nextLayer } from '../src/layers/pane'

/** The chunks as line ranges, original then buffer, which is how a person reads a diff. */
function chunks(state: EditorState, original: string): Array<[number, number, number, number]> {
  const lineOf = (pos: number) => original.slice(0, pos).split('\n').length
  return getChunks(state)!.chunks.map((chunk) => [
    lineOf(chunk.fromA), lineOf(chunk.toA), state.doc.lineAt(chunk.fromB).number, state.doc.lineAt(chunk.toB).number,
  ])
}

describe('③', () => {
  test('goes through the viewer, the editor and the diff layer', () => {
    expect(nextLayer('viewer', true)).toBe('editor')
    expect(nextLayer('editor', true)).toBe('diff')
    expect(nextLayer('diff', true)).toBe('viewer')
  })

  test('passes the diff layer over while there is nothing to compare with', () => {
    expect(nextLayer('viewer', false)).toBe('editor')
    expect(nextLayer('editor', false)).toBe('viewer')
    // Left with nothing to compare with while it was showing.
    expect(nextLayer('diff', false)).toBe('viewer')
  })
})

describe('the diff layer', () => {
  test('finds the lines changed and the lines added, the way git does', () => {
    const original = 'a\nb\nc\n'
    const state = EditorState.create({ doc: 'a\nB\nc\nnew\n', extensions: unifiedDiff(original) })
    expect(chunks(state, original)).toEqual([[2, 3, 2, 3], [4, 4, 4, 5]])
  })

  test('finds nothing where the buffer is what it compares with', () => {
    const state = EditorState.create({ doc: 'a\nb\n', extensions: unifiedDiff('a\nb\n') })
    expect(getChunks(state)!.chunks).toEqual([])
  })

  test('follows a change the buffer takes from outside', () => {
    const original = 'a\nb\nc\n'
    const state = EditorState.create({ doc: original, extensions: unifiedDiff(original) })
    const changed = state.update({ changes: { from: 2, to: 3, insert: 'B' } }).state
    expect(chunks(changed, original)).toEqual([[2, 3, 2, 3]])
  })
})

describe('the line diff', () => {
  /** [a] with [changes] applied, after checking that they are in order, inside both texts and line up between them. */
  function apply(a: string, b: string, changes: ReturnType<NonNullable<typeof lineDiffConfig.override>>): string {
    let out = ''
    let lastA = 0
    let lastB = 0
    for (const change of changes) {
      expect(change.fromA - lastA, 'what is left between changes is the same on both sides').toBe(change.fromB - lastB)
      expect(change.toA).toBeLessThanOrEqual(a.length)
      expect(change.toB).toBeLessThanOrEqual(b.length)
      out += a.slice(lastA, change.fromA) + b.slice(change.fromB, change.toB)
      lastA = change.toA
      lastB = change.toB
    }
    return out + a.slice(lastA)
  }

  test('stays inside a text whose end changed', () => {
    // The file that found it: the end of `diff.ts` went, and its last line was the new end.
    for (const [a, b] of [['x\n\ny', 'x\n'], ['x\ny\n', 'x'], ['x', 'x\n'], ['', 'x\n'], ['x\n', ''], ['a\nb\nc\n', 'a\nb\n']]) {
      expect(apply(a, b, lineDiffConfig.override!(a, b)), JSON.stringify([a, b])).toBe(b)
    }
  })

  test('turns any text into any other', () => {
    // Small texts from a few lines, with and without a newline at the end, in every pairing a seed makes.
    let seed = 1
    const random = (n: number) => ((seed = (seed * 48271) % 0x7fffffff), seed % n)
    const text = () => Array.from({ length: random(6) }, () => ['a', 'b', '', 'aa'][random(4)]).join('\n') + (random(2) ? '\n' : '')
    for (let i = 0; i < 2000; i++) {
      const a = text()
      const b = text()
      expect(apply(a, b, lineDiffConfig.override!(a, b)), JSON.stringify([a, b])).toBe(b)
    }
  })
})

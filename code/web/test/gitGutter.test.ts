// Which lines the git gutter marks, against HEAD's copy of the file, as the buffer is typed in.
import { describe, expect, test } from 'vitest'
import { EditorState } from '@codemirror/state'
import { type GitMark, gitGutter, gitMarksIn, setBaseline } from '../src/gitGutter'

/** A buffer holding [text] with the gutter, given [baseline] as HEAD's copy. */
function buffer(text: string, baseline: string | null): EditorState {
  const state = EditorState.create({ doc: text, extensions: gitGutter() })
  return state.update({ effects: setBaseline.of(baseline) }).state
}

/** The marks by line number, which is how a person reads the gutter. */
function marks(state: EditorState): Array<[number, GitMark]> {
  return gitMarksIn(state).map(([pos, mark]) => [state.doc.lineAt(pos).number, mark])
}

describe('the git gutter', () => {
  test('marks nothing where the buffer is what HEAD has', () => {
    expect(marks(buffer('a\nb\nc\n', 'a\nb\nc\n'))).toEqual([])
  })

  test('marks nothing without a baseline', () => {
    expect(marks(buffer('a\nb\n', null))).toEqual([])
    expect(marks(EditorState.create({ doc: 'a', extensions: gitGutter() }))).toEqual([])
  })

  test('marks lines added, each of them', () => {
    expect(marks(buffer('a\nnew\nnewer\nb\n', 'a\nb\n'))).toEqual([[2, 'added'], [3, 'added']])
  })

  test('marks a line changed', () => {
    expect(marks(buffer('a\nB\nc\n', 'a\nb\nc\n'))).toEqual([[2, 'changed']])
  })

  test('marks the line after lines deleted', () => {
    expect(marks(buffer('a\nd\n', 'a\nb\nc\nd\n'))).toEqual([[2, 'deleted']])
  })

  test('marks nothing for the empty change diff sometimes hands out', () => {
    // `diff` gives this pair a change with no lines on either side, at "eight".
    const head = 'one\ntwo\nthree\nfour\nfive\nsix\nseven\neight\n'
    const now = 'one\nTWO\nthree\nfour\nseven\neight\nnine\n'
    expect(marks(buffer(now, head))).toEqual([[2, 'changed'], [5, 'deleted'], [7, 'added']])
  })

  test('marks lines deleted from the top, and from the end', () => {
    expect(marks(buffer('c\n', 'a\nb\nc\n'))).toEqual([[1, 'deleted']])
    // The empty line after the last newline is where the mark goes.
    expect(marks(buffer('a\n', 'a\nb\nc\n'))).toEqual([[2, 'deleted']])
  })

  test('marks every line of a file HEAD has empty as added', () => {
    expect(marks(buffer('a\nb', ''))).toEqual([[1, 'added'], [2, 'added']])
  })

  test('marks a buffer emptied of what HEAD has as deleted', () => {
    expect(marks(buffer('', 'a\nb\n'))).toEqual([[1, 'deleted']])
  })

  test('follows typing', () => {
    let state = buffer('a\nb\nc\n', 'a\nb\nc\n')
    state = state.update({ changes: { from: 2, insert: 'x\n' } }).state
    expect(marks(state)).toEqual([[2, 'added']])
    state = state.update({ changes: { from: 0, to: 1, insert: 'A' } }).state
    expect(marks(state)).toEqual([[1, 'changed'], [2, 'changed']])
    // Typed back to what HEAD has: no marks left.
    state = state.update({ changes: { from: 0, to: 3, insert: 'a' } }).state
    expect(state.doc.toString()).toBe('a\nb\nc\n')
    expect(marks(state)).toEqual([])
  })

  test('takes a baseline that moved, and drops one that went', () => {
    let state = buffer('a\nb\n', 'a\nb\n')
    // A commit that took the buffer's lines out of HEAD: the same buffer, now with lines HEAD lacks.
    state = state.update({ effects: setBaseline.of('a\n') }).state
    expect(marks(state)).toEqual([[2, 'added']])
    state = state.update({ effects: setBaseline.of(null) }).state
    expect(marks(state)).toEqual([])
  })

  test('compares as git does, a line at a time, in a file with hangul', () => {
    expect(marks(buffer('가나다\n라마바 사\n아자차\n', '가나다\n라마바\n아자차\n'))).toEqual([[2, 'changed']])
  })
})

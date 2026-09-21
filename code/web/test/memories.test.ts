// What the page keeps of the files that are open but not on the screen, once there are more of
// them than it will hold buffers for.
import { EditorState } from '@codemirror/state'
import { describe, expect, test } from 'vitest'
import { type PaneMemory, adoptInto, dirtyFlag, isDirty } from '../src/layers/pane'
import { RETAINED_BUFFERS, forgetOldBuffers } from '../src/memories'

/** What a file that has been to the editor leaves behind: a buffer, and the text it holds. */
function left(text = 'a file'): PaneMemory {
  return { state: EditorState.create({ doc: text, extensions: [dirtyFlag] }), source: text, layer: 'editor', line: 3 }
}

/** What a markdown file that has only ever been read leaves behind: no buffer, just its text. */
function read(text = 'a file'): PaneMemory {
  return { state: null, source: text, layer: 'viewer', line: 3 }
}

/** The same, after someone typed in it. A pane writes the buffer back to `source` on its way out. */
function typedIn(memory: PaneMemory): PaneMemory {
  memory.state = memory.state!.update({ changes: { from: 0, insert: 'typed ' } }).state
  memory.source = memory.state.doc.toString()
  return memory
}

/** The open files that are off the screen, the one that left the screen longest ago first. */
function offScreen(...memories: PaneMemory[]): Map<number, PaneMemory> {
  return new Map(memories.map((memory, index) => [index + 1, memory]))
}

const buffers = (memories: Map<number, PaneMemory>) => [...memories.values()].map((memory) => memory.state !== null)

describe('the buffers kept for files that are off the screen', () => {
  test('are all of them while there is room', () => {
    const memories = offScreen(left(), left(), left())
    forgetOldBuffers(memories)
    expect(buffers(memories)).toEqual([true, true, true])
  })

  test('default to thirty', () => {
    const memories = offScreen(...Array.from({ length: RETAINED_BUFFERS + 2 }, () => left()))
    forgetOldBuffers(memories)
    expect(buffers(memories).filter(Boolean)).toHaveLength(RETAINED_BUFFERS)
    expect(buffers(memories).slice(0, 2)).toEqual([false, false])
  })

  test('go in the order the files left the screen', () => {
    const memories = offScreen(left(), left(), left(), left())
    forgetOldBuffers(memories, 2)
    expect(buffers(memories)).toEqual([false, false, true, true])
  })

  test('are all a file loses: its text, its layer and its line stay', () => {
    const memories = offScreen(left('the text'), left(), left())
    forgetOldBuffers(memories, 1)
    expect(memories.get(1)).toEqual({ state: null, source: 'the text', layer: 'editor', line: 3 })
  })

  test('are never taken from a file that has been typed in', () => {
    const memories = offScreen(typedIn(left()), left(), left())
    forgetOldBuffers(memories, 1)
    expect(buffers(memories)).toEqual([true, false, true])
    expect(isDirty(memories.get(1)!.state!)).toBe(true)
  })

  test('are taken from a clean file that a typed-in one has pushed out of the places', () => {
    const memories = offScreen(left(), typedIn(left()), left())
    forgetOldBuffers(memories, 2)
    expect(buffers(memories)).toEqual([false, true, true])
  })

  test('are taken again once the typed-in file has taken the file\'s text', () => {
    const memories = offScreen(typedIn(left()), left(), left())
    memories.get(1)!.state = adoptInto(memories.get(1)!.state!, 'what the file says now')
    forgetOldBuffers(memories, 1)
    expect(buffers(memories)).toEqual([false, false, true])
  })

  test('are not a place taken by a file that has never been to the editor', () => {
    const memories = offScreen(left(), read(), left())
    forgetOldBuffers(memories, 2)
    expect(buffers(memories)).toEqual([true, false, true])
  })
})

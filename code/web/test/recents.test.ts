// What the palette remembers between one page and the next.
import { afterEach, describe, expect, test } from 'vitest'
import { createPalette } from '../src/palette'
import { readRecents, writeRecents } from '../src/recents'

/** The WebView's storage, which plain node has none of. */
function fakeStorage(held: Record<string, string> = {}): Storage {
  const store = new Map(Object.entries(held))
  return {
    get length() { return store.size },
    clear: () => store.clear(),
    getItem: (key: string) => store.get(key) ?? null,
    key: (index: number) => [...store.keys()][index] ?? null,
    removeItem: (key: string) => void store.delete(key),
    setItem: (key: string, value: string) => void store.set(key, value),
  }
}

const give = (storage: Storage | null) => {
  globalThis.localStorage = storage as Storage
}

afterEach(() => give(null))

describe('what the palette ran last', () => {
  test('is nothing at all when the WebView has no storage', () => {
    // `localStorage` is null until domStorageEnabled is set, which is how an older build behaves.
    give(null)
    expect([...readRecents()]).toEqual([])
    expect(() => writeRecents(new Map([['command', ['Toggle Layer']]]))).not.toThrow()
  })

  test('comes back the way it went in, mode by mode', () => {
    give(fakeStorage())
    writeRecents(new Map([['command', ['Toggle Layer', 'Reset Zoom']], ['file', ['notes.md']]]))
    expect([...readRecents()]).toEqual([['command', ['Toggle Layer', 'Reset Zoom']], ['file', ['notes.md']]])
  })

  test('is nothing when what is held there is not what was left', () => {
    for (const held of ['', 'not json', '"a string"', '[1, 2]', '{"command": "not a list"}']) {
      give(fakeStorage({ 'palette.recent': held }))
      expect([...readRecents()]).toEqual([])
    }
  })

  test('takes the names out of a list that has other things in it, and stops at five', () => {
    give(fakeStorage({ 'palette.recent': JSON.stringify({ command: ['a', 7, 'b', null, 'c', 'd', 'e', 'f'], nonsense: ['x'] }) }))
    expect([...readRecents()]).toEqual([['command', ['a', 'b', 'c', 'd', 'e']]])
  })

  test('is what the palette opens on', () => {
    give(fakeStorage())
    const first = createPalette({ items: () => [{ name: 'Toggle Layer', run: () => {} }] })
    first.open()
    first.type('layer')
    first.run()
    const next = createPalette({ items: () => [{ name: 'Toggle Layer', run: () => {} }] })
    next.open()
    expect(next.results.map((item) => item.name)).toEqual(['Toggle Layer'])
  })
})

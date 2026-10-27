// The sidebar's outline tab: what Markdown's headings and a source file's symbols become, which tab
// is shown, where the screen is, and an outline whose source answers late.
import { afterEach, describe, expect, test } from 'vitest'
import {
  type OutlineFile,
  type OutlineItem,
  type OutlineSource,
  createOutline,
  currentItem,
  outlineEnabled,
  rememberTab,
  rememberedTab,
  sourceFor,
  symbolItems,
  tabToShow,
} from '../src/outline'
import type { DocSymbol } from '../src/symbols'

/** The items as `depth name:line`, indented by depth, which reads better in a failure than objects do. */
const flat = (items: OutlineItem[]): string[] => items.map((item) => `${'  '.repeat(item.depth)}${item.name}:${item.line}`)

const file = (text: string, name = 'a.md', id = name): OutlineFile => ({ id, name, text })

async function headings(text: string): Promise<string[]> {
  return flat(await sourceFor('a.md')!.items(file(text)))
}

describe('the outline of a Markdown file', () => {
  test('is its headings indented by level, the shallowest at the edge', async () => {
    const text = [
      '## Install', // 1
      '',
      '### Needs', // 3
      '#### Build', // 4
      '',
      'Use', // 6
      '---',
      '',
      '##### Deep', // 9
    ].join('\n')

    expect(await headings(text)).toEqual([
      'Install:1',
      '  Needs:3',
      '    Build:4',
      'Use:6',
      '      Deep:9',
    ])
  })

  test('leaves a skipped level skipped', async () => {
    expect(await headings('# One\n\n### Three\n\n# Again')).toEqual(['One:1', '    Three:3', 'Again:5'])
  })

  test('has underlined headings at their level', async () => {
    expect(await headings('Top\n===\n\nSide\n----\n\n### Small')).toEqual(['Top:1', '  Side:4', '    Small:7'])
  })

  test('has no # from a code block, and no heading from inside a list or a quote', async () => {
    const text = ['# Real', '', '```', '# not a heading', '```', '', '- # in a list', '', '> # in a quote', '', '## Also real'].join('\n')
    expect(await headings(text)).toEqual(['Real:1', '  Also real:11'])
  })

  test('is empty without headings, with the line the source picked for that', async () => {
    expect(await headings('just text\n\nmore')).toEqual([])
    expect(sourceFor('a.md')!.empty).toBe('noHeadings')
  })
})

describe('a source', () => {
  test('is there for Markdown and for the languages whose symbols are read, by the name', () => {
    expect(sourceFor('README.md')?.empty).toBe('noHeadings')
    expect(sourceFor('notes.markdown')?.empty).toBe('noHeadings')
    for (const name of ['main.ts', 'view.tsx', 'a.js', 'b.jsx', 'main.py']) expect(sourceFor(name)?.empty).toBe('noSymbols')
  })

  test('is not there for any other language, or for a file that has none', () => {
    for (const name of ['main.go', 'Main.kt', 'notes.txt', 'config.json', 'Makefile']) expect(sourceFor(name)).toBeNull()
  })
})

/** A symbol as the palette's outline has it, at [line] to [end], with what is inside it. */
const symbol = (name: string, kind: DocSymbol['kind'], line: number, end: number, children: DocSymbol[] = []): DocSymbol =>
  ({ name, kind, line, end, children })

/** The items as `name:line-end`, indented by depth. */
const spans = (items: OutlineItem[]): string[] => items.map((item) => `${'  '.repeat(item.depth)}${item.name}:${item.line}-${item.end}`)

describe('the outline of a source file', () => {
  test('is its symbols indented by how deep they sit, each with its end', () => {
    const outline = [
      symbol('load', 'function', 1, 3),
      symbol('Pane', 'class', 5, 20, [symbol('show', 'method', 6, 9), symbol('Inner', 'class', 10, 18, [symbol('m', 'method', 11, 12)])]),
    ]
    expect(spans(symbolItems(outline))).toEqual(['load:1-3', 'Pane:5-20', '  show:6-9', '  Inner:10-18', '    m:11-12'])
    expect(symbolItems(outline).map((item) => item.kind)).toEqual(['function', 'class', 'method', 'class', 'method'])
  })

  test('is what the server answered when one is up to answer', async () => {
    const server = [symbol('from_server', 'function', 2, 4)]
    const items = await sourceFor('a.py')!.items({ ...file('def from_tree(): pass', 'a.py'), serverOutline: async () => server })
    expect(spans(items)).toEqual(['from_server:2-4'])
  })

  test('is read from the file when the server is not up, cannot say, or is not there at all', async () => {
    const text = 'class A:\n    def m(self):\n        pass\n\ndef f():\n    pass\n'
    const read = ['A:1-3', '  m:2-3', 'f:5-6']
    expect(spans(await sourceFor('a.py')!.items({ ...file(text, 'a.py'), serverOutline: async () => null }))).toEqual(read)
    expect(spans(await sourceFor('a.py')!.items(file(text, 'a.py')))).toEqual(read)
  })

  test('is empty without symbols, with the line the source picked for that', async () => {
    expect(await sourceFor('a.ts')!.items(file('const a = 1', 'a.ts'))).toEqual([])
  })
})

describe('the outline tab', () => {
  test('can be picked for Markdown and source files in the viewer and the editor', () => {
    for (const name of ['a.md', 'main.py', 'main.ts']) {
      expect(outlineEnabled({ name, layer: 'viewer' })).toBe(true)
      expect(outlineEnabled({ name, layer: 'editor' })).toBe(true)
    }
  })

  test('cannot be picked in the diff layer, without a source, or without a document', () => {
    expect(outlineEnabled({ name: 'a.md', layer: 'diff' })).toBe(false)
    expect(outlineEnabled({ name: 'main.py', layer: 'diff' })).toBe(false)
    expect(outlineEnabled({ name: 'main.go', layer: 'viewer' })).toBe(false)
    expect(outlineEnabled(null)).toBe(false)
  })

  test('is shown when it was the last one and there is an outline to show', () => {
    expect(tabToShow('outline', { name: 'a.md', layer: 'viewer' })).toBe('outline')
    expect(tabToShow('tree', { name: 'a.md', layer: 'viewer' })).toBe('tree')
  })

  test('gives way to the tree when there is no outline to show', () => {
    expect(tabToShow('outline', { name: 'main.go', layer: 'viewer' })).toBe('tree')
    expect(tabToShow('outline', { name: 'a.md', layer: 'diff' })).toBe('tree')
    expect(tabToShow('outline', null)).toBe('tree')
  })
})

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

describe('the tab the sidebar was left on', () => {
  afterEach(() => give(null))

  test('comes back the way it went in', () => {
    give(fakeStorage())
    rememberTab('outline')
    expect(rememberedTab()).toBe('outline')
    rememberTab('tree')
    expect(rememberedTab()).toBe('tree')
  })

  test('is the tree without storage, or with something else held there', () => {
    give(null)
    expect(rememberedTab()).toBe('tree')
    expect(() => rememberTab('outline')).not.toThrow()
    for (const held of ['', 'toc', 'Outline', '"outline"', 'tree ']) {
      give(fakeStorage({ 'sidebar.tab': held }))
      expect(rememberedTab()).toBe('tree')
    }
  })

  test('is still the outline after a file without one has been on the screen', () => {
    give(fakeStorage())
    rememberTab('outline')
    expect(tabToShow(rememberedTab(), { name: 'main.go', layer: 'viewer' })).toBe('tree')
    expect(tabToShow(rememberedTab(), { name: 'a.md', layer: 'viewer' })).toBe('outline')
  })
})

const items = (...lines: number[]): OutlineItem[] => lines.map((line) => ({ name: `at ${line}`, kind: 'heading', line, depth: 0 }))

describe('where the screen is', () => {
  test('is the last item that starts on the top line or before it', () => {
    const outline = items(3, 10, 20)
    expect(currentItem(outline, 3)).toBe(0)
    expect(currentItem(outline, 9)).toBe(0)
    expect(currentItem(outline, 10)).toBe(1)
    expect(currentItem(outline, 500)).toBe(2)
  })

  test('is nowhere above the first item, or in an empty outline', () => {
    expect(currentItem(items(3, 10), 2)).toBe(-1)
    expect(currentItem([], 40)).toBe(-1)
  })

  test('is the innermost symbol whose lines hold the top line, and none between them', () => {
    // 1 import, 3-5 f, 8-20 class A with m at 9-12 and n at 15-18, 23 g to 25.
    const outline = symbolItems([
      symbol('f', 'function', 3, 5),
      symbol('A', 'class', 8, 20, [symbol('m', 'method', 9, 12), symbol('n', 'method', 15, 18)]),
      symbol('g', 'function', 23, 25),
    ])
    const at = (line: number) => outline[currentItem(outline, line)]?.name ?? null
    expect(at(1)).toBeNull()
    expect([at(3), at(5)]).toEqual(['f', 'f'])
    expect([at(6), at(7)]).toEqual([null, null])
    expect([at(8), at(9), at(12)]).toEqual(['A', 'm', 'm'])
    expect([at(13), at(14), at(19), at(20)]).toEqual(['A', 'A', 'A', 'A'])
    expect([at(16), at(21), at(24), at(30)]).toEqual(['n', null, 'g', null])
  })
})

/** A source that answers when told to, in whatever order, as a language server might. */
function lateSource(): { source: OutlineSource; answer(index: number, items: OutlineItem[]): Promise<void> } {
  const waiting: ((items: OutlineItem[]) => void)[] = []
  return {
    source: { empty: 'noHeadings', items: () => new Promise((resolve) => waiting.push(resolve)) },
    async answer(index, found) {
      waiting[index](found)
      await new Promise((resolve) => setTimeout(resolve))
    },
  }
}

describe('an outline whose source answers late', () => {
  // Not Markdown: the list only ever sees items, so a source of functions works the same way.
  const functions = (...lines: number[]): OutlineItem[] => lines.map((line) => ({ name: `f${line}`, kind: 'function', line, depth: 1 }))

  test('has nothing until the answer, then has it and calls back', async () => {
    let calls = 0
    const outline = createOutline(() => calls++)
    const late = lateSource()
    outline.load(late.source, file('', 'main.ts'))
    expect(outline.items()).toBeNull()
    await late.answer(0, functions(4, 9))
    expect(outline.items()).toEqual(functions(4, 9))
    expect(calls).toBe(1)
    expect(currentItem(outline.items()!, 5)).toBe(0)
  })

  test('keeps what it drew for the same file while it asks again', async () => {
    const outline = createOutline(() => {})
    const late = lateSource()
    outline.load(late.source, file('', 'main.ts'))
    await late.answer(0, functions(4))
    outline.load(late.source, file('', 'main.ts'))
    expect(outline.items()).toEqual(functions(4))
    await late.answer(1, functions(4, 12))
    expect(outline.items()).toEqual(functions(4, 12))
  })

  test('shows nothing of another file while it asks', async () => {
    const outline = createOutline(() => {})
    const late = lateSource()
    outline.load(late.source, file('', 'a.ts'))
    await late.answer(0, functions(4))
    outline.load(late.source, file('', 'b.ts'))
    expect(outline.items()).toBeNull()
  })

  test('drops an answer that comes back after a later question', async () => {
    let calls = 0
    const outline = createOutline(() => calls++)
    const late = lateSource()
    outline.load(late.source, file('', 'a.ts'))
    outline.load(late.source, file('', 'b.ts'))
    await late.answer(1, functions(7))
    await late.answer(0, functions(4))
    expect(outline.items()).toEqual(functions(7))
    expect(calls).toBe(1)
  })

  test('drops an answer after it was cleared', async () => {
    let calls = 0
    const outline = createOutline(() => calls++)
    const late = lateSource()
    outline.load(late.source, file('', 'a.ts'))
    outline.clear()
    await late.answer(0, functions(4))
    expect(outline.items()).toBeNull()
    expect(calls).toBe(0)
  })
})

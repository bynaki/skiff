// The sidebar's outline tab: what Markdown's headings become, which tab is shown, where the screen
// is, and an outline whose source answers late.
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
  tabToShow,
} from '../src/outline'

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
  test('is there for Markdown by its name, and not for anything else yet', () => {
    expect(sourceFor('README.md')).not.toBeNull()
    expect(sourceFor('notes.markdown')).not.toBeNull()
    expect(sourceFor('main.ts')).toBeNull()
    expect(sourceFor('notes.txt')).toBeNull()
    expect(sourceFor('Makefile')).toBeNull()
  })
})

describe('the outline tab', () => {
  test('can be picked for Markdown in the viewer and the editor', () => {
    expect(outlineEnabled({ name: 'a.md', layer: 'viewer' })).toBe(true)
    expect(outlineEnabled({ name: 'a.md', layer: 'editor' })).toBe(true)
  })

  test('cannot be picked in the diff layer, without a source, or without a document', () => {
    expect(outlineEnabled({ name: 'a.md', layer: 'diff' })).toBe(false)
    expect(outlineEnabled({ name: 'main.py', layer: 'viewer' })).toBe(false)
    expect(outlineEnabled(null)).toBe(false)
  })

  test('is shown when it was the last one and there is an outline to show', () => {
    expect(tabToShow('outline', { name: 'a.md', layer: 'viewer' })).toBe('outline')
    expect(tabToShow('tree', { name: 'a.md', layer: 'viewer' })).toBe('tree')
  })

  test('gives way to the tree when there is no outline to show', () => {
    expect(tabToShow('outline', { name: 'main.py', layer: 'viewer' })).toBe('tree')
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
    expect(tabToShow(rememberedTab(), { name: 'main.py', layer: 'viewer' })).toBe('tree')
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

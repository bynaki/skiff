// What the palette's symbol mode finds: in a file with no language server behind it, and what a
// server answers with when there is one.
import { describe, expect, test } from 'vitest'
import {
  type DocSymbol,
  type LspDocumentSymbol,
  type ProjectSymbol,
  createProjectSearch,
  outlineFromServer,
  outlineOf,
  projectItems,
  projectSymbols,
  symbols,
} from '../src/symbols'

/** The outline as `kind name:line`, children indented, which reads better in a failure than objects do. */
function flat(outline: DocSymbol[], depth = 0): string[] {
  return outline.flatMap((symbol) => [
    `${'  '.repeat(depth)}${symbol.kind} ${symbol.name}:${symbol.line}`,
    ...flat(symbol.children, depth + 1),
  ])
}

describe('the outline of a TypeScript file', () => {
  test('has its functions and classes, and the methods inside the classes', async () => {
    const text = [
      'export function load(a: number) {}',
      'class Pane {',
      '  show() {}',
      '  get layer() { return 1 }',
      '  static count = 1',
      '  close = () => 2',
      '}',
      'const open = () => 1, size = 3, keep = function () {}',
      'interface Shape { x: number }',
      'type Name = string',
      'function outer() { function inner() {} }',
      'export default class {}',
    ].join('\n')

    expect(flat(await outlineOf('main.ts', text))).toEqual([
      'function load:1',
      'class Pane:2',
      '  method show:3',
      '  method layer:4',
      '  method close:6',
      'function open:8',
      'function keep:8',
      'function outer:11',
    ])
  })

  test('is the same for JavaScript', async () => {
    expect(flat(await outlineOf('a.js', 'function f() {}\nclass A { m() {} }'))).toEqual([
      'function f:1',
      'class A:2',
      '  method m:2',
    ])
  })
})

describe('the outline of a Python file', () => {
  test('has its functions and classes, decorated or not, and a class inside a class', async () => {
    const text = [
      'def f(x):',
      '    def inner(): pass',
      'class A(B):',
      '    def m(self): pass',
      '    @property',
      '    async def n(self): pass',
      '    class Meta:',
      '        def o(self): pass',
      '@dataclass',
      'class C: pass',
    ].join('\n')

    expect(flat(await outlineOf('a.py', text))).toEqual([
      'function f:1',
      'class A:3',
      '  method m:4',
      '  method n:6',
      '  class Meta:7',
      '    method o:8',
      'class C:10',
    ])
  })
})

describe('the outline of a Markdown file', () => {
  test('is its headings, without their marks, and not a # inside a code block', async () => {
    const text = ['# One', 'text', '## Two *b* ##', 'Three', '=====', '```', '# not', '```', '###### Six'].join('\n')

    expect(flat(await outlineOf('README.md', text))).toEqual([
      'heading One:1',
      'heading Two *b*:3',
      'heading Three:4',
      'heading Six:9',
    ])
  })

  test('counts lines the same with Windows line endings', async () => {
    expect(flat(await outlineOf('a.md', '# One\r\n\r\n# Two'))).toEqual(['heading One:1', 'heading Two:3'])
  })
})

test('a language with no rules here has no outline yet', async () => {
  expect(await outlineOf('config.json', '{"a": 1}')).toEqual([])
  expect(await outlineOf('notes.txt', '# not a heading')).toEqual([])
})

describe('the palette entries', () => {
  test('name a method by its class, and go to the line of what was picked', async () => {
    const outline = await outlineOf('a.py', 'class A:\n    class B:\n        def m(self): pass\ndef f(): pass')
    const went: number[] = []
    const items = symbols(outline, (line) => went.push(line))

    expect(items.map((item) => item.name)).toEqual(['A', 'A.B', 'A.B.m', 'f'])
    items[2].run()
    expect(went).toEqual([3])
  })
})

/** A `DocumentSymbol` whose name starts at [line] (0-based, as a server counts). */
const tree = (name: string, kind: number, line: number, children: LspDocumentSymbol[] = []): LspDocumentSymbol => {
  const at = { line, character: 4 }
  return { name, kind, selectionRange: { start: at, end: at }, children }
}

describe('the outline a language server gives', () => {
  test('keeps classes and what is in them, and leaves out what is inside a function', () => {
    // What pyright answered for `def local_helper(x)` with `값 = …` inside `main`, and a class.
    const answer = [
      tree('local_helper', 12, 3, [tree('x', 13, 3)]),
      tree('main', 12, 7, [tree('값', 13, 8)]),
      tree('LIMIT', 14, 14),
      tree('mode', 13, 15),
      tree('Greeter', 5, 16, [tree('hello', 6, 17, [tree('name', 13, 17)]), tree('count', 8, 18), tree('total', 13, 19)]),
    ]

    expect(flat(outlineFromServer(answer)!)).toEqual([
      'function local_helper:4',
      'function main:8',
      'other LIMIT:15',
      'other mode:16',
      'class Greeter:17',
      '  method hello:18',
      '  other count:19',
    ])
  })

  test('is read from the file instead when the server sends a flat list', () => {
    const flatList = [{ name: 'f', kind: 12, location: { uri: 'file:///p/a.py', range: { start: { line: 0, character: 0 }, end: { line: 0, character: 1 } } } }]
    expect(outlineFromServer(flatList)).toBeNull()
  })

  test('is empty when the server has nothing', () => {
    expect(outlineFromServer(null)).toEqual([])
    expect(outlineFromServer([])).toEqual([])
  })
})

/** A `SymbolInformation` in [uri] at [line]:[character], 0-based. */
const found = (name: string, uri: string, line: number, character: number, containerName?: string, kind = 12) => ({
  name,
  kind,
  location: { uri, range: { start: { line, character }, end: { line, character } } },
  ...(containerName ? { containerName } : {}),
})

describe('what a language server finds across the project', () => {
  test('is named through what holds it, by its path from the root, and leaves out the file on the screen', () => {
    const answer = [
      found('local_helper', 'file:///home/alice/proj/app.py', 3, 4),
      found('hello', 'file:///home/alice/proj/pkg/util.py', 6, 8, 'Greeter'),
      found('greet', 'file:///home/alice/proj/pkg/util.py', 0, 4),
    ]

    expect(projectSymbols(answer, 'file:///home/alice/proj/app.py', 'file:///home/alice/proj')).toEqual([
      { name: 'Greeter.hello', path: 'pkg/util.py', uri: 'file:///home/alice/proj/pkg/util.py', line: 7, col: 9 },
      { name: 'greet', path: 'pkg/util.py', uri: 'file:///home/alice/proj/pkg/util.py', line: 1, col: 5 },
    ])
  })

  test('decodes what the URI encodes, however the server chose to encode it', () => {
    // Kotlin encodes the root's `한` in capitals; a server may write it back in lower case.
    const answer = [found('f', 'file:///home/alice/%ed%95%9c/a%20b.py', 0, 0)]
    const [symbol] = projectSymbols(answer, 'file:///home/alice/%ED%95%9C/main.py', 'file:///home/alice/%ED%95%9C')
    expect(symbol.path).toBe('a b.py')
  })

  test('is shown whole when it is outside the root', () => {
    const answer = [found('f', 'file:///usr/lib/python3/os.py', 0, 0)]
    expect(projectSymbols(answer, 'file:///home/alice/proj/a.py', 'file:///home/alice/proj')[0].path).toBe('/usr/lib/python3/os.py')
  })

  test('has a variable only at the top of a file', () => {
    // What pyright answered for `m`: a module's variable, a parameter, a local, and an attribute.
    const answer = [
      found('mode', 'file:///p/m.py', 1, 0, undefined, 13),
      found('mx', 'file:///p/m.py', 12, 9, 'make', 13),
      found('made', 'file:///p/m.py', 7, 8, 'Box.__init__', 13),
      found('mine', 'file:///p/m.py', 6, 13, 'Box', 13),
      found('many', 'file:///p/m.py', 9, 8, 'Box', 6),
    ]
    expect(projectSymbols(answer, 'file:///p/a.py', 'file:///p').map((symbol) => symbol.name)).toEqual(['mode', 'Box.many'])
  })

  test('is empty when the server has nothing', () => {
    expect(projectSymbols(null, 'file:///p/a.py', 'file:///p')).toEqual([])
  })

  test('becomes entries that show their file and open it', () => {
    const symbol: ProjectSymbol = { name: 'greet', path: 'util.py', uri: 'file:///p/util.py', line: 1, col: 5 }
    const opened: ProjectSymbol[] = []
    const [item] = projectItems([symbol], (picked) => opened.push(picked))

    expect(item.name).toBe('greet')
    expect(item.elsewhere).toBe('util.py')
    item.run()
    expect(opened).toEqual([symbol])
  })
})

/** A search whose server answers only when told to, and a count of the times it said the answer is in. */
function search() {
  const asked: { query: string; answer: (names: string[]) => void }[] = []
  let arrived = 0
  const projectSearch = createProjectSearch(
    (query) => new Promise((resolve) => asked.push({
      query,
      answer: (names) => resolve(names.map((name) => ({ name, path: 'a.py', uri: 'file:///p/a.py', line: 1, col: 1 }))),
    })),
    () => arrived++,
  )
  return { projectSearch, asked, arrivals: () => arrived }
}

const settle = () => new Promise((resolve) => setTimeout(resolve, 0))
const namesOf = (symbols: ProjectSymbol[]) => symbols.map((symbol) => symbol.name)

describe('asking the server as the query is typed', () => {
  test('asks once for a query, and says when the answer is in', async () => {
    const { projectSearch, asked, arrivals } = search()
    expect(projectSearch.find('gr')).toEqual([])
    expect(projectSearch.find('gr')).toEqual([])
    expect(asked.map((ask) => ask.query)).toEqual(['gr'])

    asked[0].answer(['greet'])
    await settle()
    expect(arrivals()).toBe(1)
    expect(namesOf(projectSearch.find('gr'))).toEqual(['greet'])
    expect(asked).toHaveLength(1)
  })

  test('asks nothing for nothing typed', () => {
    const { projectSearch, asked } = search()
    expect(projectSearch.find('')).toEqual([])
    expect(asked).toEqual([])
  })

  test('keeps one question out at a time, and asks the latest query once it is answered', async () => {
    const { projectSearch, asked } = search()
    projectSearch.find('g')
    projectSearch.find('gr')
    projectSearch.find('gre')
    expect(asked.map((ask) => ask.query)).toEqual(['g'])

    asked[0].answer(['greet', 'go'])
    await settle()
    expect(asked.map((ask) => ask.query)).toEqual(['g', 'gre'])
    // Until then, what the query before found, for the palette to narrow.
    expect(namesOf(projectSearch.find('gre'))).toEqual(['greet', 'go'])

    asked[1].answer(['greet'])
    await settle()
    expect(namesOf(projectSearch.find('gre'))).toEqual(['greet'])
    expect(asked).toHaveLength(2)
  })

  test('drops an answer that arrives after it was forgotten, and asks again', async () => {
    const { projectSearch, asked, arrivals } = search()
    projectSearch.find('gr')
    projectSearch.forget()
    asked[0].answer(['greet'])
    await settle()
    expect(arrivals()).toBe(0)

    expect(projectSearch.find('gr')).toEqual([])
    expect(asked.map((ask) => ask.query)).toEqual(['gr', 'gr'])
  })
})

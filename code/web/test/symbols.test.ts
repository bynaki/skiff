// What the palette's symbol mode finds in a file with no language server behind it.
import { describe, expect, test } from 'vitest'
import { type DocSymbol, outlineOf, symbols } from '../src/symbols'

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

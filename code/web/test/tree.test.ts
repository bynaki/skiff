// The sidebar's tree: what is unfolded, what Kotlin is asked to list, and the rows that come of it.
import { describe, expect, test } from 'vitest'
import { type Listing, type Row, createTree } from '../src/tree'

const skiff = { id: 'p1', name: 'skiff', where: 'alice@host:22/home/alice/skiff' }
const blog = { id: 'p2', name: 'blog', where: 'alice@host:22/home/alice/blog' }

/** Listings Kotlin has not answered yet, which the test answers by hand. */
function pending() {
  const asked: { project: string; path: string; answer: (listing: Listing) => void; fail: (error: Error) => void }[] = []
  let changes = 0
  const tree = createTree(
    (project, path) => new Promise<Listing>((answer, fail) => asked.push({ project, path, answer, fail })),
    () => changes++,
  )
  const answer = (path: string, listing: Listing, project = 'p1') => {
    const index = asked.findIndex((ask) => ask.project === project && ask.path === path)
    asked.splice(index, 1)[0].answer(listing)
  }
  return { tree, asked, answer, changes: () => changes }
}

const settle = () => new Promise((resolve) => setTimeout(resolve, 0))

/** A row as one line, so a whole tree reads as a list. */
function line(row: Row): string {
  const indent = 'depth' in row ? '  '.repeat(row.depth) : ''
  switch (row.kind) {
    case 'project': return `${row.open ? '▾' : '▸'} ${row.project.name}`
    case 'directory': return `${indent}${row.open ? '▾' : '▸'} ${row.name}/`
    case 'file': return `${indent}${row.name}${row.current ? ' *' : ''}`
    case 'listing': return `${indent}…`
    case 'empty': return `${indent}(empty)`
    case 'failed': return `${indent}! ${row.message}`
  }
}

const lines = (tree: ReturnType<typeof createTree>) => tree.rows().map(line)

describe('the project tree', () => {
  test('starts folded, and unfolding a project lists its root', async () => {
    const { tree, asked, answer } = pending()
    tree.set([skiff, blog], null)
    expect(lines(tree)).toEqual(['▸ skiff', '▸ blog'])
    expect(asked).toEqual([])

    tree.toggle('p1', '')
    expect(lines(tree)).toEqual(['▾ skiff', '  …', '▸ blog'])
    answer('', { entries: [{ name: 'src', directory: true }, { name: 'README.md', directory: false }] })
    await settle()

    expect(lines(tree)).toEqual(['▾ skiff', '  ▸ src/', '  README.md', '▸ blog'])
  })

  test('unfolds down to the file on the screen and marks it', async () => {
    const { tree, asked, answer } = pending()
    tree.set([skiff], { project: 'p1', path: 'code/web/main.ts' })

    expect(asked.map((ask) => ask.path)).toEqual(['', 'code', 'code/web'])
    answer('', { entries: [{ name: 'code', directory: true }] })
    answer('code', { entries: [{ name: 'web', directory: true }] })
    answer('code/web', { entries: [{ name: 'main.ts', directory: false }, { name: 'tree.ts', directory: false }] })
    await settle()

    expect(lines(tree)).toEqual(['▾ skiff', '  ▾ code/', '    ▾ web/', '      main.ts *', '      tree.ts'])
  })

  test('a file at the root unfolds only the project', () => {
    const { tree, asked } = pending()
    tree.set([skiff], { project: 'p1', path: 'README.md' })
    expect(asked.map((ask) => ask.path)).toEqual([''])
  })

  test('folding keeps what was listed, and unfolding again shows it while it is listed anew', async () => {
    const { tree, asked, answer } = pending()
    tree.set([skiff], null)
    tree.toggle('p1', '')
    answer('', { entries: [{ name: 'a.md', directory: false }] })
    await settle()

    tree.toggle('p1', '')
    expect(lines(tree)).toEqual(['▸ skiff'])
    tree.toggle('p1', '')
    expect(lines(tree)).toEqual(['▾ skiff', '  a.md'])
    expect(asked.map((ask) => ask.path)).toEqual([''])
    answer('', { entries: [{ name: 'a.md', directory: false }, { name: 'b.md', directory: false }] })
    await settle()
    expect(lines(tree)).toEqual(['▾ skiff', '  a.md', '  b.md'])
  })

  test('refresh lists every unfolded directory once, however often it is asked', async () => {
    const { tree, asked, answer } = pending()
    tree.set([skiff], { project: 'p1', path: 'src/a.kt' })
    answer('', { entries: [{ name: 'src', directory: true }] })
    answer('src', { entries: [{ name: 'a.kt', directory: false }] })
    await settle()

    tree.refresh()
    tree.refresh()
    expect(asked.map((ask) => ask.path)).toEqual(['', 'src'])
  })

  test('a directory that cannot be read says why, and an empty one says so', async () => {
    const { tree, asked, answer } = pending()
    tree.set([skiff], null)
    tree.toggle('p1', '')
    answer('', { entries: [{ name: 'locked', directory: true }, { name: 'empty', directory: true }] })
    await settle()
    tree.toggle('p1', 'locked')
    tree.toggle('p1', 'empty')
    answer('locked', { failed: 'Permission denied' })
    answer('empty', { entries: [] })
    await settle()

    expect(lines(tree)).toEqual(['▾ skiff', '  ▾ locked/', '    ! Permission denied', '  ▾ empty/', '    (empty)'])

    // A call that did not come back at all is a failure of the same kind.
    tree.toggle('p1', 'empty')
    tree.toggle('p1', 'empty')
    asked[0].fail(new Error('bridge gone'))
    await settle()
    expect(lines(tree)).toContain('    ! Error: bridge gone')
  })

  test('a project that has gone takes its folds with it, and an answer about it is dropped', async () => {
    const { tree, answer, changes } = pending()
    tree.set([skiff, blog], null)
    tree.toggle('p2', '')
    tree.set([skiff], null)
    answer('', { entries: [{ name: 'post.md', directory: false }] }, 'p2')
    await settle()

    expect(changes()).toBe(0)
    expect(lines(tree)).toEqual(['▸ skiff'])
    tree.set([skiff, blog], null)
    expect(lines(tree)).toEqual(['▸ skiff', '▸ blog'])
  })

  test('the file on the screen in a project that is not listed marks nothing', () => {
    const { tree, asked } = pending()
    tree.set([skiff], { project: 'gone', path: 'a.md' })
    expect(asked).toEqual([])
    expect(lines(tree)).toEqual(['▸ skiff'])
  })
})

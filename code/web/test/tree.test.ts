// The sidebar's tree: what is unfolded, what Kotlin is asked to list, and the rows that come of it.
import { describe, expect, test } from 'vitest'
import { type Change, type ChangeKind, type Listing, type Row, createTree } from '../src/tree'

const skiff = { id: 'p1', name: 'skiff', where: 'alice@host:22/home/alice/skiff' }
const blog = { id: 'p2', name: 'blog', where: 'alice@host:22/home/alice/blog' }

/** Listings and git statuses Kotlin has not answered yet, which the test answers by hand. */
function pending() {
  const asked: { project: string; path: string; answer: (listing: Listing) => void; fail: (error: Error) => void }[] = []
  const gitAsked: { project: string; answer: (changes: Change[] | null) => void; fail: (error: Error) => void }[] = []
  let changes = 0
  const tree = createTree(
    (project, path) => new Promise<Listing>((answer, fail) => asked.push({ project, path, answer, fail })),
    () => changes++,
    (project) => new Promise<Change[] | null>((answer, fail) => gitAsked.push({ project, answer, fail })),
  )
  const answer = (path: string, listing: Listing, project = 'p1') => {
    const index = asked.findIndex((ask) => ask.project === project && ask.path === path)
    asked.splice(index, 1)[0].answer(listing)
  }
  const answerGit = (changes: Change[] | null, project = 'p1') => {
    const index = gitAsked.findIndex((ask) => ask.project === project)
    gitAsked.splice(index, 1)[0].answer(changes)
  }
  return { tree, asked, answer, gitAsked, answerGit, changes: () => changes }
}

const modified = (path: string): Change => ({ path, kind: 'modified', directory: false })
const untracked = (path: string, directory = false): Change => ({ path, kind: 'untracked', directory })

const settle = () => new Promise((resolve) => setTimeout(resolve, 0))

/** A row as one line, so a whole tree reads as a list. */
function line(row: Row): string {
  const indent = 'depth' in row ? '  '.repeat(row.depth) : ''
  switch (row.kind) {
    case 'project': return `${row.open ? '▾' : '▸'} ${row.project.name}`
    case 'directory': return `${indent}${row.open ? '▾' : '▸'} ${row.name}/${mark(row.change)}`
    case 'file': return `${indent}${row.name}${row.current ? ' *' : ''}${mark(row.change)}`
    case 'listing': return `${indent}…`
    case 'empty': return `${indent}(empty)`
    case 'failed': return `${indent}! ${row.message}`
  }
}

/** A changed line's color, as a letter after it: M for modified, U for untracked. */
const mark = (change?: ChangeKind) =>
  change === 'modified' ? ' M' : change === 'untracked' ? ' U' : ''

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

describe('what git says changed', () => {
  /** skiff unfolded down to src/app, with a file and a folder beside it at each level. */
  async function listed() {
    const it = pending()
    it.tree.set([skiff], { project: 'p1', path: 'src/app/main.ts' })
    it.tree.refresh()
    it.answer('', { entries: [{ name: 'src', directory: true }, { name: 'notes', directory: true }, { name: 'README.md', directory: false }] })
    it.answer('src', { entries: [{ name: 'app', directory: true }, { name: 'lib', directory: true }, { name: 'index.ts', directory: false }] })
    it.answer('src/app', { entries: [{ name: 'main.ts', directory: false }, { name: 'new.ts', directory: false }] })
    await settle()
    return it
  }

  test('a file takes its own change, and every folder above it the strongest one under it', async () => {
    const { tree, answerGit } = await listed()
    answerGit([modified('src/app/main.ts'), untracked('src/app/new.ts'), untracked('src/lib/extra.ts'), modified('README.md')])
    await settle()

    expect(lines(tree)).toEqual([
      '▾ skiff',
      '  ▾ src/ M',
      '    ▾ app/ M',
      '      main.ts * M',
      '      new.ts U',
      '    ▸ lib/ U',
      '    index.ts',
      '  ▸ notes/',
      '  README.md M',
    ])
  })

  test('a folder git does not track is new, and so is everything under it', async () => {
    const { tree, answerGit } = await listed()
    answerGit([untracked('src/app', true)])
    await settle()
    expect(lines(tree)).toEqual([
      '▾ skiff', '  ▾ src/ U', '    ▾ app/ U', '      main.ts * U', '      new.ts U', '    ▸ lib/', '    index.ts', '  ▸ notes/', '  README.md',
    ])
  })

  test('a folded project is not asked, and unfolding one asks', () => {
    const { tree, gitAsked } = pending()
    tree.set([skiff, blog], null)
    tree.refresh()
    expect(gitAsked).toEqual([])

    tree.toggle('p2', '')
    expect(gitAsked.map((ask) => ask.project)).toEqual(['p2'])
  })

  test('asked again, the old colors stay until the answer, and it is asked once however often', async () => {
    const { tree, gitAsked, answerGit } = await listed()
    answerGit([modified('README.md')])
    await settle()

    tree.refresh()
    tree.refresh()
    expect(gitAsked).toHaveLength(1)
    expect(lines(tree)).toContain('  README.md M')

    answerGit([])
    await settle()
    expect(lines(tree)).toContain('  README.md')
  })

  test('no answer, or a failed one, is no color', async () => {
    const { tree, gitAsked, answerGit } = await listed()
    answerGit([modified('README.md')])
    await settle()
    tree.refresh()
    answerGit(null)
    await settle()
    expect(lines(tree).some((row) => / [MU]$/.test(row))).toBe(false)

    tree.refresh()
    gitAsked[0].fail(new Error('bridge gone'))
    await settle()
    expect(lines(tree).some((row) => / [MU]$/.test(row))).toBe(false)
  })

  test('an answer about a project that has gone is dropped', async () => {
    const { tree, answerGit, changes } = pending()
    tree.set([skiff, blog], null)
    tree.toggle('p2', '')
    tree.set([skiff], null)
    answerGit([modified('post.md')], 'p2')
    await settle()
    expect(changes()).toBe(0)
  })
})

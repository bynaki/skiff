// What the palette's file mode offers, and how the directory behind it is listed.
import { describe, expect, test } from 'vitest'
import { createFolder, files } from '../src/files'

const open = [
  { id: 1, name: 'a.py', where: '/srv/a.py' },
  { id: 2, name: 'b.py', where: '/srv/b.py' },
  { id: 3, name: 'c.md', where: '/srv/docs/c.md' },
]

describe('the file mode', () => {
  test('offers the other open files, then the files beside the one on the screen', () => {
    const ran: string[] = []
    const items = files({
      open: () => open,
      active: () => 2,
      beside: () => ['d.py', 'e.py'],
      activate: (id) => ran.push(`activate ${id}`),
      openBeside: (name) => ran.push(`open ${name}`),
    })

    expect(items.map((item) => item.name)).toEqual(['a.py', 'c.md', 'd.py', 'e.py'])
    items[1].run()
    items[3].run()
    expect(ran).toEqual(['activate 3', 'open e.py'])
  })

  test('with nothing open offers nothing', () => {
    const items = files({ open: () => [], active: () => null, beside: () => [], activate: () => {}, openBeside: () => {} })
    expect(items).toEqual([])
  })
})

/** A listing Kotlin has not answered yet, which the test answers by hand. */
function pending() {
  const asked: { id: number; answer: (names: string[]) => void; fail: (error: Error) => void }[] = []
  const list = (id: number) => new Promise<string[]>((answer, fail) => asked.push({ id, answer, fail }))
  let arrived = 0
  const folder = createFolder(list, () => arrived++)
  return { folder, asked, arrivals: () => arrived }
}

const settle = () => new Promise((resolve) => setTimeout(resolve, 0))

describe('the directory beside the file', () => {
  test('is listed once, and says when the names are in', async () => {
    const { folder, asked, arrivals } = pending()
    expect(folder.names(1)).toEqual([])
    expect(folder.names(1)).toEqual([])
    expect(asked.map((ask) => ask.id)).toEqual([1])

    asked[0].answer(['x.py'])
    await settle()
    expect(arrivals()).toBe(1)
    expect(folder.names(1)).toEqual(['x.py'])
    expect(asked).toHaveLength(1)
  })

  test('is listed again once forgotten, which is what opening the palette does', async () => {
    const { folder, asked } = pending()
    folder.names(1)
    asked[0].answer(['x.py'])
    await settle()

    folder.forget()
    expect(folder.names(1)).toEqual([])
    expect(asked).toHaveLength(2)
  })

  test('drops an answer about a file the screen has left', async () => {
    const { folder, asked, arrivals } = pending()
    folder.names(1)
    folder.names(2)
    asked[0].answer(['old.py'])
    await settle()
    expect(arrivals()).toBe(0)
    expect(folder.names(2)).toEqual([])

    asked[1].answer(['new.py'])
    await settle()
    expect(folder.names(2)).toEqual(['new.py'])
  })

  test('that could not be listed answers nothing, and is not asked again at every keystroke', async () => {
    const { folder, asked } = pending()
    folder.names(1)
    asked[0].fail(new Error('connection lost'))
    await settle()

    expect(folder.names(1)).toEqual([])
    expect(asked).toHaveLength(1)
  })
})

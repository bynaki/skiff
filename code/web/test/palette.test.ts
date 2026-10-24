// Where the command palette goes: the button, the open input, the results, and back.
import { describe, expect, test } from 'vitest'
import { MODES, MOST_RECENT, type PaletteItem, type PaletteMode, createPalette, isList } from '../src/palette'

/** What each mode offers. Only the command mode has anything until the later items. */
const ITEMS: Record<PaletteMode, string[]> = {
  command: ['Toggle Layer', 'Reset Zoom', 'Toggle Sidebar'],
  file: ['notes.md'],
  symbol: [],
  theme: [],
  download: [],
}

/** A palette over those names, and a record of which of them were run. */
function palette() {
  const ran: string[] = []
  const machine = createPalette({
    items: (mode) => ITEMS[mode].map((name) => ({ name, run: () => void ran.push(name) })),
  })
  return { machine, ran }
}

const names = (results: readonly { name: string }[]) => results.map((item) => item.name)

describe('the command palette', () => {
  test('starts as the button alone', () => {
    const { machine } = palette()
    expect(machine.stage).toBe('button')
    expect(machine.mode).toBe('command')
  })

  test('opens onto an empty input', () => {
    const { machine } = palette()
    machine.open()
    expect(machine.stage).toBe('input')
    expect(machine.query).toBe('')
    expect(machine.results).toEqual([])
  })

  test('shows what was typed matches, the first of them selected', () => {
    const { machine } = palette()
    machine.open()
    machine.type('tog')
    expect(machine.stage).toBe('results')
    expect(names(machine.results)).toEqual(['Toggle Layer', 'Toggle Sidebar'])
    expect(machine.selected).toBe(0)
  })

  test('stays out for a query that matches nothing, so it can say so', () => {
    const { machine } = palette()
    machine.open()
    machine.type('xyzzy')
    expect(machine.stage).toBe('results')
    expect(machine.results).toEqual([])
  })

  test('is back to the empty input once the query is backspaced away', () => {
    const { machine } = palette()
    machine.open()
    machine.type('tog')
    machine.type('')
    expect(machine.stage).toBe('input')
    expect(machine.results).toEqual([])
  })

  test('runs the selected one and goes back to the button', () => {
    const { machine, ran } = palette()
    machine.open()
    machine.type('tog')
    machine.move(1)
    machine.run()
    expect(ran).toEqual(['Toggle Sidebar'])
    expect(machine.stage).toBe('button')
    expect(machine.query).toBe('')
  })

  test('runs nothing when nothing matched', () => {
    const { machine, ran } = palette()
    machine.open()
    machine.type('xyzzy')
    machine.run()
    expect(ran).toEqual([])
    expect(machine.stage).toBe('results')
  })

  test('is closed before what it runs is run, so a command may open it again', () => {
    const machine = createPalette({ items: () => [{ name: 'Open Palette', run: () => machine.open() }] })
    machine.open()
    machine.type('open')
    machine.run()
    expect(machine.stage).toBe('input')
  })

  test('goes back to the button when cancelled, with the query gone', () => {
    const { machine } = palette()
    machine.open()
    machine.type('tog')
    machine.cancel()
    expect(machine.stage).toBe('button')
    expect(machine.query).toBe('')
    expect(machine.results).toEqual([])
  })

  test('keeps the mode across a run and a cancel', () => {
    const { machine } = palette()
    machine.cycleMode(1)
    machine.open()
    machine.type('notes')
    machine.run()
    expect(machine.mode).toBe('file')
    machine.open()
    machine.cancel()
    expect(machine.mode).toBe('file')
  })

  test('searches the mode it is in, and again when the mode changes under a query', () => {
    const { machine } = palette()
    machine.open()
    machine.type('o')
    // All three have an `o` in the middle of a word and nothing else to tell them apart, so they
    // come back in the order the mode offered them.
    expect(names(machine.results)).toEqual(['Toggle Layer', 'Reset Zoom', 'Toggle Sidebar'])
    machine.cycleMode(1)
    expect(names(machine.results)).toEqual(['notes.md'])
  })

  test('goes round the modes, both ways', () => {
    const { machine } = palette()
    expect(MODES).toEqual(['command', 'file', 'symbol'])
    machine.cycleMode(1)
    expect(machine.mode).toBe('file')
    machine.cycleMode(1)
    expect(machine.mode).toBe('symbol')
    machine.cycleMode(1)
    expect(machine.mode).toBe('command')
    machine.cycleMode(-1)
    expect(machine.mode).toBe('symbol')
  })

  test('puts the results in the order the score gives them', () => {
    const { machine } = palette()
    machine.open()
    machine.type('tl')
    // Both have a `t` and a later `l`; only one of them has it where `Layer` starts.
    expect(names(machine.results)).toEqual(['Toggle Layer', 'Toggle Sidebar'])
  })

  test('moves the selection around the ends', () => {
    const { machine } = palette()
    machine.open()
    machine.type('tog')
    machine.move(-1)
    expect(machine.selected).toBe(1)
    machine.move(1)
    expect(machine.selected).toBe(0)
  })

  test('offers what was run last when nothing is typed yet', () => {
    const { machine, ran } = palette()
    machine.open()
    machine.type('reset')
    machine.run()
    expect(ran).toEqual(['Reset Zoom'])
    machine.open()
    expect(machine.stage).toBe('input')
    expect(names(machine.results)).toEqual(['Reset Zoom'])
    // And it can be run from there, without typing anything.
    machine.run()
    expect(ran).toEqual(['Reset Zoom', 'Reset Zoom'])
  })

  test('has nothing to offer until something has been run', () => {
    const { machine } = palette()
    machine.open()
    expect(machine.results).toEqual([])
  })

  test('puts the newest first and counts each of them once', () => {
    const { machine } = palette()
    const run = (query: string) => { machine.open(); machine.type(query); machine.run() }
    run('layer')
    run('reset')
    run('layer')
    machine.open()
    expect(names(machine.results)).toEqual(['Toggle Layer', 'Reset Zoom'])
  })

  test('remembers five and lets the sixth push the oldest out', () => {
    const many = Array.from({ length: MOST_RECENT + 1 }, (_, index) => `Command ${index}`)
    const machine = createPalette({ items: () => many.map((name) => ({ name, run: () => {} })) })
    for (const name of many) {
      machine.open()
      machine.type(name)
      machine.run()
    }
    machine.open()
    expect(names(machine.results)).toEqual([...many].reverse().slice(0, MOST_RECENT))
  })

  test('keeps a list for each mode', () => {
    const { machine } = palette()
    machine.open()
    machine.type('layer')
    machine.run()
    machine.cycleMode(1)
    machine.open()
    expect(machine.results).toEqual([])
    machine.type('notes')
    machine.run()
    expect(names(machine.results)).toEqual([])
    machine.open()
    expect(names(machine.results)).toEqual(['notes.md'])
    machine.cycleMode(-1)
    expect(names(machine.results)).toEqual(['Toggle Layer'])
  })

  test('ignores typing while it is still the button', () => {
    const { machine } = palette()
    machine.type('tog')
    expect(machine.stage).toBe('button')
    expect(machine.results).toEqual([])
  })

  test('searches again when what a mode offers arrives while it is open, and not while it is closed', () => {
    let offered: string[] = []
    const machine = createPalette({ items: () => offered.map((name) => ({ name, run: () => {} })) })
    machine.open()
    machine.type('no')
    expect(names(machine.results)).toEqual([])

    offered = ['notes.md', 'main.ts']
    machine.refresh()
    expect(machine.stage).toBe('results')
    expect(machine.query).toBe('no')
    expect(names(machine.results)).toEqual(['notes.md'])

    machine.cancel()
    machine.refresh()
    expect(machine.stage).toBe('button')
    expect(machine.results).toEqual([])
  })
})

describe('a source that searches by the query itself', () => {
  test('is handed what is typed, trimmed', () => {
    const queries: string[] = []
    const machine = createPalette({ items: (_mode, query) => (queries.push(query), []) })
    machine.open()
    machine.type(' gr ')
    expect(queries).toEqual(['', 'gr'])
  })

  test('has what is elsewhere put after everything that is here, however well it fits', () => {
    const machine = createPalette({
      items: () => [
        { name: 'greet', elsewhere: 'util.py', run: () => {} },
        { name: 'Greeter.hello', run: () => {} },
        { name: 'grow', run: () => {} },
      ],
    })
    machine.open()
    machine.type('gr')
    expect(names(machine.results)).toEqual(['Greeter.hello', 'grow', 'greet'])
  })
})

describe('the lists a command opens', () => {
  /** A promise and what settles it, for a download that has not answered yet. */
  function pending() {
    let settle: (ok: boolean) => void = () => {}
    const promise = new Promise<void>((resolve, reject) => (settle = (ok) => (ok ? resolve() : reject(new Error('failed')))))
    return { promise, settle }
  }

  /** A palette whose theme list is [themes] and whose download list is what [download] says now. */
  function lists() {
    const ran: string[] = []
    const downloads: { name: string; settle: (ok: boolean) => void }[] = []
    let changed = 0
    let download: PaletteItem[] = [{ name: 'Loading themes…', inert: true, run: () => void ran.push('loading') }]
    const themes: PaletteItem[] = ['System', 'Light', 'Dark', 'ocean'].map((name) => ({ name, run: () => void ran.push(name) }))
    const machine = createPalette({
      items: (mode) => (mode === 'theme' ? themes : mode === 'download' ? download : ITEMS[mode].map((name) => ({ name, run: () => void ran.push(name) }))),
      changed: () => void changed++,
    })
    const listed = (names: [string, string][]) => {
      download = names.map(([name, detail]) => ({
        name,
        detail,
        run: () => {
          const { promise, settle } = pending()
          downloads.push({ name, settle })
          return promise
        },
      }))
      machine.refresh()
    }
    return { machine, ran, downloads, listed, changed: () => changed }
  }

  const settled = () => new Promise((resolve) => setTimeout(resolve, 0))

  test('are not modes a swipe reaches', () => {
    expect(MODES.some(isList)).toBe(false)
    const { machine } = palette()
    machine.open()
    const seen = new Set<PaletteMode>()
    for (let i = 0; i < 6; i++) {
      machine.cycleMode(1)
      seen.add(machine.mode)
      machine.cycleMode(-3)
      seen.add(machine.mode)
    }
    expect([...seen].some(isList)).toBe(false)
  })

  test('open onto everything in them, before anything is typed', () => {
    const { machine } = lists()
    machine.enter('theme')
    expect(machine.mode).toBe('theme')
    expect(machine.stage).toBe('input')
    expect(names(machine.results)).toEqual(['System', 'Light', 'Dark', 'ocean'])
  })

  test('can be entered from a command run, which closes the palette first', () => {
    const { machine } = lists()
    const enter: PaletteItem = { name: 'Theme...', run: () => machine.enter('theme') }
    const outer = createPalette({ items: () => [enter] })
    outer.open()
    outer.type('theme')
    outer.run()
    expect(machine.mode).toBe('theme')
    expect(machine.stage).toBe('input')
  })

  test('go back to > once a theme is picked, and do not remember it', () => {
    const { machine, ran } = lists()
    machine.enter('theme')
    machine.select(3)
    machine.run()
    expect(ran).toEqual(['ocean'])
    expect(machine.stage).toBe('button')
    expect(machine.mode).toBe('command')
    machine.open()
    expect(machine.results).toEqual([])
    machine.enter('theme')
    expect(names(machine.results)).toEqual(['System', 'Light', 'Dark', 'ocean'])
  })

  test('go back to > when cancelled', () => {
    const { machine } = lists()
    machine.enter('theme')
    machine.type('oc')
    machine.cancel()
    expect(machine.stage).toBe('button')
    expect(machine.mode).toBe('command')
  })

  test('search by the name and the collection', () => {
    const { machine, listed } = lists()
    machine.enter('download')
    listed([['Catppuccin Mocha', 'Catppuccin'], ['Gruvbox Dark Soft', 'Gruvbox'], ['Gruvbox Light Hard', 'Gruvbox'], ['Ayu Mirage', 'Ayu']])
    machine.type('mocha')
    expect(names(machine.results)).toEqual(['Catppuccin Mocha'])
    machine.type('gruv soft')
    expect(names(machine.results)[0]).toBe('Gruvbox Dark Soft')
    machine.type('Ayu')
    expect(names(machine.results)).toEqual(['Ayu Mirage'])
  })

  test('keep the loading line whatever is typed, and run nothing from it', () => {
    const { machine, ran } = lists()
    machine.enter('download')
    expect(names(machine.results)).toEqual(['Loading themes…'])
    machine.type('mocha')
    expect(names(machine.results)).toEqual(['Loading themes…'])
    machine.run()
    expect(ran).toEqual([])
    expect(machine.stage).toBe('results')
    expect(machine.mode).toBe('download')
  })

  test('show what came once it comes, under what is already typed', () => {
    const { machine, listed } = lists()
    machine.enter('download')
    machine.type('mocha')
    listed([['Catppuccin Mocha', 'Catppuccin'], ['Ayu Mirage', 'Ayu']])
    expect(names(machine.results)).toEqual(['Catppuccin Mocha'])
  })

  test('stay open on a download until it answers, then go back to > and say so', async () => {
    const { machine, downloads, listed, changed } = lists()
    machine.enter('download')
    listed([['Catppuccin Mocha', 'Catppuccin'], ['Ayu Mirage', 'Ayu']])
    machine.run()
    expect(machine.stage).toBe('input')
    expect(machine.busy?.name).toBe('Catppuccin Mocha')
    machine.move(1)
    machine.run()
    machine.cycleMode(1)
    expect(downloads.map((it) => it.name)).toEqual(['Catppuccin Mocha'])
    expect(machine.mode).toBe('download')

    downloads[0].settle(true)
    await settled()
    expect(machine.stage).toBe('button')
    expect(machine.mode).toBe('command')
    expect(machine.busy).toBeNull()
    expect(changed()).toBe(1)
  })

  test('go back to > when a download fails, too', async () => {
    const { machine, downloads, listed } = lists()
    machine.enter('download')
    listed([['Ayu Mirage', 'Ayu']])
    machine.run()
    downloads[0].settle(false)
    await settled()
    expect(machine.stage).toBe('button')
    expect(machine.mode).toBe('command')
  })

  test('leave a palette opened since alone when a download given up on answers', async () => {
    const { machine, downloads, listed, changed } = lists()
    machine.enter('download')
    listed([['Ayu Mirage', 'Ayu']])
    machine.run()
    machine.cancel()
    expect(machine.busy).toBeNull()
    machine.enter('theme')
    downloads[0].settle(true)
    await settled()
    expect(machine.mode).toBe('theme')
    expect(machine.stage).toBe('input')
    expect(changed()).toBe(0)
  })

  test('swipe round from >, where closing them would have left the palette', () => {
    const { machine } = lists()
    machine.enter('theme')
    machine.cycleMode(1)
    expect(machine.mode).toBe('file')
  })
})

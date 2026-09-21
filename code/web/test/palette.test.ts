// Where the command palette goes: the button, the open input, the results, and back.
import { describe, expect, test } from 'vitest'
import { MODES, MOST_RECENT, type PaletteMode, createPalette } from '../src/palette'

/** What each mode offers. Only the command mode has anything until the later items. */
const ITEMS: Record<PaletteMode, string[]> = {
  command: ['Toggle Layer', 'Reset Zoom', 'Toggle Sidebar'],
  file: ['notes.md'],
  symbol: [],
}

/** A palette over those names, and a record of which of them were run. */
function palette() {
  const ran: string[] = []
  const machine = createPalette({
    items: (mode) => ITEMS[mode].map((name) => ({ name, run: () => ran.push(name) })),
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
})

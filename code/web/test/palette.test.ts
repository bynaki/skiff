// Where the command palette goes: the button, the open input, the results, and back.
import { describe, expect, test } from 'vitest'
import { type PaletteMode, createPalette } from '../src/palette'

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
    machine.setMode('file')
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
    expect(names(machine.results)).toEqual(['Toggle Layer', 'Reset Zoom', 'Toggle Sidebar'])
    machine.setMode('file')
    expect(names(machine.results)).toEqual(['notes.md'])
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

  test('ignores typing while it is still the button', () => {
    const { machine } = palette()
    machine.type('tog')
    expect(machine.stage).toBe('button')
    expect(machine.results).toEqual([])
  })
})

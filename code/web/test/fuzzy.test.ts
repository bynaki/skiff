// How the palette decides which of the names a query fits comes first.
import { describe, expect, test } from 'vitest'
import { rank, score } from '../src/fuzzy'

/** The score, or 'no' where the query does not fit at all, which reads better in a table. */
const fit = (query: string, name: string) => score(query, name) ?? 'no'

const better = (query: string, name: string, than: string) => score(query, name)! > score(query, than)!

describe('the fuzzy score', () => {
  test('wants the query letters in order, and nothing else', () => {
    expect(fit('tl', 'Toggle Layer')).not.toBe('no')
    expect(fit('lt', 'Toggle Layer')).toBe('no')
    expect(fit('toggx', 'Toggle Layer')).toBe('no')
  })

  test('ignores case in both', () => {
    expect(fit('TOG', 'Toggle Layer')).toBe(fit('tog', 'toggle layer'))
  })

  test('treats a space in the query as a letter to find', () => {
    expect(fit('tog lay', 'Toggle Layer')).not.toBe('no')
    expect(fit('gg la', 'Toggle Layer')).not.toBe('no')
  })

  test('is nothing to say about an empty query', () => {
    expect(score('', 'Toggle Layer')).toBe(0)
  })

  test('puts letters that run together above letters that are scattered', () => {
    expect(better('togg', 'Toggle Layer', 'The Old Grey Goose')).toBe(true)
  })

  test('puts the start of a word above the middle of one', () => {
    expect(better('tl', 'Toggle Layer', 'Title')).toBe(true)
    expect(better('rz', 'Reset Zoom', 'Resize')).toBe(true)
  })

  test('reads a capital as the start of a word, and a separator as one too', () => {
    expect(better('pt', 'paletteTest', 'palette-in-two')).toBe(false)
    expect(better('pt', 'palette-two', 'paletteatwo')).toBe(true)
  })

  test('takes the best way the query fits, not the first one found', () => {
    // The `l` of `Layer` is the one meant, though the `l` of `Toggle` comes first.
    expect(score('tl', 'Toggle Layer')).toBe(score('tl', 'Toggle Lint'))
  })
})

describe('ranking', () => {
  const names = ['Toggle Sidebar', 'Toggle Layer', 'Reset Zoom']

  test('drops what does not fit and puts the best first', () => {
    // `Toggle Sidebar` has a `t` and a later `l` too; it is the one that fits worse, not one that
    // does not fit.
    expect(rank('tl', names, (name) => name)).toEqual(['Toggle Layer', 'Toggle Sidebar'])
    expect(rank('xyz', names, (name) => name)).toEqual([])
    expect(rank('tog', names, (name) => name)).toEqual(['Toggle Sidebar', 'Toggle Layer'])
  })

  test('leaves names that score the same in the order they came in', () => {
    expect(rank('t', ['Toggle Sidebar', 'Toggle Layer'], (name) => name)).toEqual(['Toggle Sidebar', 'Toggle Layer'])
  })

  test('keeps everything for an empty query, which the palette never asks for', () => {
    expect(rank('', names, (name) => name)).toEqual(names)
  })
})

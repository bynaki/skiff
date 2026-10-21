// The nav bar's arithmetic (docs/skiffcode.spec/nav-bar.md): where on the bar each mark and the box go.
import { describe, expect, test } from 'vitest'
import { type Placed, barRuns, shownSeverity, viewBox } from '../src/navBar'

describe('barRuns', () => {
  test('puts a mark at the same share of the bar as of what scrolls', () => {
    // 1000px of document on a 100px bar: a line at 500–520 is rows 50–52.
    expect(barRuns([{ top: 500, bottom: 520, mark: 'added' }], 1000, 100)).toEqual([{ lane: 'git', y: 50, height: 2, mark: 'added' }])
    expect(barRuns([{ top: 0, bottom: 100, mark: 'changed' }], 1000, 100)).toEqual([{ lane: 'git', y: 0, height: 10, mark: 'changed' }])
  })

  test('keeps git changes on the left lane and diagnostics on the right, both shown where they overlap', () => {
    const marks: Placed[] = [
      { top: 100, bottom: 200, mark: 'changed' },
      { top: 150, bottom: 160, mark: 'error' },
    ]
    expect(barRuns(marks, 1000, 100)).toEqual([
      { lane: 'git', y: 10, height: 10, mark: 'changed' },
      { lane: 'diagnostic', y: 15, height: 2, mark: 'error' },
    ])
  })

  test('draws a line in a long file at least two pixels tall, centred where it is', () => {
    // One 20px line in 100,000px of document is a fiftieth of a pixel on a 100px bar.
    expect(barRuns([{ top: 50000, bottom: 50020, mark: 'added' }], 100000, 100)).toEqual([{ lane: 'git', y: 49, height: 2, mark: 'added' }])
  })

  test('draws a deletion, which has no height, where the lines met', () => {
    expect(barRuns([{ top: 300, bottom: 300, mark: 'deleted' }], 1000, 100)).toEqual([{ lane: 'git', y: 29, height: 2, mark: 'deleted' }])
  })

  test('keeps a mark at either end inside the bar', () => {
    expect(barRuns([{ top: 0, bottom: 0, mark: 'deleted' }], 1000, 100)).toEqual([{ lane: 'git', y: 0, height: 2, mark: 'deleted' }])
    expect(barRuns([{ top: 1000, bottom: 1000, mark: 'deleted' }], 1000, 100)).toEqual([{ lane: 'git', y: 98, height: 2, mark: 'deleted' }])
  })

  test('makes marks that meet on a row one run, the stronger taking the row', () => {
    const lines: Placed[] = [
      { top: 100, bottom: 120, mark: 'added' },
      { top: 120, bottom: 140, mark: 'added' },
      { top: 140, bottom: 160, mark: 'added' },
    ]
    expect(barRuns(lines, 1000, 100)).toEqual([{ lane: 'git', y: 10, height: 6, mark: 'added' }])
    // A deletion is not lost under the lines either side of it, and an error wins over a warning.
    expect(barRuns([...lines, { top: 130, bottom: 130, mark: 'deleted' }], 1000, 100)).toEqual([
      { lane: 'git', y: 10, height: 2, mark: 'added' },
      { lane: 'git', y: 12, height: 2, mark: 'deleted' },
      { lane: 'git', y: 14, height: 2, mark: 'added' },
    ])
    expect(barRuns([
      { top: 100, bottom: 200, mark: 'warning' },
      { top: 140, bottom: 160, mark: 'error' },
    ], 1000, 100)).toEqual([
      { lane: 'diagnostic', y: 10, height: 4, mark: 'warning' },
      { lane: 'diagnostic', y: 14, height: 2, mark: 'error' },
      { lane: 'diagnostic', y: 16, height: 4, mark: 'warning' },
    ])
  })

  test('draws nothing with no marks, no document or no bar', () => {
    expect(barRuns([], 1000, 100)).toEqual([])
    expect(barRuns([{ top: 0, bottom: 20, mark: 'added' }], 0, 100)).toEqual([])
    expect(barRuns([{ top: 0, bottom: 20, mark: 'added' }], 1000, 0)).toEqual([])
  })

  test('fills the bar for a one-line file that is all change', () => {
    expect(barRuns([{ top: 0, bottom: 21, mark: 'added' }], 21, 100)).toEqual([{ lane: 'git', y: 0, height: 100, mark: 'added' }])
  })
})

describe('viewBox', () => {
  test('is the share of what scrolls that is on the screen, where it is', () => {
    expect(viewBox(0, 250, 1000, 100)).toEqual({ y: 0, height: 25 })
    expect(viewBox(500, 250, 1000, 100)).toEqual({ y: 50, height: 25 })
    expect(viewBox(750, 250, 1000, 100)).toEqual({ y: 75, height: 25 })
  })

  test('is never shorter than eight pixels, and stays inside the bar at the end', () => {
    expect(viewBox(0, 10, 100000, 100)).toEqual({ y: 0, height: 8 })
    expect(viewBox(99990, 10, 100000, 100)).toEqual({ y: 92, height: 8 })
  })

  test('is the whole bar for a file shorter than the screen', () => {
    expect(viewBox(0, 800, 800, 100)).toEqual({ y: 0, height: 100 })
  })

  test('is nothing with no document or no bar', () => {
    expect(viewBox(0, 800, 0, 100)).toEqual({ y: 0, height: 0 })
    expect(viewBox(0, 800, 1000, 0)).toEqual({ y: 0, height: 0 })
  })
})

describe('shownSeverity', () => {
  test('shows errors and warnings, and leaves info and hints off the bar', () => {
    expect(shownSeverity('error')).toBe('error')
    expect(shownSeverity('warning')).toBe('warning')
    expect(shownSeverity('info')).toBeNull()
    expect(shownSeverity('hint')).toBeNull()
  })
})

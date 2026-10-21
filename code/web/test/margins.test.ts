// The room the code views leave under the cursor and under the last line (docs/skiffcode.spec/margins.md).
import { describe, expect, test } from 'vitest'
import { bottomPadding, rightPadding, scrollBottom, scrollRight, sideChars } from '../src/settings'

describe('scrolloff', () => {
  test('leaves that many lines below the cursor at the line height the view is at', () => {
    expect(scrollBottom(5, 21)).toBe(105)
    // A pinch to twice the size leaves twice the room.
    expect(scrollBottom(5, 42)).toBe(210)
  })

  test('leaves as many lines under the last one, in line heights so it grows with a pinch', () => {
    expect(bottomPadding(5)).toBe('5lh')
    expect(bottomPadding(1)).toBe('1lh')
  })

  test('at 0 leaves nothing', () => {
    expect(scrollBottom(0, 21)).toBe(0)
    expect(bottomPadding(0)).toBe('0lh')
  })
})

describe('sidescrolloff', () => {
  test('leaves that many characters right of the cursor at the width the view is at', () => {
    expect(scrollRight(5, 8.4)).toBeCloseTo(42)
    // The same five at font scale 1.5.
    expect(scrollRight(5, 12.6)).toBeCloseTo(63)
  })

  test('leaves as many after the longest line, in ch so it grows with a pinch', () => {
    expect(rightPadding(5)).toBe('5ch')
    expect(rightPadding(0)).toBe('0ch')
  })

  test('is not there at all while lines wrap', () => {
    expect(sideChars(5, false)).toBe(5)
    expect(sideChars(5, true)).toBe(0)
    expect(sideChars(0, false)).toBe(0)
  })
})

describe('the nav bar', () => {
  test('widens the room at the right by its width, wrapped or not', () => {
    expect(rightPadding(5, 6)).toBe('calc(5ch + 6px)')
    expect(rightPadding(0, 6)).toBe('calc(0ch + 6px)')
    expect(scrollRight(5, 8.4, 6)).toBeCloseTo(48)
    expect(scrollRight(0, 8.4, 6)).toBe(6)
  })

  test('leaves the room as it was while it is off', () => {
    expect(rightPadding(5, 0)).toBe('5ch')
    expect(scrollRight(5, 8.4, 0)).toBeCloseTo(42)
  })
})

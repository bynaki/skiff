// When letting go of the open files menu's bar puts the menu away.
import { describe, expect, test } from 'vitest'
import { CLOSE_FRACTION, FLING_SPEED, SPEED_WINDOW_MS, TAP_SLOP, recentSpeed, releaseCloses } from '../src/dropdown'

const HEIGHT = 320

describe('letting go of the bar', () => {
  test('after a slow drag past a quarter of the menu closes it', () => {
    expect(releaseCloses(HEIGHT * CLOSE_FRACTION, 0, HEIGHT)).toBe(true)
  })

  test('after a slow drag short of that lets it fall back', () => {
    expect(releaseCloses(HEIGHT * CLOSE_FRACTION - 1, 0.1, HEIGHT)).toBe(false)
  })

  test('after a quick flick closes it however little has moved', () => {
    expect(releaseCloses(TAP_SLOP, FLING_SPEED, HEIGHT)).toBe(true)
  })

  test('after a finger only resting there is not a drag at all', () => {
    expect(releaseCloses(TAP_SLOP - 1, FLING_SPEED * 4, HEIGHT)).toBe(false)
  })

  test('after pushing up and coming back down lets it fall back', () => {
    expect(releaseCloses(HEIGHT * CLOSE_FRACTION - 1, -FLING_SPEED, HEIGHT)).toBe(false)
  })
})

describe('how fast the menu was going', () => {
  test('is measured over the last stretch, not the last step', () => {
    // 120 Hz, 6 px a frame, then a finger slowing to nothing in the last two frames: the last step
    // alone reads as standing still.
    const samples = Array.from({ length: 20 }, (_, i) => ({ at: i * 8, travel: i * 6 }))
    samples.push({ at: 160, travel: 114.5 }, { at: 168, travel: 114.5 })
    expect(recentSpeed(samples.slice(-2))).toBe(0)
    expect(recentSpeed(samples)).toBeGreaterThan(FLING_SPEED)
  })

  test('comes to nothing for a finger that stopped before letting go', () => {
    const samples = [{ at: 0, travel: 0 }, { at: 50, travel: 60 }, { at: 50 + SPEED_WINDOW_MS, travel: 60 }]
    expect(recentSpeed(samples)).toBe(0)
  })

  test('is taken over the whole drag when it is shorter than the window', () => {
    expect(recentSpeed([{ at: 0, travel: 0 }, { at: 40, travel: 40 }])).toBe(1)
  })

  test('is nothing with nothing to measure', () => {
    expect(recentSpeed([])).toBe(0)
    expect(recentSpeed([{ at: 5, travel: 0 }])).toBe(0)
  })
})

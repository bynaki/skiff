// When a wait is long enough to be worth saying anything about.
import { afterEach, beforeEach, describe, expect, test, vi } from 'vitest'
import { MIN_VISIBLE_MS, SHOW_AFTER_MS, createLoading } from '../src/loading'

/** Every answer [createLoading] gave, in order. */
const record = () => {
  const said: boolean[] = []
  return { said, loading: createLoading((on) => said.push(on)) }
}

beforeEach(() => vi.useFakeTimers())
afterEach(() => vi.useRealTimers())

describe('the loading line', () => {
  test('never appears for an open that is already back', () => {
    const { said, loading } = record()
    loading.start()
    vi.advanceTimersByTime(SHOW_AFTER_MS - 1)
    loading.stop()
    vi.advanceTimersByTime(10_000)
    expect(said).toEqual([])
  })

  test('appears once the open has taken long enough', () => {
    const { said, loading } = record()
    loading.start()
    vi.advanceTimersByTime(SHOW_AFTER_MS)
    expect(said).toEqual([true])
  })

  test('stays its minimum, so it is not gone in the frame it arrived in', () => {
    const { said, loading } = record()
    loading.start()
    vi.advanceTimersByTime(SHOW_AFTER_MS)
    loading.stop()
    expect(said).toEqual([true])
    vi.advanceTimersByTime(MIN_VISIBLE_MS - 1)
    expect(said).toEqual([true])
    vi.advanceTimersByTime(1)
    expect(said).toEqual([true, false])
  })

  test('leaves at once when it has been up longer than that', () => {
    const { said, loading } = record()
    loading.start()
    vi.advanceTimersByTime(SHOW_AFTER_MS + MIN_VISIBLE_MS)
    loading.stop()
    expect(said).toEqual([true, false])
  })

  test('a newer link while it is on the way out keeps the one line', () => {
    const { said, loading } = record()
    loading.start()
    vi.advanceTimersByTime(SHOW_AFTER_MS)
    loading.stop()
    loading.start()
    vi.advanceTimersByTime(10_000)
    expect(said).toEqual([true])
    loading.stop()
    expect(said).toEqual([true, false])
  })
})

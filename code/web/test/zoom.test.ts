// The zoom the page opens at: what it was left at, once it is still a size.
import { afterEach, describe, expect, test } from 'vitest'
import { DEFAULT_FONT_SIZE, MAX_FONT_SIZE, MIN_FONT_SIZE, storedFontSize } from '../src/zoom'

/** The WebView's storage, which plain node has none of. */
const give = (held: string | null) => {
  globalThis.localStorage = (held === null ? null : { getItem: () => held }) as unknown as Storage
}

afterEach(() => give(null))

describe('the size the page opens at', () => {
  test('is the default with no storage to have kept one', () => {
    give(null)
    expect(storedFontSize()).toBe(DEFAULT_FONT_SIZE)
  })

  test('is the one that was left there', () => {
    give('22')
    expect(storedFontSize()).toBe(22)
  })

  test('is the default when what is there is not a size', () => {
    for (const held of ['', 'large', '{}', 'NaN']) {
      give(held)
      expect(storedFontSize()).toBe(DEFAULT_FONT_SIZE)
    }
  })

  test('stays inside what a pinch could have reached', () => {
    give('900')
    expect(storedFontSize()).toBe(MAX_FONT_SIZE)
    give('-4')
    expect(storedFontSize()).toBe(MIN_FONT_SIZE)
  })
})

// Which files show a square beside a color, and which spellings of a color it stands beside.
import { describe, expect, test } from 'vitest'
import { colorsIn, showsSwatches } from '../src/swatches'

describe('the files with color squares', () => {
  test('are themes, stylesheets and JSON', () => {
    for (const name of ['dark.toml', 'settings.toml', 'site.css', 'package.json', 'THEME.TOML']) {
      expect(showsSwatches(name), name).toBe(true)
    }
  })

  test('are not code or prose, where a # starts a comment or a heading', () => {
    for (const name of ['main.py', 'README.md', 'main.ts', 'events.jsonl', 'toml', 'json.txt']) {
      expect(showsSwatches(name), name).toBe(false)
    }
  })
})

describe('the colors a square stands beside', () => {
  test('are every length a theme accepts, in a string or not', () => {
    const text = 'background = "#0b3d5c"\nfg = "#abc"\na = "#abcd"\nb = "#0b3d5c80"\ncolor: #FFF;'
    expect(colorsIn(text).map((found) => found.color)).toEqual(['#0b3d5c', '#abc', '#abcd', '#0b3d5c80', '#FFF'])
  })

  test('start at the #', () => {
    expect(colorsIn('x = "#abc"')).toEqual([{ from: 5, color: '#abc' }])
  })

  test('are not a run of hex digits of another length, or one glued to a word', () => {
    for (const text of ['"#abcde"', '"#0b3d5c8"', '"#0b3d5c800"', 'a#abc', '##abc', '#abcx', '#abc-d', '#ggg']) {
      expect(colorsIn(text), text).toEqual([])
    }
  })
})

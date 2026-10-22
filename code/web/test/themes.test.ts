// The bundled community themes are written by `scripts/themes/generate.ts` from their data files, and
// committed (docs/skiffcode.spec/community-themes.md). These hold what is committed to being what the
// script writes, and the script to the rules it writes by.
/// <reference types="node" />
import { readFileSync, readdirSync } from 'node:fs'
import { describe, expect, test } from 'vitest'
import {
  type Bundle,
  type ThemeData,
  FALLBACK,
  MIN_CONTRAST,
  THEMES_DIR,
  contrast,
  contrasts,
  index,
  notice,
  over,
  readBundles,
  resolve,
  toml,
} from '../scripts/themes/generate'
import { type Dump, GROUPS, SOURCES as NEOVIM_SOURCES, fromNeovim } from '../scripts/themes/neovim'
import { tokenColor } from '../scripts/themes/vscode'

const committed = (name: string) => readFileSync(THEMES_DIR + name, 'utf8')

/** A bundled file's colors, `section.name` → value, the way `theme.test.ts` reads them. */
function colorsOf(text: string): Record<string, string> {
  const colors: Record<string, string> = {}
  let section = ''
  for (const line of text.split('\n')) {
    const header = /^\[(\w+)\]/.exec(line)
    if (header) section = header[1]
    const pair = /^(\w+) = "([^"]*)"/.exec(line)
    if (pair && section) colors[`${section}.${pair[1]}`] = pair[2]
  }
  return colors
}

const bundles = readBundles()

/** A theme with only what nothing stands in for, each named after its key. */
function bare(colors: Record<string, string> = {}): ThemeData {
  const given: Record<string, string> = {
    'editor.background': '#101010',
    'editor.foreground': '#e0e0e0',
    'syntax.keyword': '#a0a0ff',
    'syntax.literal': '#ffa0a0',
    'syntax.string': '#a0ffa0',
    'syntax.definition': '#a0ffff',
    'syntax.type': '#ffffa0',
    'syntax.comment': '#808080',
    'syntax.invalid': '#ff0000',
    'syntax.warning': '#ffaa00',
    'diff.added': '#00ff00',
    'diff.removed': '#ff0000',
    'diff.changed': '#0000ff',
    'ui.border': '#333333',
    'ui.subtle': '#222222',
    ...colors,
  }
  return {
    id: 'bare',
    title: 'Bare',
    base: 'dark',
    source: 'nowhere',
    colors: Object.fromEntries(Object.entries(given).map(([key, value]) => [key, { value, from: key }])),
  }
}

describe('the bundled themes', () => {
  test('are what the script writes from their data', () => {
    expect(bundles.length).toBeGreaterThan(0)
    for (const [name, bundle] of bundles) {
      for (const theme of bundle.themes) expect(committed(`${theme.id}.toml`), theme.id).toBe(toml(bundle, theme, name))
    }
    const all = bundles.map(([, bundle]) => bundle)
    expect(committed('index.json')).toBe(index(all))
    expect(committed('NOTICE.md')).toBe(notice(all))
  })

  test('are every toml beside light and dark, and nothing more', () => {
    const written = bundles.flatMap(([, bundle]) => bundle.themes.map((theme) => `${theme.id}.toml`))
    const files = readdirSync(THEMES_DIR).filter((name) => name.endsWith('.toml'))
    expect(files.sort()).toEqual(['dark.toml', 'light.toml', ...written].sort())
  })

  test('each say where they came from and under what license', () => {
    for (const [, bundle] of bundles) {
      for (const theme of bundle.themes) {
        const head = committed(`${theme.id}.toml`).split('\n').slice(0, 6).join('\n')
        expect(head, theme.id).toContain(`# ${theme.title}, converted to a Skiff Code theme.`)
        expect(head, theme.id).toContain(`${bundle.repository} at ${bundle.commit}`)
        expect(head, theme.id).toMatch(/ at [0-9a-f]{40},\n/)
        expect(head, theme.id).toContain(`# License: ${bundle.license}, ${bundle.copyright}.`)
      }
    }
  })

  test('have each license in full in NOTICE.md', () => {
    const text = committed('NOTICE.md')
    for (const [, bundle] of bundles) {
      expect(text).toContain(`## ${bundle.family}\n`)
      expect(text).toContain(bundle.licenseText.trimEnd())
      expect(bundle.licenseText.length).toBeGreaterThan(200)
    }
  })

  test(`have editor.foreground at ${MIN_CONTRAST}:1 or more over editor.background, Skiff Code's own too`, () => {
    const files = readdirSync(THEMES_DIR).filter((name) => name.endsWith('.toml'))
    expect(files.length).toBeGreaterThan(2)
    const excused = new Set(bundles.flatMap(([, bundle]) => bundle.themes.filter((theme) => theme.lowContrast).map((theme) => `${theme.id}.toml`)))
    for (const name of files) {
      const measured = contrasts(colorsOf(committed(name)))['editor.foreground']
      // A theme let be below needs to be: the reason goes when the source's colors change.
      if (excused.has(name)) expect(measured, name).toBeLessThan(MIN_CONTRAST)
      else expect(measured, name).toBeGreaterThanOrEqual(MIN_CONTRAST)
    }
  })

  test('are light when their background is nearer white than black, and dark when not', () => {
    for (const name of readdirSync(THEMES_DIR).filter((it) => it.endsWith('.toml'))) {
      const text = committed(name)
      const background = colorsOf(text)['editor.background']
      const light = contrast(background, '#000000') > contrast(background, '#ffffff')
      expect(/^base = "(\w+)"/m.exec(text)?.[1], name).toBe(light ? 'light' : 'dark')
    }
  })

  test('have ids the settings file can name, and titles of their own', () => {
    const themes = bundles.flatMap(([, bundle]) => bundle.themes)
    for (const theme of themes) expect(theme.id).toMatch(/^[a-z][a-z0-9-]*$/)
    expect(new Set(themes.map((theme) => theme.id)).size).toBe(themes.length)
    expect(new Set(themes.map((theme) => theme.title)).size).toBe(themes.length)
  })
})

describe('the script', () => {
  test("knows every key the app's own light theme has, in its order", () => {
    expect(Object.keys(FALLBACK)).toEqual(Object.keys(colorsOf(committed('light.toml'))))
  })

  test('fills a key the source lacks from the one the table names, saying so', () => {
    const colors = resolve(bare())
    expect(colors['ui.background']).toEqual({ value: '#101010', from: 'none in the source: editor.background' })
    // Through a chain: faint from muted, muted from comment.
    expect(colors['ui.faint']).toEqual({ value: '#808080', from: 'none in the source: ui.muted' })
    expect(colors['syntax.atom'].value).toBe('#ffa0a0')
    expect(colors['editor.selection'].value).toBe('#222222')
    expect(colors['syntax.property'].value).toBe('#e0e0e0')
    expect(Object.keys(colors)).toEqual(Object.keys(FALLBACK))
  })

  test("makes a scrim from the background at the bundled themes' alpha", () => {
    expect(resolve(bare())['ui.scrim'].value).toBe('#10101099')
    expect(resolve(bare({ 'editor.background': '#abc' }))['ui.scrim'].value).toBe('#aabbcc99')
  })

  test('keeps alpha everywhere but diff, which it draws over the background', () => {
    const colors = resolve(bare({ 'editor.selection': '#ffffff40', 'diff.added': '#00ff0080' }))
    expect(colors['editor.selection'].value).toBe('#ffffff40')
    expect(colors['diff.added']).toEqual({ value: over('#00ff0080', '#101010'), from: 'diff.added, over editor.background' })
    expect(colors['diff.added'].value).toBe('#088808')
    expect(colors['diff.removed'].value).toBe('#ff0000')
  })

  test('refuses a theme missing a key nothing stands in for, a color that is not one, or a key no file has', () => {
    const missing = bare()
    delete missing.colors['syntax.keyword']
    expect(() => resolve(missing)).toThrow('bare: the source gives no syntax.keyword')
    expect(() => resolve(bare({ 'syntax.string': 'green' }))).toThrow('"green" is not a color')
    expect(() => resolve(bare({ 'syntax.strings': '#000' }))).toThrow('syntax.strings is not a theme key')
  })

  test('refuses two keys that only stand in for each other', () => {
    const neither = bare()
    delete neither.colors['ui.border']
    expect(() => resolve(neither)).toThrow('neither ui.border nor editor.gutter_border')
  })

  test("measures WCAG's contrast", () => {
    expect(contrast('#000', '#fff')).toBeCloseTo(21, 5)
    expect(contrast('#777', '#fff')).toBeCloseTo(4.48, 2)
    expect(contrast('#123456', '#123456')).toBe(1)
  })

  test('writes a key per line with where it came from, under the comment that says what the table is for', () => {
    const bundle: Bundle = {
      family: 'Bare',
      repository: 'https://example.com/bare',
      commit: '0'.repeat(40),
      license: 'MIT',
      copyright: 'Copyright (c) Nobody',
      licenseText: 'text',
      themes: [bare()],
    }
    const text = toml(bundle, bare(), 'bare.json')
    expect(text).toContain('base = "dark"\n\n# The menu, the sidebar')
    expect(text).toContain('[ui]\nbackground = "#101010" # none in the source: editor.background\n')
    expect(text).toContain('[diff]\nadded = "#00ff00" # diff.added\n')
    expect(colorsOf(text)['syntax.keyword']).toBe('#a0a0ff')
  })
})

describe("reading a VS Code theme's tokens", () => {
  const rules = [
    { scope: 'keyword', settings: { foreground: '#111111' } },
    { scope: ['keyword.control', 'storage'], settings: { foreground: '#222222' } },
    { scope: 'meta.function keyword.control.flow', settings: { foreground: '#333333' } },
    { scope: 'string, constant.numeric', settings: { foreground: '#444444' } },
    { scope: 'string', settings: { foreground: '#555555' } },
    { scope: 'comment', settings: { fontStyle: 'italic' } },
  ]

  test('takes the deepest selector that is the scope or a parent of it', () => {
    expect(tokenColor(rules, 'keyword.control.flow')).toEqual({ value: '#222222', from: 'token keyword.control (for keyword.control.flow)' })
    expect(tokenColor(rules, 'keyword.operator')?.value).toBe('#111111')
  })

  test('lets a later rule win a tie, and passes over a selector that needs a parent scope', () => {
    expect(tokenColor(rules, 'string.quoted')?.value).toBe('#555555')
    expect(tokenColor(rules, 'constant.numeric.integer')?.value).toBe('#444444')
  })

  test('finds nothing for a scope no rule colors', () => {
    expect(tokenColor(rules, 'comment.line')).toBeNull()
    expect(tokenColor(rules, 'keywords')).toBeNull()
  })
})

describe("reading a Neovim colorscheme's groups", () => {
  const dump: Dump = {
    background: 'light',
    groups: {
      Normal: { fg: '#111111', bg: '#eeeeee' },
      '@keyword': { fg: '#222222' },
      Keyword: { fg: '#333333' },
      '@number': { fg: '#444444', via: 'Constant' },
      PmenuSel: { fg: '#eeeeee', bg: '#111111', reverse: true },
      NormalFloat: {},
    },
  }

  test('takes the first group that has the color, saying where its links ended and that it was reversed', () => {
    const { base, colors } = fromNeovim(dump)
    expect(base).toBe('light')
    expect(colors['editor.background']).toEqual({ value: '#eeeeee', from: 'Normal bg' })
    expect(colors['syntax.keyword']).toEqual({ value: '#222222', from: '@keyword fg' })
    expect(colors['syntax.literal']).toEqual({ value: '#444444', from: '@number fg (via Constant)' })
    expect(colors['ui.strong_background']).toEqual({ value: '#111111', from: 'PmenuSel bg (reversed)' })
  })

  test("is light by its background's color, not by what the colorscheme left 'background'", () => {
    expect(fromNeovim({ ...dump, background: 'dark' }).base).toBe('light')
    expect(fromNeovim({ background: 'light', groups: { Normal: { bg: '#202020' } } }).base).toBe('dark')
    expect(fromNeovim({ background: 'light', groups: {} }).base).toBe('light')
  })

  test('leaves out a key no group colors, for the table to fill', () => {
    const { colors } = fromNeovim(dump)
    expect(colors['ui.background']).toBeUndefined()
    expect(colors['syntax.string']).toBeUndefined()
  })

  test('reads a group for every key the files have, and each group once', () => {
    expect(Object.keys(NEOVIM_SOURCES)).toEqual(Object.keys(FALLBACK).filter((key) => key !== 'ui.scrim'))
    expect(new Set(GROUPS).size).toBe(GROUPS.length)
  })
})

// The theme's colors become CSS variables, and what the page wears before Kotlin sends one is the
// bundled light theme.
/// <reference types="vite/client" />
import { describe, expect, test } from 'vitest'
import { variableOf } from '../src/theme'
import page from '../index.html?raw'
import lightToml from '../../src/main/assets/themes/light.toml?raw'
import darkToml from '../../src/main/assets/themes/dark.toml?raw'

/** A bundled theme's colors, `section.name` → value. The bundled files use nothing but tables of strings. */
function colorsOf(toml: string): Map<string, string> {
  const colors = new Map<string, string>()
  let section = ''
  for (const line of toml.split('\n')) {
    const header = /^\[(\w+)\]/.exec(line)
    if (header) section = header[1]
    const pair = /^(\w+) = "([^"]*)"/.exec(line)
    if (pair && section) colors.set(`${section}.${pair[1]}`, pair[2])
  }
  return colors
}

/** The declarations in `index.html`'s `:root` block. */
function rootOf(html: string): Map<string, string> {
  const block = /:root \{([^}]*)\}/.exec(html)![1]
  return new Map([...block.matchAll(/(--[\w-]+):\s*([^;]+);/g)].map((match) => [match[1], match[2].trim()]))
}

describe('the theme', () => {
  test('names a variable after its section and key', () => {
    expect(variableOf('ui.background')).toBe('--ui-background')
    expect(variableOf('ui.strong_background')).toBe('--ui-strong-background')
    expect(variableOf('editor.gutter_foreground')).toBe('--editor-gutter-foreground')
  })

  test('is light in the page until Kotlin sends one, color for color', () => {
    const light = colorsOf(lightToml)
    expect(light.size).toBeGreaterThan(30)
    expect(rootOf(page)).toEqual(new Map([...light].map(([key, color]) => [variableOf(key), color])))
  })

  test('has the same colors in dark as in light', () => {
    expect([...colorsOf(darkToml).keys()]).toEqual([...colorsOf(lightToml).keys()])
  })

  test('is read by the page for every color it has, and for nothing it lacks', () => {
    // Every var(--…) the page and the code views use, apart from the ones the settings and the
    // layout write themselves, is a theme color.
    const code = import.meta.glob<string>('../src/**/*.ts', { query: '?raw', import: 'default', eager: true })
    const used = new Set(
      [page, ...Object.values(code)].flatMap((text) => [...text.matchAll(/var\((--[\w-]+)/g)].map((match) => match[1])),
    )
    const layout = ['--topbar-space', '--banner-space', '--code-font', '--code-font-size', '--tab-size', '--diff-alpha']
    const themed = [...colorsOf(lightToml).keys()].map(variableOf)
    expect([...used].filter((name) => !layout.includes(name)).sort()).toEqual(themed.sort())
  })
})

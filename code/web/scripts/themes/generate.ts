// Writes the bundled community themes from their bundles' data files
// (docs/skiffcode.spec/community-themes.md "바꾸는 방법"):
//
//   npm run themes
//
// reads every `data/*.json` and writes `assets/themes/<id>.toml` for each theme in it, `index.json`
// and `NOTICE.md`, then prints each theme's contrast against its background. The files it writes are
// committed, and `themes.test.ts` holds them to being what this writes. `light.toml` and `dark.toml`
// are Skiff Code's own and are not written here. Nothing is fetched: a data file holds the colors
// already read from its source, by `vscode.ts` or by hand, each with the name it came from.
import { readFileSync, readdirSync, rmSync, writeFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'

/** One color, and the name in the source it was read from. */
export interface Picked {
  value: string
  from: string
}

export interface ThemeData {
  /** Lowercase and `-`: `settings.toml`'s `theme` and the file's name. */
  id: string
  /** What the palette shows. */
  title: string
  base: 'light' | 'dark'
  /** The file in the source the colors were read from, at the bundle's commit. */
  source: string
  /** By Skiff key; a key left out is filled by [FALLBACK]. */
  colors: Record<string, Picked>
  /** Why this theme's `editor.foreground` is let be below [MIN_CONTRAST]: the source's own colors. */
  lowContrast?: string
}

/** One collection of themes from one source, as a data file holds it. */
export interface Bundle {
  family: string
  repository: string
  commit: string
  /** An SPDX id. */
  license: string
  copyright: string
  licenseText: string
  themes: ThemeData[]
}

/**
 * Every Skiff key in the order the files list them, with the key that fills it when the source has
 * nothing for it: the mapping table's last column. Null is a key the source must give. Kotlin's
 * `ThemeToml.KEYS` is the list itself, and `themes.test.ts` holds this to the bundled `light.toml`,
 * which `ThemeTomlTest` holds to that.
 */
export const FALLBACK: Record<string, string | null> = {
  'ui.background': 'editor.background',
  'ui.foreground': 'editor.foreground',
  'ui.muted': 'syntax.comment',
  'ui.border': 'editor.gutter_border',
  'ui.faint': 'ui.muted',
  'ui.subtle': 'editor.selection',
  'ui.accent': 'syntax.definition',
  'ui.scrim': 'editor.background',
  'ui.strong_background': 'ui.foreground',
  'ui.strong_foreground': 'ui.background',
  'editor.background': null,
  'editor.foreground': null,
  'editor.gutter_background': 'editor.background',
  'editor.gutter_foreground': 'ui.muted',
  'editor.gutter_border': 'ui.border',
  'editor.cursor': 'editor.foreground',
  'editor.selection': 'ui.subtle',
  'editor.code_background': 'ui.background',
  'syntax.keyword': null,
  'syntax.atom': 'syntax.literal',
  'syntax.literal': null,
  'syntax.string': null,
  'syntax.regexp': 'syntax.string',
  'syntax.definition': null,
  'syntax.local': 'editor.foreground',
  'syntax.type': null,
  'syntax.class': 'syntax.type',
  'syntax.macro': 'syntax.keyword',
  'syntax.property': 'editor.foreground',
  'syntax.comment': null,
  'syntax.meta': 'syntax.comment',
  'syntax.invalid': null,
  'syntax.warning': null,
  'diff.added': null,
  'diff.removed': null,
  'diff.changed': null,
}

/** The alpha a scrim filled from the background gets: what the bundled themes' scrims have. */
const SCRIM_ALPHA = '99'

/** What `ThemeToml` takes as a color. */
const COLOR = /^#(?:[0-9a-f]{3,4}|[0-9a-f]{6}|[0-9a-f]{8})$/

/** What each table is for, the same words `light.toml` and `dark.toml` use. */
const SECTIONS: Record<string, string> = {
  ui: 'The menu, the sidebar, the palette, the open files menu and the banner.',
  editor: 'The document: the code views and rendered markdown.',
  syntax: 'The colors of code, in the code views and in rendered markdown.',
  diff: "The diff layer's lines, tinted by diff_alpha, and the git gutter's bars.",
}

/** [color] as `#rrggbbaa`. */
function expand(color: string): string {
  const hex = color.slice(1)
  const full = hex.length <= 4 ? [...hex].map((it) => it + it).join('') : hex
  return `#${full.length === 6 ? `${full}ff` : full}`
}

function channels(color: string): number[] {
  const full = expand(color)
  return [1, 3, 5, 7].map((at) => parseInt(full.slice(at, at + 2), 16))
}

/** [color] drawn over the opaque [under], as the opaque color that results. */
export function over(color: string, under: string): string {
  const [r, g, b, a] = channels(color)
  const [ur, ug, ub] = channels(under)
  const mix = (top: number, bottom: number) => Math.round((top * a + bottom * (255 - a)) / 255)
  return `#${[mix(r, ur), mix(g, ug), mix(b, ub)].map((it) => it.toString(16).padStart(2, '0')).join('')}`
}

/** WCAG's contrast ratio of two opaque colors. */
export function contrast(a: string, b: string): number {
  const luminance = (color: string) => {
    const [r, g, bl] = channels(color).map((it) => {
      const c = it / 255
      return c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4
    })
    return 0.2126 * r + 0.7152 * g + 0.0722 * bl
  }
  const [hi, lo] = [luminance(a), luminance(b)].sort((x, y) => y - x)
  return (hi + 0.05) / (lo + 0.05)
}

/**
 * Every key of [theme], each with the name it came from: its own color, or the one [FALLBACK] names.
 * A color with alpha is kept as it is, except under `diff`, where `diff_alpha` multiplies it again
 * — those are drawn over the background first. Throws, naming the theme and the key, for a color
 * that is not one, a key no file has, or a key the source must give and does not.
 */
export function resolve(theme: ThemeData): Record<string, Picked> {
  for (const [key, picked] of Object.entries(theme.colors)) {
    if (!(key in FALLBACK)) throw new Error(`${theme.id}: ${key} is not a theme key`)
    if (!COLOR.test(picked.value)) throw new Error(`${theme.id}: ${key} = "${picked.value}" is not a color`)
  }
  const done: Record<string, Picked> = {}
  const pick = (key: string, asking: string[]): Picked => {
    if (done[key]) return done[key]
    const own = theme.colors[key]
    if (own) return (done[key] = own)
    const from = FALLBACK[key]
    if (from === null) throw new Error(`${theme.id}: the source gives no ${key}, and nothing stands in for it`)
    if (asking.includes(from)) throw new Error(`${theme.id}: the source gives neither ${[...asking, key].join(' nor ')}`)
    const value = pick(from, [...asking, key]).value
    if (key === 'ui.scrim') {
      return (done[key] = { value: `${expand(value).slice(0, 7)}${SCRIM_ALPHA}`, from: `none in the source: ${from} at alpha ${SCRIM_ALPHA}` })
    }
    return (done[key] = { value, from: `none in the source: ${from}` })
  }
  const colors: Record<string, Picked> = {}
  for (const key of Object.keys(FALLBACK)) colors[key] = pick(key, [])
  const background = colors['editor.background'].value
  for (const key of ['diff.added', 'diff.removed', 'diff.changed']) {
    const { value, from } = colors[key]
    if (expand(value).endsWith('ff')) continue
    colors[key] = { value: over(value, background), from: `${from}, over editor.background` }
  }
  return colors
}

/** The comment a bundled file starts with: what it is, where from, under what license. */
function header(bundle: Bundle, theme: ThemeData, dataName: string): string[] {
  return [
    `# ${theme.title}, converted to a Skiff Code theme.`,
    `# Source: ${bundle.repository} at ${bundle.commit},`,
    `#   ${theme.source}`,
    `# License: ${bundle.license}, ${bundle.copyright}. The full text is in NOTICE.md beside this file.`,
    `# Written by code/web/scripts/themes/generate.ts from scripts/themes/data/${dataName}. The comment`,
    '# after each color names what in the source it came from.',
  ]
}

/** [theme]'s file, from the data file [dataName] of [bundle]. */
export function toml(bundle: Bundle, theme: ThemeData, dataName: string): string {
  const colors = resolve(theme)
  const lines = [...header(bundle, theme, dataName), `base = "${theme.base}"`]
  let section = ''
  for (const [key, { value, from }] of Object.entries(colors)) {
    const [table, name] = key.split('.')
    if (table !== section) {
      lines.push('', `# ${SECTIONS[table]}`, `[${table}]`)
      section = table
    }
    lines.push(`${name} = "${value}" # ${from}`)
  }
  return `${lines.join('\n')}\n`
}

/** The lines `index.json` starts with, which no data file writes. */
const OWN = [
  { id: 'light', title: 'Light', family: 'Skiff', base: 'light' },
  { id: 'dark', title: 'Dark', family: 'Skiff', base: 'dark' },
]

/** `assets/themes/index.json`: Skiff Code's own two, then every theme of [bundles] in order. */
export function index(bundles: Bundle[]): string {
  const entries = [...OWN, ...bundles.flatMap((bundle) => bundle.themes.map(({ id, title, base }) => ({ id, title, family: bundle.family, base })))]
  const line = (entry: (typeof OWN)[number]) =>
    `  { "id": ${JSON.stringify(entry.id)}, "title": ${JSON.stringify(entry.title)}, "family": ${JSON.stringify(entry.family)}, "base": ${JSON.stringify(entry.base)} }`
  return `[\n${entries.map(line).join(',\n')}\n]\n`
}

/** `assets/themes/NOTICE.md`: each bundle's source and license in full. */
export function notice(bundles: Bundle[]): string {
  const parts = [
    '# Bundled themes',
    '',
    "The bundled themes other than `light` and `dark` are converted from the projects below. Each one's",
    'colors were read from the source named and written into Skiff Code\'s theme format, with each',
    "file's comments saying what every color came from; a color the source has none for is filled",
    'from another of its colors. Nothing else of these projects is included.',
  ]
  for (const bundle of bundles) {
    parts.push(
      '',
      `## ${bundle.family}`,
      '',
      `- Themes: ${bundle.themes.map((theme) => `\`${theme.id}\``).join(', ')}`,
      `- Source: ${bundle.repository} at \`${bundle.commit}\``,
      `- License: ${bundle.license}, ${bundle.copyright}`,
      '',
      '```text',
      bundle.licenseText.trimEnd(),
      '```',
    )
  }
  return `${parts.join('\n')}\n`
}

/** The keys whose contrast against `editor.background` the table shows. */
const MEASURED = ['editor.foreground', 'syntax.comment', 'syntax.keyword', 'syntax.string', 'syntax.definition', 'syntax.type', 'syntax.literal']

/** Below this, `editor.foreground` fails the run unless the theme says why; the others are only shown. */
export const MIN_CONTRAST = 4.5

/** Each [MEASURED] key's contrast against [colors]' `editor.background`. */
export function contrasts(colors: Record<string, string>): Record<string, number> {
  const background = colors['editor.background']
  const opaque = expand(background).endsWith('ff') ? background : over(background, '#000')
  return Object.fromEntries(MEASURED.map((key) => [key, contrast(over(colors[key], opaque), opaque)]))
}

const HERE = fileURLToPath(new URL('.', import.meta.url))
export const DATA_DIR = `${HERE}data/`
export const THEMES_DIR = fileURLToPath(new URL('../../../src/main/assets/themes/', import.meta.url))

/** Every data file, by name, in the order `index.json` lists them. */
export function readBundles(): [string, Bundle][] {
  return readdirSync(DATA_DIR)
    .filter((name) => name.endsWith('.json'))
    .sort()
    .map((name) => [name, JSON.parse(readFileSync(DATA_DIR + name, 'utf8')) as Bundle])
}

function main(): void {
  const bundles = readBundles()
  const files = new Map<string, string>()
  const failing: string[] = []
  const rows: string[] = [['theme', ...MEASURED.map((key) => key.split('.')[1])].join('\t')]
  for (const [name, bundle] of bundles) {
    for (const theme of bundle.themes) {
      files.set(`${theme.id}.toml`, toml(bundle, theme, name))
      const measured = contrasts(Object.fromEntries(Object.entries(resolve(theme)).map(([key, picked]) => [key, picked.value])))
      rows.push([theme.id, ...MEASURED.map((key) => measured[key].toFixed(1))].join('\t'))
      if (measured['editor.foreground'] >= MIN_CONTRAST) continue
      if (theme.lowContrast) console.warn(`${theme.id}: editor.foreground is below ${MIN_CONTRAST}:1, let be: ${theme.lowContrast}`)
      else failing.push(theme.id)
    }
  }
  console.log(rows.join('\n'))
  if (failing.length > 0) {
    console.error(`editor.foreground is below ${MIN_CONTRAST}:1 in ${failing.join(', ')}; nothing was written`)
    process.exit(1)
  }
  for (const old of readdirSync(THEMES_DIR)) {
    if (old.endsWith('.toml') && !files.has(old) && !OWN.some((own) => `${own.id}.toml` === old)) rmSync(THEMES_DIR + old)
  }
  for (const [file, text] of files) writeFileSync(THEMES_DIR + file, text)
  writeFileSync(`${THEMES_DIR}index.json`, index(bundles.map(([, bundle]) => bundle)))
  writeFileSync(`${THEMES_DIR}NOTICE.md`, notice(bundles.map(([, bundle]) => bundle)))
  console.log(`wrote ${files.size} themes, index.json and NOTICE.md`)
}

if (process.argv[1] === fileURLToPath(import.meta.url)) main()

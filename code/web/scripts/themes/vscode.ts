// Reads a VS Code color theme into a theme's colors for a bundle's data file
// (docs/skiffcode.spec/community-themes.md "바꾸는 방법"). This is what is run once, when a bundle's data
// is written from its source; the build never runs it.
//
//   node scripts/themes/vscode.ts <theme.json>
//
// prints `{ title, base, colors }`, where each color is `{ value, from }` and `from` names the
// workbench color or the TextMate scope it was read from. The file may be JSONC, as VS Code allows. A key the theme says nothing about is left
// out, for `generate.ts` to fill by the mapping table's last column.
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'

/** One color read from the source, and the name in the source it came from. */
export interface Picked {
  value: string
  from: string
}

interface TokenRule {
  scope?: string | string[]
  settings?: { foreground?: string }
}

/** The part of a VS Code theme file this reads. */
export interface VscodeTheme {
  name?: string
  type?: string
  colors?: Record<string, string>
  tokenColors?: TokenRule[]
}

/**
 * Where each key is read from, in the order tried: a workbench color (`ui:`) or the color VS Code
 * gives a token of a TextMate scope (`scope:`). The scopes are the ones a grammar gives such a token
 * — `keyword.control` rather than `keyword` — so that a theme which only styles the longer name is
 * still read, the way VS Code would show it.
 */
export const SOURCES: Record<string, string[]> = {
  'ui.background': ['ui:sideBar.background'],
  'ui.foreground': ['ui:sideBar.foreground', 'ui:foreground'],
  'ui.muted': ['ui:descriptionForeground'],
  // A theme that hides its panel borders (Rosé Pine) still draws its widgets'.
  'ui.border': ['ui:panel.border', 'ui:sideBar.border', 'ui:editorWidget.border'],
  'ui.faint': ['ui:disabledForeground'],
  'ui.subtle': ['ui:list.hoverBackground'],
  // Only an opaque one: Rosé Pine's focusBorder is a faint gray, which marks nothing.
  'ui.accent': ['ui:focusBorder', 'ui:button.background'],
  'ui.strong_background': ['ui:button.background'],
  'ui.strong_foreground': ['ui:button.foreground'],
  'editor.background': ['ui:editor.background'],
  'editor.foreground': ['ui:editor.foreground'],
  'editor.gutter_background': ['ui:editorGutter.background'],
  'editor.gutter_foreground': ['ui:editorLineNumber.foreground'],
  'editor.gutter_border': ['ui:editorGroup.border'],
  'editor.cursor': ['ui:editorCursor.foreground'],
  'editor.selection': ['ui:editor.selectionBackground'],
  'editor.code_background': ['ui:textCodeBlock.background'],
  'syntax.keyword': ['scope:keyword.control', 'scope:storage.type'],
  'syntax.atom': ['scope:constant.language'],
  'syntax.literal': ['scope:constant.numeric'],
  'syntax.string': ['scope:string.quoted'],
  'syntax.regexp': ['scope:string.regexp'],
  'syntax.definition': ['scope:entity.name.function'],
  'syntax.local': ['scope:variable.parameter'],
  'syntax.type': ['scope:entity.name.type', 'scope:support.type'],
  'syntax.class': ['scope:entity.name.class', 'scope:entity.name.type.class'],
  'syntax.macro': ['scope:entity.name.function.preprocessor'],
  'syntax.property': ['scope:variable.other.property'],
  'syntax.comment': ['scope:comment.line'],
  'syntax.meta': ['scope:meta.decorator', 'scope:storage.type.annotation'],
  'syntax.invalid': ['scope:invalid', 'ui:editorError.foreground'],
  'syntax.warning': ['ui:editorWarning.foreground'],
  'diff.added': ['ui:editorGutter.addedBackground'],
  'diff.removed': ['ui:editorGutter.deletedBackground'],
  'diff.changed': ['ui:editorGutter.modifiedBackground'],
}

/**
 * What VS Code draws for a workbench color the theme does not give, for the ones [SOURCES] reads that
 * have such a default: a color for dark and light themes, or the color it follows. From `registerColor`
 * in microsoft/vscode at 3a12a6ee5e86e2a80470da49cc8f1bf04f6e27f9. Tried only after every source of a key.
 */
export const DEFAULTS: Record<string, { dark: string; light: string } | string> = {
  'panel.border': { dark: '#80808059', light: '#80808059' },
  'disabledForeground': { dark: '#cccccc80', light: '#61616180' },
  'editorLineNumber.foreground': { dark: '#858585', light: '#237893' },
  'editorCursor.foreground': { dark: '#aeafad', light: '#000000' },
  'editorError.foreground': { dark: '#f14c4c', light: '#e51400' },
  'editorWarning.foreground': { dark: '#cca700', light: '#bf8803' },
  'editorGutter.addedBackground': { dark: '#487e02', light: '#48985d' },
  'editorGutter.deletedBackground': 'editorError.foreground',
  'editorGutter.modifiedBackground': { dark: '#1b81a8', light: '#2090d3' },
}

/** The keys that take only an opaque color from the theme, passing over one with alpha. */
const OPAQUE = new Set(['ui.accent'])

/** A color written as `#rrggbbaa` with `00` alpha draws nothing, and is taken as not given. */
function invisible(color: string): boolean {
  return /^#[0-9a-f]{6}00$/i.test(color) || /^#[0-9a-f]{3}0$/i.test(color)
}

/** Whether [color], `#rgba` or `#rrggbbaa`, lets what is under it show. */
function translucent(color: string): boolean {
  const hex = color.slice(1).toLowerCase()
  if (hex.length === 4) return hex[3] !== 'f'
  if (hex.length === 8) return hex.slice(6) !== 'ff'
  return false
}

/**
 * The foreground VS Code gives a token of [scope], and the selector that gave it: of the rules whose
 * selector is [scope] or a parent of it (`keyword` for `keyword.control`), the one with the most
 * segments, a later one winning a tie. Selectors that need a parent scope (`meta.function
 * entity.name`) or exclude one (`a - b`) are passed over: there is no parent here to match them.
 */
export function tokenColor(rules: TokenRule[], scope: string): Picked | null {
  let best: { value: string; selector: string; depth: number } | null = null
  for (const rule of rules) {
    const foreground = rule.settings?.foreground
    if (!foreground || rule.scope === undefined) continue
    const selectors = (Array.isArray(rule.scope) ? rule.scope : rule.scope.split(',')).map((it) => it.trim())
    for (const selector of selectors) {
      if (!selector || /\s/.test(selector)) continue
      if (selector !== scope && !scope.startsWith(`${selector}.`)) continue
      const depth = selector.split('.').length
      if (!best || depth >= best.depth) best = { value: foreground, selector, depth }
    }
  }
  return best && { value: best.value.toLowerCase(), from: `token ${best.selector} (for ${scope})` }
}

/**
 * [text] as JSON with what VS Code also takes in a theme file left out: line and block comments and
 * trailing commas, outside strings.
 */
export function parseJsonc(text: string): unknown {
  let out = ''
  for (let at = 0; at < text.length; at++) {
    const char = text[at]
    if (char === '"') {
      const end = /"(?:[^"\\]|\\.)*"/y
      end.lastIndex = at
      const string = end.exec(text)?.[0] ?? text.slice(at)
      out += string
      at += string.length - 1
    } else if (text.startsWith('//', at)) {
      at = text.indexOf('\n', at) === -1 ? text.length : text.indexOf('\n', at) - 1
    } else if (text.startsWith('/*', at)) {
      at = text.indexOf('*/', at + 2) === -1 ? text.length : text.indexOf('*/', at + 2) + 1
    } else {
      out += char
    }
  }
  return JSON.parse(out.replace(/,(\s*[}\]])/g, '$1'))
}

/** What VS Code draws for the workbench color [name] in [theme] when the theme leaves it out, if anything. */
function byDefault(theme: VscodeTheme, base: 'light' | 'dark', name: string): Picked | null {
  const fallback = DEFAULTS[name]
  if (fallback === undefined) return null
  if (typeof fallback === 'object') return { value: fallback[base], from: `${name}: VS Code's default for a ${base} theme` }
  const own = theme.colors?.[fallback]
  if (own !== undefined) return { value: own.toLowerCase(), from: `${name}: VS Code's default, ${fallback}` }
  const followed = byDefault(theme, base, fallback)
  return followed && { value: followed.value, from: `${name}: VS Code's default, ${followed.from}` }
}

/**
 * Every key [theme] gives a color for, each from the first of [SOURCES] it has, or else from the first
 * of them VS Code has a default for: what VS Code would draw.
 */
export function fromVscode(theme: VscodeTheme): { title: string; base: 'light' | 'dark'; colors: Record<string, Picked> } {
  const base = theme.type === 'light' ? 'light' : 'dark'
  const colors: Record<string, Picked> = {}
  for (const [key, sources] of Object.entries(SOURCES)) {
    const parsed = sources.map((source) => [source.slice(0, source.indexOf(':')), source.slice(source.indexOf(':') + 1)])
    for (const [kind, name] of parsed) {
      const picked =
        kind === 'ui'
          ? theme.colors?.[name] !== undefined ? { value: theme.colors[name].toLowerCase(), from: name } : null
          : tokenColor(theme.tokenColors ?? [], name)
      if (picked && !invisible(picked.value) && !(OPAQUE.has(key) && translucent(picked.value))) {
        colors[key] = picked
        break
      }
    }
    if (colors[key]) continue
    for (const [kind, name] of parsed) {
      const picked = kind === 'ui' && theme.colors?.[name] === undefined ? byDefault(theme, base, name) : null
      if (picked) {
        colors[key] = picked
        break
      }
    }
  }
  return { title: theme.name ?? '', base, colors }
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  const theme = parseJsonc(readFileSync(process.argv[2], 'utf8')) as VscodeTheme
  console.log(JSON.stringify(fromVscode(theme), null, 2))
}

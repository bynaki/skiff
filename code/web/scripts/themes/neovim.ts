// Reads a Neovim colorscheme into a theme's colors for a bundle's data file
// (docs/skiffcode.spec/community-themes.md "바꾸는 방법"), the way `vscode.ts` reads a VS Code theme. This
// is what is run once, when a bundle's data is written from its source; the build never runs it.
//
//   node scripts/themes/neovim.ts <plugin dir> <colorscheme> [light|dark] [<ex command>...]
//
// loads the colorscheme from the plugin's directory in a headless `nvim --clean`, with `background`
// set first when it is given (a colorscheme that serves both, like oxocarbon) and the ex commands run
// before it (its options: `let g:gruvbox_contrast_dark = 'hard'`), and prints
// `{ base, colors }`, where each color is `{ value, from }` and `from` names the highlight group it
// was read from. The groups are read as Neovim draws them, links followed: a group the colorscheme
// leaves to Neovim's default links is what it links to. A key no group gives a color for is left out,
// for `generate.ts` to fill by the mapping table's last column.
//
// This runs the colorscheme's own code, as Neovim would. Its caches go to a temporary directory.
import { execFileSync } from 'node:child_process'
import { mkdtempSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { contrast } from './generate.ts'
import type { Picked } from './vscode.ts'

/** One group as `dump` reads it: its colors with `reverse` applied, and the group its links end at. */
export interface Group {
  fg?: string
  bg?: string
  /** The group the links end at, when it is not this one. */
  via?: string
  /** Drawn with fg and bg swapped, which `fg` and `bg` already are. */
  reverse?: boolean
}

/** What `dump` reads from a loaded colorscheme. */
export interface Dump {
  /** `vim.o.background` after loading, which a light colorscheme does not always set. */
  background: 'light' | 'dark'
  groups: Record<string, Group>
}

/**
 * Where each key is read from, in the order tried: a highlight group and which of its colors. The
 * tree-sitter groups come first, as what Neovim draws code with; the legacy ones after, for a
 * colorscheme that only sets those. A class name is `@lsp.type.class` once a language server
 * colors it and `@type` before.
 */
export const SOURCES: Record<string, string[]> = {
  'ui.background': ['NormalFloat bg'],
  'ui.foreground': ['NormalFloat fg'],
  'ui.muted': ['Comment fg'],
  'ui.border': ['FloatBorder fg', 'WinSeparator fg', 'VertSplit fg'],
  'ui.faint': ['NonText fg'],
  'ui.subtle': ['CursorLine bg'],
  'ui.accent': ['Function fg'],
  'ui.strong_background': ['PmenuSel bg'],
  // A PmenuSel with no fg keeps the menu's.
  'ui.strong_foreground': ['PmenuSel fg', 'Pmenu fg'],
  'editor.background': ['Normal bg'],
  'editor.foreground': ['Normal fg'],
  'editor.gutter_background': ['SignColumn bg'],
  'editor.gutter_foreground': ['LineNr fg'],
  'editor.gutter_border': ['WinSeparator fg', 'VertSplit fg'],
  'editor.cursor': ['Cursor bg'],
  'editor.selection': ['Visual bg'],
  'editor.code_background': ['ColorColumn bg', 'CursorLine bg'],
  'syntax.keyword': ['@keyword fg', 'Keyword fg'],
  'syntax.atom': ['@boolean fg', '@constant.builtin fg', 'Boolean fg'],
  'syntax.literal': ['@number fg', 'Number fg'],
  'syntax.string': ['@string fg', 'String fg'],
  'syntax.regexp': ['@string.regexp fg'],
  'syntax.definition': ['@function fg', 'Function fg'],
  'syntax.local': ['@variable.parameter fg'],
  'syntax.type': ['@type fg', 'Type fg'],
  'syntax.class': ['@lsp.type.class fg', '@type fg'],
  'syntax.macro': ['@function.macro fg', 'Macro fg'],
  'syntax.property': ['@property fg', '@variable.member fg'],
  'syntax.comment': ['@comment fg', 'Comment fg'],
  'syntax.meta': ['@attribute fg', 'PreProc fg'],
  // A Vim colorscheme from before Neovim's diagnostics colors them as ALE's signs.
  'syntax.invalid': ['DiagnosticError fg', 'ALEErrorSign fg'],
  'syntax.warning': ['DiagnosticWarn fg', 'ALEWarningSign fg'],
  'diff.added': ['GitSignsAdd fg', 'diffAdded fg', 'Added fg'],
  'diff.removed': ['GitSignsDelete fg', 'diffRemoved fg', 'Removed fg'],
  'diff.changed': ['GitSignsChange fg', 'diffChanged fg', 'Changed fg'],
}

/** Every group [SOURCES] names, once. */
export const GROUPS = [...new Set(Object.values(SOURCES).flat().map((source) => source.split(' ')[0]))]

/**
 * Every key [dump] gives a color for, each from the first of [SOURCES] it has, and whether it is light:
 * by its `Normal` background, nearer white or black. `background` cannot say — `cyberdream-light` and
 * `kanagawa-lotus` load their light colors and leave it `dark`.
 */
export function fromNeovim(dump: Dump): { base: 'light' | 'dark'; colors: Record<string, Picked> } {
  const colors: Record<string, Picked> = {}
  for (const [key, sources] of Object.entries(SOURCES)) {
    for (const source of sources) {
      const [name, which] = source.split(' ') as [string, 'fg' | 'bg']
      const group = dump.groups[name]
      const value = group?.[which]
      if (value) {
        const notes = [group.via && `via ${group.via}`, group.reverse && 'reversed'].filter(Boolean)
        colors[key] = { value, from: notes.length > 0 ? `${source} (${notes.join(', ')})` : source }
        break
      }
    }
  }
  const background = colors['editor.background']?.value
  const base = background ? (contrast(background, '#000000') > contrast(background, '#ffffff') ? 'light' : 'dark') : dump.background
  return { base, colors }
}

/**
 * Reads [GROUPS] in the editor, after `vim.o.background` is set and the colorscheme loaded. A reversed
 * group draws the colors it lacks from the text under it, the menu's for `PmenuSel` and `Normal`'s
 * for the rest: Neovim's own `PmenuSel` is only `reverse`.
 *
 * A group the colorscheme left as `:highlight clear` makes it, and that draws as it would without the
 * colorscheme, is read as empty: its colors are Neovim's own colorscheme's, not the theme's. A Vim
 * colorscheme older than `NormalFloat` and tree-sitter (gruvbox) would otherwise have Neovim's gray
 * floats and parameters. One whose default link ends at a group the theme set is the theme's: vague's
 * `FloatBorder`, linked to its `NormalFloat`, and gruvbox's `@keyword`, linked to its `Keyword`.
 */
const LUA = `
local hex = function(n) return n and string.format('#%06x', n) or nil end
local function read(name)
  local hl = vim.api.nvim_get_hl(0, { name = name, link = false })
  local fg, bg = hex(hl.fg), hex(hl.bg)
  if hl.reverse then
    local under = vim.api.nvim_get_hl(0, { name = name == 'PmenuSel' and 'Pmenu' or 'Normal', link = false })
    fg, bg = bg or hex(under.bg), fg or hex(under.fg)
  end
  -- Through links, and from an empty tree-sitter group to its parent, as Neovim draws it.
  local at, seen = name, {}
  while true do
    local definition = vim.api.nvim_get_hl(0, { name = at, link = true })
    local to = definition.link
    if not to and next(definition) == nil and at:find('^@.+%.') then to = at:gsub('%.[^.]*$', '') end
    if not to or seen[to] then break end
    seen[to] = true
    at = to
  end
  return { fg = fg, bg = bg, via = at ~= name and at or nil, reverse = hl.reverse }
end
local names = vim.json.decode(vim.env.SKIFF_GROUPS)
local out = { background = vim.o.background, groups = vim.empty_dict() }
local own = {}
for _, name in ipairs(names) do
  out.groups[name] = read(name)
  own[name] = vim.api.nvim_get_hl(0, { name = name, link = true })
end
vim.cmd('highlight clear')
for _, name in ipairs(names) do
  local default = read(name)
  local untouched = vim.deep_equal(own[name], vim.api.nvim_get_hl(0, { name = name, link = true }))
  local group = out.groups[name]
  if untouched and group.fg == default.fg and group.bg == default.bg then
    out.groups[name] = vim.empty_dict()
  end
end
io.stdout:write(vim.json.encode(out))
`

/** [colorscheme] from the plugin at [dir], loaded in `nvim --headless --clean` after [before]. */
export function dump(dir: string, colorscheme: string, background?: 'light' | 'dark', before: string[] = []): Dump {
  const temp = mkdtempSync(join(tmpdir(), 'skiff-neovim-'))
  try {
    const script = join(temp, 'dump.lua')
    writeFileSync(script, LUA)
    const commands = [`set runtimepath^=${resolve(dir).replace(/([ ,\\])/g, '\\$1')}`, 'set termguicolors']
    if (background) commands.push(`set background=${background}`)
    commands.push(...before)
    commands.push(`colorscheme ${colorscheme}`, `luafile ${script}`, 'qall!')
    const xdg = Object.fromEntries(['XDG_CACHE_HOME', 'XDG_STATE_HOME', 'XDG_DATA_HOME', 'XDG_CONFIG_HOME'].map((name) => [name, join(temp, name)]))
    const output = execFileSync('nvim', ['--headless', '--clean', ...commands.flatMap((command) => ['-c', command])], {
      env: { ...process.env, ...xdg, SKIFF_GROUPS: JSON.stringify(GROUPS) },
      encoding: 'utf8',
    })
    return JSON.parse(output) as Dump
  } finally {
    rmSync(temp, { recursive: true, force: true })
  }
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  const [dir, colorscheme, background, ...before] = process.argv.slice(2)
  console.log(JSON.stringify(fromNeovim(dump(dir, colorscheme, background as 'light' | 'dark' | undefined, before)), null, 2))
}

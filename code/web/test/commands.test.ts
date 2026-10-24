// Which commands the palette offers, which depends on what is on the screen.
import { describe, expect, test } from 'vitest'
import { CURRENT, type CommandSource, INSTALLED, LOADING, catalogItems, commands, themeItems } from '../src/commands'
import type { LayerName } from '../src/layers/pane'
import type { ThemeEntry } from '../src/settings'

/** A theme as Kotlin lists it, with colors of its own so that a swatch can be told from another. */
const entry = (id: string, title: string): ThemeEntry => ({ id, title, dark: false, swatch: [`#${id.length}00000`, '#111', '#222', '#333', '#444', '#555'] })

/** What every install has, as Kotlin names them. */
const BASES = [entry('system', 'System'), entry('light', 'Light'), entry('dark', 'Dark')]

/** A source that says what it was asked to do, over a screen described by [state]. */
function source(state: { open: boolean; layer: LayerName | null; ownTheme?: boolean; lsp?: boolean; back?: boolean; forward?: boolean }) {
  const ran: string[] = []
  const command: CommandSource = {
    open: () => state.open,
    layer: () => state.layer,
    save: () => ran.push('save'),
    reload: () => ran.push('reload'),
    close: () => ran.push('close'),
    toggleLayer: () => ran.push('toggleLayer'),
    show: (layer) => ran.push(`show ${layer}`),
    zoom: (by) => ran.push(`zoom ${by}`),
    resetZoom: () => ran.push('resetZoom'),
    toggleSidebar: () => ran.push('toggleSidebar'),
    openSettings: () => ran.push('openSettings'),
    chooseTheme: () => ran.push('chooseTheme'),
    downloadTheme: () => ran.push('downloadTheme'),
    importSettings: () => ran.push('importSettings'),
    exportSettings: () => ran.push('exportSettings'),
    importTheme: () => ran.push('importTheme'),
    exportTheme: () => ran.push('exportTheme'),
    ownTheme: () => state.ownTheme ?? false,
    copyTheme: () => ran.push('copyTheme'),
    editTheme: () => ran.push('editTheme'),
    deleteTheme: () => ran.push('deleteTheme'),
    resetData: () => ran.push('resetData'),
    undo: () => ran.push('undo'),
    redo: () => ran.push('redo'),
    lsp: () => state.lsp ?? false,
    goToDefinition: () => ran.push('goToDefinition'),
    showHover: () => ran.push('showHover'),
    canBack: () => state.back ?? false,
    canForward: () => state.forward ?? false,
    back: () => ran.push('back'),
    forward: () => ran.push('forward'),
  }
  return { command, ran }
}

const names = (state: { open: boolean; layer: LayerName | null; ownTheme?: boolean; lsp?: boolean; back?: boolean; forward?: boolean }) =>
  commands(source(state).command).map((item) => item.name)

describe('what the palette offers', () => {
  test('is everything on a file being edited', () => {
    expect(names({ open: true, layer: 'editor' })).toEqual([
      'Save File',
      'Toggle Layer',
      'Show Editor',
      'Show Viewer',
      'Undo',
      'Redo',
      'Zoom In',
      'Zoom Out',
      'Reset Zoom',
      'Toggle Sidebar',
      'Open Settings',
      'Import Settings',
      'Export Settings',
      'Theme...',
      'Download Theme...',
      'Import Theme',
      'Export Theme',
      'Copy Theme',
      'Edit Theme',
      'Reset Data…',
      'Reload File',
      'Close File',
    ])
  })

  test('asks the language server only for a file that has one', () => {
    expect(names({ open: true, layer: 'viewer' })).not.toContain('Go to Definition')
    const asked = names({ open: true, layer: 'viewer', lsp: true })
    expect(asked.slice(asked.indexOf('Show Viewer') + 1, asked.indexOf('Show Viewer') + 3)).toEqual(['Go to Definition', 'Show Hover'])
    const { command, ran } = source({ open: true, layer: 'editor', lsp: true })
    for (const item of commands(command)) if (item.name === 'Go to Definition' || item.name === 'Show Hover') item.run()
    expect(ran).toEqual(['goToDefinition', 'showHover'])
  })

  test('goes back and forward only where the jump list has somewhere to go', () => {
    expect(names({ open: true, layer: 'viewer' })).not.toContain('Go Back')
    expect(names({ open: true, layer: 'viewer' })).not.toContain('Go Forward')
    const { command, ran } = source({ open: true, layer: 'diff', back: true, forward: true })
    const listed = commands(command)
    const at = listed.findIndex((item) => item.name === 'Go Back')
    expect(listed.slice(at - 1, at + 2).map((item) => item.name)).toEqual(['Show Viewer', 'Go Back', 'Go Forward'])
    listed[at].run()
    listed[at + 1].run()
    expect(ran).toEqual(['back', 'forward'])
    // A file that could not be shown is still a spot to leave from.
    expect(names({ open: true, layer: null, back: true })).toEqual(['Go Back', ...names({ open: true, layer: null })])
  })

  test('leaves out undo and redo where there is no typing', () => {
    expect(names({ open: true, layer: 'viewer' })).not.toContain('Undo')
    expect(names({ open: true, layer: 'viewer' })).toContain('Save File')
  })

  test('is what can be done to a file that could not be shown', () => {
    // Too large, or binary: there is an open file and a notice where the document would be.
    expect(names({ open: true, layer: null })).toEqual([
      'Zoom In',
      'Zoom Out',
      'Reset Zoom',
      'Toggle Sidebar',
      'Open Settings',
      'Import Settings',
      'Export Settings',
      'Theme...',
      'Download Theme...',
      'Import Theme',
      'Export Theme',
      'Copy Theme',
      'Edit Theme',
      'Reset Data…',
      'Reload File',
      'Close File',
    ])
  })

  test('is only what needs no file when none is open', () => {
    expect(names({ open: false, layer: null })).toEqual([
      'Zoom In',
      'Zoom Out',
      'Reset Zoom',
      'Toggle Sidebar',
      'Open Settings',
      'Import Settings',
      'Export Settings',
      'Theme...',
      'Download Theme...',
      'Import Theme',
      'Export Theme',
      'Copy Theme',
      'Edit Theme',
      'Reset Data…',
    ])
  })

  test('runs what it says it runs', () => {
    const { command, ran } = source({ open: true, layer: 'editor' })
    for (const item of commands(command)) item.run()
    expect(ran).toEqual([
      'save',
      'toggleLayer',
      'show editor',
      'show viewer',
      'undo',
      'redo',
      'zoom 1',
      'zoom -1',
      'resetZoom',
      'toggleSidebar',
      'openSettings',
      'importSettings',
      'exportSettings',
      'chooseTheme',
      'downloadTheme',
      'importTheme',
      'exportTheme',
      'copyTheme',
      'editTheme',
      'resetData',
      'reload',
      'close',
    ])
  })

  test('offers no theme by name: Theme... and Download Theme... open the lists', () => {
    const { command, ran } = source({ open: false, layer: null })
    const listed = commands(command)
    expect(listed.filter((item) => item.name.startsWith('Theme: '))).toEqual([])
    listed.find((item) => item.name === 'Theme...')?.run()
    listed.find((item) => item.name === 'Download Theme...')?.run()
    expect(ran).toEqual(['chooseTheme', 'downloadTheme'])
  })

  test("offers Delete Theme only on a theme of the person's own", () => {
    expect(names({ open: false, layer: null })).not.toContain('Delete Theme')
    const { command, ran } = source({ open: false, layer: null, ownTheme: true })
    const listed = commands(command)
    expect(listed.map((item) => item.name).slice(-5)).toEqual(['Export Theme', 'Copy Theme', 'Edit Theme', 'Delete Theme', 'Reset Data…'])
    listed[listed.length - 2].run()
    expect(ran).toEqual(['deleteTheme'])
  })

  test('offers Reset Data with or without a file, and leaves the asking to Kotlin', () => {
    const { command, ran } = source({ open: false, layer: null })
    commands(command).find((item) => item.name === 'Reset Data…')?.run()
    expect(ran).toEqual(['resetData'])
  })
})

describe("Theme...'s list", () => {
  const installed = [...BASES, entry('ocean (1)', 'ocean (1)'), entry('catppuccin-mocha', 'catppuccin-mocha')]

  test('is every installed theme in the order Kotlin gives, by its title and its colors', () => {
    const items = themeItems(installed, 'system', () => {})
    expect(items.map((item) => item.name)).toEqual(['System', 'Light', 'Dark', 'ocean (1)', 'catppuccin-mocha'])
    expect(items.map((item) => item.swatch)).toEqual(installed.map((theme) => theme.swatch))
  })

  test('marks the theme settings.toml names, and only that one', () => {
    expect(themeItems(installed, 'ocean (1)', () => {}).map((item) => item.mark)).toEqual([undefined, undefined, undefined, CURRENT, undefined])
    expect(themeItems(installed, 'system', () => {}).map((item) => item.mark)).toEqual([CURRENT, undefined, undefined, undefined, undefined])
  })

  test('sets a theme by its id', () => {
    const chosen: string[] = []
    themeItems(installed, 'system', (id) => chosen.push(id))[3].run()
    expect(chosen).toEqual(['ocean (1)'])
  })
})

describe("Download Theme...'s list", () => {
  const listed = {
    loading: false as const,
    themes: [
      { id: 'catppuccin-mocha', title: 'Catppuccin Mocha', family: 'Catppuccin', dark: true, swatch: ['#1e1e2e', '#cdd6f4', '#cba6f7', '#a6e3a1', '#89b4fa', '#cba6f7'] },
      { id: 'gruvbox-dark-soft', title: 'Gruvbox Dark Soft', family: 'Gruvbox', dark: true, swatch: ['#32302f', '#ebdbb2', '#fb4934', '#b8bb26', '#b8bb26', '#fe8019'] },
    ],
    installed: ['gruvbox-dark-soft'],
  }

  test('is one line saying it is coming until Kotlin answers, which is not run', () => {
    const items = catalogItems({ loading: true }, () => Promise.resolve())
    expect(items.map((item) => [item.name, item.inert])).toEqual([[LOADING, true]])
  })

  test('is each theme by its title, its collection and its colors, the installed ones marked', () => {
    const items = catalogItems(listed, () => Promise.resolve())
    expect(items.map((item) => [item.name, item.detail, item.mark])).toEqual([
      ['Catppuccin Mocha', 'Catppuccin', undefined],
      ['Gruvbox Dark Soft', 'Gruvbox', INSTALLED],
    ])
    expect(items[0].swatch).toEqual(listed.themes[0].swatch)
  })

  test('asks for a theme by its id, and answers with the asking', async () => {
    const asked: string[] = []
    const answer = catalogItems(listed, (id) => {
      asked.push(id)
      return Promise.resolve('installed')
    })[1].run()
    expect(asked).toEqual(['gruvbox-dark-soft'])
    await expect(answer).resolves.toBe('installed')
  })
})

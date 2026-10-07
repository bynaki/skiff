// Which commands the palette offers, which depends on what is on the screen.
import { describe, expect, test } from 'vitest'
import { type CommandSource, commands } from '../src/commands'
import type { LayerName } from '../src/layers/pane'

/** A source that says what it was asked to do, over a screen described by [state]. */
function source(state: { open: boolean; layer: LayerName | null; themes?: string[]; ownTheme?: boolean; lsp?: boolean }) {
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
    themes: () => state.themes ?? ['system', 'light', 'dark'],
    setTheme: (name) => ran.push(`setTheme ${name}`),
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
  }
  return { command, ran }
}

const names = (state: { open: boolean; layer: LayerName | null; themes?: string[]; ownTheme?: boolean; lsp?: boolean }) =>
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
      'Theme: System',
      'Theme: Light',
      'Theme: Dark',
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
      'Theme: System',
      'Theme: Light',
      'Theme: Dark',
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
      'Theme: System',
      'Theme: Light',
      'Theme: Dark',
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
      'setTheme system',
      'setTheme light',
      'setTheme dark',
      'importTheme',
      'exportTheme',
      'copyTheme',
      'editTheme',
      'resetData',
      'reload',
      'close',
    ])
  })

  test('offers an imported theme by its own name, after the ones every install has', () => {
    const { command, ran } = source({ open: false, layer: null, themes: ['system', 'light', 'dark', 'ocean (1)', '바다'] })
    const themes = commands(command).filter((item) => item.name.startsWith('Theme: '))
    expect(themes.map((item) => item.name)).toEqual([
      'Theme: System',
      'Theme: Light',
      'Theme: Dark',
      'Theme: ocean (1)',
      'Theme: 바다',
    ])
    themes[3].run()
    expect(ran).toEqual(['setTheme ocean (1)'])
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

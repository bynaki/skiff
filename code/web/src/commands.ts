// What the `>` mode of the palette can run (docs/skiffcode.plan.md "커맨드 레지스트리").
//
// The list is made fresh every time the palette asks, because what a command would do depends on
// what is on the screen: with nothing open there is nothing to save, close or undo, and a file
// that could not be shown — too large, binary — has no layers under it but can still be closed.
// Leaving a command out is how the palette says so; an entry that answers a tap with nothing is
// worse than no entry.
//
// The names are English and written here rather than coming from Kotlin's string resources, like
// everything else the palette shows (2026-09-21 사용자 결정).
import type { LayerName } from './layers/pane'
import type { PaletteItem } from './palette'
import type { ThemeEntry } from './settings'

/** What the commands act on, and what says which of them there is anything to act on. */
export interface CommandSource {
  /** A file is open, whether or not there is a document under it. */
  open(): boolean
  /** The layer showing, or null when nothing is on the screen but a notice. */
  layer(): LayerName | null
  save(): void
  reload(): void
  close(): void
  toggleLayer(): void
  show(layer: LayerName): void
  /** By [ZOOM_STEP] up or down, around the middle of the screen. */
  zoom(by: number): void
  resetZoom(): void
  toggleSidebar(): void
  /** Opens `settings.toml`, which saving applies. */
  openSettings(): void
  /** Opens the palette onto the installed themes, [themeItems]. */
  chooseTheme(): void
  /** Opens the palette onto the themes the repository has, [catalogItems], and asks Kotlin for them. */
  downloadTheme(): void
  /** Each through the system's file picker; what came of it arrives later, on the banner. */
  importSettings(): void
  exportSettings(): void
  importTheme(): void
  /** The theme on the screen, which for `system` is the bundled one it is following. */
  exportTheme(): void
  /** Whether the theme on the screen is the person's own — imported or copied — rather than bundled. */
  ownTheme(): boolean
  /** Each on the theme on the screen, as Export Theme is. Edit on a bundled theme offers a copy instead. */
  copyTheme(): void
  editTheme(): void
  deleteTheme(): void
  /** Kotlin asks what to clear, in a dialog of its own. */
  resetData(): void
  undo(): void
  redo(): void
  /** Whether the file on the screen has a language server to ask, from a code view. */
  lsp(): boolean
  /** Each at the cursor. */
  goToDefinition(): void
  showHover(): void
  /** Whether the jump list has a spot that way; see `jumps.ts`. */
  canBack(): boolean
  canForward(): boolean
  back(): void
  forward(): void
}

/**
 * In the order they are offered before anything is typed — the fuzzy score keeps that order for
 * anything it scores the same, and the recent list is what usually answers first anyway.
 */
export function commands(source: CommandSource): PaletteItem[] {
  const layer = source.layer()
  const items: PaletteItem[] = []
  if (layer) {
    items.push(
      { name: 'Save File', run: () => source.save() },
      { name: 'Toggle Layer', run: () => source.toggleLayer() },
      { name: 'Show Editor', run: () => source.show('editor') },
      { name: 'Show Viewer', run: () => source.show('viewer') },
    )
  }
  if (source.lsp()) {
    items.push(
      { name: 'Go to Definition', run: () => source.goToDefinition() },
      { name: 'Show Hover', run: () => source.showHover() },
    )
  }
  if (source.canBack()) items.push({ name: 'Go Back', run: () => source.back() })
  if (source.canForward()) items.push({ name: 'Go Forward', run: () => source.forward() })
  // Only where typing is: undo in the reading layer would take back what cannot be seen from there.
  if (layer === 'editor') {
    items.push({ name: 'Undo', run: () => source.undo() }, { name: 'Redo', run: () => source.redo() })
  }
  items.push(
    { name: 'Zoom In', run: () => source.zoom(1) },
    { name: 'Zoom Out', run: () => source.zoom(-1) },
    { name: 'Reset Zoom', run: () => source.resetZoom() },
    { name: 'Toggle Sidebar', run: () => source.toggleSidebar() },
    { name: 'Open Settings', run: () => source.openSettings() },
    { name: 'Import Settings', run: () => source.importSettings() },
    { name: 'Export Settings', run: () => source.exportSettings() },
    { name: 'Theme...', run: () => source.chooseTheme() },
    { name: 'Download Theme...', run: () => source.downloadTheme() },
    { name: 'Import Theme', run: () => source.importTheme() },
    { name: 'Export Theme', run: () => source.exportTheme() },
    { name: 'Copy Theme', run: () => source.copyTheme() },
    { name: 'Edit Theme', run: () => source.editTheme() },
  )
  // A bundled theme is part of the app: there is nothing to delete.
  if (source.ownTheme()) items.push({ name: 'Delete Theme', run: () => source.deleteTheme() })
  items.push({ name: 'Reset Data…', run: () => source.resetData() })
  if (source.open()) {
    items.push(
      { name: 'Reload File', run: () => source.reload() },
      { name: 'Close File', run: () => source.close() },
    )
  }
  return items
}

/** What marks the theme `settings.toml` names, in `Theme...`'s list. */
export const CURRENT = 'current'

/** What marks a theme of the repository's that is already one of the person's own. */
export const INSTALLED = 'installed'

/** What the download list says until Kotlin has answered. */
export const LOADING = 'Loading themes…'

/**
 * `Theme...`'s list: every theme installed, in Kotlin's order — `System`, `Light`, `Dark`, then the
 * person's own — each drawn by its colors, [chosen] marked. Picking one writes it to `settings.toml`
 * with [choose], which applies it.
 */
export function themeItems(themes: ThemeEntry[], chosen: string, choose: (id: string) => void): PaletteItem[] {
  return themes.map((theme) => ({
    name: theme.title,
    swatch: theme.swatch,
    mark: theme.id === chosen ? CURRENT : undefined,
    run: () => choose(theme.id),
  }))
}

/** A theme of the repository's, as Kotlin's `themeCatalog` lists it. */
export interface CatalogTheme {
  id: string
  title: string
  family: string
  dark: boolean
  swatch: string[]
}

/** The download list: still being asked for, or what came, with which of it is installed. */
export type Catalog = { loading: true } | { loading: false; themes: CatalogTheme[]; installed: string[] }

/**
 * `Download Theme...`'s list, in Kotlin's order — by the name each shows — searched by that name and
 * the collection. Picking one asks Kotlin with [download] to download it, keep it and put it on the
 * screen, and the palette stays open on that line until it answers.
 */
export function catalogItems(catalog: Catalog, download: (id: string) => Promise<unknown>): PaletteItem[] {
  if (catalog.loading) return [{ name: LOADING, inert: true, run: () => {} }]
  const installed = new Set(catalog.installed)
  return catalog.themes.map((theme) => ({
    name: theme.title,
    detail: theme.family,
    swatch: theme.swatch,
    mark: installed.has(theme.id) ? INSTALLED : undefined,
    run: () => download(theme.id),
  }))
}

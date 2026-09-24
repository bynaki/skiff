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
  undo(): void
  redo(): void
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
  // Only where typing is: undo in the reading layer would take back what cannot be seen from there.
  if (layer === 'editor') {
    items.push({ name: 'Undo', run: () => source.undo() }, { name: 'Redo', run: () => source.redo() })
  }
  items.push(
    { name: 'Zoom In', run: () => source.zoom(1) },
    { name: 'Zoom Out', run: () => source.zoom(-1) },
    { name: 'Reset Zoom', run: () => source.resetZoom() },
    { name: 'Toggle Sidebar', run: () => source.toggleSidebar() },
  )
  if (source.open()) {
    items.push(
      { name: 'Reload File', run: () => source.reload() },
      { name: 'Close File', run: () => source.close() },
    )
  }
  return items
}

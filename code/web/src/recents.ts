// The last things the palette ran, kept where the page can find them again.
//
// This is not a setting: nobody opens a file to edit which commands they used last, it is a trace
// that gathers as the palette is used. So it lives in the page's own storage rather than in
// `settings.toml`, which is the file a person edits.
import { MODES, MOST_RECENT, type PaletteMode } from './palette'
import { read, write } from './storage'

const KEY = 'palette.recent'

/** What was run last in each mode, newest first. Anything unreadable is nothing. */
export function readRecents(): Map<PaletteMode, string[]> {
  const recent = new Map<PaletteMode, string[]>()
  const held = read(KEY)
  if (held === null) return recent
  let parsed: unknown
  try {
    parsed = JSON.parse(held)
  } catch {
    return recent
  }
  if (typeof parsed !== 'object' || parsed === null) return recent
  for (const mode of MODES) {
    const names = (parsed as Record<string, unknown>)[mode]
    if (!Array.isArray(names)) continue
    const kept = names.filter((name): name is string => typeof name === 'string').slice(0, MOST_RECENT)
    if (kept.length > 0) recent.set(mode, kept)
  }
  return recent
}

export function writeRecents(recent: Map<PaletteMode, string[]>): void {
  write(KEY, JSON.stringify(Object.fromEntries(recent)))
}

// The last things the palette ran, kept where the page can find them again.
//
// This is not a setting: nobody opens a file to edit which commands they used last, it is a trace
// that gathers as the palette is used. So it lives in the page's own storage rather than in
// `settings.toml`, which is the file a person edits.
//
// The storage is the WebView's, per origin, under the app's own data — no other app and no server
// sees it, and clearing the app's data clears it. Everything here survives it being missing:
// `localStorage` is null until `domStorageEnabled` is set, and a page under test has none at all.
import { MODES, MOST_RECENT, type PaletteMode } from './palette'

const KEY = 'palette.recent'

/** Null rather than a throwing object is what a WebView without DOM storage gives. */
function storage(): Storage | null {
  try {
    return globalThis.localStorage ?? null
  } catch {
    return null
  }
}

/** What was run last in each mode, newest first. Anything unreadable is nothing. */
export function readRecents(): Map<PaletteMode, string[]> {
  const recent = new Map<PaletteMode, string[]>()
  let held: string | null = null
  try {
    held = storage()?.getItem(KEY) ?? null
  } catch {
    return recent
  }
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

/** Writing is best effort: storage can be off or full, and the palette works either way. */
export function writeRecents(recent: Map<PaletteMode, string[]>): void {
  try {
    storage()?.setItem(KEY, JSON.stringify(Object.fromEntries(recent)))
  } catch {
    // Nothing to do about it and nothing to tell the user: the list is a convenience.
  }
}

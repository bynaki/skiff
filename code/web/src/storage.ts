// The page's own storage: what gathers as the app is used, rather than what a person sets
// (plan.md "상태 저장"). The last things the palette ran are in here, and so is the zoom.
//
// It is the WebView's `localStorage`, per origin, under the app's own data — no other app and no
// server sees it, and clearing the app's data clears it. Everything that reads it survives it
// being missing: it is null until `domStorageEnabled` is set, and a page under test has none at
// all. Keys are named for what keeps them: `palette.recent`, `zoom.size`.
/** Null rather than a throwing object is what a WebView without DOM storage gives. */
export function storage(): Storage | null {
  try {
    return globalThis.localStorage ?? null
  } catch {
    return null
  }
}

/** What is held under [key], or null when there is nothing, no storage, or it refused to say. */
export function read(key: string): string | null {
  try {
    return storage()?.getItem(key) ?? null
  } catch {
    return null
  }
}

/** Best effort: storage can be off or full, and everything that keeps something here works without it. */
export function write(key: string, value: string): void {
  try {
    storage()?.setItem(key, value)
  } catch {
    // Nothing to do about it and nothing to tell the user: what is kept here is a convenience.
  }
}

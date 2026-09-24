// When the loading line is on the screen, which is not the same as while a file is being opened.
// A local file is back in a few milliseconds, and a line that flashes on every one of those is
// worse than no line at all; one that appears at the end of a slow open and leaves in the same
// frame is the same flash from the other side.

/** Nothing shows before this: an open that beats it never puts anything on the screen. */
export const SHOW_AFTER_MS = 200
/** Once something is up it stays this long, so it is not gone before it was noticed. */
export const MIN_VISIBLE_MS = 300

export interface Loading {
  /** A link is on its way to the screen. */
  start(): void
  /** It arrived, failed, or was replaced by a newer link. */
  stop(): void
}

/**
 * [show] is told to turn the loading state on and off, never twice with the same answer. A second
 * [start] while one is still on the screen keeps it there rather than restarting it: what the user
 * sees is one wait, however many links went by underneath.
 */
export function createLoading(show: (on: boolean) => void): Loading {
  let appearing: ReturnType<typeof setTimeout> | null = null
  let leaving: ReturnType<typeof setTimeout> | null = null
  /** When the line went up, or 0 while nothing is on the screen. */
  let since = 0

  return {
    start() {
      // Still up from the last link, on its way out: keep it, rather than taking it away and
      // putting the same line back.
      if (leaving !== null) {
        clearTimeout(leaving)
        leaving = null
        return
      }
      if (appearing !== null || since !== 0) return
      appearing = setTimeout(() => {
        appearing = null
        since = Date.now()
        show(true)
      }, SHOW_AFTER_MS)
    },
    stop() {
      if (appearing !== null) {
        clearTimeout(appearing)
        appearing = null
        return
      }
      if (since === 0) return
      const left = MIN_VISIBLE_MS - (Date.now() - since)
      if (left <= 0) {
        since = 0
        show(false)
        return
      }
      leaving = setTimeout(() => {
        leaving = null
        since = 0
        show(false)
      }, left)
    },
  }
}

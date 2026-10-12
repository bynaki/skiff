// Where the reader has been, for going back and forward the way vim's Ctrl+O and Ctrl+I do.
//
// A spot is written down just before the reader leaves it — for another file, or for another line
// of this one by a definition, a symbol or a link's line — and going back walks those spots in
// reverse. It is the browser's history in shape: going back and then somewhere new drops what was
// ahead. The design is in `docs/skiffcode.spec/jumps.md`.
//
// It is kept in memory only. A file's id is a number Kotlin hands out within one process, so the
// same id in the next run names some other file, or none.

/** A spot: which open file, and the source line at the top of the screen there. */
export interface Jump {
  file: number
  line: number
}

/** How many spots are kept. Past it the oldest go first. */
export const JUMP_LIMIT = 100

export interface Jumps {
  /** Writes down [at], the spot about to be left. Anything ahead of where going back had reached is dropped. */
  record(at: Jump): void
  /**
   * The spot before, or null when there is none. [now] is where the reader is: coming back from the
   * end of the list, it is written down first, so that going forward again returns to it.
   */
  back(now: Jump): Jump | null
  /** The spot after the one going back reached, or null at the end. */
  forward(): Jump | null
  /** Takes out every spot in [file], which has been closed. What was either side of them now meet. */
  forget(file: number): void
  /**
   * Whether there is a spot to go back to. At the end of a list holding one spot it says yes without
   * knowing whether the reader is standing on that spot, in which case [back] still answers null.
   */
  readonly canBack: boolean
  readonly canForward: boolean
}

const same = (a: Jump | undefined, b: Jump | undefined) =>
  a !== undefined && b !== undefined && a.file === b.file && a.line === b.line

export function createJumps(limit = JUMP_LIMIT): Jumps {
  let spots: Jump[] = []
  // Which spot going back has reached, or spots.length when it has not gone back at all: then the
  // reader is somewhere past the end, a spot not yet written down.
  let at = 0

  /** Puts [jump] at the end, unless it is where the end already is, and keeps under the limit. */
  function push(jump: Jump): void {
    if (!same(spots[spots.length - 1], jump)) spots.push({ file: jump.file, line: jump.line })
    if (spots.length > limit) spots.splice(0, spots.length - limit)
  }

  return {
    record(leaving) {
      // Having gone back, the spot reached is the one being left, wherever it has scrolled to since:
      // it is written down again as it is now, and what was ahead of it goes.
      spots.length = Math.min(at, spots.length)
      push(leaving)
      at = spots.length
    },

    back(now) {
      if (at < spots.length) {
        if (at === 0) return null
        at -= 1
        return spots[at]
      }
      const before = spots.length
      push(now)
      // Only now itself, or nothing: there is nowhere to go, and nothing to have written down.
      if (spots.length < 2) {
        spots.length = Math.min(before, spots.length)
        return null
      }
      at = spots.length - 2
      return spots[at]
    },

    forward() {
      if (at >= spots.length - 1) return null
      at += 1
      return spots[at]
    },

    forget(file) {
      const kept: Jump[] = []
      let reached = at
      spots.forEach((spot, index) => {
        if (index === at) reached = kept.length
        if (spot.file === file) return
        // With the closed file's spots gone, the two either side may be one spot twice.
        if (same(kept[kept.length - 1], spot)) {
          if (index === at) reached = kept.length - 1
          return
        }
        kept.push(spot)
      })
      if (at >= spots.length) reached = kept.length
      spots = kept
      at = Math.min(reached, spots.length)
    },

    get canBack() {
      return at < spots.length ? at > 0 : spots.length > 0
    },

    get canForward() {
      return at < spots.length - 1
    },
  }
}

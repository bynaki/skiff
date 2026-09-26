// Whether a drag on the bar under the open files menu closes it, once the finger lets go. The menu
// follows the finger up while it is down; this is what decides between putting it away and letting
// it fall back into place.

/** Less than this and the finger was only resting: a tap, not a drag. */
export const TAP_SLOP = 8
/** Pushed at least this far up its own height, the menu is on its way out. */
export const CLOSE_FRACTION = 1 / 4
/** px/ms upward. A flick this fast closes the menu however little of it has moved. */
export const FLING_SPEED = 0.5

/**
 * [travel] is how far up the finger has taken the menu, [speed] how fast it was going up at the end
 * (px/ms, negative for down), and [height] how tall the menu is.
 */
export function releaseCloses(travel: number, speed: number, height: number): boolean {
  if (travel < TAP_SLOP) return false
  return travel >= height * CLOSE_FRACTION || speed >= FLING_SPEED
}

/** Where the finger had taken the menu, and when. */
export interface Sample {
  at: number
  travel: number
}

/** How far back [recentSpeed] looks: long enough that one uneven step does not decide it. */
export const SPEED_WINDOW_MS = 100

/**
 * How fast the menu was going up at the end, over the last [SPEED_WINDOW_MS] of [samples] (oldest
 * first), or over all of them for a drag shorter than that. A finger slows in the last few events
 * of a flick, so the step between the last two alone says too little.
 */
export function recentSpeed(samples: Sample[]): number {
  const last = samples[samples.length - 1]
  if (!last) return 0
  let first = samples[0]
  for (const sample of samples) if (last.at - sample.at >= SPEED_WINDOW_MS) first = sample
  const elapsed = last.at - first.at
  return elapsed > 0 ? (last.travel - first.travel) / elapsed : 0
}

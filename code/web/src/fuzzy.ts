// How well a query fits a name, for the palette's results (docs/skiffcode.spec.md "커맨드 버튼과 팔레트").
//
// The query's letters have to appear in the name in order, but not together: `tl` finds
// `Toggle Layer`. What is left to decide is which of the names that match comes first, and the
// answer is where the letters landed — letters that run together, and letters that start a word,
// are what a person means when they type an abbreviation.
//
// The scan is one pass per query letter, keeping the best score each letter could have ending at
// each place in the name. That is enough to find the best way the whole query fits, rather than
// the first way a left-to-right walk happens to find: in `Toggle Layer`, `tl` can take the `l` of
// `Toggle` or the one that starts `Layer`, and only looking ahead tells them apart.

/** The name begins with it. Nothing is a better place for a query to start. */
const AT_START = 10
/** It starts a word, which is what an abbreviation is made of. */
const AT_WORD = 8
/**
 * It follows the letter before it, so the query is spelling the name out. Worth more than a word
 * start is once the skip to it is paid for, so that `togg` finds `Toggle Layer` before it finds
 * `The Old Grey Goose`, whose every letter starts a word.
 */
const RUNS_ON = 8
/** Somewhere in the middle of a word: it matched, and that is all. */
const ANYWHERE = 1
/** Skipping over letters to get there. Flat, so how far apart the letters are does not decide it. */
const SKIPPED = -2

const SEPARATORS = ' ._-/\\:'

/** Whether the letter at [index] starts a word, by a separator before it or by its own case. */
function startsWord(name: string, index: number): boolean {
  if (index === 0) return true
  const before = name[index - 1]
  if (SEPARATORS.includes(before)) return true
  return before === before.toLowerCase() && name[index] !== name[index].toLowerCase()
}

/**
 * What [query] scores against [name], or null when its letters are not in there in order. Case is
 * ignored; spaces in the query are letters like any other. An empty query scores 0 — the palette
 * has nothing to show for one, and does not ask.
 */
export function score(query: string, name: string): number | null {
  if (query === '') return 0
  const needle = query.toLowerCase()
  const haystack = name.toLowerCase()
  // The best score for the query so far, ending at each place in the name, or null where the query
  // cannot end there. Before any letter is matched, the query ends nowhere and costs nothing.
  let previous: (number | null)[] = new Array(haystack.length).fill(null)
  let matchedAny = false
  for (let q = 0; q < needle.length; q += 1) {
    const next: (number | null)[] = new Array(haystack.length).fill(null)
    // The best the query could have done ending anywhere before where we are now, which is what a
    // letter that does not follow the one before it has to build on.
    let bestBefore: number | null = q === 0 ? 0 : null
    matchedAny = false
    for (let i = 0; i < haystack.length; i += 1) {
      if (haystack[i] === needle[q]) {
        const place = i === 0 ? AT_START : startsWord(name, i) ? AT_WORD : ANYWHERE
        // The first letter of the query starts the match rather than skipping to it.
        const skipping = bestBefore === null ? null : bestBefore + place + (q === 0 ? 0 : SKIPPED)
        const before = q === 0 || i === 0 ? null : previous[i - 1]
        const running = before === null ? null : before + Math.max(place, RUNS_ON)
        const best = running === null ? skipping : skipping === null ? running : Math.max(running, skipping)
        next[i] = best
        if (best !== null) matchedAny = true
      }
      const endingHere = previous[i]
      if (q > 0 && endingHere !== null && (bestBefore === null || endingHere > bestBefore)) bestBefore = endingHere
    }
    if (!matchedAny) return null
    previous = next
  }
  let best: number | null = null
  for (const ending of previous) if (ending !== null && (best === null || ending > best)) best = ending
  return best
}

/** The names that [query] fits, best first. Names that score the same keep the order they came in. */
export function rank<T>(query: string, items: readonly T[], name: (item: T) => string): T[] {
  const scored: { item: T; score: number; index: number }[] = []
  items.forEach((item, index) => {
    const fit = score(query, name(item))
    if (fit !== null) scored.push({ item, score: fit, index })
  })
  scored.sort((a, b) => b.score - a.score || a.index - b.index)
  return scored.map((each) => each.item)
}

// What the command palette is doing, with nothing of the screen in it (plan.md "커맨드 버튼과 팔레트").
//
// Three states. **A** is the button alone in the corner, out of the way. **B** is that button
// tapped: the input is open and empty, and the button has become the one that runs what is typed.
// **C** is something typed, so there are results and one of them is selected. Cancelling returns to
// A from either, and so does running something — but the mode stays, because the next thing looked
// for is usually the same kind of thing as the last.
//
// The modes are here so that they can survive that return. Which of them the palette is in is the
// input button's own: a swipe up or down it goes round [MODES], and the view turns that into the
// glyph the button wears.
import { rank } from './fuzzy'
import { readRecents, writeRecents } from './recents'

/** `>` commands, 🔍 files, `@` symbols. */
export type PaletteMode = 'command' | 'file' | 'symbol'

/** The order a swipe goes round, which is the order the plan lists them in. */
export const MODES: readonly PaletteMode[] = ['command', 'file', 'symbol']

/**
 * How many of the last things run are offered before anything is typed, per mode. `recents.ts`
 * keeps them between one page and the next.
 */
export const MOST_RECENT = 5

/** A: the button alone. B: open and waiting. C: typed in, so there are results. */
export type PaletteStage = 'button' | 'input' | 'results'

/** One thing the palette can run. Its name is what is searched and what is shown. */
export interface PaletteItem {
  name: string
  run(): void
}

export interface Palette {
  readonly stage: PaletteStage
  readonly mode: PaletteMode
  readonly query: string
  /** What the query matched, empty in A and B. */
  readonly results: PaletteItem[]
  /** Which result is on Enter, an index into [results]. */
  readonly selected: number
  /** A → B. */
  open(): void
  /** What is in the input now. Empty is B again, so backspacing all the way is not a dead end. */
  type(query: string): void
  /** Round [MODES] by [by] places, which a swipe up or down the button asks for. */
  cycleMode(by: number): void
  select(index: number): void
  /** Up or down the results, around the ends. */
  move(by: number): void
  /** Runs the selected result and returns to A. With nothing to run, nothing happens. */
  run(): void
  /** B or C → A, keeping the mode. */
  cancel(): void
}

export function createPalette(sources: { items(mode: PaletteMode): PaletteItem[] }): Palette {
  let stage: PaletteStage = 'button'
  let mode: PaletteMode = 'command'
  let query = ''
  let results: PaletteItem[] = []
  let selected = 0
  /** The names of the last things run, newest first, one list per mode, from the last page too. */
  const recent = readRecents()

  /**
   * What the open palette is showing. With nothing typed it is the last few things run in this
   * mode, which is what B is for — most of the time the next command is one of them. With something
   * typed it is what fits, best first.
   *
   * An item that matches nothing still leaves C: the query is what makes the state, not whether it
   * found anything, so an empty result list can say so instead of looking like a palette that did
   * not open.
   */
  function search(): void {
    const needle = query.trim()
    const offered = sources.items(mode)
    stage = query === '' ? 'input' : 'results'
    results = needle === ''
      // By name, because the items are made fresh each time and the one remembered may be gone.
      ? (recent.get(mode) ?? []).map((name) => offered.find((item) => item.name === name)).filter((item): item is PaletteItem => item !== undefined)
      : rank(needle, offered, (item) => item.name)
    selected = 0
  }

  /** Puts what was just run at the front of its mode's list, and lets go of the oldest. */
  function remember(item: PaletteItem): void {
    const names = (recent.get(mode) ?? []).filter((name) => name !== item.name)
    recent.set(mode, [item.name, ...names].slice(0, MOST_RECENT))
    writeRecents(recent)
  }

  function close(): void {
    stage = 'button'
    query = ''
    results = []
    selected = 0
  }

  return {
    get stage() { return stage },
    get mode() { return mode },
    get query() { return query },
    get results() { return results },
    get selected() { return selected },
    open() {
      if (stage !== 'button') return
      close()
      stage = 'input'
      search()
    },
    type(next) {
      if (stage === 'button') return
      query = next
      search()
    },
    cycleMode(by) {
      const place = MODES.indexOf(mode) + by
      mode = MODES[((place % MODES.length) + MODES.length) % MODES.length]
      if (stage !== 'button') search()
    },
    select(index) {
      if (index >= 0 && index < results.length) selected = index
    },
    move(by) {
      const length = results.length
      if (length === 0) return
      selected = ((selected + by) % length + length) % length
    },
    run() {
      const item = results[selected]
      if (!item) return
      remember(item)
      // Closed before it runs: a command that opens the palette itself would otherwise be undone
      // by the close that followed it.
      close()
      item.run()
    },
    cancel: close,
  }
}

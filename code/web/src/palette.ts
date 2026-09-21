// What the command palette is doing, with nothing of the screen in it (plan.md "커맨드 버튼과 팔레트").
//
// Three states. **A** is the button alone in the corner, out of the way. **B** is that button
// tapped: the input is open and empty, and the button has become the one that runs what is typed.
// **C** is something typed, so there are results and one of them is selected. Cancelling returns to
// A from either, and so does running something — but the mode stays, because the next thing looked
// for is usually the same kind of thing as the last.
//
// The modes are here so that they can survive that return. The swipe that changes them and the
// fuzzy score that orders the results are the next item; what this one settles is when results
// exist at all, and where the page goes afterwards.

/** `>` commands, 🔍 files, `@` symbols. */
export type PaletteMode = 'command' | 'file' | 'symbol'

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
  setMode(mode: PaletteMode): void
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

  /**
   * Plain substring, not a fuzzy score: ordering the results is the next item, and an item that
   * matches nothing still leaves C — the query is what makes the state, not whether it found
   * anything, so an empty result list can say so instead of looking like a palette that did not open.
   */
  function search(): void {
    const needle = query.trim().toLowerCase()
    stage = query === '' ? 'input' : 'results'
    results = needle === '' ? [] : sources.items(mode).filter((item) => item.name.toLowerCase().includes(needle))
    selected = 0
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
    },
    type(next) {
      if (stage === 'button') return
      query = next
      search()
    },
    setMode(next) {
      mode = next
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
      // Closed before it runs: a command that opens the palette itself would otherwise be undone
      // by the close that followed it.
      close()
      item.run()
    },
    cancel: close,
  }
}

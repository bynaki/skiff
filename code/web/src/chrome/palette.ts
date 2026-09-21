// The button in the bottom-right corner and what opens out of it (plan.md "커맨드 버튼과 팔레트").
// `palette.ts` holds which of A, B and C it is in; this draws that and hands the taps back.
//
// **Everything a person reads in here is English** (2026-09-21 사용자 결정), and it is written in
// this file rather than arriving from Kotlin's string resources the way the rest of the page's text
// does. It is the one surface that is not localised, so there is nothing for `values-ko` to hold.
import { type Palette, type PaletteItem, type PaletteMode, createPalette } from '../palette'

/** What the button shows in each mode, which is also what the mode is called in the plan. */
const GLYPH: Record<PaletteMode, string> = {
  command: '>',
  file: '🔍',
  symbol: '@',
}

const PLACEHOLDER: Record<PaletteMode, string> = {
  command: 'Run a command',
  file: 'Go to a file',
  symbol: 'Go to a symbol',
}

const NO_MATCHES = 'No matches'

/**
 * [items] is asked every time the query changes, for the mode the palette is in. The fuzzy score
 * that will order what comes back, and the rest of what can come back, are the next two items.
 */
export function createPaletteView(items: (mode: PaletteMode) => PaletteItem[]): void {
  const machine: Palette = createPalette({ items })

  // Covers the screen while the palette is open so that a tap anywhere else cancels it, which is
  // the way out the plan asks for ("빈 곳을 누르는 등으로 취소하면 A로 돌아간다"). It is not dimmed:
  // what is being searched for is usually something in the document behind it.
  const scrim = document.createElement('div')
  scrim.id = 'palette-scrim'

  const box = document.createElement('div')
  box.id = 'palette'
  const results = document.createElement('ul')
  results.className = 'results'
  const row = document.createElement('div')
  row.className = 'row'
  const input = document.createElement('input')
  input.type = 'text'
  input.autocomplete = 'off'
  input.spellcheck = false
  // What the palette is searched with is English, so the soft keyboard should open on English.
  // Nothing can make it: an app may only hint. Measured on the tablet (Samsung Keyboard, Korean and
  // English both added), `inputmode="email"` is the hint it takes — `lang="en"` never reaches the
  // IME at all (Chromium leaves `EditorInfo.hintLocales` null) and a URL field stays Korean. It is
  // not an email address, but the shape an email field asks for is the shape a command name has:
  // Latin letters, no autocorrect, no capital at the front, and `@` on the keyboard, which is the
  // symbol mode's own glyph. Another keyboard may ignore it; `EditorInfo.hintLocales` from Kotlin
  // is the way further (plan.md M4).
  input.inputMode = 'email'
  const button = document.createElement('button')
  button.type = 'button'
  button.className = 'mode'
  row.append(input, button)
  box.append(results, row)
  document.body.append(scrim, box)

  /**
   * Nothing in here takes the focus off the input on the way down, and nothing happens until the
   * tap lands. A tap that moved the focus first would drop the soft keyboard mid-gesture, and the
   * page, which is as tall as what the keyboard leaves, would grow under the finger and carry what
   * was being tapped out from under it. Acting on the way down instead is no better: the tap then
   * ends on whatever the screen puts there afterwards — the sidebar a command has just opened takes
   * that stray tap on its scrim and closes again, which is what it did before this.
   */
  const keepFocus = (event: Event) => event.preventDefault()

  function render(): void {
    const open = machine.stage !== 'button'
    box.classList.toggle('open', open)
    scrim.classList.toggle('open', open)
    button.textContent = GLYPH[machine.mode]
    button.title = open ? 'Run' : 'Commands'
    button.setAttribute('aria-label', button.title)
    input.placeholder = PLACEHOLDER[machine.mode]
    if (input.value !== machine.query) input.value = machine.query
    if (machine.stage !== 'results') {
      results.replaceChildren()
      results.hidden = true
      return
    }
    results.hidden = false
    if (machine.results.length === 0) {
      const none = document.createElement('li')
      none.className = 'none'
      none.textContent = NO_MATCHES
      results.replaceChildren(none)
      return
    }
    results.replaceChildren(...machine.results.map((item, index) => {
      const entry = document.createElement('li')
      entry.textContent = item.name
      if (index === machine.selected) entry.className = 'selected'
      entry.addEventListener('pointerdown', keepFocus)
      entry.addEventListener('click', () => {
        machine.select(index)
        run()
      })
      return entry
    }))
  }

  function open(): void {
    machine.open()
    render()
    input.focus()
  }

  function cancel(): void {
    machine.cancel()
    input.blur()
    render()
  }

  function run(): void {
    machine.run()
    if (machine.stage === 'button') input.blur()
    render()
  }

  button.addEventListener('pointerdown', keepFocus)
  button.addEventListener('click', () => (machine.stage === 'button' ? open() : run()))
  scrim.addEventListener('pointerdown', keepFocus)
  scrim.addEventListener('click', cancel)
  input.addEventListener('input', () => {
    machine.type(input.value)
    render()
  })
  input.addEventListener('keydown', (event) => {
    const step = event.key === 'ArrowDown' ? 1 : event.key === 'ArrowUp' ? -1 : 0
    if (step !== 0) machine.move(step)
    else if (event.key === 'Enter') return void run()
    else if (event.key === 'Escape') return cancel()
    else return
    event.preventDefault()
    render()
  })
  render()
}

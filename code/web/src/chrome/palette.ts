// The button in the bottom-right corner and what opens out of it (plan.md "커맨드 버튼과 팔레트").
// `palette.ts` holds which of A, B and C it is in; this draws that and hands the taps back.
//
// **Everything a person reads in here is English** (2026-09-21 사용자 결정), and it is written in
// this file rather than arriving from Kotlin's string resources the way the rest of the page's text
// does. It is the one surface that is not localised, so there is nothing for `values-ko` to hold.
import { type Palette, type PaletteItem, type PaletteMode, createPalette } from '../palette'
import { svg } from './topbar'

/**
 * What the button wears in each mode. The plan names the modes by `>`, 🔍 and `@`, and these draw
 * those three marks the way the menu's icons are drawn — one stroke, the colour of the text, no
 * fill — rather than setting them as text. As characters they do not belong together: 🔍 is an
 * emoji the system paints in its own colours and weight, and the other two are whatever weight the
 * font has at 22px, sitting where the font's baseline puts them rather than in the middle of the
 * button.
 */
const GLYPH: Record<PaletteMode, string> = {
  // A prompt: the mark itself, over the line a command is typed on.
  command: svg('<path d="M4 17l6-5-6-5"/><path d="M12 19h8"/>'),
  file: svg('<circle cx="11" cy="11" r="6.5"/><path d="M15.8 15.8L20 20"/>'),
  // The `@`, drawn: the inner circle and the stroke that curls around it and stops.
  symbol: svg('<circle cx="12" cy="12" r="3.6"/><path d="M15.6 8.4v5a2.9 2.9 0 0 0 5.8 0v-1.4a9.4 9.4 0 1 0-3.7 7.5"/>'),
}

const PLACEHOLDER: Record<PaletteMode, string> = {
  command: 'Run a command',
  file: 'Go to a file',
  symbol: 'Go to a symbol',
}

const NO_MATCHES = 'No matches'

/**
 * How far the finger travels up or down the button before it is a swipe rather than a tap. One
 * gesture turns the mode once however far it goes on: three modes are few enough that a flick
 * which lands on a different one each time would be a thing to aim, and this one cannot be missed.
 */
const SWIPE_STEP = 24

/**
 * [items] is asked every time the query changes, for the mode the palette is in, and `fuzzy.ts`
 * decides which of what comes back comes first. The file and symbol modes have nothing to offer
 * until the registry item builds them a source.
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

  /** Set by a swipe that has just ended, for the click it leaves behind to be let go of. */
  let swiped = false

  function render(): void {
    const open = machine.stage !== 'button'
    box.classList.toggle('open', open)
    scrim.classList.toggle('open', open)
    button.innerHTML = GLYPH[machine.mode]
    button.title = open ? 'Run' : 'Commands'
    button.setAttribute('aria-label', button.title)
    input.placeholder = PLACEHOLDER[machine.mode]
    if (input.value !== machine.query) input.value = machine.query
    // Before anything is typed the list is the last few things run, and there is nothing to show
    // until something has been. Only a query that found nothing says so.
    if (machine.results.length === 0) {
      const none = document.createElement('li')
      none.className = 'none'
      none.textContent = NO_MATCHES
      results.replaceChildren(...(machine.stage === 'results' ? [none] : []))
      results.hidden = machine.stage !== 'results'
      return
    }
    results.hidden = false
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

  // Where the finger came down on the button, while it is still down, and whether it has moved far
  // enough to have been a swipe rather than a tap. Only while the palette is open: the plan gives
  // the swipe to the input button, and in the corner the button has the one thing to do.
  let swipe: { from: number; turned: boolean } | null = null

  button.addEventListener('pointerdown', (event) => {
    keepFocus(event)
    if (machine.stage === 'button') return
    swipe = { from: event.clientY, turned: false }
    button.setPointerCapture(event.pointerId)
  })
  button.addEventListener('pointermove', (event) => {
    if (!swipe || swipe.turned) return
    const moved = event.clientY - swipe.from
    if (Math.abs(moved) < SWIPE_STEP) return
    // Up the screen is forward through the modes, the way a list moves under a finger.
    machine.cycleMode(moved < 0 ? 1 : -1)
    swipe.turned = true
    render()
  })
  const released = () => {
    // The tap that ends a swipe is not a tap: it must not run what the palette is showing.
    swiped = swipe?.turned ?? false
    swipe = null
  }
  button.addEventListener('pointerup', released)
  button.addEventListener('pointercancel', released)
  button.addEventListener('click', () => {
    if (swiped) return void (swiped = false)
    if (machine.stage === 'button') open()
    else run()
  })
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

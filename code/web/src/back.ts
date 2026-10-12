// What the system's back gesture does on the page. Kotlin takes it from the system only while there is
// something here for it to do, and says which edge it came from: the right edge (and the back button)
// goes back through the jump list, the left edge forward. The design is in
// `docs/skiffcode.spec/jumps.md`.

export type Direction = 'back' | 'forward'

/** Something that floats over the document and goes away on back, as Android does it. */
export interface Overlay {
  isOpen(): boolean
  hide(): void
}

/** What the page tells Kotlin, which registers its callback while any of it is true. */
export interface NavigationState {
  back: boolean
  forward: boolean
  overlay: boolean
}

/**
 * Closes whatever of [overlays] is out, whichever edge the gesture came from; only with nothing out
 * does it [move]. A way with nowhere to go is the jump list's to answer with nothing.
 */
export function backGesture(direction: Direction, overlays: Overlay[], move: (direction: Direction) => void): void {
  const out = overlays.filter((overlay) => overlay.isOpen())
  if (out.length > 0) return out.forEach((overlay) => overlay.hide())
  move(direction)
}

/**
 * Reads the state each time it is asked to and sends it when it has changed: Kotlin registers and
 * unregisters on what it hears, and the same answer twice would only cross the bridge for nothing.
 */
export function createNavigationReporter(read: () => NavigationState, send: (state: NavigationState) => void): () => void {
  let told: NavigationState | null = null
  return () => {
    const now = read()
    if (told && told.back === now.back && told.forward === now.forward && told.overlay === now.overlay) return
    told = { ...now }
    send(told)
  }
}

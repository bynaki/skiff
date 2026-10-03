// ④'s menu (docs/skiffcode.spec.md "화면 메뉴"): what the diff layer compares the file on the screen
// with — HEAD, or the file before the last commit that changed it. It hangs below ④ at the right and
// goes away when one is chosen, when ④ is tapped again, or when anything outside it is: a clear scrim
// takes that tap, so it does not land on the document as well.
import type { CompareTo } from '../layers/pane'

/** From Kotlin's string resources like every other text on the page. */
export interface MoreLabels {
  compareHead: string
  comparePrevious: string
}

export interface MoreMenu {
  label(labels: MoreLabels): void
  /** Opens it with [compare] marked as chosen, and nothing to choose unless [enabled]; or closes it. */
  toggle(compare: CompareTo, enabled: boolean): void
  hide(): void
}

/** [opened] hears it open and close, for ④ to say so. */
export function createMoreMenu(choose: (compare: CompareTo) => void, opened: (open: boolean) => void): MoreMenu {
  const scrim = document.createElement('div')
  scrim.id = 'more-scrim'
  scrim.hidden = true
  const menu = document.createElement('div')
  menu.id = 'more'
  menu.setAttribute('role', 'menu')
  menu.hidden = true

  const item = (compare: CompareTo) => {
    const element = document.createElement('button')
    element.type = 'button'
    element.setAttribute('role', 'menuitemradio')
    element.addEventListener('click', () => {
      hide()
      choose(compare)
    })
    return element
  }
  const items: Record<CompareTo, HTMLButtonElement> = { head: item('head'), previous: item('previous') }
  menu.append(items.head, items.previous)
  document.body.append(scrim, menu)

  function hide(): void {
    if (menu.hidden) return
    menu.hidden = true
    scrim.hidden = true
    opened(false)
  }
  scrim.addEventListener('click', hide)

  return {
    label(labels) {
      items.head.textContent = labels.compareHead
      items.previous.textContent = labels.comparePrevious
    },
    toggle(compare, enabled) {
      if (!menu.hidden) return hide()
      for (const [name, element] of Object.entries(items)) {
        element.setAttribute('aria-checked', String(name === compare))
        element.disabled = !enabled
      }
      menu.hidden = false
      scrim.hidden = false
      opened(true)
    },
    hide,
  }
}

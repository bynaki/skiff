// The menu along the top (plan.md "화면 메뉴"): ① sidebar on the left; ② original size, ③ layer and
// ④ more on the right. It floats over the layer, slides away while the layer scrolls down and comes
// back when it scrolls up. Only ② does anything yet; the others are placeholders for later steps.
import { lastZoomTime } from '../zoom'

/** Room the layers leave above their content so the menu does not cover the first line. */
export const TOPBAR_SPACE = 56

/** Accessible names, from Kotlin's string resources like every other text on the page. */
export interface TopbarLabels {
  sidebar: string
  resetZoom: string
  layer: string
  more: string
}

// Scroll that follows a zoom is the zoom holding its spot, not the user scrolling.
const ZOOM_SETTLE_MS = 250
// Ignores the jitter of a finger resting on the screen.
const MIN_SCROLL_DELTA = 4

const svg = (body: string) =>
  `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">${body}</svg>`

const ICONS = {
  sidebar: svg('<rect x="3" y="4" width="18" height="16" rx="2"/><path d="M9 4v16"/>'),
  // "1:1"
  resetZoom: svg('<path d="M5 8l2-2v12M17 8l2-2v12"/><circle cx="12" cy="10" r=".6"/><circle cx="12" cy="15" r=".6"/>'),
  viewer: svg('<path d="M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7S2 12 2 12z"/><circle cx="12" cy="12" r="3"/>'),
  more: svg('<circle cx="12" cy="5" r="1"/><circle cx="12" cy="12" r="1"/><circle cx="12" cy="19" r="1"/>'),
}

export interface Topbar {
  label(labels: TopbarLabels): void
  /** Brings the menu back, for a new document. */
  show(): void
}

export function createTopbar(actions: { resetZoom(): void }): Topbar {
  document.documentElement.style.setProperty('--topbar-space', `${TOPBAR_SPACE}px`)
  const bar = document.createElement('div')
  bar.id = 'topbar'

  const button = (icon: string, onClick?: () => void) => {
    const element = document.createElement('button')
    element.type = 'button'
    element.innerHTML = icon
    if (onClick) element.addEventListener('click', onClick)
    else element.disabled = true
    return element
  }
  const sidebar = button(ICONS.sidebar)
  const resetZoom = button(ICONS.resetZoom, actions.resetZoom)
  const layer = button(ICONS.viewer)
  const more = button(ICONS.more)
  const group = document.createElement('div')
  group.className = 'group'
  group.append(resetZoom, layer, more)
  bar.append(sidebar, group)
  document.body.append(bar)

  const setHidden = (hidden: boolean) => bar.classList.toggle('hidden', hidden)

  // Scroll events do not bubble, so one capturing listener hears every scroller: CodeMirror's and
  // the markdown one alike. A scroller seen for the first time only records where it is, so jumping
  // to a link's line does not count as scrolling down.
  const lastTop = new WeakMap<Element, number>()
  document.addEventListener('scroll', (event) => {
    if (!(event.target instanceof Element)) return
    const top = event.target.scrollTop
    const last = lastTop.get(event.target)
    lastTop.set(event.target, top)
    if (last === undefined || performance.now() - lastZoomTime() < ZOOM_SETTLE_MS) return
    if (top <= 0 || last - top > MIN_SCROLL_DELTA) setHidden(false)
    else if (top - last > MIN_SCROLL_DELTA) setHidden(true)
  }, { capture: true, passive: true })

  return {
    label(labels) {
      for (const [element, text] of [[sidebar, labels.sidebar], [resetZoom, labels.resetZoom], [layer, labels.layer], [more, labels.more]] as const) {
        element.title = text
        element.setAttribute('aria-label', text)
      }
    },
    show: () => setHidden(false),
  }
}

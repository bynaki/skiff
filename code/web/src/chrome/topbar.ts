// The menu along the top (plan.md "화면 메뉴"): ① sidebar on the left; ② original size, ③ layer and
// ④ more on the right. It floats over the layer, slides away while the layer scrolls down and comes
// back when it scrolls up. ④ is a placeholder for a later step.
import type { LayerName } from '../layers/pane'
import { lastZoomTime } from '../zoom'

/** Room the layers leave above their content so the menu does not cover the first line. */
export const TOPBAR_SPACE = 56

/** Accessible names, from Kotlin's string resources like every other text on the page. */
export interface TopbarLabels {
  sidebar: string
  resetZoom: string
  /** ③ names the layer it is showing, the way its icon does. */
  layerViewer: string
  layerEditor: string
  layerDiff: string
  more: string
}

const LAYER_LABEL: Record<LayerName, keyof TopbarLabels> = {
  viewer: 'layerViewer',
  editor: 'layerEditor',
  diff: 'layerDiff',
}

// Scroll that follows a zoom is the zoom holding its spot, not the user scrolling.
const ZOOM_SETTLE_MS = 250
// Ignores the jitter of a finger resting on the screen.
const MIN_SCROLL_DELTA = 4

const svg = (body: string) =>
  `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">${body}</svg>`

/** ①, which the drawer itself repeats in the same spot so that it is also the way back. */
export const SIDEBAR_ICON = svg('<rect x="3" y="4" width="18" height="16" rx="2"/><path d="M9 4v16"/>')

const ICONS = {
  sidebar: SIDEBAR_ICON,
  // "1:1"
  resetZoom: svg('<path d="M5 8l2-2v12M17 8l2-2v12"/><circle cx="12" cy="10" r=".6"/><circle cx="12" cy="15" r=".6"/>'),
  viewer: svg('<path d="M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7S2 12 2 12z"/><circle cx="12" cy="12" r="3"/>'),
  editor: svg('<path d="M4 20h4L19 9a2.8 2.8 0 0 0-4-4L4 16v4z"/><path d="M14 6l4 4"/>'),
  // The same "+" and "-" the diff layer puts in its sign gutter.
  diff: svg('<path d="M4 7h7M7.5 3.5v7M13 17h7"/>'),
  more: svg('<circle cx="12" cy="5" r="1"/><circle cx="12" cy="12" r="1"/><circle cx="12" cy="19" r="1"/>'),
}

export interface Topbar {
  label(labels: TopbarLabels): void
  /** Which layer ③ shows, or null when no document is open and it does nothing. */
  setLayer(name: LayerName | null): void
  /** Brings the menu back, for a new document. */
  show(): void
}

export function createTopbar(actions: { toggleSidebar(): void; resetZoom(): void; toggleLayer(): void }): Topbar {
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
  const sidebar = button(ICONS.sidebar, actions.toggleSidebar)
  const resetZoom = button(ICONS.resetZoom, actions.resetZoom)
  const layer = button(ICONS.viewer, actions.toggleLayer)
  const more = button(ICONS.more)
  layer.disabled = true
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

  // The labels arrive from Kotlin after the first document may already be showing, so ③'s name is
  // applied again whenever either half of it is known.
  let labels: TopbarLabels | null = null
  let shown: LayerName | null = null

  const name = (element: HTMLButtonElement, text: string) => {
    element.title = text
    element.setAttribute('aria-label', text)
  }

  const applyLayer = () => {
    layer.disabled = shown === null
    layer.innerHTML = ICONS[shown ?? 'viewer']
    if (labels && shown) name(layer, labels[LAYER_LABEL[shown]])
  }

  return {
    label(next) {
      labels = next
      for (const [element, text] of [[sidebar, next.sidebar], [resetZoom, next.resetZoom], [more, next.more]] as const) {
        name(element, text)
      }
      applyLayer()
    },
    setLayer(next) {
      shown = next
      applyLayer()
    },
    show: () => setHidden(false),
  }
}

// The menu along the top (docs/skiffcode.spec.md "화면 메뉴"): ① sidebar on the left, then the file on the
// screen with a dot while it has been typed in, which opens the list of open files when tapped;
// ② original size, ③ layer and ④ more on the right. It floats over the layer, slides away while the
// layer scrolls down and comes back when it scrolls up. ④ is a placeholder for a later step.
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
  /** The dot beside the file's name. The name itself is data and needs none. */
  unsaved: string
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

/** How every icon on the page is drawn: one stroke of the current colour, no fill. */
export const svg = (body: string) =>
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
  /**
   * The file on the screen, or null when nothing is open. [dirty] puts the dot beside it; a file
   * that could not be opened has no buffer and so is never dirty.
   */
  setFile(name: string | null, dirty: boolean): void
  /** Whether the open files menu is out. It hangs from the menu, so the menu comes back with it. */
  setFilesOpen(open: boolean): void
  /** Brings the menu back, for a new document. */
  show(): void
}

/** [layers] is where the documents scroll; only a scroll inside it moves the menu. */
export function createTopbar(
  actions: { toggleSidebar(): void; toggleFiles(): void; resetZoom(): void; toggleLayer(): void },
  layers: HTMLElement,
): Topbar {
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
  // Its name is what it says, the file and the dot, rather than a label of its own: the name is the
  // thing a person tapping it is looking for.
  const file = button('', actions.toggleFiles)
  file.className = 'file'
  file.disabled = true
  file.setAttribute('aria-haspopup', 'true')
  file.setAttribute('aria-expanded', 'false')
  const fileName = document.createElement('span')
  fileName.className = 'name'
  const dot = document.createElement('span')
  dot.className = 'dot'
  dot.setAttribute('role', 'img')
  dot.hidden = true
  file.append(fileName, dot)
  const group = document.createElement('div')
  group.className = 'group'
  group.append(resetZoom, layer, more)
  bar.append(sidebar, file, group)
  document.body.append(bar)

  const setHidden = (hidden: boolean) => bar.classList.toggle('hidden', hidden)

  // Scroll events do not bubble, so one capturing listener hears every scroller: CodeMirror's and
  // the markdown one alike. A scroller seen for the first time only records where it is, so jumping
  // to a link's line does not count as scrolling down. It hears the palette's list and the open files
  // menu's too, which are not the document and must not move the menu.
  let lastTop = new WeakMap<Element, number>()
  document.addEventListener('scroll', (event) => {
    if (!(event.target instanceof Element) || !layers.contains(event.target)) return
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
      dot.title = next.unsaved
      dot.setAttribute('aria-label', next.unsaved)
      applyLayer()
    },
    setFile(name, dirty) {
      fileName.textContent = name ?? ''
      dot.hidden = name === null || !dirty
      file.disabled = name === null
    },
    setFilesOpen(open) {
      file.setAttribute('aria-expanded', String(open))
      if (open) setHidden(false)
    },
    setLayer(next) {
      shown = next
      applyLayer()
      // A layer switch puts the surface it arrives at on the line the other one was showing. In a
      // markdown file that is the rendered document's own scroller, still holding where it was left,
      // so the jump would count as scrolling and hide the menu that was just tapped. Forgetting every
      // scroller makes the jump a first sighting, the way a link's line already is.
      lastTop = new WeakMap()
    },
    show: () => setHidden(false),
  }
}

// The page: shows whichever document Kotlin has open. Kotlin says `documentChanged` when that
// changes and the page asks for it, so a reloaded page and a new link take the same path.
import { onNotify, rpc } from './bridge'
import { type BannerLabels, createBanner } from './chrome/banner'
import { type TopbarLabels, createTopbar } from './chrome/topbar'
import { type Pane, openPane } from './layers/pane'
import { DEFAULT_FONT_SIZE, currentFontSize, setFontSize } from './zoom'

/** Every text in here that a person reads comes from Kotlin's string resources, already localised. */
type DocumentState =
  | { state: 'empty'; message: string }
  | { state: 'text'; name: string; text: string; line?: number }
  | { state: 'refused'; name: string; title: string; message: string }

/** What the file did while it was open, from Kotlin's `FileWatcher`. */
type FileChange =
  | { change: 'text'; text: string; message: string }
  | { change: 'notice'; message: string }

type Labels = TopbarLabels & BannerLabels

const root = document.getElementById('viewer')!
let pane: Pane | null = null

const banner = createBanner()

const topbar = createTopbar({
  // Keeps the line at the middle of the screen where it is.
  resetZoom() {
    const middle = root.getBoundingClientRect().top + root.clientHeight / 2
    if (pane) pane.hold(middle)(DEFAULT_FONT_SIZE, middle)
    else setFontSize(DEFAULT_FONT_SIZE)
  },
  toggleLayer() {
    pane?.toggle()
    topbar.setLayer(pane?.layer ?? null)
  },
})

function show(doc: DocumentState) {
  pane?.close()
  pane = null
  banner.hide()
  root.replaceChildren()
  topbar.show()
  document.title = doc.state === 'empty' ? 'Skiff Code' : doc.name
  switch (doc.state) {
    case 'text':
      pane = openPane(root, doc)
      break
    case 'refused':
      root.append(notice(doc.title, doc.message))
      break
    case 'empty':
      root.append(notice(null, doc.message))
      break
  }
  topbar.setLayer(pane?.layer ?? null)
}

function notice(title: string | null, message: string): HTMLElement {
  const box = document.createElement('div')
  box.className = 'notice'
  if (title) box.append(Object.assign(document.createElement('h1'), { textContent: title }))
  box.append(Object.assign(document.createElement('p'), { textContent: message }))
  return box
}

// Replies come back in the order they were asked, so the last one asked is the current document.
const refresh = () => rpc<DocumentState>('document').then(show).catch((error) => console.log(`document: ${error}`))

/**
 * The file changed under the document. A buffer nobody has typed in takes it silently — that is
 * what "실시간 반영" means — and one that has been typed in is asked, because only this side knows
 * which it is.
 */
onNotify<FileChange>('fileChanged', (change) => {
  if (change.change === 'notice' || !pane) return banner.tell(change.message)
  if (pane.dirty) return banner.ask(change.message, () => pane?.adopt(change.text))
  pane.adopt(change.text)
  banner.hide()
})

setFontSize(currentFontSize())
rpc<Labels>('labels').then((labels) => {
  topbar.label(labels)
  banner.label(labels)
}).catch((error) => console.log(`labels: ${error}`))
onNotify('documentChanged', refresh)
refresh()

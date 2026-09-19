// The page: shows whichever document Kotlin has open. Kotlin says `documentChanged` when that
// changes and the page asks for it, so a reloaded page and a new link take the same path.
import { onNotify, rpc } from './bridge'
import { showDocument } from './layers/viewer'
import { currentFontSize, setFontSize } from './zoom'

/** Every text in here that a person reads comes from Kotlin's string resources, already localised. */
type DocumentState =
  | { state: 'empty'; message: string }
  | { state: 'text'; name: string; text: string; line?: number }
  | { state: 'refused'; name: string; title: string; message: string }

const root = document.getElementById('viewer')!
let takeDown: (() => void) | null = null

function show(doc: DocumentState) {
  takeDown?.()
  takeDown = null
  root.replaceChildren()
  document.title = doc.state === 'empty' ? 'Skiff Code' : doc.name
  switch (doc.state) {
    case 'text':
      takeDown = showDocument(root, doc)
      break
    case 'refused':
      root.append(notice(doc.title, doc.message))
      break
    case 'empty':
      root.append(notice(null, doc.message))
      break
  }
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

setFontSize(currentFontSize())
onNotify('documentChanged', refresh)
refresh()

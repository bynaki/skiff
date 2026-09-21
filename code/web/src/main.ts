// The page: shows whichever of the open files Kotlin has made active. Kotlin says
// `documentsChanged` when the list or that choice moves — a link arrived, the sidebar switched
// files, one was closed — and the page asks what it is now, so a reloaded page, a new link and a
// tap in the sidebar all take the same path.
//
// One file is on the screen at a time and the rest are what they left behind (`PaneMemory`): their
// buffer, the layer they were on and the line they were showing. That is also what makes switching
// safe while a file is being typed in.
import { onNotify, rpc } from './bridge'
import { type BannerLabels, createBanner } from './chrome/banner'
import { createPaletteView } from './chrome/palette'
import { type OpenFile, type SidebarLabels, createSidebar } from './chrome/sidebar'
import { type TopbarLabels, createTopbar } from './chrome/topbar'
import { type Pane, type PaneMemory, adoptInto, isDirty, openPane } from './layers/pane'
import { forgetOldBuffers } from './memories'
import type { PaletteItem, PaletteMode } from './palette'
import { DEFAULT_FONT_SIZE, currentFontSize, setFontSize } from './zoom'

/** Every text in here that a person reads comes from Kotlin's string resources, already localised. */
type DocumentState =
  | { state: 'empty'; message: string }
  | { state: 'text'; name: string; text: string; line?: number }
  | { state: 'refused'; name: string; title: string; message: string }

/** What the file did while it was open, from Kotlin's `FileWatcher`, and which file it was. */
type FileChange =
  | { id: number; change: 'text'; text: string; message: string }
  | { id: number; change: 'notice'; message: string }

/** What is open and which one the screen is on. */
interface Documents {
  active: number | null
  files: OpenFile[]
}

type Labels = TopbarLabels & BannerLabels & SidebarLabels

const root = document.getElementById('viewer')!
let pane: Pane | null = null
let activeId: number | null = null
/**
 * What each open file that is not on the screen left behind, the one that left longest ago first:
 * a file comes out of here when it goes to the screen and back in at the end when it leaves, which
 * is the order [forgetOldBuffers] lets buffers go in.
 */
const memories = new Map<number, PaneMemory>()
/**
 * A change that reached a file while it was in the background and its buffer could not take
 * silently — it had been typed in, or there was nothing to merge. It is put to the user when that
 * file comes to the screen.
 */
const waiting = new Map<number, { message: string; text?: string }>()

const banner = createBanner()

const sidebar = createSidebar({
  activate: (id) => void rpc('activate', { id }).catch((error) => console.log(`activate: ${error}`)),
  close: (id) => void rpc('close', { id }).catch((error) => console.log(`close: ${error}`)),
  // Only this side knows: Kotlin is never told whether a buffer has been typed in.
  dirty: (id) => (id === activeId ? pane?.dirty ?? false : dirtyInBackground(id)),
})

// Keeps the line at the middle of the screen where it is.
function resetZoom(): void {
  const middle = root.getBoundingClientRect().top + root.clientHeight / 2
  if (pane) pane.hold(middle)(DEFAULT_FONT_SIZE, middle)
  else setFontSize(DEFAULT_FONT_SIZE)
}

function toggleLayer(): void {
  pane?.toggle()
  topbar.setLayer(pane?.layer ?? null)
}

const topbar = createTopbar({ toggleSidebar: () => sidebar.toggle(), resetZoom, toggleLayer })

/**
 * What the palette can run. These three are the menu's own, which is what makes the palette
 * something that runs rather than a shape on the screen; the registry that saves, opens the
 * settings and the rest of it is the next item, and so are the file and symbol modes.
 *
 * Their names are English, like everything else in the palette (2026-09-21 사용자 결정).
 */
function commands(mode: PaletteMode): PaletteItem[] {
  if (mode !== 'command') return []
  return [
    { name: 'Toggle Layer', run: toggleLayer },
    { name: 'Reset Zoom', run: resetZoom },
    { name: 'Toggle Sidebar', run: () => sidebar.toggle() },
  ]
}

createPaletteView(commands)

function dirtyInBackground(id: number): boolean {
  const memory = memories.get(id)
  return memory?.state ? isDirty(memory.state) : false
}

/**
 * Brings the page to what Kotlin says is open. Everything that changes the list goes through here,
 * whoever started it, so there is one way the page arrives at a state. [goToLine] comes with a link
 * that named a file which was already open, since that file keeps the buffer it has rather than
 * being read again.
 */
async function reconcile(goToLine?: number | null): Promise<void> {
  const { active, files } = await rpc<Documents>('documents')
  const open = (id: number) => files.some((file) => file.id === id)
  for (const id of [...memories.keys()]) if (!open(id)) memories.delete(id)
  for (const id of [...waiting.keys()]) if (!open(id)) waiting.delete(id)
  if (active !== activeId) {
    const leaving = pane?.close()
    // Not for a file that has just been closed: what it left is not coming back.
    if (leaving && activeId !== null && open(activeId)) memories.set(activeId, leaving)
    pane = null
    activeId = active
    // The file arriving is the pane's from here, not the map's. Taking it out is also what puts it
    // at the end of the order when it leaves again, and it has to happen before the buffers are
    // counted: as the file that left the screen longest ago, it would otherwise be the first one
    // to lose the buffer it is about to be built from.
    const arriving = active === null ? undefined : memories.get(active)
    if (active !== null) memories.delete(active)
    forgetOldBuffers(memories)
    await show(files, arriving)
  }
  sidebar.show(files, active)
  if (goToLine) pane?.goToLine(goToLine)
}

/** Puts the active file on the screen, from what it left behind if it has been there before. */
async function show(files: OpenFile[], memory?: PaneMemory): Promise<void> {
  const id = activeId
  const file = id === null ? undefined : files.find((each) => each.id === id)
  // A file that has been on the screen is rebuilt from its own buffer, which may be ahead of the
  // text Kotlin holds. Anything else — the first look at a file, a refusal, nothing open — is asked.
  const doc: DocumentState = memory && file
    ? { state: 'text', name: file.name, text: memory.source }
    : await rpc<DocumentState>('document', id === null ? {} : { id })
  banner.hide()
  root.replaceChildren()
  topbar.show()
  document.title = doc.state === 'empty' ? 'Skiff Code' : doc.name
  switch (doc.state) {
    case 'text':
      pane = openPane(root, doc, memory)
      break
    case 'refused':
      root.append(notice(doc.title, doc.message))
      break
    case 'empty':
      root.append(notice(null, doc.message))
      break
  }
  topbar.setLayer(pane?.layer ?? null)
  if (id !== null) askWhatWaited(id)
}

/** What happened to this file while it was in the background, now that it can be answered. */
function askWhatWaited(id: number): void {
  const held = waiting.get(id)
  if (!held) return
  waiting.delete(id)
  if (held.text === undefined || !pane) banner.tell(held.message)
  else banner.ask(held.message, () => pane?.adopt(held.text as string))
}

function notice(title: string | null, message: string): HTMLElement {
  const box = document.createElement('div')
  box.className = 'notice'
  if (title) box.append(Object.assign(document.createElement('h1'), { textContent: title }))
  box.append(Object.assign(document.createElement('p'), { textContent: message }))
  return box
}

// One at a time, in the order they were asked for: two of these overlapping would each close a pane
// the other is still counting on.
let queue: Promise<void> = Promise.resolve()
const refresh = (goToLine?: number | null) => {
  queue = queue.then(() => reconcile(goToLine)).catch((error) => console.log(`documents: ${error}`))
}

/**
 * The file changed under a document. A buffer nobody has typed in takes it silently — that is what
 * "실시간 반영" means — and one that has been typed in is asked, because only this side knows which
 * it is. A file that is not on the screen is merged where it lies, or waits to be asked.
 */
onNotify<FileChange>('fileChanged', (change) => {
  if (change.id === activeId) {
    if (change.change === 'notice' || !pane) return banner.tell(change.message)
    if (pane.dirty) return banner.ask(change.message, () => pane?.adopt(change.text))
    pane.adopt(change.text)
    banner.hide()
    return
  }
  const memory = memories.get(change.id)
  // Open but never shown: Kotlin keeps its text and hands over the current one when it is.
  if (!memory) return
  if (change.change === 'notice') return void waiting.set(change.id, { message: change.message })
  if (memory.state && isDirty(memory.state)) {
    return void waiting.set(change.id, { message: change.message, text: change.text })
  }
  if (memory.state) memory.state = adoptInto(memory.state, change.text)
  memory.source = change.text
  waiting.delete(change.id)
})

setFontSize(currentFontSize())
rpc<Labels>('labels').then((labels) => {
  topbar.label(labels)
  banner.label(labels)
  sidebar.label(labels)
}).catch((error) => console.log(`labels: ${error}`))
onNotify<{ goToLine?: number | null }>('documentsChanged', (params) => refresh(params?.goToLine))
refresh()

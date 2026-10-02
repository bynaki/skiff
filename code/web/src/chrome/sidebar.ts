// The drawer behind ① (docs/skiffcode.spec.md "화면 메뉴"): it slides in from the left, and slides back
// when ① or anything outside it is tapped.
//
// It carries ① again at the top, in the place the menu's own sits, so the button that opened the
// drawer is under the finger that opens it — the drawer covers the menu while it is out.
//
// Under it is every project, each a line of its own with its files folded beneath (tree.ts). Each
// opening asks Kotlin again what the projects are and where the file on the screen is, unfolds down to
// that file, and lists the unfolded directories again. Tapping a file opens it and puts the drawer
// away; holding a project down asks to remove it (2026-10-02 사용자 결정). The files that are open
// moved out of here to the name in the menu (2026-09-26 사용자 결정).
import { SIDEBAR_ICON } from './topbar'
import { type Here, type Listing, type ProjectInfo, type Row, createTree } from '../tree'

/** Accessible names and the tree's own words, from Kotlin's string resources like every other text on the page. */
export interface SidebarLabels {
  /** The same name ① carries in the menu, since it is the same button. */
  sidebar: string
  noProjects: string
  listing: string
  emptyDirectory: string
}

/** What the drawer asks of Kotlin. A place is a project and a path relative to its root. */
export interface SidebarSource {
  projects(): Promise<{ projects: ProjectInfo[]; active: Here | null }>
  list(project: string, path: string): Promise<Listing>
  open(project: string, path: string): void
  remove(project: string): void
}

export interface Sidebar {
  label(labels: SidebarLabels): void
  toggle(): void
  hide(): void
  /**
   * What it shows may have moved — a project was made or removed, or another file came to the
   * screen: the drawer, if it is out, asks again and follows the file there.
   */
  changed(): void
}

/** How long a project is held down before it asks to be removed, and how far the finger may wander. */
const HOLD_MS = 500
const HOLD_SLOP = 10

export function createSidebar(source: SidebarSource): Sidebar {
  const scrim = document.createElement('div')
  scrim.id = 'scrim'
  const panel = document.createElement('aside')
  panel.id = 'sidebar'
  const header = document.createElement('header')
  const handle = document.createElement('button')
  handle.type = 'button'
  handle.innerHTML = SIDEBAR_ICON
  header.append(handle)
  const empty = document.createElement('p')
  empty.className = 'empty'
  empty.hidden = true
  const list = document.createElement('ul')
  list.className = 'tree'
  panel.append(header, empty, list)
  document.body.append(scrim, panel)

  let labels: SidebarLabels | null = null
  /**
   * Which row each line is, by the element rather than by its place in the list: a listing that comes
   * in under the finger redraws the list, and a place counted from before that is another row now.
   */
  const rowsOf = new WeakMap<Element, Row>()
  /** Set as the drawer opens, so the file on the screen is scrolled to once it has been listed. */
  let reveal = false
  const tree = createTree(source.list, render)

  function isOpen(): boolean {
    return panel.classList.contains('open')
  }

  function hide() {
    scrim.classList.remove('open')
    panel.classList.remove('open')
  }

  function load(): void {
    source.projects().then(({ projects, active }) => {
      tree.set(projects, active)
      tree.refresh()
      empty.hidden = projects.length > 0
      render()
    }).catch((error) => console.log(`projects: ${error}`))
  }

  function render(): void {
    list.replaceChildren(...tree.rows().map(item))
    if (!reveal) return
    const current = list.querySelector('.current')
    if (!current) return
    reveal = false
    current.scrollIntoView({ block: 'nearest' })
  }

  function item(row: Row): HTMLLIElement {
    const li = document.createElement('li')
    const depth = row.kind === 'project' ? 0 : row.depth
    if (row.kind === 'listing' || row.kind === 'empty' || row.kind === 'failed') {
      li.className = row.kind === 'failed' ? 'note failed' : 'note'
      li.style.setProperty('--depth', String(depth))
      li.textContent = row.kind === 'failed' ? row.message : row.kind === 'empty' ? labels?.emptyDirectory ?? '' : labels?.listing ?? ''
      return li
    }
    const button = document.createElement('button')
    button.type = 'button'
    rowsOf.set(button, row)
    button.style.setProperty('--depth', String(depth))
    const chevron = document.createElement('span')
    chevron.className = 'chevron'
    if (row.kind !== 'file') chevron.textContent = row.open ? '▾' : '▸'
    const text = document.createElement('span')
    text.className = 'text'
    const name = Object.assign(document.createElement('span'), { className: 'name' })
    text.append(name)
    if (row.kind === 'project') {
      li.className = 'project'
      name.textContent = row.project.name
      // A left-to-right mark, so the path reads forwards in a box that clips from the left.
      text.append(Object.assign(document.createElement('span'), { className: 'where', textContent: `‎${row.project.where}` }))
    } else {
      name.textContent = row.name
      if (row.kind === 'file' && row.current) li.classList.add('current')
    }
    button.append(chevron, text)
    li.append(button)
    return li
  }

  // Held down on a project: asks to remove it, and the click the finger's lifting sends is not a fold.
  let holding: { timer: number; x: number; y: number } | null = null
  let held = false

  function letGo(): void {
    if (holding) clearTimeout(holding.timer)
    holding = null
  }

  list.addEventListener('pointerdown', (event) => {
    const row = rowOf(event.target)
    held = false
    if (row?.kind !== 'project') return
    const project = row.project.id
    holding = {
      x: event.clientX,
      y: event.clientY,
      timer: window.setTimeout(() => {
        holding = null
        held = true
        source.remove(project)
      }, HOLD_MS),
    }
  })
  list.addEventListener('pointermove', (event) => {
    if (holding && Math.hypot(event.clientX - holding.x, event.clientY - holding.y) > HOLD_SLOP) letGo()
  })
  // A scroll of the list cancels the pointer, which is a finger that was not holding still.
  for (const type of ['pointerup', 'pointercancel', 'pointerleave']) list.addEventListener(type, letGo)
  // Otherwise holding a line down selects its text, or brings up the WebView's own menu.
  panel.addEventListener('contextmenu', (event) => event.preventDefault())

  list.addEventListener('click', (event) => {
    if (held) {
      held = false
      return
    }
    const row = rowOf(event.target)
    if (!row) return
    switch (row.kind) {
      case 'project':
        tree.toggle(row.project.id, '')
        return render()
      case 'directory':
        tree.toggle(row.project, row.path)
        return render()
      case 'file':
        source.open(row.project, row.path)
        return hide()
    }
  })

  function rowOf(target: EventTarget | null): Row | undefined {
    const button = (target as Element | null)?.closest?.('#sidebar .tree button')
    return button ? rowsOf.get(button) : undefined
  }

  scrim.addEventListener('click', hide)
  handle.addEventListener('click', hide)

  return {
    label(next) {
      labels = next
      handle.title = next.sidebar
      handle.setAttribute('aria-label', next.sidebar)
      empty.textContent = next.noProjects
      render()
    },
    toggle() {
      if (isOpen()) return hide()
      scrim.classList.add('open')
      panel.classList.add('open')
      reveal = true
      load()
    },
    hide,
    changed() {
      if (!isOpen()) return
      reveal = true
      load()
    },
  }
}

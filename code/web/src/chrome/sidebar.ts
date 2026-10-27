// The drawer behind ① (docs/skiffcode.spec/top-menu.md): it slides in from the left, and slides back
// when ① or anything outside it is tapped.
//
// It carries ① again at the top, in the place the menu's own sits, so the button that opened the
// drawer is under the finger that opens it — the drawer covers the menu while it is out.
//
// Under it is every project, each a line of its own with its files folded beneath (tree.ts). Each
// opening asks Kotlin again what the projects are and where the file on the screen is, unfolds down to
// that file, and lists the unfolded directories again, coloring what git says changed (2026-10-26
// 사용자 결정). Tapping a file opens it and puts the drawer away; holding a project down asks to remove
// it (2026-10-02 사용자 결정). The files that are open moved out of here to the name in the menu
// (2026-09-26 사용자 결정).
//
// Beside ① are two tabs, the tree and the outline of the file on the screen (docs/skiffcode.spec/
// outline.md, 2026-10-26 사용자 결정). It opens on the one it was left on, unless that is the outline
// and the file has none. Tapping a line of the outline goes there and puts the drawer away.
import { SIDEBAR_ICON, svg } from './topbar'
import type { LayerName } from '../layers/pane'
import type { SymbolKind } from '../symbols'
import {
  type OutlineFile,
  type OutlineSource,
  type SidebarTab,
  createOutline,
  currentItem,
  outlineEnabled,
  rememberTab,
  rememberedTab,
  sourceFor,
  tabToShow,
} from '../outline'
import { type Change, type Here, type Listing, type ProjectInfo, type Row, createTree } from '../tree'

/** Accessible names and the tree's own words, from Kotlin's string resources like every other text on the page. */
export interface SidebarLabels {
  /** The same name ① carries in the menu, since it is the same button. */
  sidebar: string
  noProjects: string
  listing: string
  emptyDirectory: string
  treeTab: string
  outlineTab: string
  /** An outline source's lines for an outline with nothing in it. */
  noHeadings: string
  noSymbols: string
}

/** What the drawer asks of Kotlin. A place is a project and a path relative to its root. */
export interface SidebarSource {
  projects(): Promise<{ projects: ProjectInfo[]; active: Here | null }>
  list(project: string, path: string): Promise<Listing>
  /** What git says changed in the project, or null where it cannot be asked. */
  changes(project: string): Promise<Change[] | null>
  open(project: string, path: string): void
  remove(project: string): void
  /** The file on the screen as the outline asks for it, and the layer it is in; null without one. */
  showing(): { file: OutlineFile; layer: LayerName } | null
  /** The source line at the top of the screen, which the outline marks the place of. */
  topLine(): number
  /** Goes to [line] in the file on the screen, as a move the jump list remembers. */
  goTo(line: number): void
}

export interface Sidebar {
  label(labels: SidebarLabels): void
  toggle(): void
  hide(): void
  /** Whether it is out, which the back gesture closes first (`back.ts`). */
  isOpen(): boolean
  /**
   * What it shows may have moved — a project was made or removed, or another file came to the
   * screen: the drawer, if it is out, asks again and follows the file there.
   */
  changed(): void
}

/** How long a project is held down before it asks to be removed, and how far the finger may wander. */
const HOLD_MS = 500
const HOLD_SLOP = 10

/** A folder, for the tree; lines indented under each other, for the outline of whatever kind of file. */
const TREE_ICON = svg('<path d="M3 7a2 2 0 0 1 2-2h4l2 2h8a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/>')
const OUTLINE_ICON = svg('<path d="M4 6h16M8 12h12M12 18h8"/>')

/**
 * What kind of symbol a line of a source file's outline is (2026-10-27 사용자 결정): a box for a
 * class, an f for a function, brackets for a method, a ring for the rest. A heading has none.
 */
const KIND_ICONS: Partial<Record<SymbolKind, string>> = {
  class: svg('<path d="M12 3l8 4.5v9L12 21l-8-4.5v-9zM4 7.5l8 4.5 8-4.5M12 12v9"/>'),
  function: svg('<path d="M16 4h-2a3 3 0 0 0-3 3v13M7 10h8"/>'),
  method: svg('<path d="M9 4c-3.5 4-3.5 12 0 16M15 4c3.5 4 3.5 12 0 16"/>'),
  other: svg('<circle cx="12" cy="12" r="4"/>'),
}

export function createSidebar(source: SidebarSource): Sidebar {
  const scrim = document.createElement('div')
  scrim.id = 'scrim'
  const panel = document.createElement('aside')
  panel.id = 'sidebar'
  const header = document.createElement('header')
  const handle = document.createElement('button')
  handle.type = 'button'
  handle.innerHTML = SIDEBAR_ICON
  const tabs = document.createElement('div')
  tabs.className = 'tabs'
  tabs.setAttribute('role', 'tablist')
  const tabButton = (icon: string): HTMLButtonElement => {
    const button = Object.assign(document.createElement('button'), { type: 'button', innerHTML: icon })
    button.setAttribute('role', 'tab')
    tabs.append(button)
    return button
  }
  const tabsOf: Record<SidebarTab, HTMLButtonElement> = { tree: tabButton(TREE_ICON), outline: tabButton(OUTLINE_ICON) }
  header.append(handle, tabs)
  const empty = document.createElement('p')
  empty.className = 'empty'
  empty.hidden = true
  const list = document.createElement('ul')
  list.className = 'tree'
  const outlineList = document.createElement('ul')
  outlineList.className = 'outline'
  panel.append(header, empty, list, outlineList)
  document.body.append(scrim, panel)

  let labels: SidebarLabels | null = null
  /**
   * Which row each line is, by the element rather than by its place in the list: a listing that comes
   * in under the finger redraws the list, and a place counted from before that is another row now.
   */
  const rowsOf = new WeakMap<Element, Row>()
  /** Set as the drawer opens, so the file on the screen is scrolled to once it has been listed. */
  let reveal = false
  const tree = createTree(source.list, render, source.changes)
  /** The tab on show, picked again each time the drawer opens or the file under it changes. */
  let tab: SidebarTab = 'tree'
  /** What gives the outline on show, which also has the words for one with nothing in it. */
  let outlineSource: OutlineSource | null = null
  /** Set with [reveal], for the outline: the line the screen is in is scrolled to once it is drawn. */
  let revealOutline = false
  const outline = createOutline(renderOutline)

  function isOpen(): boolean {
    return panel.classList.contains('open')
  }

  function hide() {
    scrim.classList.remove('open')
    panel.classList.remove('open')
  }

  /** Picks the tab for what is on the screen now, and asks for what it shows. */
  function load(): void {
    const showing = source.showing()
    const here = showing && { name: showing.file.name, layer: showing.layer }
    tab = tabToShow(rememberedTab(), here)
    tabsOf.outline.disabled = !outlineEnabled(here)
    for (const [name, button] of Object.entries(tabsOf)) {
      const on = name === tab
      button.classList.toggle('on', on)
      button.setAttribute('aria-selected', String(on))
    }
    list.hidden = tab !== 'tree'
    outlineList.hidden = tab !== 'outline'
    if (tab === 'tree') {
      outline.clear()
      outlineSource = null
      return loadTree()
    }
    empty.hidden = true
    outlineSource = sourceFor(showing!.file.name)
    revealOutline = true
    outline.load(outlineSource!, showing!.file)
    renderOutline()
  }

  function loadTree(): void {
    source.projects().then(({ projects, active }) => {
      tree.set(projects, active)
      tree.refresh()
      // An answer that lands after the outline tab was picked leaves the tree under it alone.
      empty.hidden = tab !== 'tree' || projects.length > 0
      render()
    }).catch((error) => console.log(`projects: ${error}`))
  }

  /** The outline's lines, indented by depth, the one the top of the screen is in marked. Nothing until it is in. */
  function renderOutline(): void {
    const items = outline.items()
    if (!items) return outlineList.replaceChildren()
    if (items.length === 0) {
      const note = Object.assign(document.createElement('li'), { className: 'note' })
      note.textContent = outlineSource && labels ? labels[outlineSource.empty] : ''
      return outlineList.replaceChildren(note)
    }
    const current = currentItem(items, source.topLine())
    outlineList.replaceChildren(...items.map((entry, index) => {
      const li = document.createElement('li')
      if (index === current) li.className = 'current'
      const button = Object.assign(document.createElement('button'), { type: 'button' })
      button.dataset.line = String(entry.line)
      button.style.setProperty('--depth', String(entry.depth))
      const icon = KIND_ICONS[entry.kind]
      if (icon) button.append(Object.assign(document.createElement('span'), { className: 'kind', innerHTML: icon }))
      button.append(Object.assign(document.createElement('span'), { className: 'name', textContent: entry.name }))
      li.append(button)
      return li
    }))
    if (!revealOutline) return
    revealOutline = false
    outlineList.querySelector('.current')?.scrollIntoView({ block: 'nearest' })
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
      // A changed file, or a folder with one in it, takes the color of its name (top-menu.md).
      if (row.change) li.classList.add(row.change)
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

  outlineList.addEventListener('click', (event) => {
    const button = (event.target as Element | null)?.closest?.('#sidebar .outline button') as HTMLElement | null
    const line = Number(button?.dataset.line)
    if (!line) return
    source.goTo(line)
    hide()
  })

  for (const [name, button] of Object.entries(tabsOf) as [SidebarTab, HTMLButtonElement][]) {
    button.addEventListener('click', () => {
      if (name === tab || button.disabled) return
      rememberTab(name)
      reveal = true
      load()
    })
  }

  scrim.addEventListener('click', hide)
  handle.addEventListener('click', hide)

  return {
    isOpen,
    label(next) {
      labels = next
      handle.title = next.sidebar
      handle.setAttribute('aria-label', next.sidebar)
      for (const [button, text] of [[tabsOf.tree, next.treeTab], [tabsOf.outline, next.outlineTab]] as const) {
        button.title = text
        button.setAttribute('aria-label', text)
      }
      empty.textContent = next.noProjects
      render()
      renderOutline()
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

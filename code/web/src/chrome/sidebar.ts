// The drawer behind ① (plan.md "화면 메뉴"): it slides in from the left with the files that are
// open, and slides back when ① or anything outside it is tapped.
//
// It carries ① again at the top, in the place the menu's own sits, so the button that opened the
// drawer is under the finger that opens it — the drawer covers the menu while it is out.
//
// For now it holds the open files alone. Project list and the project's file tree are M5, and this
// is the panel they will arrive in.
import { SIDEBAR_ICON } from './topbar'

/** One open file, as Kotlin's `documents` lists it. */
export interface OpenFile {
  id: number
  name: string
  /** Where the file is — a path, or a server and a path. The end of it is what tells two apart. */
  where: string
}

/** Accessible names, from Kotlin's string resources like every other text on the page. */
export interface SidebarLabels {
  /** The same name ① carries in the menu, since it is the same button. */
  sidebar: string
  noFiles: string
  close: string
  closeDirty: string
  cancel: string
}

export interface Sidebar {
  label(labels: SidebarLabels): void
  toggle(): void
  hide(): void
  /** Draws the list again: after a file is opened, switched to or closed. */
  show(files: OpenFile[], active: number | null): void
}

/**
 * A strong left-to-right mark. The path is written in a right-to-left box so that it is the
 * *end* — the directory the file is in — that survives on a narrow screen, and this keeps the path
 * itself reading forwards inside it.
 */
const LRM = '‎'

export function createSidebar(actions: {
  activate(id: number): void
  close(id: number): void
  /** Whether that file has been typed in, which is what makes closing it ask first. */
  dirty(id: number): boolean
}): Sidebar {
  const scrim = document.createElement('div')
  scrim.id = 'scrim'
  const panel = document.createElement('aside')
  panel.id = 'sidebar'
  const header = document.createElement('header')
  const handle = document.createElement('button')
  handle.type = 'button'
  handle.innerHTML = SIDEBAR_ICON
  header.append(handle)
  const list = document.createElement('ul')
  panel.append(header, list)
  document.body.append(scrim, panel)

  let labels: SidebarLabels | null = null
  let files: OpenFile[] = []
  let active: number | null = null
  // The file whose close button has been tapped and is waiting for an answer, if any.
  let confirming: number | null = null

  const button = (text: string, className: string, onClick: () => void) => {
    const element = document.createElement('button')
    element.type = 'button'
    element.className = className
    element.textContent = text
    element.addEventListener('click', onClick)
    return element
  }

  function entry(file: OpenFile): HTMLLIElement {
    const row = document.createElement('li')
    if (file.id === active) row.className = 'current'
    if (file.id === confirming && labels) {
      row.classList.add('confirming')
      const question = document.createElement('p')
      question.textContent = labels.closeDirty
      const answers = document.createElement('div')
      answers.className = 'answers'
      answers.append(
        button(labels.cancel, 'cancel', () => {
          confirming = null
          render()
        }),
        button(labels.close, 'confirm', () => {
          confirming = null
          actions.close(file.id)
        }),
      )
      row.append(question, answers)
      return row
    }
    const open = document.createElement('button')
    open.type = 'button'
    open.className = 'entry'
    const name = document.createElement('span')
    name.className = 'name'
    name.textContent = file.name
    const where = document.createElement('span')
    where.className = 'where'
    where.textContent = LRM + file.where
    open.append(name, where)
    open.title = file.where
    open.addEventListener('click', () => {
      hide()
      actions.activate(file.id)
    })
    row.append(open)
    if (labels) {
      // A buffer that has been typed in cannot be saved yet (the command palette is M4), so
      // closing it is asked about where it is rather than carried out.
      const close = button('×', 'close', () => {
        if (actions.dirty(file.id)) {
          confirming = file.id
          render()
          return
        }
        actions.close(file.id)
      })
      close.title = labels.close
      close.setAttribute('aria-label', labels.close)
      row.append(close)
    }
    return row
  }

  function render() {
    if (!labels) return
    handle.title = labels.sidebar
    handle.setAttribute('aria-label', labels.sidebar)
    if (files.length === 0) {
      const empty = document.createElement('li')
      empty.className = 'empty'
      empty.textContent = labels.noFiles
      list.replaceChildren(empty)
      return
    }
    list.replaceChildren(...files.map(entry))
  }

  function hide() {
    confirming = null
    scrim.classList.remove('open')
    panel.classList.remove('open')
    render()
  }

  scrim.addEventListener('click', hide)
  handle.addEventListener('click', hide)

  return {
    label(next) {
      labels = next
      render()
    },
    toggle() {
      const opening = !panel.classList.contains('open')
      if (!opening) return hide()
      confirming = null
      render()
      scrim.classList.add('open')
      panel.classList.add('open')
    },
    hide,
    show(next, current) {
      files = next
      active = current
      if (confirming !== null && !files.some((file) => file.id === confirming)) confirming = null
      render()
    },
  }
}

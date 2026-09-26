// The drawer behind ① (docs/skiffcode.spec.md "화면 메뉴"): it slides in from the left, and slides back
// when ① or anything outside it is tapped.
//
// It carries ① again at the top, in the place the menu's own sits, so the button that opened the
// drawer is under the finger that opens it — the drawer covers the menu while it is out.
//
// It is where the project's files will be (M5: the project list and the project's file tree). The
// files that are open moved out of it to the name in the menu (2026-09-26 사용자 결정), so until
// then it says only that there is no project.
import { SIDEBAR_ICON } from './topbar'

/** Accessible names, from Kotlin's string resources like every other text on the page. */
export interface SidebarLabels {
  /** The same name ① carries in the menu, since it is the same button. */
  sidebar: string
  noProjects: string
}

export interface Sidebar {
  label(labels: SidebarLabels): void
  toggle(): void
  hide(): void
}

export function createSidebar(): Sidebar {
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
  panel.append(header, empty)
  document.body.append(scrim, panel)

  function hide() {
    scrim.classList.remove('open')
    panel.classList.remove('open')
  }

  scrim.addEventListener('click', hide)
  handle.addEventListener('click', hide)

  return {
    label(labels) {
      handle.title = labels.sidebar
      handle.setAttribute('aria-label', labels.sidebar)
      empty.textContent = labels.noProjects
    },
    toggle() {
      if (panel.classList.contains('open')) return hide()
      scrim.classList.add('open')
      panel.classList.add('open')
    },
    hide,
  }
}

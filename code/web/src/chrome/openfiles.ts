// The files that are open, behind the file's name in the menu (docs/skiffcode.spec.md "화면 메뉴"). It
// drops down from behind the menu, five rows tall at most, and goes back up when the name is tapped
// again, when anything outside it is, or when the bar along its bottom is tapped or drawn upwards.
//
// This list used to be the sidebar's (2026-09-26 사용자 결정): the sidebar is for the project's files
// now, and the name at the top is the thing a person looks at when they want another file.
import { type Sample, TAP_SLOP, recentSpeed, releaseCloses } from '../dropdown'

/** One open file, as Kotlin's `documents` lists it. */
export interface OpenFile {
  id: number
  name: string
  /** Where the file is — a path, or a server and a path. The end of it is what tells two apart. */
  where: string
}

/** Accessible names, from Kotlin's string resources like every other text on the page. */
export interface OpenFilesLabels {
  noFiles: string
  /** Both the × on a row and the bar that puts the menu away. */
  close: string
  closeDirty: string
  cancel: string
}

export interface OpenFiles {
  label(labels: OpenFilesLabels): void
  toggle(): void
  hide(): void
  /**
   * Opens the menu with that file's row asking whether to close it, which is where the palette's
   * Close File sends a buffer that has been typed in: the question belongs with the file it is
   * about, and the row is where it is already asked.
   */
  askClose(id: number): void
  /** Draws the list again: after a file is opened, switched to or closed. */
  show(files: OpenFile[], active: number | null): void
}

/** How many rows show before the list scrolls. */
const VISIBLE_ROWS = 5

/**
 * A strong left-to-right mark. The path is written in a right-to-left box so that it is the
 * *end* — the directory the file is in — that survives on a narrow screen, and this keeps the path
 * itself reading forwards inside it.
 */
const LRM = '‎'

export function createOpenFiles(actions: {
  activate(id: number): void
  close(id: number): void
  /** Whether that file has been typed in, which is what makes closing it ask first. */
  dirty(id: number): boolean
  /** The menu came out or went away, for the name that opens it. */
  opened(open: boolean): void
}): OpenFiles {
  const scrim = document.createElement('div')
  scrim.id = 'open-files-scrim'
  const panel = document.createElement('div')
  panel.id = 'open-files'
  const list = document.createElement('ul')
  const handle = document.createElement('div')
  handle.className = 'handle'
  handle.setAttribute('role', 'button')
  panel.append(list, handle)
  document.body.append(scrim, panel)

  let labels: OpenFilesLabels | null = null
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
      // What has been typed is only in the buffer until Save File writes it, so closing it is
      // asked about where the file is rather than carried out.
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
    handle.title = labels.close
    handle.setAttribute('aria-label', labels.close)
    if (files.length === 0) {
      const empty = document.createElement('li')
      empty.className = 'empty'
      empty.textContent = labels.noFiles
      list.replaceChildren(empty)
    } else {
      list.replaceChildren(...files.map(entry))
    }
    // Five rows, measured rather than assumed: the WebView scales the text by the system's font
    // size, and a row's height with it. A row asking whether to close is taller, and counts as one.
    const fifth = list.children[VISIBLE_ROWS - 1] as HTMLElement | undefined
    list.style.maxHeight = fifth ? `${fifth.offsetTop + fifth.offsetHeight}px` : ''
  }

  const isOpen = () => panel.classList.contains('open')

  function hide() {
    if (!isOpen()) return
    confirming = null
    scrim.classList.remove('open')
    panel.classList.remove('open')
    document.documentElement.classList.remove('files-open')
    actions.opened(false)
    render()
  }

  function open() {
    scrim.classList.add('open')
    panel.classList.add('open')
    // What floats over the document stops handing a drag on to it while the menu is out (index.html).
    document.documentElement.classList.add('files-open')
    // Measured once it is laid out as it will be seen.
    render()
    actions.opened(true)
  }

  scrim.addEventListener('click', hide)

  // The bar takes the menu with the finger while it is drawn upwards, and on letting go either puts
  // it away or lets it fall back. The menu does not follow a finger drawn downwards: it is already
  // as far down as it goes.
  let drag: { from: number; samples: Sample[] } | null = null
  // Set by a drag that has just ended, for the click it may leave behind to be let go of. Cleared
  // when the next finger comes down: the WebView sends no click after a drag at all, and the flag
  // would otherwise eat the next tap.
  let dragged = false

  const follow = (travel: number) => {
    panel.style.transform = travel > 0 ? `translateY(${-travel}px)` : ''
  }

  handle.addEventListener('pointerdown', (event) => {
    event.preventDefault()
    dragged = false
    drag = { from: event.clientY, samples: [{ at: event.timeStamp, travel: 0 }] }
    handle.setPointerCapture(event.pointerId)
    panel.classList.add('dragging')
  })
  handle.addEventListener('pointermove', (event) => {
    if (!drag) return
    const travel = Math.max(0, drag.from - event.clientY)
    drag.samples.push({ at: event.timeStamp, travel })
    follow(travel)
  })
  const released = (cancelled: boolean) => {
    if (!drag) return
    const { samples } = drag
    drag = null
    const travel = samples[samples.length - 1].travel
    dragged = travel >= TAP_SLOP
    // Measured while the finger still holds it, and decided before anything changes: the menu then
    // slides the rest of the way, out or back, from where the finger left it rather than jumping.
    const closes = !cancelled && releaseCloses(travel, recentSpeed(samples), panel.offsetHeight)
    panel.classList.remove('dragging')
    follow(0)
    if (closes) hide()
  }
  handle.addEventListener('pointerup', () => released(false))
  handle.addEventListener('pointercancel', () => released(true))
  handle.addEventListener('click', () => {
    if (dragged) return void (dragged = false)
    hide()
  })

  return {
    label(next) {
      labels = next
      render()
    },
    toggle() {
      if (isOpen()) return hide()
      confirming = null
      open()
    },
    hide,
    askClose(id) {
      confirming = id
      open()
    },
    show(next, current) {
      files = next
      active = current
      if (confirming !== null && !files.some((file) => file.id === confirming)) confirming = null
      render()
    },
  }
}

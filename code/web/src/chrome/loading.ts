// What a wait looks like: a line along the very top, and the shape of a document under it while
// there is nothing on the screen yet.
//
// At the top edge rather than under the menu, which slides away with the scroll and would carry
// the line off with it — the same reason the banner sits along the bottom.

/** The document shape: how wide each of its lines is, as a percentage, so it reads as code. */
const ROWS = [62, 41, 78, 30, 55, 71, 44, 66, 35]

export interface LoadingView {
  /** The line along the top, for a wait with a document already on the screen or without one. */
  line(on: boolean): void
  /** The shape underneath it, which is only ever for a screen with nothing on it. */
  skeleton(on: boolean): void
}

export function createLoadingView(): LoadingView {
  const line = document.createElement('div')
  line.id = 'loading'
  line.hidden = true

  const shape = document.createElement('div')
  shape.id = 'skeleton'
  shape.hidden = true
  for (const width of ROWS) {
    const row = document.createElement('div')
    row.className = 'row'
    row.style.width = `${width}%`
    shape.append(row)
  }

  document.body.append(line, shape)

  return {
    line(on) {
      line.hidden = !on
    },
    skeleton(on) {
      shape.hidden = !on
    },
  }
}

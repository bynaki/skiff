// A small square of the color beside each hex color in a theme, a stylesheet or JSON
// (2026-09-29 사용자 요청): `"#0b3d5c"` reads as the color it is without leaving the file. Only
// for those files, because elsewhere `#add` or `#cafe` in a comment is a word, not a color — and
// only to look at: the square is not a picker.
import { type Extension } from '@codemirror/state'
import { Decoration, type DecorationSet, EditorView, MatchDecorator, ViewPlugin, type ViewUpdate, WidgetType } from '@codemirror/view'

/** The files the squares are shown in, by extension. */
const SHOWN_IN = /\.(toml|css|json)$/i

/**
 * `#rgb`, `#rgba`, `#rrggbb` and `#rrggbbaa` — what a theme file accepts — standing alone: not the
 * tail of a longer run of letters and digits, and not after one, as in `a#fff` or `#fffff`.
 */
const COLOR = /(?<![\w#])#(?:[0-9a-fA-F]{8}|[0-9a-fA-F]{6}|[0-9a-fA-F]{3,4})(?![\w-])/g

export function showsSwatches(name: string): boolean {
  return SHOWN_IN.test(name)
}

/** Every color in [text] a square would stand beside, where it starts and what it says. */
export function colorsIn(text: string): { from: number; color: string }[] {
  return [...text.matchAll(COLOR)].map((match) => ({ from: match.index, color: match[0] }))
}

class Swatch extends WidgetType {
  constructor(readonly color: string) {
    super()
  }

  eq(other: Swatch): boolean {
    return other.color === this.color
  }

  toDOM(): HTMLElement {
    const square = document.createElement('span')
    square.className = 'cm-swatch'
    square.style.backgroundColor = this.color
    square.setAttribute('aria-hidden', 'true')
    return square
  }
}

const swatches = new MatchDecorator({
  regexp: COLOR,
  decorate: (add, from, _to, match) => add(from, from, Decoration.widget({ widget: new Swatch(match[0]), side: -1 })),
})

const plugin = ViewPlugin.fromClass(
  class {
    decorations: DecorationSet
    constructor(view: EditorView) {
      this.decorations = swatches.createDeco(view)
    }
    update(update: ViewUpdate) {
      this.decorations = swatches.updateDeco(update, this.decorations)
    }
  },
  { decorations: (value) => value.decorations },
)

const style = EditorView.theme({
  '.cm-swatch': {
    display: 'inline-block',
    width: '0.8em',
    height: '0.8em',
    marginRight: '0.3em',
    verticalAlign: '-0.05em',
    borderRadius: '2px',
    // Against the editor's own background a square of that color would vanish without it.
    boxShadow: '0 0 0 1px var(--editor-gutter-border)',
  },
})

/** The squares for a file called [name], or nothing when it is not one of the files they are for. */
export function swatchesFor(name: string): Extension {
  return showsSwatches(name) ? [plugin, style] : []
}

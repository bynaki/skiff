// A pane is one file, full screen, with the layers stacked on it (plan.md "레이어").
//
// The layers are not separate editors. One `EditorView` and one `EditorState` hold the file, and a
// layer is the bundle of extensions a `Compartment` carries: CodeMirror keeps the value of every
// state field that stays in the configuration and only creates what the layer being entered adds
// (`StateField.slot(...).reconfigure`), so the document, its parse tree, the height map and the
// undo history are shared and nothing of a layer outlives it. That is also why the diff layer can
// join later without a second view: `unifiedMergeView` is a plain extension array that compares
// against `state.doc`, which is the buffer being edited.
import { Compartment, EditorState, type Extension } from '@codemirror/state'
import { EditorView, keymap, lineNumbers } from '@codemirror/view'
import { defaultKeymap, history, historyKeymap } from '@codemirror/commands'
import { LanguageDescription, defaultHighlightStyle, syntaxHighlighting } from '@codemirror/language'
import { languages } from '@codemirror/language-data'
import { type MarkdownSurface, showMarkdown } from '../markdown'
import { TOPBAR_SPACE } from '../chrome/topbar'
import { hardwareKeyboard } from '../keyboard'
import { type Hold, anchorAt, applyFontSize, codeFontSize, holdLine, installPinchZoom } from '../zoom'

/** ③ cycles through these. `diff` waits for M5 and is not in the cycle yet. */
export type LayerName = 'viewer' | 'editor' | 'diff'

export interface TextDocument {
  name: string
  text: string
  /** 1-based, from a link's `?line=`. */
  line?: number
}

const BUNDLES: Record<LayerName, Extension> = {
  viewer: [EditorState.readOnly.of(true), EditorView.editable.of(false)],
  // Typing arrives through the DOM on its own; the keymap is what a hardware keyboard needs.
  editor: keymap.of([...defaultKeymap, ...historyKeymap]),
  diff: EditorState.readOnly.of(true),
}

export interface Pane {
  readonly layer: LayerName
  /** To the next layer. */
  toggle(): void
  /** What ② original size zooms around, on whichever layer is showing. */
  hold: Hold
  close(): void
}

/** Shows [doc] in [parent], in the viewer layer. */
export function openPane(parent: HTMLElement, doc: TextDocument): Pane {
  // By name, not content: language-data knows extensions and names such as Makefile.
  const language = LanguageDescription.matchFilename(languages, doc.name)
  const markdown = language?.name === 'Markdown' ? showMarkdown(parent, doc.text) : null
  const layerBundle = new Compartment()
  let layer: LayerName = 'viewer'
  let view: EditorView | null = null

  function createView(): EditorView {
    const syntax = new Compartment()
    const created = new EditorView({
      parent,
      state: EditorState.create({
        doc: doc.text,
        extensions: [
          lineNumbers(),
          // Outside the compartment, so undo still reaches an edit made before a trip to the viewer.
          history(),
          syntax.of([]),
          syntaxHighlighting(defaultHighlightStyle),
          layerBundle.of(BUNDLES[layer]),
          codeFontSize(),
          EditorView.theme({
            '&': { height: '100%' },
            '.cm-scroller': { fontFamily: 'monospace', lineHeight: '1.5', touchAction: 'pan-x pan-y' },
            // Gutters follow the content's padding, so the line numbers move down with it.
            '.cm-content': { paddingTop: 'var(--topbar-space)' },
          }),
        ],
      }),
    })
    // Each language's parser is its own chunk, fetched the first time a file needs it.
    language?.load().then((support) => {
      if (!created.dom.isConnected) return
      created.dispatch({ effects: syntax.reconfigure(support) })
    }).catch((error) => console.log(`language ${language.name} did not load: ${error}`))
    return created
  }

  /** The source line at the top of the screen, whichever surface is showing. */
  function topLine(): number {
    if (markdown && layer === 'viewer') return markdown.topLine()
    const top = view!.scrollDOM.getBoundingClientRect().top + TOPBAR_SPACE
    return view!.state.doc.lineAt(anchorAt(view!, top).pos).number
  }

  function lineStart(target: EditorView, line: number): number {
    return target.state.doc.line(Math.min(Math.max(1, line), target.state.doc.lines)).from
  }

  function scrollViewToLine(target: EditorView, line: number): void {
    target.dispatch({ effects: EditorView.scrollIntoView(lineStart(target, line), { y: 'start', yMargin: TOPBAR_SPACE }) })
  }

  /**
   * Markdown only: the rendered document and the editor are two DOMs, so one gives way to the other.
   * `important` because CodeMirror's base theme sets `display: flex !important` on its own root, and
   * a plain inline `display: none` leaves the editor standing below the rendered document.
   */
  function swapSurface(line: number): void {
    if (layer === 'viewer') {
      view!.dom.style.setProperty('display', 'none', 'important')
      markdown!.update(view!.state.doc.toString())
      markdown!.visible(true)
      markdown!.scrollToLine(line)
      return
    }
    markdown!.visible(false)
    if (view) {
      view.dom.style.removeProperty('display')
      // CodeMirror measures nothing while it is display:none, and the markdown surface may have
      // been zoomed meanwhile; the size it carries settles both on the way back.
      applyFontSize(view)
    } else {
      view = createView()
    }
    scrollViewToLine(view, line)
  }

  function setLayer(next: LayerName): void {
    if (next === layer) return
    const line = topLine()
    const showing = view
    layer = next
    if (markdown) swapSurface(line)
    // A view made just now was built with the new bundle already. An existing one swaps it, which
    // changes no line heights between viewer and editor, so its scroll stays where it was.
    if (showing) showing.dispatch({ effects: layerBundle.reconfigure(BUNDLES[next]) })
    // Only with a hardware keyboard, where focus is what lets typing start. Without one it would
    // raise the soft keyboard over a document the user has not asked to type in; the first tap on
    // the text focuses it and puts the caret where it landed. The caret goes to the line at the top
    // of the screen first: focusing on its own would leave it at the start of the document and take
    // the screen there with the first keystroke.
    if (next === 'editor' && view && hardwareKeyboard()) {
      view.dispatch({ selection: { anchor: lineStart(view, line) } })
      view.focus()
    }
  }

  if (markdown) {
    if (doc.line) markdown.scrollToLine(doc.line)
  } else {
    view = createView()
    if (doc.line) scrollViewToLine(view, doc.line)
  }

  /**
   * The soft keyboard shrinks the WebView — `MainActivity` adds the ime inset to the frame — which
   * leaves the caret under the keyboard without the selection having moved. CodeMirror brings the
   * caret back into view when the selection changes, not when the viewport does, so the resize has
   * to ask for it. Measured on the tablet: tapping near the bottom of a 1601 line file put the caret
   * at y 590 in a viewport that had just become 337 tall. `nearest` scrolls only when the caret is
   * outside, so a keyboard closing or a rotation that makes room costs nothing.
   */
  function keepCaretVisible(): void {
    if (!view || document.activeElement !== view.contentDOM) return
    view.dispatch({
      effects: EditorView.scrollIntoView(view.state.selection.main.head, { y: 'nearest', yMargin: TOPBAR_SPACE }),
    })
  }
  window.addEventListener('resize', keepCaretVisible)

  // One for the pane, not one per surface: it asks `hold` which layer is showing when a pinch starts.
  const hold: Hold = (clientY) => (markdown && layer === 'viewer' ? markdown.hold : holdLine(view!))(clientY)
  const uninstallPinchZoom = installPinchZoom(hold)

  return {
    hold,
    get layer() {
      return layer
    },
    toggle: () => setLayer(layer === 'viewer' ? 'editor' : 'viewer'),
    close() {
      window.removeEventListener('resize', keepCaretVisible)
      uninstallPinchZoom()
      view?.destroy()
      markdown?.remove()
    },
  }
}

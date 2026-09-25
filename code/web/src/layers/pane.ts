// A pane is one file, full screen, with the layers stacked on it (docs/skiffcode.spec.md "레이어").
//
// The layers are not separate editors. One `EditorView` and one `EditorState` hold the file, and a
// layer is the bundle of extensions a `Compartment` carries: CodeMirror keeps the value of every
// state field that stays in the configuration and only creates what the layer being entered adds
// (`StateField.slot(...).reconfigure`), so the document, its parse tree, the height map and the
// undo history are shared and nothing of a layer outlives it. That is also why the diff layer can
// join later without a second view: `unifiedMergeView` is a plain extension array that compares
// against `state.doc`, which is the buffer being edited.
import {
  Annotation,
  Compartment,
  EditorState,
  type Extension,
  StateField,
  type TransactionSpec,
} from '@codemirror/state'
import { diff } from '@codemirror/merge'
import { EditorView, keymap, lineNumbers } from '@codemirror/view'
import { defaultKeymap, history, historyKeymap, redo, undo } from '@codemirror/commands'
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

/** Marks the transaction that takes a change made outside, so it does not count as the user typing. */
const External = Annotation.define<boolean>()

/**
 * Whether the buffer has been typed in since it was read or last took the file's text.
 *
 * It rides in the state rather than in this module, so that it survives a file going to the
 * background — where its state is all that is left of it — and comes back with it. A change made
 * outside is what clears it: that transaction is the file and the buffer becoming the same thing
 * again.
 *
 * Exported so a buffer can be built without a view, which is how the tests make one.
 */
export const dirtyFlag = StateField.define<boolean>({
  create: () => false,
  update: (value, tr) => (tr.annotation(External) ? false : value || tr.docChanged),
})

export function isDirty(state: EditorState): boolean {
  return state.field(dirtyFlag)
}

/**
 * The transaction that takes [text] into a buffer, replacing only the ranges that differ so that
 * the cursor, the selection and the scroll come through its mapping. Handing CodeMirror a whole
 * new document would move every one of those to the end of it.
 */
function externalChanges(state: EditorState, text: string): TransactionSpec {
  const current = state.doc.toString()
  const changes = current === text
    ? []
    : diff(current, text, { timeout: DIFF_TIMEOUT }).map((change) => ({
      from: change.fromA,
      to: change.toA,
      insert: text.slice(change.fromB, change.toB),
    }))
  // Even with nothing to change: the annotation is what says this buffer and the file agree again.
  return { changes, annotations: External.of(true) }
}

/** The same, for a file in the background, whose state is all there is of it while it is not showing. */
export function adoptInto(state: EditorState, text: string): EditorState {
  return state.update(externalChanges(state, text)).state
}

/**
 * The compartments are one per module, not one per pane: a file that comes back to the screen is a
 * new pane around the state it left behind, and a compartment reconfigures only the states built
 * with that same instance. The value inside one still belongs to each state, so every file keeps
 * its own language and its own layer.
 */
const layerBundle = new Compartment()
const syntax = new Compartment()

/**
 * How long the diff may spend being precise before it falls back to the coarser algorithm. The
 * result is a correct merge either way; past this it is a bigger one.
 */
const DIFF_TIMEOUT = 250

const BUNDLES: Record<LayerName, Extension> = {
  viewer: [EditorState.readOnly.of(true), EditorView.editable.of(false)],
  // Typing arrives through the DOM on its own; the keymap is what a hardware keyboard needs.
  editor: keymap.of([...defaultKeymap, ...historyKeymap]),
  diff: EditorState.readOnly.of(true),
}

/** What a file leaves behind when it goes to the background, and comes back to the screen with. */
export interface PaneMemory {
  /** The buffer with its undo history, its selection and whether it has been typed in. */
  state: EditorState | null
  /** The text while no state holds it: a markdown file that has not been to the editor yet. */
  source: string
  layer: LayerName
  /** The source line that was at the top of the screen. */
  line: number
}

export interface Pane {
  readonly layer: LayerName
  /** Whether the buffer has been typed in since it was loaded or last took the file's text. */
  readonly dirty: boolean
  /** What is in the buffer now, which is what a save writes. */
  readonly text: string
  /** Puts the file's new text into the buffer, keeping the cursor, the selection and the scroll. */
  adopt(text: string): void
  /** The buffer has just been written to the file, so the two are the same thing again. */
  saved(): void
  /** To the next layer. */
  toggle(): void
  /** To a named layer, which is what the palette's Show Viewer and Show Editor run. */
  show(layer: LayerName): void
  /** Undo and redo for a finger: without a keyboard there is nothing else that reaches them. */
  undo(): void
  redo(): void
  /** Puts [line] at the top of the screen, for a link that asked for one. */
  goToLine(line: number): void
  /** What ② original size zooms around, on whichever layer is showing. */
  hold: Hold
  /** Takes the pane off the screen and hands back what the file needs to come back to it. */
  close(): PaneMemory
}

/** The name a .jsonl file is matched under, so language-data's JSON entry answers for it. */
export const jsonLinesAsJson = (name: string): string =>
  name.endsWith('.jsonl') ? name.slice(0, -1) : name

/**
 * Shows [doc] in [parent]. With [memory] it is a file coming back to the screen: the buffer it
 * left, the layer it was on and the line it was showing, rather than the file as it was read.
 * [onDirtyChange] hears [Pane.dirty] turn over — typing, a save and a change taken from outside
 * all arrive as transactions — and only when it does.
 */
export function openPane(
  parent: HTMLElement,
  doc: TextDocument,
  memory?: PaneMemory,
  onDirtyChange?: (dirty: boolean) => void,
): Pane {
  // By name, not content: language-data knows extensions and names such as Makefile.
  // Its JSON entry claims .json and .map but not .jsonl, and every line of a .jsonl file is JSON,
  // so that name is matched as if it were one. The parser recovers at each line break, which
  // leaves the whole file highlighted rather than only its first record.
  const language = LanguageDescription.matchFilename(languages, jsonLinesAsJson(doc.name))
  // The document's text while no view holds it, which is the markdown viewer before its first trip
  // to the editor. Once there is a view, the view is the buffer.
  let source = memory?.source ?? doc.text
  const markdown = language?.name === 'Markdown' ? showMarkdown(parent, source) : null
  let layer: LayerName = memory?.layer ?? 'viewer'
  let view: EditorView | null = null
  // Used once, by the first view this pane builds: after that the view holds it.
  let restore = memory?.state ?? null

  function createView(): EditorView {
    const state = restore ?? EditorState.create({
      doc: source,
      extensions: [
        lineNumbers(),
        // Outside the compartment, so undo still reaches an edit made before a trip to the viewer.
        history(),
        dirtyFlag,
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
    })
    const restored = restore !== null
    restore = null
    // On the view, not an updateListener in the state: a state that comes back from the background
    // brings its extensions with it, and a listener there would still be the pane it left.
    const created = new EditorView({
      parent,
      state,
      dispatchTransactions(transactions, target) {
        const before = isDirty(target.state)
        target.update(transactions)
        if (isDirty(target.state) !== before) onDirtyChange?.(!before)
      },
    })
    // A state built before the last pinch carries that pinch's size; the font is one for the whole
    // page, so the view is brought to it rather than the other way round.
    if (restored) applyFontSize(created)
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

  /**
   * Takes the file's new text into the buffer as one transaction, so the cursor, the selection and
   * the scroll come through it. Only the ranges that differ are replaced: handing CodeMirror a
   * whole new document would move every one of those to the end of it.
   */
  function adopt(text: string): void {
    source = text
    if (view) view.dispatch(externalChanges(view.state, text))
    // The rendered markdown is its own DOM and does not follow the buffer, so it is re-rendered
    // and put back on the line it was showing.
    if (markdown && layer === 'viewer') {
      const line = markdown.topLine()
      markdown.update(text)
      markdown.scrollToLine(line)
    }
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

  function goToLine(line: number): void {
    if (markdown && layer === 'viewer') markdown.scrollToLine(line)
    else if (view) scrollViewToLine(view, line)
  }

  // A file coming back shows the layer and the line it left; one being opened shows the viewer, at
  // the line a link asked for.
  const startLine = memory?.line ?? doc.line
  if (markdown && layer === 'viewer') {
    if (startLine) markdown.scrollToLine(startLine)
  } else {
    markdown?.visible(false)
    view = createView()
    if (startLine) scrollViewToLine(view, startLine)
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
    adopt,
    get layer() {
      return layer
    },
    get dirty() {
      return view !== null && isDirty(view.state)
    },
    get text() {
      return view ? view.state.doc.toString() : source
    },
    // The same annotation a change from outside carries, and for the same reason: the buffer and
    // the file agree again, so nothing here has been typed since.
    saved: () => view?.dispatch({ annotations: External.of(true) }),
    toggle: () => setLayer(layer === 'viewer' ? 'editor' : 'viewer'),
    show: setLayer,
    undo: () => void (view && undo(view)),
    redo: () => void (view && redo(view)),
    goToLine,
    close() {
      window.removeEventListener('resize', keepCaretVisible)
      uninstallPinchZoom()
      // Read while both surfaces are still on screen, since this is where the file comes back to.
      const memory: PaneMemory = { state: view?.state ?? null, source, layer, line: topLine() }
      if (memory.state) memory.source = memory.state.doc.toString()
      view?.destroy()
      markdown?.remove()
      return memory
    },
  }
}

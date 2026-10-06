// A pane is one file, full screen, with the layers stacked on it (docs/skiffcode.spec.md "레이어").
//
// The layers are not separate editors. One `EditorView` and one `EditorState` hold the file, and a
// layer is the bundle of extensions a `Compartment` carries: CodeMirror keeps the value of every
// state field that stays in the configuration and only creates what the layer being entered adds
// (`StateField.slot(...).reconfigure`), so the document, its parse tree, the height map and the
// undo history are shared and nothing of a layer outlives it. That is also why the diff layer needs
// no second view: `unifiedMergeView` is a plain extension array that compares against `state.doc`,
// which is the buffer being edited.
import {
  Annotation,
  Compartment,
  EditorState,
  type Extension,
  StateField,
  type TransactionSpec,
} from '@codemirror/state'
import { diff } from '@codemirror/merge'
import { BlockType, EditorView, keymap, lineNumbers } from '@codemirror/view'
import { defaultKeymap, history, historyKeymap, redo, undo } from '@codemirror/commands'
import { LanguageDescription } from '@codemirror/language'
import { languages } from '@codemirror/language-data'
import { type MarkdownSurface, showMarkdown } from '../markdown'
import { TOPBAR_SPACE } from '../chrome/topbar'
import { hardwareKeyboard } from '../keyboard'
import { type Hold, anchorAt, applyFontSize, codeFontSize, holdLine, installPinchZoom } from '../zoom'
import { applyEditorSettings, editorSettings } from '../settings'
import { editorTheme } from '../theme'
import { swatchesFor } from '../swatches'
import { gitGutter, setBaseline } from '../gitGutter'
import { unifiedDiff } from '../diff'
import { type LspTarget, goToDefinition, lspAvailable, lspPlugin, lspSupport, showHover, wakeLsp, watchLsp } from '../lsp'

/** ③ cycles through these, passing over `diff` while the file has nothing to be compared with. */
export type LayerName = 'viewer' | 'editor' | 'diff'

/**
 * What the diff layer compares the buffer with, chosen in ④ for each file while it is open
 * (2026-10-03 사용자 결정): HEAD, or the file as it was before the last commit that changed it.
 */
export type CompareTo = 'head' | 'previous'

export interface TextDocument {
  name: string
  text: string
  /** 1-based, from a link's `?line=`. */
  line?: number
  /** 1-based, in UTF-16 code units as LSP counts them, from a link's `?col=` or a definition's place. */
  col?: number
  /** From a link's `?layer=`. */
  layer?: LayerName
  /** The project's language server for this file, when it has one. */
  lsp?: LspTarget
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
/** The running language server's plugin, swapped whenever its server is started or let go of (`lsp.ts`). */
const lspSlot = new Compartment()

/**
 * How long the diff may spend being precise before it falls back to the coarser algorithm. The
 * result is a correct merge either way; past this it is a bigger one.
 */
const DIFF_TIMEOUT = 250

const reading = [EditorState.readOnly.of(true), EditorView.editable.of(false)]

/** The diff layer's is only where it starts: what it compares with is added per file (`bundleFor`). */
const BUNDLES: Record<LayerName, Extension> = {
  viewer: reading,
  // Typing arrives through the DOM on its own; the keymap is what a hardware keyboard needs.
  editor: keymap.of([...defaultKeymap, ...historyKeymap]),
  diff: reading,
}

/** Where ③ goes from [layer]: the viewer, the editor, then the diff layer when there is something to compare with. */
export function nextLayer(layer: LayerName, comparable: boolean): LayerName {
  if (layer === 'viewer') return 'editor'
  return layer === 'editor' && comparable ? 'diff' : 'viewer'
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
  /** What ④ chose for the diff layer. The text it names is asked for again when the file is back. */
  compare: CompareTo
  /** The file's language server, which Kotlin named only when the file was first read. */
  lsp?: LspTarget
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
  /**
   * To a named layer, which is what the palette's Show Viewer and Show Editor run, and a link's
   * `?layer=`. The diff layer only while there is something to compare with; asked for before that
   * is known, it is entered when the text arrives, unless another layer has been chosen meanwhile.
   */
  show(layer: LayerName): void
  /** Undo and redo for a finger: without a keyboard there is nothing else that reaches them. */
  undo(): void
  redo(): void
  /**
   * Puts [line] at the top of the screen and the cursor on it, at [col] or its start (2026-10-05
   * 사용자 결정), for a link, a definition or a symbol.
   */
  goToLine(line: number, col?: number): void
  /** Whether the file has a language server that may be asked about it, and a code view to ask from. */
  readonly lsp: boolean
  /** Asks the language server where what is at the cursor is defined, and goes there. */
  goToDefinition(): void
  /** Shows what the language server says about what is at the cursor. */
  showHover(): void
  /** HEAD's copy of the file for the git gutter and the diff layer, or null for none. */
  setBaseline(text: string | null): void
  /** What the diff layer compares with, as ④ chose. */
  readonly compare: CompareTo
  /** Whether HEAD has the file, without which ④ has nothing to compare it with. */
  readonly comparable: boolean
  /**
   * Compares the diff layer with [previous], the file before the last commit that changed it, or
   * with HEAD again when it is null. A diff layer left with nothing to compare with goes to the viewer.
   */
  compareWith(previous: string | null): void
  /** Takes up the settings the page has just been given; see `settings.ts`. */
  settingsChanged(): void
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
  // A link's diff layer waits for what it compares with (`wantDiff`), so the pane opens on the viewer.
  let layer: LayerName = memory?.layer ?? (doc.layer === 'editor' ? 'editor' : 'viewer')
  // The diff layer was asked for before what it compares with had arrived.
  let wantDiff = !memory && doc.layer === 'diff'
  let view: EditorView | null = null
  // Used once, by the first view this pane builds: after that the view holds it.
  let restore = memory?.state ?? null
  // HEAD's copy, held for a view not built yet too, which is the markdown viewer: the gutter is on the
  // code views only. Undefined until Kotlin has answered, null for none.
  let baseline: string | null | undefined
  let compare: CompareTo = memory?.compare ?? 'head'
  // The file before the last commit that changed it, while ④ has chosen it; undefined until asked for.
  let previous: string | null | undefined
  // What the diff layer's bundle compares with now, or null while it shows nothing to compare.
  let compared: string | null = null
  let unwatchLsp: (() => void) | null = null

  /** What the diff layer would compare with: undefined while it is not known yet, null for nothing. */
  function original(): string | null | undefined {
    return compare === 'head' ? baseline : previous
  }

  function bundleFor(next: LayerName): Extension {
    const text = original()
    compared = next === 'diff' && typeof text === 'string' ? text : null
    return compared === null ? BUNDLES[next] : [BUNDLES.diff, unifiedDiff(compared)]
  }

  function createView(): EditorView {
    // A buffer back from the background still holds the plugin of the client it left with, which
    // may since have been let go of.
    const restoring = restore && doc.lsp ? restore.update({ effects: lspSlot.reconfigure(lspPlugin(doc.lsp)) }).state : restore
    const state = restoring ?? EditorState.create({
      doc: source,
      extensions: [
        lineNumbers(),
        // Outside the layer compartment, like the line numbers beside it, so every layer shows it.
        gitGutter(),
        // Outside the compartment, so undo still reaches an edit made before a trip to the viewer.
        history(),
        dirtyFlag,
        syntax.of([]),
        editorTheme,
        // Outside the compartment, so every layer shows them.
        swatchesFor(doc.name),
        layerBundle.of(bundleFor(layer)),
        doc.lsp ? [lspSupport(doc.lsp), lspSlot.of(lspPlugin(doc.lsp))] : [],
        codeFontSize(),
        editorSettings(),
        EditorView.theme({
          '&': { height: '100%' },
          '.cm-scroller': { fontFamily: 'var(--code-font, monospace)', lineHeight: '1.5', touchAction: 'pan-x pan-y' },
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
        // Typing is what wants an asleep server back. After this update, which starting one dispatches into.
        const lsp = doc.lsp
        if (lsp && transactions.some((tr) => tr.docChanged && !tr.annotation(External))) queueMicrotask(() => wakeLsp(lsp))
      },
    })
    // A state built before the last pinch carries that pinch's size, and one built before the last
    // save of the settings carries those; both are the page's, so the view is brought to them
    // rather than the other way round.
    if (restored) {
      applyFontSize(created)
      applyEditorSettings(created)
    }
    if (baseline !== undefined) created.dispatch({ effects: setBaseline.of(baseline) })
    if (doc.lsp) {
      const lsp = doc.lsp
      unwatchLsp = watchLsp(lsp, () => void (created.dom.isConnected && created.dispatch({ effects: lspSlot.reconfigure(lspPlugin(lsp)) })))
      // A file coming to the screen is when its server is wanted.
      wakeLsp(lsp)
    }
    // A buffer back from the background brings the bundle it left with, which may compare with
    // something else by now.
    if (restored && layer === 'diff') {
      created.dispatch({ effects: layerBundle.reconfigure(bundleFor(layer)) })
    }
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

  /** The cursor on [line] at [col], or at the line's start; past its end is its end. */
  function placeCursor(target: EditorView, line: number, col?: number): void {
    const at = target.state.doc.line(Math.min(Math.max(1, line), target.state.doc.lines))
    target.dispatch({ selection: { anchor: at.from + Math.min(Math.max(0, (col ?? 1) - 1), at.length) } })
  }

  /**
   * Puts [line] at the top of the screen. Lines the diff layer shows as deleted just before it are
   * one block with it, and they belong above it, so they stay on the screen — up to half of it, which
   * keeps the line itself there when a great many were deleted. Their height is CodeMirror's guess
   * until they have been drawn, which the first scroll does, so it is done once more after that.
   */
  function scrollViewToLine(target: EditorView, line: number): void {
    const pos = lineStart(target, line)
    const scroll = () => {
      const block = target.lineBlockAt(pos)
      const text = Array.isArray(block.type) ? block.type.find((part) => part.type === BlockType.Text) : undefined
      const above = text ? Math.min(text.top - block.top, target.scrollDOM.clientHeight / 2) : 0
      target.dispatch({ effects: EditorView.scrollIntoView(pos, { y: 'start', yMargin: TOPBAR_SPACE + above }) })
      return above > 0
    }
    if (scroll()) requestAnimationFrame(() => void (target.dom.isConnected && scroll()))
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
    wantDiff = next === 'diff' && original() === undefined
    if (next === layer) return
    if (next === 'diff' && typeof original() !== 'string') return
    const line = topLine()
    const showing = view
    const leaving = layer
    layer = next
    if (markdown) swapSurface(line)
    // A view made just now was built with the new bundle already. An existing one swaps it, which
    // changes no line heights between viewer and editor, so its scroll stays where it was. The diff
    // layer's deleted lines do change them, so going in or out of it puts the line back.
    if (showing) {
      showing.dispatch({ effects: layerBundle.reconfigure(bundleFor(next)) })
      if ((next === 'diff' || leaving === 'diff') && !(markdown && next === 'viewer')) scrollViewToLine(showing, line)
    }
    // Only with a hardware keyboard, where focus is what lets typing start. Without one it would
    // raise the soft keyboard over a document the user has not asked to type in; the first tap on
    // the text focuses it and puts the caret where it landed. The caret goes to the line at the top
    // of the screen first: focusing on its own would leave it at the start of the document and take
    // the screen there with the first keystroke.
    // A cursor already on the screen — a definition or a link put it there — stays where it is.
    if (next === 'editor' && view && hardwareKeyboard()) {
      const head = view.state.selection.main.head
      if (!view.visibleRanges.some((range) => range.from <= head && head <= range.to)) {
        view.dispatch({ selection: { anchor: lineStart(view, line) } })
      }
      view.focus()
    }
  }

  /**
   * Brings the diff layer to what it should compare with now, keeping the line at the top of the
   * screen where it was; and out of it when there is nothing left to compare with. A text not heard
   * yet changes nothing: it is on its way.
   */
  function compareChanged(): void {
    if (wantDiff && original() !== undefined) return setLayer('diff')
    if (layer !== 'diff' || !view) return
    const text = original()
    if (text === undefined || text === compared) return
    if (text === null) return setLayer('viewer')
    const line = topLine()
    view.dispatch({ effects: layerBundle.reconfigure(bundleFor('diff')) })
    scrollViewToLine(view, line)
  }

  function goToLine(line: number, col?: number): void {
    // The rendered markdown has no cursor; the editor's is put there when the view is.
    if (view) placeCursor(view, line, col)
    if (markdown && layer === 'viewer') markdown.scrollToLine(line)
    else if (view) scrollViewToLine(view, line)
  }

  /** The code view, when it is the one showing: the rendered markdown has nothing to ask a server about. */
  function codeView(): EditorView | null {
    return markdown && layer === 'viewer' ? null : view
  }

  // A file coming back shows the layer and the line it left; one being opened shows the layer and
  // the line a link asked for, with the cursor there.
  const startLine = memory?.line ?? doc.line
  if (markdown && layer === 'viewer') {
    if (startLine) markdown.scrollToLine(startLine)
  } else {
    markdown?.visible(false)
    view = createView()
    if (!memory && doc.line) placeCursor(view, doc.line, doc.col)
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
    toggle: () => setLayer(nextLayer(layer, typeof original() === 'string')),
    show: setLayer,
    undo: () => void (view && undo(view)),
    redo: () => void (view && redo(view)),
    goToLine,
    get lsp() {
      return !!doc.lsp && lspAvailable(doc.lsp) && codeView() !== null
    },
    goToDefinition() {
      const shown = codeView()
      if (shown && doc.lsp) void goToDefinition(shown, doc.lsp, shown.state.selection.main.head)
    },
    showHover() {
      const shown = codeView()
      if (shown && doc.lsp) void showHover(shown, doc.lsp, shown.state.selection.main.head)
    },
    setBaseline(text) {
      baseline = text
      view?.dispatch({ effects: setBaseline.of(text) })
      compareChanged()
    },
    get compare() {
      return compare
    },
    get comparable() {
      return typeof baseline === 'string'
    },
    compareWith(text) {
      compare = text === null ? 'head' : 'previous'
      previous = text ?? undefined
      compareChanged()
    },
    settingsChanged: () => void (view && applyEditorSettings(view)),
    close() {
      unwatchLsp?.()
      window.removeEventListener('resize', keepCaretVisible)
      uninstallPinchZoom()
      // Read while both surfaces are still on screen, since this is where the file comes back to.
      const memory: PaneMemory = { state: view?.state ?? null, source, layer, line: topLine(), compare, lsp: doc.lsp }
      if (memory.state) memory.source = memory.state.doc.toString()
      view?.destroy()
      markdown?.remove()
      return memory
    },
  }
}

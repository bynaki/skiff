// A project's language servers, as the page talks to them (docs/skiffcode.spec.md "git과 LSP").
//
// One connection per project and kind of server, as Kotlin runs one `LspProcess` per pair. Each time
// a server is started the connection gets a new `LSPClient`, and the file on the screen swaps its
// plugin for the new client's: an `LSPClient` cannot be connected a second time cleanly — its
// `disconnect` keeps the transport, and what was sent meanwhile would reach the next server ahead of
// its `didOpen`. A new client's plugin sends `didOpen` with the buffer as it is, which is how a
// restarted server gets its document back.
//
// Only the file on the screen has a view, so only it is open on its server (2026-10-05 사용자 결정):
// switching files closes one and opens the other, through the plugin.
import { LSPClient, LSPPlugin, languageServerExtensions, type Transport } from '@codemirror/lsp-client'
import { type Diagnostic, forEachDiagnostic } from '@codemirror/lint'
import { type EditorState, type Extension, Facet, Prec, StateEffect, StateField } from '@codemirror/state'
import { Decoration, EditorView, type Tooltip, ViewPlugin, keymap, showTooltip } from '@codemirror/view'
import { notify, onNotify, rpc } from './bridge'
import { type EndReason, afterEnd } from './lspPolicy'
import { sanitizeHTML } from './sanitize'

/** Which language server a file goes to, as Kotlin's document says (`MainActivity.languageServerFor`). */
export interface LspTarget {
  project: string
  /** `LanguageServer`'s name: Python, TypeScript or Markdown. */
  server: string
  languageId: string
  rootUri: string
  uri: string
}

/** What this module needs from the rest of the page. */
export interface LspHooks {
  /** On the banner until dismissed. */
  tell(message: string): void
  /** On the banner for a moment. */
  flash(message: string): void
  /** Kotlin's words for the two things said here, or null until they have arrived. */
  labels(): { goToDefinition: string; noDefinition: string } | null
  /** Puts the cursor in the file on the screen at [line] and [col], both 1-based, with the line at the top. */
  goTo(line: number, col: number): void
}

interface Connection {
  project: string
  server: string
  rootUri: string
  /** Null while the server is not running. */
  client: LSPClient | null
  startedAt: number
  quickFailures: number
  /** Not to be started again while the page is up: see `afterEnd`. */
  gone: boolean
  /** Why it is [gone], said again when a double tap or F12 asks it for something. */
  goneSaid: string | null
  /** The running client's, which hears what Kotlin passes on. */
  handlers: Set<(message: string) => void>
  /** The file on the screen, when it is one of this server's, told when [client] changes. */
  watchers: Set<() => void>
}

const connections = new Map<string, Connection>()
let hooks: LspHooks | null = null
/** `settings.toml`'s `[lsp] timeout_seconds`, for each request after `initialize`. */
let timeoutMs = 10_000

/**
 * How long `initialize` may take. The first one for a project connects to the server, checks what
 * the account may run and looks for the command, all before the language server has started; a
 * request timeout that gave up on it would leave the client unusable and start the server again.
 */
const INITIALIZE_TIMEOUT_MS = 60_000

/** The longest wait from a tap's lift to the next touch that makes the two a double tap. */
const DOUBLE_TAP_MS = 300

/** How far apart a double tap's two touches may be, and how far a finger may drift and still be tapping. */
const DOUBLE_TAP_SLOP = 24

export function setLspHooks(given: LspHooks): void {
  hooks = given
}

export function setLspTimeout(ms: number): void {
  timeoutMs = ms
}

const keyOf = (project: string, server: string) => `${project}\n${server}`

function connectionOf(target: LspTarget): Connection {
  const key = keyOf(target.project, target.server)
  let connection = connections.get(key)
  if (!connection) {
    connection = {
      project: target.project,
      server: target.server,
      rootUri: target.rootUri,
      client: null,
      startedAt: 0,
      quickFailures: 0,
      gone: false,
      goneSaid: null,
      handlers: new Set(),
      watchers: new Set(),
    }
    connections.set(key, connection)
  }
  return connection
}

/**
 * Messages carry the server's JSON as a string, which Kotlin frames for the server's stdio without
 * reading it (M0). The transport knows nothing of framing either: `send`, `subscribe`, `unsubscribe`.
 */
function transportOf(connection: Connection): Transport {
  return {
    send(message) {
      notify('lspSend', { project: connection.project, server: connection.server, message })
    },
    subscribe(handler) {
      connection.handlers.add(handler)
    },
    unsubscribe(handler) {
      connection.handlers.delete(handler)
    },
  }
}

/** The library keeps the request timeout private; `initialize` needs a longer one than the rest. */
const requestTimeout = (client: LSPClient) => client as unknown as { timeout: number }

/** Starts [connection]'s server unless it is running or gone, and tells the file on the screen. */
function start(connection: Connection): void {
  if (connection.gone || connection.client) return
  const client = new LSPClient({
    rootUri: connection.rootUri,
    timeout: Math.max(INITIALIZE_TIMEOUT_MS, timeoutMs),
    sanitizeHTML,
    extensions: languageServerExtensions(),
  })
  connection.client = client
  connection.startedAt = performance.now()
  client.connect(transportOf(connection))
  client.initializing.then(
    () => void (requestTimeout(client).timeout = timeoutMs),
    // No answer, or a refusal: as if it had failed, which may start it once more.
    (error) => {
      console.log(`lsp ${connection.server} initialize: ${JSON.stringify(error)}`)
      if (connection.client === client) ended(connection, 'failed')
    },
  )
  for (const changed of [...connection.watchers]) changed()
}

/** Starts the server [target] goes to if it is not running: something on the screen wants it now. */
export function wakeLsp(target: LspTarget): void {
  start(connectionOf(target))
}

/** Whether [target]'s server may still be asked for anything, which the palette's commands follow. */
export function lspAvailable(target: LspTarget): boolean {
  return !connectionOf(target).gone
}

/** The running client's plugin for [target], for the pane's LSP compartment, or nothing while there is none. */
export function lspPlugin(target: LspTarget): Extension {
  const client = connectionOf(target).client
  return client ? client.plugin(target.uri, target.languageId) : []
}

/** Calls [changed] whenever [target]'s client is started or dropped. Returns a function that stops it. */
export function watchLsp(target: LspTarget, changed: () => void): () => void {
  const watchers = connectionOf(target).watchers
  watchers.add(changed)
  return () => watchers.delete(changed)
}

function ended(connection: Connection, reason: EndReason, message?: string): void {
  const lived = connection.client ? performance.now() - connection.startedAt : 0
  connection.client?.disconnect()
  connection.client = null
  const after = afterEnd(reason, lived, connection.quickFailures)
  connection.quickFailures = after.quickFailures
  if (after.next === 'never') {
    connection.gone = true
    connection.goneSaid = message ?? null
    // A server that is not there is said only when something asks it (2026-10-06 사용자 결정): a
    // project without one would otherwise say so every time one of its files came up.
    if (message && reason !== 'notInstalled') hooks?.tell(message)
  }
  for (const changed of [...connection.watchers]) changed()
  if (after.next === 'now' && connection.watchers.size > 0) start(connection)
}

onNotify<{ project: string; server: string; message: string }>('lspMessage', ({ project, server, message }) => {
  for (const handler of connections.get(keyOf(project, server))?.handlers ?? []) handler(message)
})

onNotify<{ project: string; server: string; reason: EndReason; message?: string }>('lspEnded', (params) => {
  const connection = connections.get(keyOf(params.project, params.server))
  if (connection) ended(connection, params.reason, params.message)
})

// The app is back on the screen: the file there gets its server again, if the link dropped under it
// or it was stopped while the app was away.
onNotify('lspWake', () => {
  for (const connection of connections.values()) if (connection.watchers.size > 0) start(connection)
})

/**
 * The plugin of the view's running client, starting it first if it is asleep; null when there is
 * none to be had, which is said on the banner for a moment when it is known why.
 */
function pluginOf(view: EditorView, target: LspTarget): LSPPlugin | null {
  wakeLsp(target)
  const plugin = LSPPlugin.get(view)
  const said = connectionOf(target).goneSaid
  if (!plugin && said) hooks?.flash(said)
  return plugin
}

/**
 * Goes to the definition of what is at [pos]: in this file, the cursor moves there with its line at
 * the top of the screen (2026-10-05 사용자 결정); in another, Kotlin opens it at that line and column.
 */
export async function goToDefinition(view: EditorView, target: LspTarget, pos: number): Promise<void> {
  const plugin = pluginOf(view, target)
  if (!plugin) return
  const client = plugin.client
  client.sync()
  try {
    await client.initializing
    const found = client.serverCapabilities?.definitionProvider
      ? await client.withMapping(async (mapping): Promise<Definition | null> => {
        const result = await client.request<object, Location | Location[] | LocationLink[] | null>('textDocument/definition', {
          textDocument: { uri: plugin.uri },
          position: plugin.toPosition(pos),
        })
        const first = Array.isArray(result) ? result[0] : result
        if (!first) return null
        const uri = 'targetUri' in first ? first.targetUri : first.uri
        const start = ('targetUri' in first ? first.targetSelectionRange : first.range).start
        if (uri !== plugin.uri) return { uri, line: start.line + 1, col: start.character + 1 }
        // The buffer may have been typed in while the server was answering.
        return { at: mapping.getMapping(uri) ? mapping.mapPosition(uri, start) : plugin.fromPosition(start) }
      })
      : null
    if (!found) {
      const said = hooks?.labels()?.noDefinition
      if (said) hooks?.flash(said)
    } else if ('at' in found) {
      if (!view.dom.isConnected) return
      const line = view.state.doc.lineAt(found.at)
      hooks?.goTo(line.number, found.at - line.from + 1)
    } else {
      await rpc('openDefinition', { project: target.project, uri: found.uri, line: found.line, col: found.col })
    }
  } catch (error) {
    console.log(`lsp definition: ${error instanceof Error ? error.message : JSON.stringify(error)}`)
  }
}

/**
 * Shows what the server says about [pos], the diagnostics there and a way to its definition, in a
 * tooltip under the finger. The library's own hover follows a pointer, which a finger does not leave
 * resting anywhere.
 */
export async function showHover(view: EditorView, target: LspTarget, pos: number): Promise<void> {
  const plugin = pluginOf(view, target)
  if (!plugin) return
  const client = plugin.client
  client.sync()
  let contents: HoverContents | null = null
  try {
    await client.initializing
    if (client.serverCapabilities?.hoverProvider) {
      const hover = await client.request<object, { contents: HoverContents } | null>('textDocument/hover', {
        textDocument: { uri: plugin.uri },
        position: plugin.toPosition(pos),
      })
      contents = hover?.contents ?? null
    }
  } catch (error) {
    console.log(`lsp hover: ${error instanceof Error ? error.message : JSON.stringify(error)}`)
  }
  if (!view.dom.isConnected) return
  const at = Math.min(pos, view.state.doc.length)
  const diagnostics = diagnosticsAt(view.state, at)
  const html = contents === null ? null : contentsToHTML(plugin, contents)
  const word = view.state.wordAt(at)
  view.dispatch({
    effects: setHover.of({
      tooltip: {
        pos: at,
        above: true,
        create: () => ({ dom: hoverDom(view, target, at, html, diagnostics) }),
      },
      word: word && word.from < word.to ? { from: word.from, to: word.to } : null,
    }),
  })
}

/** Where a definition is: a place in the same buffer, or a file the server names and where in it. */
type Definition = { at: number } | { uri: string; line: number; col: number }

type MarkedString = string | { language: string; value: string }
type HoverContents = MarkedString | MarkedString[] | { kind: string; value: string }
interface Position { line: number; character: number }
interface Range { start: Position; end: Position }
interface Location { uri: string; range: Range }
interface LocationLink { targetUri: string; targetRange: Range; targetSelectionRange: Range }

/** Each shape LSP allows a hover's contents in, as the plugin renders docs: sanitized. */
function contentsToHTML(plugin: LSPPlugin, contents: HoverContents): string {
  const one = (part: MarkedString | { kind: string; value: string }): string => {
    if (typeof part === 'string') return plugin.docToHTML(part, 'markdown')
    if ('kind' in part) return plugin.docToHTML(part as { kind: 'markdown' | 'plaintext'; value: string })
    return plugin.docToHTML('```' + part.language + '\n' + part.value + '\n```', 'markdown')
  }
  return Array.isArray(contents) ? contents.map(one).join('<hr>') : one(contents)
}

function diagnosticsAt(state: EditorState, pos: number): Diagnostic[] {
  const found: Diagnostic[] = []
  forEachDiagnostic(state, (diagnostic, from, to) => {
    if (from <= pos && pos <= to) found.push(diagnostic)
  })
  return found
}

function hoverDom(view: EditorView, target: LspTarget, pos: number, html: string | null, diagnostics: Diagnostic[]): HTMLElement {
  const dom = document.createElement('div')
  dom.className = 'cm-lsp-hover-tooltip cm-lsp-documentation skiff-hover'
  if (html) {
    const body = document.createElement('div')
    body.innerHTML = html
    dom.append(body)
  }
  for (const diagnostic of diagnostics) {
    dom.append(Object.assign(document.createElement('p'), {
      className: `skiff-diagnostic skiff-diagnostic-${diagnostic.severity}`,
      textContent: diagnostic.message,
    }))
  }
  const label = hooks?.labels()?.goToDefinition
  if (label) {
    const button = Object.assign(document.createElement('button'), { className: 'skiff-definition', textContent: label })
    button.addEventListener('click', () => {
      view.dispatch({ effects: setHover.of(null) })
      void goToDefinition(view, target, pos)
    })
    dom.append(button)
  }
  return dom
}

/** The file's server, for what the view does on its own: the double tap and F12. */
const lspTargetFacet = Facet.define<LspTarget, LspTarget | null>({ combine: (values) => values[0] ?? null })

/** The touch hover, and the word it is about, if it is on one. */
interface Hover {
  tooltip: Tooltip
  word: { from: number; to: number } | null
}

const setHover = StateEffect.define<Hover | null>()

/** The word a hover is about, in the selection's color while the hover is up (2026-10-06 사용자 결정). */
const hoverWord = Decoration.mark({ class: 'skiff-hover-word' })

/**
 * The touch hover, gone with the next edit, a key, or a tap anywhere but on it (`doubleTap`). Not with
 * the selection: the browser follows the second tap with a mouse press at the same place, which
 * selects the word, and CodeMirror reads that back from the DOM without saying a pointer did it.
 */
const hoverField = StateField.define<Hover | null>({
  create: () => null,
  update(value, tr) {
    for (const effect of tr.effects) if (effect.is(setHover)) return effect.value
    return tr.docChanged ? null : value
  },
  provide: (field) => [
    showTooltip.from(field, (hover) => hover?.tooltip ?? null),
    EditorView.decorations.from(field, (hover) =>
      hover?.word ? Decoration.set(hoverWord.range(hover.word.from, hover.word.to)) : Decoration.none),
  ],
})

/**
 * Two quick taps on a word in a reading layer bring up its hover (2026-10-06 사용자 결정; it was a
 * long press, which Android also answers by selecting the word and raising its menu — a long press
 * is left to that, for copying). Not in the editor, where a double tap selects the word; there the
 * palette's Show Hover asks at the cursor. A finger that moves is a scroll, and the browser cancels
 * the pointer then. Nothing waits on a single tap: a reading layer gives it no meaning of its own.
 */
const doubleTap = ViewPlugin.define((view) => {
  // The touch on the screen now, while it is still a tap.
  let touching: { x: number; y: number; id: number; second: boolean } | null = null
  // Where and when the last tap lifted, while a second one may still follow it.
  let lastTap: { x: number; y: number; at: number } | null = null

  const down = (event: PointerEvent) => {
    if (event.pointerType !== 'touch') return
    // A second finger is a pinch: neither is a tap.
    if (touching) {
      touching = null
      lastTap = null
      return
    }
    if (!view.state.facet(lspTargetFacet) || !view.state.readOnly) return
    const second = lastTap !== null && event.timeStamp - lastTap.at <= DOUBLE_TAP_MS &&
      Math.hypot(event.clientX - lastTap.x, event.clientY - lastTap.y) <= DOUBLE_TAP_SLOP
    touching = { x: event.clientX, y: event.clientY, id: event.pointerId, second }
  }
  const move = (event: PointerEvent) => {
    if (touching?.id !== event.pointerId) return
    if (Math.hypot(event.clientX - touching.x, event.clientY - touching.y) > DOUBLE_TAP_SLOP) touching = null
  }
  const up = (event: PointerEvent) => {
    if (touching?.id !== event.pointerId) return
    const tap = touching
    touching = null
    if (!tap.second) {
      lastTap = { x: tap.x, y: tap.y, at: event.timeStamp }
      return
    }
    // A third tap starts over, so it puts the hover away instead of asking again.
    lastTap = null
    const target = view.state.facet(lspTargetFacet)
    const pos = view.posAtCoords({ x: tap.x, y: tap.y })
    if (target && pos !== null) void showHover(view, target, pos)
  }
  const cancel = (event: PointerEvent) => {
    if (touching?.id === event.pointerId) touching = null
  }
  // The mouse moves the browser makes up after a touch would raise the library's pointer hover after
  // every tap. Only a real mouse's reach it.
  const fromTouch = (event: MouseEvent) => {
    const capabilities = (event as MouseEvent & { sourceCapabilities?: { firesTouchEvents: boolean } }).sourceCapabilities
    if (capabilities?.firesTouchEvents) event.stopPropagation()
  }
  // A tap anywhere but on the tooltip puts it away, and so does a key.
  const away = (event: Event) => {
    if (!view.state.field(hoverField, false)) return
    if (event.target instanceof Element && event.target.closest('.cm-tooltip')) return
    view.dispatch({ effects: setHover.of(null) })
  }
  const content = view.contentDOM
  content.addEventListener('pointerdown', down)
  content.addEventListener('pointermove', move)
  content.addEventListener('pointerup', up)
  content.addEventListener('pointercancel', cancel)
  view.dom.addEventListener('pointerdown', away, true)
  view.dom.addEventListener('keydown', away, true)
  view.dom.addEventListener('mousemove', fromTouch, true)
  return {
    destroy() {
      content.removeEventListener('pointerdown', down)
      content.removeEventListener('pointermove', move)
      content.removeEventListener('pointerup', up)
      content.removeEventListener('pointercancel', cancel)
      view.dom.removeEventListener('pointerdown', away, true)
      view.dom.removeEventListener('keydown', away, true)
      view.dom.removeEventListener('mousemove', fromTouch, true)
    },
  }
})

/** Tooltips in the menu's colors, and the touch hover's parts. */
const lspTheme = EditorView.theme({
  '.cm-tooltip': {
    color: 'var(--ui-foreground)',
    backgroundColor: 'var(--ui-background)',
    border: '1px solid var(--ui-border)',
    borderRadius: '6px',
  },
  '.cm-tooltip.cm-tooltip-hover, .cm-tooltip .skiff-hover': { maxWidth: 'min(90vw, 40em)', maxHeight: '40vh', overflow: 'auto' },
  '.skiff-hover': { padding: '4px 8px' },
  '.skiff-hover-word': { backgroundColor: 'var(--editor-selection)' },
  '.skiff-hover pre': { whiteSpace: 'pre-wrap', margin: '4px 0' },
  '.skiff-diagnostic': { margin: '4px 0' },
  '.skiff-diagnostic-error': { color: 'var(--syntax-invalid)' },
  '.skiff-definition': {
    display: 'block',
    margin: '6px 0 2px auto',
    padding: '6px 10px',
    color: 'var(--ui-accent)',
    background: 'none',
    border: '1px solid var(--ui-border)',
    borderRadius: '6px',
    font: 'inherit',
  },
  '.cm-tooltip-autocomplete ul li[aria-selected]': { color: 'var(--ui-strong-foreground)', backgroundColor: 'var(--ui-accent)' },
})

/**
 * What a view of a file with a language server carries whether or not the server is running: the
 * touch hover, the double tap that brings it, and F12 going to the definition by the same way the
 * hover's button does — the library's own stays in the file and leaves the line wherever it lands.
 */
export function lspSupport(target: LspTarget): Extension {
  return [
    lspTargetFacet.of(target),
    hoverField,
    doubleTap,
    lspTheme,
    Prec.high(keymap.of([{
      key: 'F12',
      run: (view) => {
        void goToDefinition(view, target, view.state.selection.main.head)
        return true
      },
    }])),
  ]
}

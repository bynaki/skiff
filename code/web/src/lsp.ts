// M0 spike: @codemirror/lsp-client's Transport implemented on the Kotlin bridge instead of a socket.
import { LSPClient, LSPPlugin, languageServerExtensions, type Transport } from '@codemirror/lsp-client'
import { forEachDiagnostic } from '@codemirror/lint'
import type { EditorView } from '@codemirror/view'
import { notify, onNotify } from './bridge'

/**
 * The whole of what the transport has to be: send a string, hear strings back. LSP's own
 * Content-Length framing is not part of it, so the bridge carries bare JSON-RPC and Kotlin puts the
 * headers on stdio. Messages travel as a string inside the envelope because that is what a real
 * `LspProcess` writes to the server — it never has to parse them.
 */
export function bridgeTransport(): Transport {
  const handlers = new Set<(value: string) => void>()
  onNotify<{ message: string }>('lspMessage', ({ message }) => {
    for (const handler of handlers) handler(message)
  })
  return {
    send(message) {
      notify('lspSend', { message })
    },
    subscribe(handler) {
      handlers.add(handler)
    },
    unsubscribe(handler) {
      handlers.delete(handler)
    },
  }
}

export const LSP_URI = 'file:///project/sample.ts'

export function lspClient(): LSPClient {
  return new LSPClient({ rootUri: 'file:///project', extensions: languageServerExtensions() })
}

/** Creating the editor with this runs `openFile`, which sends `didOpen`. */
export function lspEditorExtension(client: LSPClient) {
  return LSPPlugin.create(client, LSP_URI, 'typescript')
}

/**
 * Arrival times of every `publishDiagnostics`, so the round trips can be timed from outside.
 * The method name is read out of parsed JSON, not matched as a substring: `org.json` writes `/`
 * escaped as `\/`, which is valid JSON but not the literal the raw message appears to contain.
 */
export function watchPublishes(): number[] {
  const times: number[] = []
  onNotify<{ message: string }>('lspMessage', ({ message }) => {
    if ((JSON.parse(message) as { method?: string }).method === 'textDocument/publishDiagnostics') {
      times.push(performance.now())
    }
  })
  return times
}

/** What the stub marks, so the positions it sends back can be checked against the document. */
const EXPECTED = ['원격 파일을 읽어', 'fetch(']

type Report = (key: string, value: string) => void

const waitFor = async (done: () => boolean, timeoutMs = 20_000) => {
  const deadline = performance.now() + timeoutMs
  while (!done() && performance.now() < deadline) {
    await new Promise((resolve) => requestAnimationFrame(resolve))
  }
  return done()
}

/**
 * Drives the checks that matter for the design: the handshake, diagnostics pushed by the server,
 * a request answered with a position, completion, and a full-document resync after an edit.
 */
export async function runLspSpike(client: LSPClient, view: EditorView, publishes: number[], openedAt: number, report: Report) {
  const capabilities = client.serverCapabilities ?? {}
  const sync = capabilities.textDocumentSync === 2 ? 'incremental' : `sync ${capabilities.textDocumentSync}`
  report('lsp', `${sync}, capabilities: ${Object.keys(capabilities).join(' ') || '(none)'}`)

  if (!(await waitFor(() => publishes.length > 0))) {
    report('lsp diagnostics', 'FAIL — none arrived')
    return
  }
  report('lsp diagnostics', `${(publishes[0] - openedAt).toFixed(0)} ms after didOpen, ${describeRanges(view)}`)

  report('lsp hover', await checkHover(client, view))
  report('lsp completion', await checkCompletion(client, view))

  // autoSync waits 500 ms after the last keystroke, then resends the whole document (sync is Full).
  const editedAt = performance.now()
  const before = publishes.length
  view.dispatch({ changes: { from: 0, insert: '// 한 줄을 앞에 넣는다\n' } })
  if (!(await waitFor(() => publishes.length > before))) {
    report('lsp resync', 'FAIL — an edit produced no new diagnostics')
    return
  }
  report('lsp resync', `${(publishes[publishes.length - 1] - editedAt).toFixed(0)} ms from edit to diagnostics, ${describeRanges(view)}`)
}

/** Every diagnostic range, checked against the text it should be covering. */
function describeRanges(view: EditorView): string {
  let count = 0
  const wrong: string[] = []
  forEachDiagnostic(view.state, (_diagnostic, from, to) => {
    count++
    const covered = view.state.doc.sliceString(from, to)
    if (!EXPECTED.includes(covered)) wrong.push(`${from}..${to} covers ${JSON.stringify(covered)}`)
  })
  return wrong.length === 0
    ? `${count} ranges all on the expected text`
    : `${count} ranges, ${wrong.length} misplaced: ${wrong.slice(0, 3).join('; ')}`
}

/**
 * Hover at an offset inside a Korean comment. The stub echoes the offset it computed, so a match
 * proves both sides agree on UTF-16 code units — the client never negotiates `positionEncoding`,
 * and a server counting UTF-8 bytes would land elsewhere on exactly this line.
 */
async function checkHover(client: LSPClient, view: EditorView): Promise<string> {
  const offset = view.state.doc.toString().indexOf('파일을')
  if (offset < 0) return 'FAIL — the sample has no Korean comment'
  const started = performance.now()
  const result = await client.request<object, { contents?: { value: string } }>('textDocument/hover', {
    textDocument: { uri: LSP_URI },
    position: lspPosition(view, offset),
  })
  const took = (performance.now() - started).toFixed(0)
  const value = result?.contents?.value ?? ''
  return value.includes(`오프셋 ${offset}`)
    ? `${took} ms, the server read the same offset ${offset} on a Korean line`
    : `FAIL — asked for offset ${offset}, the server answered ${JSON.stringify(value)}`
}

async function checkCompletion(client: LSPClient, view: EditorView): Promise<string> {
  const needle = 'readChunk0'
  const at = view.state.doc.toString().indexOf(needle)
  if (at < 0) return `FAIL — the sample has no ${needle}`
  const started = performance.now()
  const result = await client.request<object, { items: { label: string }[] }>('textDocument/completion', {
    textDocument: { uri: LSP_URI },
    position: lspPosition(view, at + needle.length),
  })
  const took = (performance.now() - started).toFixed(0)
  const labels = result?.items?.map((item) => item.label) ?? []
  return labels.includes(needle)
    ? `${took} ms, ${labels.length} items: ${labels.join(', ')}`
    : `FAIL — expected ${needle} among the items, got ${labels.join(', ') || '(none)'}`
}

function lspPosition(view: EditorView, offset: number) {
  const line = view.state.doc.lineAt(offset)
  return { line: line.number - 1, character: offset - line.from }
}

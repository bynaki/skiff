// JSON-RPC 2.0 over the web message listener Kotlin injects as `skiffBridge` (bridge/WebBridge.kt).
//
// The page calls Kotlin with `rpc` and gets an answer. Either side can send a notification (no id,
// no answer): `notify` from here, `onNotify` for what Kotlin pushes on its own. Kotlin can only push
// once this module has said `ready`, which it does as soon as it loads.

interface Bridge {
  onmessage: ((event: { data: string }) => void) | null
  postMessage(message: string): void
}

declare const skiffBridge: Bridge | undefined

interface Incoming {
  id?: number
  method?: string
  params?: unknown
  result?: unknown
  error?: { code: number; message: string }
}

/** A call Kotlin answered with an error. `code` is JSON-RPC's: -32601 unknown method, -32602 bad params, -32000 the handler failed. */
export class RpcError extends Error {
  constructor(readonly code: number, message: string) {
    super(message)
    this.name = 'RpcError'
  }
}

// Absent when the bundle is opened anywhere but the app's WebView at its one allowed origin.
if (typeof skiffBridge === 'undefined') throw new Error('skiffBridge is missing: not running inside Skiff Code')
const bridge: Bridge = skiffBridge

const pending = new Map<number, { resolve: (value: unknown) => void; reject: (error: Error) => void }>()
const listeners = new Map<string, Set<(params: unknown) => void>>()
let nextId = 1

bridge.onmessage = (event) => {
  const message = JSON.parse(event.data) as Incoming
  // No id means Kotlin sent this on its own: a notification, not an answer to a call.
  if (message.id === undefined) {
    for (const handler of listeners.get(message.method!) ?? []) handler(message.params)
    return
  }
  const call = pending.get(message.id)
  if (!call) return
  pending.delete(message.id)
  if (message.error) call.reject(new RpcError(message.error.code, message.error.message))
  else call.resolve(message.result)
}

export function rpc<T>(method: string, params: object = {}): Promise<T> {
  const id = nextId++
  return new Promise<T>((resolve, reject) => {
    pending.set(id, { resolve: resolve as (value: unknown) => void, reject })
    bridge.postMessage(JSON.stringify({ jsonrpc: '2.0', id, method, params }))
  })
}

/** Listen for notifications Kotlin pushes without being asked. Returns a function that stops listening. */
export function onNotify<T>(method: string, handler: (params: T) => void): () => void {
  const set = listeners.get(method) ?? new Set()
  listeners.set(method, set)
  set.add(handler as (params: unknown) => void)
  return () => set.delete(handler as (params: unknown) => void)
}

/** Fire and forget: a notification to Kotlin, with no id and so no answer. */
export function notify(method: string, params: object = {}) {
  bridge.postMessage(JSON.stringify({ jsonrpc: '2.0', method, params }))
}

// Kotlin holds its notifications until this arrives, because only a message from the page hands it
// a way to reach the page. Sent after onmessage is set, so nothing it releases is missed.
notify('ready')

// JSON-RPC over the web message listener Kotlin injects as `skiffBridge`.

interface Bridge {
  onmessage: ((event: { data: string }) => void) | null
  postMessage(message: string): void
}

declare const skiffBridge: Bridge

interface Incoming {
  id?: number
  method?: string
  params?: unknown
  result?: unknown
  error?: { code: number; message: string }
}

const pending = new Map<number, { resolve: (value: unknown) => void; reject: (error: Error) => void }>()
const listeners = new Map<string, Set<(params: unknown) => void>>()
let nextId = 1

skiffBridge.onmessage = (event) => {
  const message = JSON.parse(event.data) as Incoming
  // No id means Kotlin sent this on its own: a notification, not an answer to a call.
  if (message.id === undefined) {
    for (const handler of listeners.get(message.method!) ?? []) handler(message.params)
    return
  }
  const call = pending.get(message.id)
  if (!call) return
  pending.delete(message.id)
  if (message.error) call.reject(new Error(`${message.error.code} ${message.error.message}`))
  else call.resolve(message.result)
}

export function rpc<T>(method: string, params: object): Promise<T> {
  const id = nextId++
  return new Promise<T>((resolve, reject) => {
    pending.set(id, { resolve: resolve as (value: unknown) => void, reject })
    skiffBridge.postMessage(JSON.stringify({ jsonrpc: '2.0', id, method, params }))
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
export function notify(method: string, params: object) {
  skiffBridge.postMessage(JSON.stringify({ jsonrpc: '2.0', method, params }))
}

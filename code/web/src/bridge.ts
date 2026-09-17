// JSON-RPC over the web message listener Kotlin injects as `skiffBridge`.

interface Bridge {
  onmessage: ((event: { data: string }) => void) | null
  postMessage(message: string): void
}

declare const skiffBridge: Bridge

interface Response {
  id: number
  result?: unknown
  error?: { code: number; message: string }
}

const pending = new Map<number, { resolve: (value: unknown) => void; reject: (error: Error) => void }>()
let nextId = 1

skiffBridge.onmessage = (event) => {
  const response = JSON.parse(event.data) as Response
  const call = pending.get(response.id)
  if (!call) return
  pending.delete(response.id)
  if (response.error) call.reject(new Error(`${response.error.code} ${response.error.message}`))
  else call.resolve(response.result)
}

export function rpc<T>(method: string, params: object): Promise<T> {
  const id = nextId++
  return new Promise<T>((resolve, reject) => {
    pending.set(id, { resolve: resolve as (value: unknown) => void, reject })
    skiffBridge.postMessage(JSON.stringify({ jsonrpc: '2.0', id, method, params }))
  })
}

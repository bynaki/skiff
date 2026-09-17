// M0 spike: one JSON-RPC round trip through the web message listener Kotlin injects.

interface BridgeMessage {
  data: string
}

interface Bridge {
  onmessage: ((event: BridgeMessage) => void) | null
  postMessage(message: string): void
}

declare const skiffBridge: Bridge

const statusLine = document.getElementById('status')!
skiffBridge.onmessage = (event) => {
  console.log('round trip ok: ' + event.data)
  statusLine.textContent = 'round trip ok: ' + event.data
}
const request = { jsonrpc: '2.0', id: 1, method: 'ping', params: { text: '안녕 from vite' } }
skiffBridge.postMessage(JSON.stringify(request))
statusLine.textContent = 'sent: ' + JSON.stringify(request)

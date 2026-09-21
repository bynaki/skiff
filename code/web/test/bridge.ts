// `bridge.ts` refuses to load outside the app's WebView, and every module that reaches Kotlin
// imports it. A test that loads one of those (through `pane.ts`, say) therefore needs the global
// Kotlin injects to be there. It answers nothing: the calls those modules make as they load want
// an answer that never comes, which is what the page sees before Kotlin has replied anyway.
declare global {
  var skiffBridge: {
    onmessage: ((event: { data: string }) => void) | null
    postMessage(message: string): void
  }
}

globalThis.skiffBridge = { onmessage: null, postMessage: () => {} }

export {}

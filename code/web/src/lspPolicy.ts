// When a language server ended, whether the page starts it again (docs/skiffcode.spec.md "git과 LSP").
// Apart from `lsp.ts` so it can be tested without a bridge.

/** Why Kotlin says a server is not running (`LspEnd`). */
export type EndReason = 'idle' | 'stopped' | 'closed' | 'disconnected' | 'notInstalled' | 'unsupported' | 'failed'

/**
 * `later`: off until something on the screen needs it — the file comes up, is typed in, or is asked
 * about. `now`: start it again straight away, if a file of its is on the screen. `never`: not again
 * while the page is up.
 */
export type Next = 'later' | 'now' | 'never'

/** A failure this soon after starting counts toward giving up; one that ran longer does not. */
export const QUICK_MS = 30_000

/** How many quick failures in a row leave a server off for good. */
export const MAX_QUICK_FAILURES = 3

/**
 * What follows [reason] for a server that ran for [livedMs], given [quickFailures] in a row before
 * it, and that count afterwards.
 *
 * A server let go of — idle, the app away, its project's connection closed — comes back the next
 * time it is wanted rather than at once: starting it again only to sit unused would undo the reason
 * it was stopped. So does one the link dropped under, which would only fail again until the link is
 * back. One that is not there, or counts positions some other way, stays off. One that failed is
 * started again at once, so diagnostics come back after a crash, unless it keeps failing as soon as
 * it starts.
 */
export function afterEnd(reason: EndReason, livedMs: number, quickFailures: number): { next: Next; quickFailures: number } {
  switch (reason) {
    case 'idle':
    case 'stopped':
    case 'closed':
    // The link, not the server: starting it again at once would only fail again while the phone is
    // folded shut, and give up after a few (2026-10-06, 폴드8). It comes back with the app (`lspWake`).
    case 'disconnected':
      return { next: 'later', quickFailures: 0 }
    case 'notInstalled':
    case 'unsupported':
      return { next: 'never', quickFailures }
    case 'failed': {
      const count = livedMs < QUICK_MS ? quickFailures + 1 : 0
      return { next: count >= MAX_QUICK_FAILURES ? 'never' : 'now', quickFailures: count }
    }
  }
}

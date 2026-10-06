// Whether a language server that ended is started again.
import { describe, expect, test } from 'vitest'
import { MAX_QUICK_FAILURES, QUICK_MS, afterEnd } from '../src/lspPolicy'

describe('a server that ended', () => {
  test('because it was let go of comes back when it is next wanted', () => {
    for (const reason of ['idle', 'stopped', 'closed', 'disconnected'] as const) {
      expect(afterEnd(reason, 600_000, 2), reason).toEqual({ next: 'later', quickFailures: 0 })
    }
  })

  test('because it is not there, or counts positions otherwise, stays off', () => {
    expect(afterEnd('notInstalled', 0, 0).next).toBe('never')
    expect(afterEnd('unsupported', 100, 0).next).toBe('never')
  })

  test('by failing is started again at once', () => {
    expect(afterEnd('failed', 5_000, 0)).toEqual({ next: 'now', quickFailures: 1 })
  })

  test('by failing as soon as it starts, again and again, stays off', () => {
    let quickFailures = 0
    const nexts: string[] = []
    for (let i = 0; i < MAX_QUICK_FAILURES; i++) {
      const after = afterEnd('failed', 100, quickFailures)
      nexts.push(after.next)
      quickFailures = after.quickFailures
    }
    expect(nexts).toEqual([...Array(MAX_QUICK_FAILURES - 1).fill('now'), 'never'])
  })

  test('by failing after running a while starts the count over', () => {
    expect(afterEnd('failed', QUICK_MS, MAX_QUICK_FAILURES - 1)).toEqual({ next: 'now', quickFailures: 0 })
  })
})

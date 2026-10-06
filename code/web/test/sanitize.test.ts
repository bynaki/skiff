// Which links in a language server's markdown keep their address.
import { describe, expect, test } from 'vitest'
import { safeHref } from '../src/sanitize'

describe('a link in a docstring', () => {
  test('keeps an address another app opens', () => {
    for (const href of ['https://docs.python.org/3/', 'http://example.com', 'mailto:a@example.com', ' HTTPS://x']) {
      expect(safeHref(href), href).toBe(true)
    }
  })

  test('loses any other', () => {
    for (const href of ['javascript:alert(1)', 'JaVaScRiPt:x', 'intent://x#Intent;end', '/etc/passwd', 'file:///a', 'data:text/html,x', '']) {
      expect(safeHref(href), href).toBe(false)
    }
  })
})

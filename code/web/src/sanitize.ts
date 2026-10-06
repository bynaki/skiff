// What a language server says in markdown — a hover, a completion's documentation — is rendered by
// `@codemirror/lsp-client` with `marked`, which lets raw HTML through. Docstrings come from files on
// the server, and the page they land in holds the bridge, so the HTML is cut down to the shapes a
// rendered docstring has before it is shown (docs/skiffcode.spec.md "보안 규칙").

/** Elements a rendered docstring is made of. Any other is replaced by its text. */
const KEPT = new Set([
  'P', 'PRE', 'CODE', 'SPAN', 'DIV', 'BR', 'HR', 'EM', 'STRONG', 'B', 'I', 'DEL', 'S', 'SUB', 'SUP', 'KBD',
  'UL', 'OL', 'LI', 'BLOCKQUOTE', 'H1', 'H2', 'H3', 'H4', 'H5', 'H6',
  'TABLE', 'THEAD', 'TBODY', 'TR', 'TH', 'TD', 'A',
])

/** Elements whose content is not text to show at all. */
const DROPPED = new Set(['SCRIPT', 'STYLE', 'TEMPLATE', 'IFRAME', 'OBJECT', 'EMBED', 'SVG', 'MATH', 'NOSCRIPT', 'TITLE'])

/**
 * Whether a link may keep its address: one another app opens (`MainActivity.openExternally`). Any
 * other — `javascript:`, a relative path, `intent:` — loses it and stays as text.
 */
export function safeHref(href: string): boolean {
  return /^(https?:|mailto:)/i.test(href.trim())
}

/**
 * [html] with only [KEPT] elements, each with only its `class` (the highlighted code's) and a link's
 * [safeHref] address. Parsed in a `<template>`, whose content is inert: nothing in it loads or runs
 * while it is being looked at.
 */
export function sanitizeHTML(html: string): string {
  const template = document.createElement('template')
  template.innerHTML = html
  clean(template.content)
  return template.innerHTML
}

function clean(parent: Node): void {
  for (const node of [...parent.childNodes]) {
    if (node.nodeType === Node.COMMENT_NODE) {
      node.remove()
      continue
    }
    if (node.nodeType !== Node.ELEMENT_NODE) continue
    const element = node as Element
    if (DROPPED.has(element.tagName)) {
      element.remove()
      continue
    }
    clean(element)
    if (!KEPT.has(element.tagName)) {
      element.replaceWith(...element.childNodes)
      continue
    }
    for (const attribute of [...element.attributes]) {
      const kept = attribute.name === 'class' || (element.tagName === 'A' && attribute.name === 'href' && safeHref(attribute.value))
      if (!kept) element.removeAttribute(attribute.name)
    }
  }
}

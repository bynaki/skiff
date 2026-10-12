// What the page adds to what the library sends a language server.
import { describe, expect, test } from 'vitest'
import { splitHover, withWorkspaceFolder } from '../src/lsp'

describe('the initialize request', () => {
  test('gets the project root as its workspace folder', () => {
    const message = JSON.stringify({ jsonrpc: '2.0', id: 0, method: 'initialize', params: { rootUri: 'file:///home/alice/%ED%95%9C', capabilities: {} } })
    const sent = JSON.parse(withWorkspaceFolder(message, 'file:///home/alice/%ED%95%9C'))
    expect(sent.params.workspaceFolders).toEqual([{ uri: 'file:///home/alice/%ED%95%9C', name: '한' }])
    expect(sent.params.rootUri).toBe('file:///home/alice/%ED%95%9C')
  })

  test('is the only message changed', () => {
    const initialized = JSON.stringify({ jsonrpc: '2.0', method: 'initialized', params: {} })
    // Words in a buffer are a JSON string, with their quotes escaped.
    const change = JSON.stringify({ jsonrpc: '2.0', method: 'textDocument/didChange', params: { text: '"method":"initialize"' } })
    expect(withWorkspaceFolder(initialized, 'file:///p')).toBe(initialized)
    expect(withWorkspaceFolder(change, 'file:///p')).toBe(change)
  })
})

describe('a hover split for the touch hover', () => {
  const md = (value: string) => ({ kind: 'markdown', value })

  test('takes the code block a pyright hover opens with as the signature, and drops its rule', () => {
    const hover = splitHover(md('```python\n(function) def dumps(obj: Any) -> str\n```\n---\nSerialize ``obj`` to a JSON formatted ``str``.\n\nIf ``skipkeys`` is true...'))
    expect(hover.signature).toEqual([md('```python\n(function) def dumps(obj: Any) -> str\n```')])
    expect(hover.docs).toEqual([md('Serialize ``obj`` to a JSON formatted ``str``.\n\nIf ``skipkeys`` is true...')])
  })

  test('is all signature when there are no docs', () => {
    const hover = splitHover(md('```python\n(variable) x: int\n```'))
    expect(hover.signature).toEqual([md('```python\n(variable) x: int\n```')])
    expect(hover.docs).toEqual([])
  })

  test('keeps the code blocks a hover opens with together', () => {
    const hover = splitHover(md('```python\n@overload\ndef f(a: int) -> int\n```\n\n```python\n@overload\ndef f(a: str) -> str\n```\nDocs.'))
    expect(hover.signature).toEqual([md('```python\n@overload\ndef f(a: int) -> int\n```\n```python\n@overload\ndef f(a: str) -> str\n```')])
    expect(hover.docs).toEqual([md('Docs.')])
  })

  test('has no signature when the markdown does not start with code', () => {
    const hover = splitHover(md('Some text\n```python\nx = 1\n```'))
    expect(hover.signature).toEqual([])
    expect(hover.docs).toEqual([md('Some text\n```python\nx = 1\n```')])
  })

  test('has no signature in plain text', () => {
    const hover = splitHover({ kind: 'plaintext', value: 'def f() -> None' })
    expect(hover.signature).toEqual([])
    expect(hover.docs).toEqual([{ kind: 'plaintext', value: 'def f() -> None' }])
  })

  test('takes the leading code of an array as the signature, and everything from the first text on as docs', () => {
    const hover = splitHover([{ language: 'typescript', value: 'const a: number' }, 'Docs.', { language: 'typescript', value: 'example()' }])
    expect(hover.signature).toEqual([md('```typescript\nconst a: number\n```')])
    expect(hover.docs).toEqual([md('Docs.'), md('```typescript\nexample()\n```')])
  })

  test('is empty for empty contents', () => {
    expect(splitHover('')).toEqual({ signature: [], docs: [] })
    expect(splitHover([])).toEqual({ signature: [], docs: [] })
  })
})

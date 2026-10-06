// What the page adds to what the library sends a language server.
import { describe, expect, test } from 'vitest'
import { withWorkspaceFolder } from '../src/lsp'

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

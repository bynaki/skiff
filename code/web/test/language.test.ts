// Which grammar a file name is opened under.
import { describe, expect, test } from 'vitest'
import { LanguageDescription } from '@codemirror/language'
import { languages } from '@codemirror/language-data'
import { jsonLinesAsJson } from '../src/layers/pane'

const languageOf = (name: string) =>
  LanguageDescription.matchFilename(languages, jsonLinesAsJson(name))?.name ?? null

describe('the language a name is matched to', () => {
  test('a .jsonl file is JSON, which language-data would not have said on its own', () => {
    expect(LanguageDescription.matchFilename(languages, 'events.jsonl')).toBe(null)
    expect(languageOf('events.jsonl')).toBe('JSON')
  })

  test('the names language-data already knows are untouched', () => {
    expect(languageOf('package.json')).toBe('JSON')
    expect(languageOf('README.md')).toBe('Markdown')
    expect(languageOf('main.ts')).toBe('TypeScript')
  })
})

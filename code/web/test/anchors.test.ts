import { describe, expect, test } from 'vitest'
import { anchorLine, anchors, decodeFragment, slug } from '../src/anchors'

describe('a heading’s name, as GitHub makes it', () => {
  test('lower case, spaces as hyphens', () => {
    expect(slug('Getting Started')).toBe('getting-started')
  })

  test('punctuation and symbols go, hyphens and underscores stay', () => {
    expect(slug('What’s new in v2.0?')).toBe('whats-new-in-v20')
    expect(slug('snake_case & kebab-case')).toBe('snake_case--kebab-case')
    expect(slug('`code` (in) [brackets]!')).toBe('code-in-brackets')
  })

  test('hangul stays as it is', () => {
    expect(slug('설치 방법')).toBe('설치-방법')
    expect(slug('M9. 마크다운 문서 링크 열기')).toBe('m9-마크다운-문서-링크-열기')
  })

  test('an emoji goes, leaving its space', () => {
    expect(slug('🚀 Launch')).toBe('-launch')
  })
})

describe('a repeated heading', () => {
  test('is numbered from 1', () => {
    expect(anchors(['Usage', 'Usage', 'Usage'])).toEqual(['usage', 'usage-1', 'usage-2'])
  })

  test('steps past a name a heading already has', () => {
    expect(anchors(['a', 'a', 'a-1'])).toEqual(['a', 'a-1', 'a-1-1'])
  })
})

describe('the line a fragment goes to', () => {
  const headings = [
    { text: 'Title', line: 1 },
    { text: '설치 방법', line: 5 },
    { text: 'Usage', line: 12 },
    { text: 'Usage', line: 20 },
  ]

  test('the heading it names', () => {
    expect(anchorLine(headings, 'usage')).toBe(12)
    expect(anchorLine(headings, 'usage-1')).toBe(20)
    expect(anchorLine(headings, '설치-방법')).toBe(5)
  })

  test('written in the heading’s own case', () => {
    expect(anchorLine(headings, 'Usage')).toBe(12)
  })

  test('none when no heading has the name', () => {
    expect(anchorLine(headings, 'missing')).toBeNull()
    expect(anchorLine([], 'usage')).toBeNull()
  })

  test('a fragment as markdown-it encodes it', () => {
    expect(decodeFragment('%EC%84%A4%EC%B9%98-%EB%B0%A9%EB%B2%95')).toBe('설치-방법')
    expect(decodeFragment('%E0%A4%A')).toBeNull()
  })
})

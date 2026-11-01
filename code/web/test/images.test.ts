import MarkdownIt from 'markdown-it'
import { describe, expect, test } from 'vitest'
import { type ImageEnv, imageLabel, imageUrl, images, missingImageHtml, topBlockIndex } from '../src/images'

const md = new MarkdownIt({ html: false }).use(images)
const render = (source: string, env: ImageEnv = { docId: 7 }) => md.render(source, env)

describe('where an image is asked for', () => {
  test('a path or an https address goes to Kotlin as written, by the open file', () => {
    expect(imageUrl(7, 'img/a.png')).toBe('/doc-image/7?src=img%2Fa.png')
    expect(imageUrl(7, '../a b.png')).toBe('/doc-image/7?src=..%2Fa%20b.png')
    expect(imageUrl(7, '/img/a.png?x=1&y=2')).toBe('/doc-image/7?src=%2Fimg%2Fa.png%3Fx%3D1%26y%3D2')
    expect(imageUrl(7, 'https://img.shields.io/badge/a-b-green')).toBe('/doc-image/7?src=https%3A%2F%2Fimg.shields.io%2Fbadge%2Fa-b-green')
    expect(imageUrl(7, 'HTTPS://example.com/a.png')).toBe('/doc-image/7?src=HTTPS%3A%2F%2Fexample.com%2Fa.png')
  })

  test('a data image is left as it is', () => {
    expect(imageUrl(7, 'data:image/png;base64,AAAA')).toBe('data:image/png;base64,AAAA')
  })

  test('plain http, other schemes, nothing to name and no open file are not asked for', () => {
    for (const src of ['http://example.com/a.png', 'file:///etc/a.png', 'javascript:alert(1)', 'content://a/b', '', '#top', '?x']) {
      expect(imageUrl(7, src)).toBeNull()
    }
    expect(imageUrl(undefined, 'img/a.png')).toBeNull()
  })
})

describe('the alt box', () => {
  test('says the alt text, or the file name without its query, decoded', () => {
    expect(imageLabel('구조도', 'img/a.png')).toBe('구조도')
    expect(imageLabel('', 'img/%EA%B5%AC%EC%A1%B0.png?raw=1#x')).toBe('구조.png')
    expect(imageLabel(' ', 'https://example.com/badge/')).toBe('badge')
    expect(imageLabel('', '%zz.png')).toBe('%zz.png')
  })

  test('escapes what it is given', () => {
    const html = missingImageHtml('<b>"x"</b>', 'a.png')
    expect(html).toContain('aria-label="&lt;b&gt;&quot;x&quot;&lt;/b&gt;"')
    expect(html).toContain('<span class="label">&lt;b&gt;&quot;x&quot;&lt;/b&gt;</span>')
    expect(html).not.toContain('<b>')
  })
})

describe('rendering', () => {
  test('an image is loaded lazily from the page’s origin, keeping its src as written', () => {
    expect(render('![구조도](img/a.png "제목")')).toBe(
      '<p><img src="/doc-image/7?src=img%2Fa.png" alt="구조도" title="제목" data-src="img/a.png" loading="lazy" decoding="async"></p>\n',
    )
  })

  test('markdown-it’s normalised src is what goes, as with a link', () => {
    expect(render('![](<my pic.png>)')).toContain('src="/doc-image/7?src=my%2520pic.png"')
    expect(render('![](그림.png)')).toContain('data-src="%EA%B7%B8%EB%A6%BC.png"')
  })

  test('the alt text is the plain text of what was written', () => {
    expect(render('![**굵은** `코드`](a.png)')).toContain('alt="굵은 코드"')
  })

  test('an image that is not read is the alt box from the start', () => {
    const html = render('![로고](http://example.com/a.png)')
    expect(html).not.toContain('<img')
    expect(html).toContain('<span class="label">로고</span>')
    expect(render('![](img/a.png)', {})).toContain('<span class="label">a.png</span>')
  })

  test('a data image is drawn as it is', () => {
    expect(render('![](data:image/png;base64,AAAA)')).toContain('src="data:image/png;base64,AAAA"')
  })

  test('an image in a link stays in the link', () => {
    expect(render('[![build](https://img.shields.io/b.svg)](https://ci.example.com)')).toMatch(
      /^<p><a href="https:\/\/ci\.example\.com"><img src="\/doc-image\/7\?src=[^"]+" alt="build"/,
    )
  })
})

describe('the block an image must not push away', () => {
  test('is the first that reaches below the top', () => {
    expect(topBlockIndex([40, 90, 200], 56)).toBe(1)
    expect(topBlockIndex([40, 90, 200], 30)).toBe(0)
    expect(topBlockIndex([40, 90, 200], 90)).toBe(2)
  })

  test('is the last when the screen is past them all, and none when there are none', () => {
    expect(topBlockIndex([40, 90], 300)).toBe(1)
    expect(topBlockIndex([], 56)).toBeNull()
  })
})

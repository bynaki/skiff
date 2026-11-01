// Images in rendered markdown (docs/skiffcode.spec/markdown-images.md). The page never reads one
// itself: an image's `src` becomes an address on the page's own origin naming the open file and the
// `src` as written, and Kotlin resolves that against the file and reads it. What will not be read
// at all is drawn as the alt box straight away, and what fails is turned into one by `markdown.ts`.
import type { Env, MarkdownIt } from 'markdown-it'

/** The first path segment Kotlin answers images on (`MainActivity.documentImage`). */
const IMAGE_PATH = 'doc-image'

/** What a render of one open file carries, for its images to be asked for by. */
export interface ImageEnv extends Env {
  docId?: number
}

/**
 * The address the page loads [src] from, or null when it is not read at all: plain http, any other
 * scheme, or no open file to resolve it against. A `data:` image is left as it is — markdown-it lets
 * only the raster ones through, and the CSP allows them.
 */
export function imageUrl(docId: number | undefined, src: string): string | null {
  const link = src.trim()
  const scheme = /^([a-z][a-z0-9+.-]*):/i.exec(link)?.[1]?.toLowerCase()
  if (scheme === 'data') return link
  if (scheme !== undefined && scheme !== 'https') return null
  if (docId === undefined || link === '' || link.startsWith('#') || link.startsWith('?')) return null
  // From the root, not relative: the page itself is under /assets/web/.
  return `/${IMAGE_PATH}/${docId}?src=${encodeURIComponent(link)}`
}

/** What the alt box says: the alt text, or the file's name when the document gave none. */
export function imageLabel(alt: string, src: string): string {
  if (alt.trim() !== '') return alt
  const path = src.trim().split(/[?#]/)[0].replace(/\/+$/, '')
  const name = path.slice(path.lastIndexOf('/') + 1)
  try {
    return decodeURIComponent(name)
  } catch {
    return name
  }
}

const escapeHtml = (text: string) =>
  text.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;')

// The line icons' shape (`topbar.ts`'s `svg()`), written out here so this module stays free of the DOM.
const BROKEN_IMAGE =
  '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">' +
  '<path d="M21 13V5a2 2 0 0 0-2-2H5a2 2 0 0 0-2 2v8M3 17v2a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-2"/><path d="M3 13l4-4 4 4 3-3 7 7"/><path d="M2 2l20 20"/></svg>'

/** The box an image that is not shown leaves in its place: the icon and [imageLabel]. */
export function missingImageHtml(alt: string, src: string): string {
  const label = escapeHtml(imageLabel(alt, src))
  return `<span class="image-missing" role="img" aria-label="${label}">${BROKEN_IMAGE}<span class="label">${label}</span></span>`
}

/**
 * Draws markdown-it's images as the page loads them. The `src` it hands over has been through its
 * link check and normalisation, which is what Kotlin resolves — the same `src` a link's `href` would
 * be. `data-src` keeps it for the alt box an image that fails turns into.
 */
export function images(md: MarkdownIt): void {
  md.renderer.rules.image = (tokens, idx, options, env, self) => {
    const image = tokens[idx]
    const src = String(image.attrGet('src') ?? '')
    const alt = self.renderInlineAsText(image.children ?? [], options, env)
    const url = imageUrl((env as ImageEnv | undefined)?.docId, src)
    if (url === null) return missingImageHtml(alt, src)
    const title = image.attrGet('title')?.toString()
    return `<img src="${escapeHtml(url)}" alt="${escapeHtml(alt)}"${title ? ` title="${escapeHtml(title)}"` : ''}` +
      ` data-src="${escapeHtml(src)}" loading="lazy" decoding="async">`
  }
}

/**
 * Which of the blocks, given their bottoms, is the one at the top of the screen: the first that
 * reaches below [top]. The last one when none does, and null when there are none.
 */
export function topBlockIndex(bottoms: number[], top: number): number | null {
  if (bottoms.length === 0) return null
  const index = bottoms.findIndex((bottom) => bottom > top)
  return index === -1 ? bottoms.length - 1 : index
}

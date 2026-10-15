// The names a link's `#fragment` finds a heading by, made the way GitHub makes them, since that is
// where the markdown being read was most likely written (docs/skiffcode.spec/markdown-links.md).
// Kept apart from `markdown.ts` so it is tested on node, which has no DOM.

/** A heading of the rendered document: its text as it reads, and the source line it starts on. */
export interface Heading {
  text: string
  line: number
}

/**
 * GitHub's name for one heading: lower case, with everything that is not a letter, a mark, a
 * number, a connector, a hyphen or a space taken out, and the spaces made hyphens. Hangul is
 * letters, so it stays.
 */
export function slug(text: string): string {
  return text.toLowerCase().replace(/[^\p{L}\p{M}\p{N}\p{Pc}\- ]/gu, '').replace(/ /g, '-')
}

/** Each heading's name in order, a repeat taking `-1`, `-2` after it as GitHub numbers them. */
export function anchors(texts: readonly string[]): string[] {
  // github-slugger's counting: a name already taken bumps its base's count until one is free, and
  // the free one is taken too, so `a`, `a`, `a-1` come out `a`, `a-1`, `a-1-1`.
  const taken = new Map<string, number>()
  return texts.map((text) => {
    const base = slug(text)
    let name = base
    while (taken.has(name)) {
      const count = taken.get(base)! + 1
      taken.set(base, count)
      name = `${base}-${count}`
    }
    taken.set(name, 0)
    return name
  })
}

/**
 * The source line of the heading [fragment] names, or null when none does. The fragment arrives
 * decoded; case is not held against it, since a link written by hand often keeps the heading's own.
 */
export function anchorLine(headings: readonly Heading[], fragment: string): number | null {
  const names = anchors(headings.map((heading) => heading.text))
  const wanted = fragment.toLowerCase()
  const at = names.indexOf(wanted)
  return at < 0 ? null : headings[at].line
}

/** A fragment as the page's `href` holds it, percent-encoded by markdown-it, as text; null when it does not decode. */
export function decodeFragment(fragment: string): string | null {
  try {
    return decodeURIComponent(fragment)
  } catch {
    return null
  }
}

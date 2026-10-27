// The sidebar's second tab, the outline of the file on the screen (docs/skiffcode.spec/outline.md).
// What is shown is split from how: a source turns the file into outline items, and the list draws,
// marks and goes to items without knowing what kind of file they came from. There are two sources:
// Markdown's headings, and a source file's symbols from its language server or its lezer tree.
import { LanguageDescription } from '@codemirror/language'
import { languages } from '@codemirror/language-data'
import type { LayerName } from './layers/pane'
import { read, write } from './storage'
import { type DocSymbol, type SymbolKind, outlineOf } from './symbols'

/** One line of the outline. All the list knows of what it shows. */
export interface OutlineItem {
  name: string
  kind: SymbolKind
  /** The source line it goes to, from 1. */
  line: number
  /** How far it is indented, from 0. */
  depth: number
  /** The last line of what it names, when the source knows it: a function's, not a heading's. */
  end?: number
}

/** The file an outline is asked for: which one it is, its name, and its buffer as it is now. */
export interface OutlineFile {
  id: string
  name: string
  text: string
  /**
   * Its language server's outline, only from a server that is up already: null at once from one that
   * is not, is starting, or cannot say, and then the buffer is read instead. Without it, the buffer.
   */
  serverOutline?: () => Promise<DocSymbol[] | null>
}

export interface OutlineSource {
  /** The sidebar's label for an outline with nothing in it, which is the source's to pick. */
  empty: 'noHeadings' | 'noSymbols'
  /** Its items, in the file's order. May take its time: a language server would have to be asked. */
  items(file: OutlineFile): Promise<OutlineItem[]>
}

/**
 * Markdown's headings, read from the lezer tree the palette's `@` mode reads them from, so the two
 * never disagree about what a heading is. The shallowest level in the document is the left edge, and
 * a skipped level stays skipped: `#` then `###` is two steps in, as written.
 */
export function headingItems(outline: DocSymbol[]): OutlineItem[] {
  const headings = outline.filter((symbol) => symbol.kind === 'heading')
  const top = Math.min(...headings.map((heading) => heading.level ?? 1))
  return headings.map((heading) => ({ name: heading.name, kind: heading.kind, line: heading.line, depth: (heading.level ?? 1) - top }))
}

const markdown: OutlineSource = {
  empty: 'noHeadings',
  items: async (file) => headingItems(await outlineOf(file.name, file.text)),
}

/**
 * A source file's symbols, indented by how deep they sit in the symbol tree: a method in a class is
 * one step in. Each keeps its end, by which the place on the screen is the innermost one it is in.
 */
export function symbolItems(outline: DocSymbol[], depth = 0): OutlineItem[] {
  return outline.flatMap((symbol) => {
    const item: OutlineItem = { name: symbol.name, kind: symbol.kind, line: symbol.line, depth }
    if (symbol.end !== undefined) item.end = symbol.end
    return [item, ...symbolItems(symbol.children, depth + 1)]
  })
}

/**
 * The server's outline when one is up to give it, and the file's lezer tree when not, without waiting
 * for a server to start (2026-10-27 사용자 결정): the sidebar opens on a list, not on a gap.
 */
const code: OutlineSource = {
  empty: 'noSymbols',
  items: async (file) => symbolItems((await file.serverOutline?.()) ?? await outlineOf(file.name, file.text)),
}

/** The languages `symbols.ts` reads a tree of, Markdown aside: the ones a source file's outline is for. */
const CODE = new Set(['TypeScript', 'JavaScript', 'TSX', 'JSX', 'Python'])

/** The source for a file named [name], or null when nothing gives it an outline. By name, as the pane picks its language. */
export function sourceFor(name: string): OutlineSource | null {
  const language = LanguageDescription.matchFilename(languages, name)?.name
  if (language === 'Markdown') return markdown
  return language && CODE.has(language) ? code : null
}

/** What is on the screen, as far as the tabs care: the file's name and the layer it is in. */
export interface Showing {
  name: string
  layer: LayerName
}

/**
 * Whether the outline tab can be picked. Not without a document or a source, and not in the diff
 * layer, where deleted lines sit between the source's lines and the place on the screen no longer
 * matches one of them.
 */
export function outlineEnabled(showing: Showing | null): boolean {
  return showing !== null && showing.layer !== 'diff' && sourceFor(showing.name) !== null
}

export type SidebarTab = 'tree' | 'outline'

const TAB_KEY = 'sidebar.tab'

/** The tab the sidebar was last left on. The tree when nothing, or nothing it could be, is held. */
export function rememberedTab(): SidebarTab {
  return read(TAB_KEY) === 'outline' ? 'outline' : 'tree'
}

export function rememberTab(tab: SidebarTab): void {
  write(TAB_KEY, tab)
}

/**
 * The tab to show: the one remembered, unless that is the outline and there is none to be had. What
 * is remembered stays as it is then, so the next Markdown file opens on its outline again.
 */
export function tabToShow(remembered: SidebarTab, showing: Showing | null): SidebarTab {
  return remembered === 'outline' && outlineEnabled(showing) ? 'outline' : 'tree'
}

/**
 * The item the top of the screen is in, or -1 for none. The top rather than the cursor, since the
 * viewer has no cursor and both layers ought to answer alike.
 *
 * An item without an end (a heading) is in until the next one starts: the last that starts on
 * [topLine] or before it. An item with one (a symbol) is in from its name to its end, and of those the
 * innermost: items come in the file's order with what is inside after what holds it, so that is the
 * last one found. Between symbols, among the imports or below a function, nothing is.
 */
export function currentItem(items: OutlineItem[], topLine: number): number {
  let current = -1
  for (const [index, item] of items.entries()) {
    if (item.line > topLine) break
    if (item.end === undefined || topLine <= item.end) current = index
  }
  return current
}

export interface Outline {
  /** What to draw: the last answer for this file, or null when there is none yet. */
  items(): OutlineItem[] | null
  /** Asks [source] for [file]'s items, and calls back when they are in. */
  load(source: OutlineSource, file: OutlineFile): void
  /** Forgets what was drawn, and any answer still on its way. */
  clear(): void
}

/**
 * Holds the outline the sidebar draws, for a source that may answer late. Until it does, the same
 * file keeps what was drawn for it and another file has nothing, rather than someone else's headings.
 * Only the latest question is answered: one asked before it may come back after it, and is dropped.
 */
export function createOutline(arrived: () => void): Outline {
  let drawn: { id: string; items: OutlineItem[] } | null = null
  let asked = 0

  return {
    items: () => drawn?.items ?? null,
    load(source, file) {
      const ticket = ++asked
      if (drawn && drawn.id !== file.id) drawn = null
      source.items(file).then((items) => {
        if (ticket !== asked) return
        drawn = { id: file.id, items }
        arrived()
      }).catch((error) => console.log(`outline: ${error}`))
    },
    clear() {
      asked++
      drawn = null
    },
  }
}

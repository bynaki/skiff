// What the palette's `@` mode can go to (docs/skiffcode.spec.md "심볼 검색"). With a language server
// behind the file it is the server's outline of the file, and below that what the server finds across
// the project for what is typed. Without one it is read from the file's lezer tree, and kept small on
// purpose — functions, classes, the methods in them, and Markdown headings, in three languages. The
// shape is the server's `DocumentSymbol` either way, so the palette does not know which it got.
import type { SyntaxNode } from '@lezer/common'
import { LanguageDescription } from '@codemirror/language'
import { languages } from '@codemirror/language-data'
import type { PaletteItem } from './palette'

/** `other` is a server's alone: a variable, a constant, a field, and the rest of what LSP names. */
export type SymbolKind = 'class' | 'function' | 'method' | 'heading' | 'other'

export interface DocSymbol {
  name: string
  kind: SymbolKind
  /** The line of the symbol's name, from 1. */
  line: number
  /** What is inside it: a class's methods. */
  children: DocSymbol[]
}

/** How one language's tree is read. [at] makes a symbol out of the node that holds its name. */
type Reader = (top: SyntaxNode, at: (node: SyntaxNode, kind: SymbolKind, children?: DocSymbol[]) => DocSymbol) => DocSymbol[]

const children = (node: SyntaxNode): SyntaxNode[] => {
  const all: SyntaxNode[] = []
  for (let child = node.firstChild; child; child = child.nextSibling) all.push(child)
  return all
}

/**
 * TypeScript and JavaScript. A function bound to a name (`const f = () => …`) is a function like a
 * declared one, since that is how much of this code base is written. A function declared inside a
 * function is left out: it is mostly noise.
 */
const script: Reader = (top, at) => {
  const statements = (node: SyntaxNode): DocSymbol[] => children(node).flatMap((statement) => {
    const name = statement.getChild('VariableDefinition')
    switch (statement.name) {
      case 'ExportDeclaration': return statements(statement)
      case 'FunctionDeclaration': return name ? [at(name, 'function')] : []
      // `export default class {}` has no name to find it by.
      case 'ClassDeclaration': return name ? [at(name, 'class', members(statement.getChild('ClassBody')!))] : []
      case 'VariableDeclaration': return bound(statement, 'VariableDefinition', 'function')
      default: return []
    }
  })
  const members = (body: SyntaxNode): DocSymbol[] => children(body).flatMap((member) => {
    if (member.name === 'MethodDeclaration') {
      const name = member.getChild('PropertyDefinition') ?? member.getChild('PrivatePropertyDefinition')
      return name ? [at(name, 'method')] : []
    }
    return member.name === 'PropertyDeclaration' ? bound(member, 'PropertyDefinition', 'method') : []
  })
  /** Each function in [node] under the name just before it: `const a = 1, b = () => 2` is `b`. */
  const bound = (node: SyntaxNode, definition: string, kind: SymbolKind): DocSymbol[] => {
    let name: SyntaxNode | null = null
    return children(node).flatMap((child) => {
      if (child.name === definition) name = child
      return name && (child.name === 'ArrowFunction' || child.name === 'FunctionExpression') ? [at(name, kind)] : []
    })
  }
  return statements(top)
}

/** Python. A decorator wraps the definition in a node of its own, which is looked through. */
const python: Reader = (top, at) => {
  const definitions = (node: SyntaxNode, inClass: boolean): DocSymbol[] => children(node).flatMap((statement) => {
    const definition = statement.name === 'DecoratedStatement'
      ? statement.getChild('FunctionDefinition') ?? statement.getChild('ClassDefinition')
      : statement
    const name = definition?.getChild('VariableName')
    if (!definition || !name) return []
    if (definition.name === 'FunctionDefinition') return [at(name, inClass ? 'method' : 'function')]
    if (definition.name === 'ClassDefinition') return [at(name, 'class', definitions(definition.getChild('Body')!, true))]
    return []
  })
  return definitions(top, false)
}

/** Markdown: the headings at the top level, `#` and underlined alike. A `#` in a code block is text. */
const markdown: Reader = (top, at) =>
  children(top).filter((node) => /^(ATX|Setext)Heading\d$/.test(node.name)).map((node) => at(node, 'heading'))

const READERS: Record<string, Reader> = {
  TypeScript: script,
  JavaScript: script,
  TSX: script,
  JSX: script,
  Python: python,
  Markdown: markdown,
}

/**
 * The outline of the file named [fileName] with [text] in it. A language with no reader here has
 * none yet. The parser is fetched the way the pane fetches it, as its own chunk, and the whole text
 * is parsed at once: the palette asks once each time it opens, not at every keystroke.
 */
export async function outlineOf(fileName: string, text: string): Promise<DocSymbol[]> {
  const language = LanguageDescription.matchFilename(languages, fileName)
  const read = language ? READERS[language.name] : undefined
  if (!language || !read) return []
  const tree = (await language.load()).language.parser.parse(text)

  // Where each line starts, so an offset in the tree becomes a line by a binary search.
  const starts = [0]
  for (let at = text.indexOf('\n'); at !== -1; at = text.indexOf('\n', at + 1)) starts.push(at + 1)
  const lineOf = (offset: number): number => {
    let low = 0
    let high = starts.length - 1
    while (low < high) {
      const middle = (low + high + 1) >> 1
      if (starts[middle] <= offset) low = middle
      else high = middle - 1
    }
    return low + 1
  }
  /** A heading's text is itself without its `#`s or underline. Everything else is named by its node. */
  const nameOf = (node: SyntaxNode): string => {
    if (!/Heading\d$/.test(node.name)) return text.slice(node.from, node.to)
    let name = ''
    let from = node.from
    for (const mark of node.getChildren('HeaderMark')) {
      name += text.slice(from, mark.from)
      from = mark.to
    }
    return (name + text.slice(from, node.to)).trim()
  }

  return read(tree.topNode, (node, kind, inside = []) => ({ name: nameOf(node), kind, line: lineOf(node.from), children: inside }))
}

/**
 * The palette's entries for [outline], in the file's order. What is inside a class is named through
 * it (`Pane.show`): a method's own name is often one many classes share.
 */
export function symbols(outline: DocSymbol[], goTo: (line: number) => void, within = ''): PaletteItem[] {
  return outline.flatMap((symbol) => {
    const name = within + symbol.name
    return [{ name, run: () => goTo(symbol.line) }, ...symbols(symbol.children, goTo, `${name}.`)]
  })
}

interface LspPosition { line: number; character: number }
interface LspRange { start: LspPosition; end: LspPosition }

/** A server's `DocumentSymbol`, as much of it as is read here. */
export interface LspDocumentSymbol {
  name: string
  kind: number
  selectionRange: LspRange
  children?: LspDocumentSymbol[]
}

/** A server's `SymbolInformation`, the shape `workspace/symbol` answers in. */
export interface LspSymbolInformation {
  name: string
  kind: number
  location: { uri: string; range: LspRange }
  containerName?: string
}

/** LSP's `SymbolKind` numbers, the ones that are told apart here. */
const LSP_CLASS = new Set([5, 10, 11, 23]) // class, enum, interface, struct
const LSP_METHOD = new Set([6, 9]) // method, constructor
const LSP_FUNCTION = 12
const LSP_VARIABLE = 13

/**
 * The outline a server answered `textDocument/documentSymbol` with, or null when it answered with the
 * flat list instead of the tree, which says less than lezer does. What is inside a function is left
 * out, as the lezer outline leaves it: pyright lists every parameter and local variable there. So is
 * a variable anywhere but at the top of the file, by the rule [projectSymbols] has to keep.
 */
export function outlineFromServer(answer: LspDocumentSymbol[] | LspSymbolInformation[] | null): DocSymbol[] | null {
  if (!answer) return []
  if (answer.some((symbol) => !('selectionRange' in symbol))) return null
  const convert = (symbol: LspDocumentSymbol): DocSymbol => {
    const kind: SymbolKind = LSP_CLASS.has(symbol.kind) ? 'class'
      : LSP_METHOD.has(symbol.kind) ? 'method'
        : symbol.kind === LSP_FUNCTION ? 'function' : 'other'
    const inside = kind === 'method' || kind === 'function' ? []
      : (symbol.children ?? []).filter((child) => child.kind !== LSP_VARIABLE).map(convert)
    return { name: symbol.name, kind, line: symbol.selectionRange.start.line + 1, children: inside }
  }
  return (answer as LspDocumentSymbol[]).map(convert)
}

/** Something the server found in another file of the project, and where. */
export interface ProjectSymbol {
  /** Named through what holds it, as the file's own outline is: `Greeter.hello`. */
  name: string
  /** Where it is from the project's root, for the palette to show beside it. */
  path: string
  uri: string
  /** 1-based, and the column in UTF-16 code units, as `openDefinition` takes them. */
  line: number
  col: number
}

/** A `file:` URI's path, or the URI itself when it does not decode. */
function pathOf(uri: string): string {
  try {
    return decodeURIComponent(uri.replace(/^file:\/\//, ''))
  } catch {
    return uri
  }
}

/**
 * What a server answered `workspace/symbol` with, less what is in [here] — the file on the screen,
 * whose own outline is already above it. The path is from [rootUri], or whole for a file outside it.
 *
 * A variable is kept only at the top of a file. pyright names a function's parameters and locals as
 * variables held by the function, and an attribute as one held by its class, and the flat list does
 * not say which kind of thing holds it; the locals are the many, so both go.
 */
export function projectSymbols(answer: LspSymbolInformation[] | null, here: string, rootUri: string): ProjectSymbol[] {
  const root = pathOf(rootUri).replace(/\/+$/, '') + '/'
  const herePath = pathOf(here)
  return (answer ?? []).flatMap((symbol) => {
    const path = pathOf(symbol.location.uri)
    if (path === herePath || (symbol.kind === LSP_VARIABLE && symbol.containerName)) return []
    const start = symbol.location.range.start
    return [{
      name: symbol.containerName ? `${symbol.containerName}.${symbol.name}` : symbol.name,
      path: path.startsWith(root) ? path.slice(root.length) : path,
      uri: symbol.location.uri,
      line: start.line + 1,
      col: start.character + 1,
    }]
  })
}

/** The palette's entries for [found], each showing which file it is in. */
export function projectItems(found: ProjectSymbol[], open: (symbol: ProjectSymbol) => void): PaletteItem[] {
  return found.map((symbol) => ({ name: symbol.name, elsewhere: symbol.path, run: () => open(symbol) }))
}

export interface ProjectSearch {
  /**
   * What the server found for [query]. Until it has answered, what it found for the query before,
   * which the palette's own ranking narrows to what still fits; [arrived] is called when the answer
   * is in. Nothing for nothing typed: a server answers an empty query with nothing, or with all of it.
   */
  find(query: string): ProjectSymbol[]
  /** Asks again from the next [find]: the palette has opened, or the file on the screen has changed. */
  forget(): void
}

/**
 * The server is asked as the query is typed, but one question at a time: while one is out, only the
 * latest query is kept, and asked once the answer is in. A server slower than the typing is asked
 * less often instead of falling further behind.
 */
export function createProjectSearch(ask: (query: string) => Promise<ProjectSymbol[]>, arrived: () => void): ProjectSearch {
  let known: { query: string; found: ProjectSymbol[] } | null = null
  let asking: Promise<ProjectSymbol[]> | null = null
  let next: string | null = null

  function send(query: string): void {
    const sent = ask(query).catch((error) => {
      console.log(`workspace symbols: ${error}`)
      return []
    })
    asking = sent
    void sent.then((found) => {
      // Forgotten since: this answer is about a palette that has closed, or a file that has gone.
      if (asking !== sent) return
      asking = null
      known = { query, found }
      const waiting = next
      next = null
      if (waiting !== null && waiting !== query) send(waiting)
      arrived()
    })
  }

  return {
    find(query) {
      if (query === '') return []
      if (known?.query === query) return known.found
      if (asking) next = query
      else send(query)
      return known?.found ?? []
    },
    forget() {
      known = null
      asking = null
      next = null
    },
  }
}

// What the palette's `@` mode can go to while no language server is behind the file
// (docs/skiffcode.spec.md "심볼 검색"): read from the file's lezer tree, and kept small on purpose —
// functions, classes, the methods in them, and Markdown headings, in three languages. A language
// server takes its place in M6, and the shape here is its `DocumentSymbol` so that the palette does
// not have to change when it does.
import type { SyntaxNode } from '@lezer/common'
import { LanguageDescription } from '@codemirror/language'
import { languages } from '@codemirror/language-data'
import type { PaletteItem } from './palette'

export type SymbolKind = 'class' | 'function' | 'method' | 'heading'

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

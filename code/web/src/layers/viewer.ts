// The viewer layer: a file read-only, as highlighted code with line numbers, or as rendered
// markdown. Both pinch-zoom through the same font size.
import { Compartment, EditorState } from '@codemirror/state'
import { EditorView, lineNumbers } from '@codemirror/view'
import { LanguageDescription, defaultHighlightStyle, syntaxHighlighting } from '@codemirror/language'
import { languages } from '@codemirror/language-data'
import { blockAtLine, renderMarkdown } from '../markdown'
import { holdBlock, holdLine, installPinchZoom } from '../zoom'

export interface TextDocument {
  name: string
  text: string
  /** 1-based, from a link's `?line=`. */
  line?: number
}

/** Shows [doc] in [parent] and returns what takes it down again. */
export function showDocument(parent: HTMLElement, doc: TextDocument): () => void {
  // By name, not content: language-data knows extensions and names such as Makefile.
  const language = LanguageDescription.matchFilename(languages, doc.name)
  return language?.name === 'Markdown' ? showMarkdown(parent, doc) : showCode(parent, doc, language)
}

function showCode(parent: HTMLElement, doc: TextDocument, language: LanguageDescription | null): () => void {
  const syntax = new Compartment()
  const view = new EditorView({
    parent,
    state: EditorState.create({
      doc: doc.text,
      extensions: [
        lineNumbers(),
        EditorState.readOnly.of(true),
        EditorView.editable.of(false),
        syntax.of([]),
        syntaxHighlighting(defaultHighlightStyle),
        EditorView.theme({
          '&': { height: '100%', fontSize: 'var(--code-font-size)' },
          '.cm-scroller': { fontFamily: 'monospace', lineHeight: '1.5', touchAction: 'pan-x pan-y' },
        }),
      ],
    }),
  })
  installPinchZoom(view.scrollDOM, holdLine(view))

  if (doc.line) {
    const line = view.state.doc.line(Math.min(Math.max(1, doc.line), view.state.doc.lines))
    view.dispatch({ effects: EditorView.scrollIntoView(line.from, { y: 'start' }) })
  }
  // Each language's parser is its own chunk, fetched the first time a file needs it.
  language?.load().then((support) => {
    if (!view.dom.isConnected) return
    view.dispatch({ effects: syntax.reconfigure(support) })
  }).catch((error) => console.log(`language ${language.name} did not load: ${error}`))

  return () => view.destroy()
}

function showMarkdown(parent: HTMLElement, doc: TextDocument): () => void {
  const scroller = document.createElement('div')
  scroller.className = 'markdown-scroller'
  const body = renderMarkdown(doc.text)
  scroller.append(body)
  parent.append(scroller)
  installPinchZoom(scroller, holdBlock(scroller, body))

  const block = doc.line ? blockAtLine(body, doc.line) : null
  // The scroller is positioned (see index.html), so offsetTop is measured from it.
  if (block) scroller.scrollTop = block.offsetTop

  return () => scroller.remove()
}

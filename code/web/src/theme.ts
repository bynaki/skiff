// The theme (docs/skiffcode.spec.md "설정과 테마"): one set of CSS variables that the menu, the
// sidebar, the palette and every layer read, so changing it changes all of them at once. Kotlin
// reads the theme file, checks each color and fills in what is missing from the bundled theme; what
// arrives here is a whole set. Until it does, the page wears `index.html`'s `:root`, which is the
// light theme — `theme.test.ts` holds the two to each other.
import { HighlightStyle, syntaxHighlighting } from '@codemirror/language'
import { tags } from '@lezer/highlight'
import { EditorView } from '@codemirror/view'

/** What `settings` carries under `theme`: colors keyed as the file spells them, `ui.background`. */
export interface Theme {
  dark: boolean
  colors: Record<string, string>
}

/** `ui.strong_background` → `--ui-strong-background`. */
export function variableOf(key: string): string {
  return `--${key.replace(/[._]/g, '-')}`
}

export function showTheme(theme: Theme): void {
  const root = document.documentElement
  for (const [key, color] of Object.entries(theme.colors)) root.style.setProperty(variableOf(key), color)
  // The browser's own parts — scrollbars, a text field's caret — follow this rather than the variables.
  root.style.colorScheme = theme.dark ? 'dark' : 'light'
}

/**
 * The code views' colors. Only variables, so a new theme reaches a view on the screen without the
 * view being reconfigured. A regular theme outranks CodeMirror's base theme, whose light rules these
 * replace one for one.
 */
export const editorTheme = [
  EditorView.theme({
    '&': { color: 'var(--editor-foreground)', backgroundColor: 'var(--editor-background)' },
    // The views draw no selection or cursor of their own (no `drawSelection`): these are the browser's.
    '.cm-content': { caretColor: 'var(--editor-cursor)' },
    '& ::selection': { backgroundColor: 'var(--editor-selection)' },
    '.cm-gutters': {
      color: 'var(--editor-gutter-foreground)',
      backgroundColor: 'var(--editor-gutter-background)',
      borderColor: 'var(--editor-gutter-border)',
    },
  }),
  // CodeMirror's `defaultHighlightStyle`, tag for tag, with each color a variable.
  syntaxHighlighting(
    HighlightStyle.define([
      { tag: tags.meta, color: 'var(--syntax-meta)' },
      { tag: tags.link, textDecoration: 'underline' },
      { tag: tags.heading, textDecoration: 'underline', fontWeight: 'bold' },
      { tag: tags.emphasis, fontStyle: 'italic' },
      { tag: tags.strong, fontWeight: 'bold' },
      { tag: tags.strikethrough, textDecoration: 'line-through' },
      { tag: tags.keyword, color: 'var(--syntax-keyword)' },
      { tag: [tags.atom, tags.bool, tags.url, tags.contentSeparator, tags.labelName], color: 'var(--syntax-atom)' },
      { tag: [tags.literal, tags.inserted], color: 'var(--syntax-literal)' },
      { tag: [tags.string, tags.deleted], color: 'var(--syntax-string)' },
      { tag: [tags.regexp, tags.escape, tags.special(tags.string)], color: 'var(--syntax-regexp)' },
      { tag: tags.definition(tags.variableName), color: 'var(--syntax-definition)' },
      { tag: tags.local(tags.variableName), color: 'var(--syntax-local)' },
      { tag: [tags.typeName, tags.namespace], color: 'var(--syntax-type)' },
      { tag: tags.className, color: 'var(--syntax-class)' },
      { tag: [tags.special(tags.variableName), tags.macroName], color: 'var(--syntax-macro)' },
      { tag: tags.definition(tags.propertyName), color: 'var(--syntax-property)' },
      { tag: tags.comment, color: 'var(--syntax-comment)' },
      { tag: tags.invalid, color: 'var(--syntax-invalid)' },
    ]),
  ),
]

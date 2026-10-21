// What `settings.toml` says about how text is shown (docs/skiffcode.spec/settings-theme.md). Kotlin
// reads and checks the file and hands over the part the page applies; the size limit and the poll
// are its own. The font goes to the whole page as a CSS variable. Tab size and wrapping go to a code
// view through a compartment, for the reason [codeFontSize] gives in `zoom.ts`: a line that changes
// height has to be measured again, and a changed configuration is what makes CodeMirror do that.
// The margins ride the same compartment, so all three code layers have the same ones
// (docs/skiffcode.spec/margins.md), and so does the nav bar, which widens the right one
// (docs/skiffcode.spec/nav-bar.md).
import { Compartment, EditorState, type Extension } from '@codemirror/state'
import { EditorView } from '@codemirror/view'
import { NAV_BAR_ROOM, navBar } from './navBar'
import { type Theme, showTheme } from './theme'

/** What Kotlin's `settings` answers and `settingsChanged` carries. */
export interface Settings {
  /** A CSS font family. */
  font: string
  /** Where Reset Zoom goes. */
  fontSize: number
  tabSize: number
  wrap: boolean
  /** How strongly the diff layer tints its lines, in percent. */
  diffAlpha: number
  /** Lines kept below the cursor, and the room under the last line. */
  scrolloff: number
  /** Characters kept right of the cursor, and the room after the longest line; only without `wrap`. */
  sidescrolloff: number
  /** Whether the code views show the nav bar at their right edge. */
  navBar: boolean
  /** How many files off the screen keep their buffer; see `memories.ts`. */
  keptBuffers: number
  /** How long a language server has to answer a request; see `lsp.ts`. */
  lspTimeoutMs: number
  /** Already the one for the device's dark mode, where `settings.toml` says to follow it. */
  theme: Theme
  /** Every name `theme` may take, imported themes included, for the palette's Theme commands. */
  themes: string[]
  /** Whether the theme on the screen is the person's own, which the palette can delete. */
  ownTheme: boolean
}

type Shown = Pick<Settings, 'tabSize' | 'wrap' | 'scrolloff' | 'sidescrolloff' | 'navBar'>

/** Until Kotlin answers: its defaults. */
let shown: Shown = { tabSize: 4, wrap: true, scrolloff: 5, sidescrolloff: 5, navBar: false }

const prefs = new Compartment()

/**
 * The room under the last line: [scrolloff] lines, in `lh` so a pinch grows it with the text. Not
 * `em`: the WebView's `textZoom` multiplies the font size but not a length written in `em`, so at
 * the Fold8's font scale 1.5 an `em` stayed 14px under 21px text and five lines left 3.3 of them.
 * A changed font size reaches it through `codeFontSize`'s compartment, which is what re-measures.
 */
export function bottomPadding(scrolloff: number): string {
  return `${scrolloff}lh`
}

/**
 * What a scroll that brings the cursor into view leaves below it, in px. Read at every scroll, so
 * it is the line height of the size the view is at now.
 */
export function scrollBottom(scrolloff: number, lineHeight: number): number {
  return scrolloff * lineHeight
}

/**
 * The characters a wrapped view leaves right of the cursor and after the longest line: none, since
 * nothing runs off to the right there, and room would only narrow every line (2026-10-20 user's
 * decision).
 */
export function sideChars(sidescrolloff: number, wrap: boolean): number {
  return wrap ? 0 : sidescrolloff
}

/**
 * The room after the longest line, in `ch`. Unlike `em`, it follows the WebView's `textZoom`:
 * measured on the tablet at font scale 1.5, `10ch` came to 126.0px over characters 12.6px wide.
 * [bar] px more while the nav bar is on, so no text goes under it or past the edge the scroller now
 * reaches beyond — a wrapped view too.
 */
export function rightPadding(chars: number, bar = 0): string {
  return bar > 0 ? `calc(${chars}ch + ${bar}px)` : `${chars}ch`
}

/** What a scroll that brings the cursor into view leaves right of it, in px; see [scrollBottom]. */
export function scrollRight(chars: number, charWidth: number, bar = 0): number {
  return chars * charWidth + bar
}

function bundle(settings: Shown): Extension {
  const { scrolloff } = settings
  const side = sideChars(settings.sidescrolloff, settings.wrap)
  const bar = settings.navBar ? NAV_BAR_ROOM : 0
  return [
    EditorState.tabSize.of(settings.tabSize),
    settings.wrap ? EditorView.lineWrapping : [],
    EditorView.theme({ '.cm-content': { paddingRight: rightPadding(side, bar), paddingBottom: bottomPadding(scrolloff) } }),
    // Below and right only (2026-10-20 user's decision): a scroll that puts a line at the top reads
    // only `top`, so a jump and a pinch's anchor land where they did.
    EditorView.scrollMargins.of((view) => ({
      bottom: scrollBottom(scrolloff, view.defaultLineHeight),
      right: scrollRight(side, view.defaultCharacterWidth, bar),
    })),
    settings.navBar ? navBar() : [],
  ]
}

/**
 * Puts [settings] on the page: the variables the rendered markdown and the code views read, and what
 * a code view built from now on starts with. A view already on the screen is brought to it with
 * [applyEditorSettings].
 */
export function showSettings(settings: Settings): void {
  shown = {
    tabSize: settings.tabSize,
    wrap: settings.wrap,
    scrolloff: settings.scrolloff,
    sidescrolloff: settings.sidescrolloff,
    navBar: settings.navBar,
  }
  const root = document.documentElement
  root.style.setProperty('--code-font', settings.font)
  root.style.setProperty('--tab-size', String(settings.tabSize))
  root.style.setProperty('--diff-alpha', String(settings.diffAlpha / 100))
  root.classList.toggle('wrap', settings.wrap)
  showTheme(settings.theme)
}

/** What a code view starts with. */
export function editorSettings(): Extension {
  return prefs.of(bundle(shown))
}

/**
 * Brings [view] to the settings the page is at: one on the screen when they change, and a buffer
 * back from the background, which carries the ones it was built with.
 */
export function applyEditorSettings(view: EditorView): void {
  view.dispatch({ effects: prefs.reconfigure(bundle(shown)) })
}

// What `settings.toml` says about how text is shown (docs/skiffcode.spec.md "설정과 테마"). Kotlin
// reads and checks the file and hands over the part the page applies; the size limit and the poll
// are its own. The font goes to the whole page as a CSS variable. Tab size and wrapping go to a code
// view through a compartment, for the reason [codeFontSize] gives in `zoom.ts`: a line that changes
// height has to be measured again, and a changed configuration is what makes CodeMirror do that.
import { Compartment, EditorState, type Extension } from '@codemirror/state'
import { EditorView } from '@codemirror/view'
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
  /** How many files off the screen keep their buffer; see `memories.ts`. */
  keptBuffers: number
  /** Already the one for the device's dark mode, where `settings.toml` says to follow it. */
  theme: Theme
  /** Every name `theme` may take, imported themes included, for the palette's Theme commands. */
  themes: string[]
  /** Whether the theme on the screen is the person's own, which the palette can delete. */
  ownTheme: boolean
}

type Shown = Pick<Settings, 'tabSize' | 'wrap'>

/** Until Kotlin answers: its defaults. */
let shown: Shown = { tabSize: 4, wrap: true }

const prefs = new Compartment()

function bundle(settings: Shown): Extension {
  return [EditorState.tabSize.of(settings.tabSize), settings.wrap ? EditorView.lineWrapping : []]
}

/**
 * Puts [settings] on the page: the variables the rendered markdown and the code views read, and what
 * a code view built from now on starts with. A view already on the screen is brought to it with
 * [applyEditorSettings].
 */
export function showSettings(settings: Settings): void {
  shown = { tabSize: settings.tabSize, wrap: settings.wrap }
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

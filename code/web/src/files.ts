// What the 🔍 mode of the palette can go to (docs/skiffcode.spec.md "커맨드 버튼과 팔레트"): in a
// single file, the other open files and then the files beside the one on the screen. A project's
// `git ls-files` takes the place of the second list in M5.
import type { OpenFile } from './chrome/openfiles'
import type { PaletteItem } from './palette'

export interface FileSource {
  /** Everything open, in the open files menu's order. */
  open(): OpenFile[]
  active(): number | null
  /** The files beside the active one that are not open, by name, as far as they are known yet. */
  beside(): string[]
  activate(id: number): void
  /** Opens the file of that name beside the active one. */
  openBeside(name: string): void
}

/**
 * The open files first, since going back to one is the commoner move, then the directory. The file
 * on the screen is left out: going to it would do nothing, and an entry that answers a tap with
 * nothing is worse than no entry. Kotlin leaves the open ones out of the directory, since only it
 * can tell that a name there is a file already open.
 */
export function files(source: FileSource): PaletteItem[] {
  const active = source.active()
  return [
    ...source.open()
      .filter((file) => file.id !== active)
      .map((file) => ({ name: file.name, run: () => source.activate(file.id) })),
    ...source.beside().map((name) => ({ name, run: () => source.openBeside(name) })),
  ]
}

export interface Folder {
  /**
   * What is beside [id] as far as it is known. The first ask starts the listing and answers
   * nothing; [arrived] is called when the names are in, for the palette to search again.
   */
  names(id: number): string[]
  /** Lists again at the next ask: the palette has opened, or what is open has moved. */
  forget(): void
}

/**
 * The directory listing is Kotlin's and, on a server, a round trip, while the palette asks for its
 * items every time the query changes — so the names are listed once per opening of the palette and
 * kept. A listing that fails answers nothing rather than being asked for again at every keystroke.
 */
export function createFolder(list: (id: number) => Promise<string[]>, arrived: () => void): Folder {
  let known: { id: number; names: string[] } | null = null
  let asking: Promise<string[]> | null = null
  let askingFor: number | null = null

  return {
    names(id) {
      if (known?.id === id) return known.names
      if (askingFor === id) return []
      askingFor = id
      const ask = list(id).catch((error) => {
        console.log(`folder: ${error}`)
        return []
      })
      asking = ask
      void ask.then((names) => {
        // Forgotten, or asked for another file since: this answer is about a screen that has gone.
        if (asking !== ask) return
        asking = null
        askingFor = null
        known = { id, names }
        arrived()
      })
      return []
    },
    forget() {
      known = null
      asking = null
      askingFor = null
    },
  }
}

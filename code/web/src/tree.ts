// The sidebar's tree (docs/skiffcode.spec.md "화면 메뉴"): every project is a line of its own with its
// files folded under it, and each directory is listed by Kotlin the first time it is unfolded.
//
// What is kept here is what is unfolded and what each directory held when it was last listed; how it
// looks is chrome/sidebar.ts's. A place is named by its project and a path relative to the root,
// '' for the root itself, which is all Kotlin takes: it refuses a path that would leave the root.

export interface ProjectInfo {
  id: string
  name: string
  /** The second line: the server and the root, for telling two projects of one name apart. */
  where: string
}

export interface TreeEntry {
  name: string
  directory: boolean
}

/** Where the file on the screen is, in its project. */
export interface Here {
  project: string
  path: string
}

/** A directory's contents, or what kept them from being read, in the user's language. */
export type Listing = { entries: TreeEntry[] } | { failed: string }

export type Row =
  | { kind: 'project'; project: ProjectInfo; open: boolean }
  | { kind: 'directory'; project: string; path: string; name: string; depth: number; open: boolean }
  | { kind: 'file'; project: string; path: string; name: string; depth: number; current: boolean }
  | { kind: 'listing'; depth: number }
  | { kind: 'empty'; depth: number }
  | { kind: 'failed'; depth: number; message: string }

export interface Tree {
  /**
   * What there is now, and where the file on the screen is: its project and the directories down to
   * it are unfolded, since the sidebar follows the file (2026-10-02 사용자 결정). A project that has
   * gone takes what was unfolded in it along.
   */
  set(projects: ProjectInfo[], here: Here | null): void
  /** Folds or unfolds a project (path '') or a directory, which is listed again as it unfolds. */
  toggle(project: string, path: string): void
  /**
   * Lists every unfolded directory again: things change on a server while nobody is looking. What
   * was listed before stays on the screen until the new answer is in.
   */
  refresh(): void
  rows(): Row[]
}

const keyOf = (project: string, path: string) => `${project}\u0000${path}`

const childOf = (path: string, name: string) => (path === '' ? name : `${path}/${name}`)

/** [changed] is called when a listing comes in, for the tree to be drawn again. */
export function createTree(list: (project: string, path: string) => Promise<Listing>, changed: () => void): Tree {
  let projects: ProjectInfo[] = []
  let here: Here | null = null
  const unfolded = new Set<string>()
  const listings = new Map<string, Listing>()
  const asking = new Map<string, Promise<Listing>>()

  function load(project: string, path: string): void {
    const key = keyOf(project, path)
    if (asking.has(key)) return
    const ask = list(project, path).catch((error): Listing => ({ failed: String(error) }))
    asking.set(key, ask)
    void ask.then((listing) => {
      // The project went while this was out, and what was asked about it with it.
      if (asking.get(key) !== ask) return
      asking.delete(key)
      listings.set(key, listing)
      changed()
    })
  }

  function unfold(project: string, path: string): void {
    unfolded.add(keyOf(project, path))
    load(project, path)
  }

  function children(project: string, path: string, depth: number): Row[] {
    const listing = listings.get(keyOf(project, path))
    if (!listing) return [{ kind: 'listing', depth }]
    if ('failed' in listing) return [{ kind: 'failed', depth, message: listing.failed }]
    if (listing.entries.length === 0) return [{ kind: 'empty', depth }]
    return listing.entries.flatMap((entry): Row[] => {
      const child = childOf(path, entry.name)
      if (!entry.directory) {
        const current = here?.project === project && here.path === child
        return [{ kind: 'file', project, path: child, name: entry.name, depth, current }]
      }
      const open = unfolded.has(keyOf(project, child))
      const row: Row = { kind: 'directory', project, path: child, name: entry.name, depth, open }
      return open ? [row, ...children(project, child, depth + 1)] : [row]
    })
  }

  return {
    set(next, nextHere) {
      projects = next
      const ids = new Set(next.map((project) => project.id))
      for (const map of [unfolded, listings, asking]) {
        for (const key of [...map.keys()]) if (!ids.has(key.slice(0, key.indexOf('\u0000')))) map.delete(key)
      }
      here = nextHere && ids.has(nextHere.project) ? nextHere : null
      if (!here) return
      const segments = here.path.split('/')
      let path = ''
      unfold(here.project, path)
      for (const segment of segments.slice(0, -1)) {
        path = childOf(path, segment)
        unfold(here.project, path)
      }
    },
    toggle(project, path) {
      const key = keyOf(project, path)
      if (unfolded.delete(key)) return
      unfold(project, path)
    },
    refresh() {
      for (const key of unfolded) {
        const cut = key.indexOf('\u0000')
        load(key.slice(0, cut), key.slice(cut + 1))
      }
    },
    rows() {
      return projects.flatMap((project): Row[] => {
        const open = unfolded.has(keyOf(project.id, ''))
        const row: Row = { kind: 'project', project, open }
        return open ? [row, ...children(project.id, '', 1)] : [row]
      })
    },
  }
}

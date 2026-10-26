// The sidebar's tree (docs/skiffcode.spec/top-menu.md): every project is a line of its own with its
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

/**
 * How a path differs from HEAD (2026-10-26 사용자 결정): `modified` where HEAD has it, `untracked` where
 * it does not. A folder takes `modified` when anything under it is, `untracked` when all of it is new.
 */
export type ChangeKind = 'modified' | 'untracked'

/** One path git's status names, relative to the root. A [directory] is a folder git does not track at all. */
export interface Change {
  path: string
  kind: ChangeKind
  directory: boolean
}

export type Row =
  | { kind: 'project'; project: ProjectInfo; open: boolean }
  | { kind: 'directory'; project: string; path: string; name: string; depth: number; open: boolean; change?: ChangeKind }
  | { kind: 'file'; project: string; path: string; name: string; depth: number; current: boolean; change?: ChangeKind }
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
   * Lists every unfolded directory again, and asks git again what changed in every project whose
   * root is unfolded: things change on a server while nobody is looking. What was listed and colored
   * before stays on the screen until the new answer is in.
   */
  refresh(): void
  rows(): Row[]
}

const keyOf = (project: string, path: string) => `${project}\u0000${path}`

const childOf = (path: string, name: string) => (path === '' ? name : `${path}/${name}`)

/**
 * What each path in a project shows, folders included: every change marks its own path and every
 * folder above it, `modified` winning over `untracked`. A folder git does not track is kept apart, as
 * everything under it is new.
 */
interface Marks {
  paths: Map<string, ChangeKind>
  newFolders: string[]
}

function marksOf(changes: Change[]): Marks {
  const paths = new Map<string, ChangeKind>()
  const newFolders: string[] = []
  for (const change of changes) {
    if (change.directory) newFolders.push(change.path)
    const segments = change.path.split('/')
    for (let end = segments.length; end > 0; end--) {
      const path = segments.slice(0, end).join('/')
      if (paths.get(path) === 'modified') continue
      paths.set(path, change.kind)
    }
  }
  return { paths, newFolders }
}

function markOf(marks: Marks | undefined, path: string): ChangeKind | undefined {
  if (!marks) return undefined
  return marks.paths.get(path) ?? (marks.newFolders.some((folder) => path.startsWith(`${folder}/`)) ? 'untracked' : undefined)
}

/**
 * [changed] is called when a listing or what git says comes in, for the tree to be drawn again.
 * [changesOf] answers null where git cannot be asked, and the tree is then drawn without colors.
 */
export function createTree(
  list: (project: string, path: string) => Promise<Listing>,
  changed: () => void,
  changesOf: (project: string) => Promise<Change[] | null>,
): Tree {
  let projects: ProjectInfo[] = []
  let here: Here | null = null
  const unfolded = new Set<string>()
  const listings = new Map<string, Listing>()
  const asking = new Map<string, Promise<Listing>>()
  const marks = new Map<string, Marks>()
  const askingMarks = new Map<string, Promise<Change[] | null>>()

  function ask(project: string): void {
    if (askingMarks.has(project)) return
    const answer = changesOf(project).catch((error): Change[] | null => {
      console.log(`changes: ${error}`)
      return null
    })
    askingMarks.set(project, answer)
    void answer.then((changes) => {
      // The project went while this was out.
      if (askingMarks.get(project) !== answer) return
      askingMarks.delete(project)
      if (changes) marks.set(project, marksOf(changes))
      else marks.delete(project)
      changed()
    })
  }

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
    const marked = marks.get(project)
    return listing.entries.flatMap((entry): Row[] => {
      const child = childOf(path, entry.name)
      const change = markOf(marked, child)
      if (!entry.directory) {
        const current = here?.project === project && here.path === child
        return [{ kind: 'file', project, path: child, name: entry.name, depth, current, ...(change && { change }) }]
      }
      const open = unfolded.has(keyOf(project, child))
      const row: Row = { kind: 'directory', project, path: child, name: entry.name, depth, open, ...(change && { change }) }
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
      for (const map of [marks, askingMarks]) {
        for (const project of [...map.keys()]) if (!ids.has(project)) map.delete(project)
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
      if (path === '') ask(project)
    },
    refresh() {
      for (const key of unfolded) {
        const cut = key.indexOf('\u0000')
        const path = key.slice(cut + 1)
        load(key.slice(0, cut), path)
        // A folded project is not asked: its server is not connected to for it (top-menu.md).
        if (path === '') ask(key.slice(0, cut))
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

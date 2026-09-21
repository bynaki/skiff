// How many of the open files keep their buffer while they are off the screen.
//
// A file that leaves the screen leaves its `EditorState` behind, and that is what makes coming back
// to it cost nothing: the same undo history, the same selection, the same parse tree. It is also
// the expensive part of an open file — the parse tree and the height map hang off it — so once
// enough files have been open, the ones that left the screen longest ago let theirs go. What stays
// is the text, the layer and the line, which cost nothing and are all the file needs to be built
// again; its text is already the buffer's, since a pane writes it back on the way out.
import { type PaneMemory, isDirty } from './layers/pane'

/**
 * How many buffers are kept for files that are off the screen. The file on the screen has its own,
 * held by the pane, and is not one of these. `settings.toml` (M4) is where this becomes the user's.
 */
export const RETAINED_BUFFERS = 30

/**
 * Lets go of the buffers of the files that left the screen longest ago, keeping [limit] of them.
 *
 * The map's own order is that order: a file is taken out of it when it comes to the screen and put
 * back at the end when it leaves, so walking from the end is walking from the most recently used.
 * A buffer that has been typed in is never the one let go — the file's text cannot bring back what
 * was typed — but it does take one of the [limit] places, because it is memory being held all the
 * same. A file that has never been to the editor has no buffer to let go of and takes no place.
 */
export function forgetOldBuffers(memories: Map<number, PaneMemory>, limit = RETAINED_BUFFERS): void {
  let room = limit
  for (const memory of [...memories.values()].reverse()) {
    if (!memory.state) continue
    if (room > 0) {
      room -= 1
      continue
    }
    if (isDirty(memory.state)) continue
    memory.state = null
  }
}

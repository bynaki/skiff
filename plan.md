# Next plan

## Context

Skiff currently does everything in the original brief except the one feature that was always
scheduled last: the preview viewer. Browsing, create/rename/delete, user-controlled split view,
and transfers in every direction are implemented and verified on-device against a real OpenSSH
server.

This plan covers the viewer, plus four gaps that building the first pass left behind. Three of
them are places where a promised behaviour was designed but never wired up, and they are worth
closing before adding surface area on top.

Read `CLAUDE.md` first — the toolchain constraints there have each broken the build once.

---

## 1. Preview viewer (the stated next feature)

The seams are already in place: `fs/FileKind` classifies by extension, and `PaneScreen`'s
`onOpen` hook currently hands files to an external app. Replacing that hook is the whole
integration point.

**Route.** Add `ViewerScreen(FileRef(sourceId, path))` to `ui/viewer/`. `WorkspaceViewModel`
resolves the `FileRef` through `SourceRegistry`, so the viewer reads through `FileSystem` and
never learns whether the file is local or remote — same as every other consumer.

**Getting the bytes.** Local files read directly. Remote files stream into
`context.cacheDir` first, because rendering wants random access and re-reading over SFTP per
scroll would be unusable. Cache by `sourceId + path + mtime + size` so an edited remote file is
re-fetched rather than served stale.

**Guard rails before reading.** `stat` first and refuse over ~2 MB for text (offer "open
externally" instead) — a syntax highlighter on a 200 MB log will hang the app. Detect binary by
scanning the first 8 KB for NUL bytes rather than trusting the extension, since `FileKind` is
extension-only and a mislabelled file must not render as mojibake.

**Encoding.** Decode UTF-8 with a replacement policy, and fall back to EUC-KR when that
produces replacement characters — Korean text files predate UTF-8 often enough to matter here.

**Rendering.** Verified current versions:

| Need | Library |
|---|---|
| Markdown | `com.mikepenz:multiplatform-markdown-renderer-m3:0.45.0` |
| Code highlighting | `dev.snipme:highlights:1.1.0` |
| Images | `io.coil-kt.coil3:coil-compose:3.6.2` |

Branch on `FileKind`: `MARKDOWN` renders, `CODE`/`TEXT` highlight (plain monospace when the
language is unknown — never fail to show the file because highlighting could not classify it),
`IMAGE` uses Coil, everything else keeps the current external-app behaviour.

**Viewer chrome.** Read-only in this pass. Word-wrap toggle, monospace, line numbers for code,
and a "render / source" switch for markdown. Editing is out of scope — it needs write-back,
conflict handling, and an unsaved-changes story, and should be its own plan.

Files: new `ui/viewer/`, plus the `onOpen` change in `ui/workspace/WorkspaceScreen.kt`.

---

## 2. Conflict policy is designed but not reachable

`ConflictPolicy` has four values. `TransferQueue.runJob` hardcodes `KEEP_BOTH` at both call
sites (`transfer/TransferQueue.kt:91,101`), so `ASK` and `OVERWRITE` are dead in production.
Every transfer onto an existing name silently produces `file (1).txt`, which is the safe default
but not what a file manager should only ever do.

`CopyEngine` already honours all three non-interactive policies and is tested for them, so this
is UI and plumbing only:

- A conflict dialog offering overwrite / skip / keep both, with an "apply to the rest" checkbox.
- `ASK` needs the engine to suspend mid-execution for an answer. Follow the `HostKeyPrompter`
  pattern — a process-scoped prompter with a `CompletableDeferred`, because a transfer can hit
  a conflict while no screen is on top.
- Carry the chosen policy on `TransferJob` so a resumed or queued job keeps its answer.

---

## 3. Dead interface surface: decide, then act

Two things exist but nothing uses them. Each should be either wired up or deleted — leaving them
is worse than either.

- **`FileSystem.freeSpace`** has zero callers. Intended use was a pre-flight check so a large
  transfer fails immediately instead of at 90%. Note `SftpFileSystem.freeSpace` returns null by
  design (the SFTP base protocol has no such call), so the check can only ever run for local
  destinations — which is still the common download case. Wire it into `CopyEngine.plan`, and
  raise `FsError.NoSpace` before any bytes move.
- **`androidx.window`** is declared in `libs.versions.toml` and imported nowhere. It was meant
  for snapping the split divider to the fold hinge on this device. Either implement that with
  `WindowInfoTracker`/`FoldingFeature` in `ui/workspace/SplitContainer.kt`, or drop the
  dependency. The device is a Z Fold 7, so this is real value, not decoration — but an unused
  dependency should not sit in the catalog either way.

---

## 4. Smaller items

- **`.kotlin/` is not gitignored.** It is a build artifact; add it.
- **Remote symlink icons are wrong.** `SftpFileSystem` sets `linkTargetIsDirectory = false` for
  every entry because resolving each link would cost a round trip per row. Tapping resolves
  correctly (fixed earlier), but a symlinked directory still shows a file icon. Options: resolve
  lazily as rows scroll into view, or mark symlinks distinctly and stop implying a type.
- **No permission editing.** `setPermissions` was in the original design and dropped. `FileNode`
  already carries `mode` and the properties dialog displays it; making it editable is a small
  addition to the interface plus `SFTPClient.chmod` / `Files.setPosixFilePermissions`.
- **Properties on remote shows no owner or group.** SFTP reports uid/gid; the dialog does not.

---

## Verification

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17
./gradlew :app:testDebugUnitTest
./gradlew :app:installDebug
```

- **Viewer**: unit-test the parts that are decisions, not rendering — size limit, binary
  detection, encoding fallback, cache key invalidation on changed mtime. Extend
  `SftpFileSystemTest` (real MINA SSH server) with a remote file opened through the viewer's
  fetch path, including one with Hangul content, to prove the cache round trip.
- **Conflict policy**: `CopyEngine` already has fake-filesystem coverage for overwrite / skip /
  keep-both; add cases for a mid-transfer `ASK` answer applying to the remainder.
- **On-device**: transfer a file whose name already exists on the far side and confirm each of
  the three choices behaves; open a markdown file, a source file, and a large log from both
  panes. Watch `adb logcat -s Skiff:V` — anything the UI swallows into a message is logged
  there, and silence is the signal.

## Explicitly not in this plan

Key-based and keyboard-interactive auth (`AuthMethod` extension points exist and throw today),
in-viewer editing, transfer resume after failure, search, drag-and-drop between panes, and
archive extraction. Each is its own plan.

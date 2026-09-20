# AGENTS.md

Guidance for coding agents working in this repository.

Skiff is an Android SSH file manager. The original brief: ordinary file operations (create,
move, delete), file transfer between phone and server, and a split screen with one side on each
so moving things between them is direct. A code/markdown preview viewer comes later. The
constraint that shapes every decision below is **nothing may be installed on the server**.

That constraint is what keeps a remote daemon out of Skiff Code too, and the reasoning is in
`plan.md`: these apps are distributed to other people, so auto-installing a resident process on
their servers is the thing the constraint forbids — while running a `git` the server already has
installs nothing. A daemon would not even replace `exec`, since SFTP cannot start one.

## Commands

`java` is not on PATH. Every Gradle invocation needs `JAVA_HOME`, or it fails with
"Unable to locate a Java Runtime":

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17

./gradlew :app:assembleDebug
./gradlew :app:installDebug          # pushes to the connected device
./gradlew :core:testDebugUnitTest    # the filesystem and SFTP tests live here
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug             # report: app/build/reports/lint-results-debug.xml
```

Single test class, or one method (backticked names are passed with their spaces):

```bash
./gradlew :core:testDebugUnitTest --tests "com.naki.skiff.FsPathTest"
./gradlew :app:testDebugUnitTest --tests "com.naki.skiff.CopyEngineTest.a symlink cycle terminates instead of recursing forever"
```

Gradle prints only a pass/fail summary. For per-test results, parse
`<module>/build/test-results/testDebugUnitTest/*.xml`.

`local.properties` is gitignored and must point at the SDK:
`sdk.dir=/opt/homebrew/share/android-commandlinetools`. The build needs `platforms;android-37.1`
and `build-tools;37.0.0`.

### On-device

```bash
adb devices                                  # get the serial
adb -s <serial> shell am start -n com.naki.skiff/.ui.MainActivity
adb -s <serial> logcat -s Skiff:V            # everything the UI swallowed into a message
```

`ui/Log.kt`'s `logFailure` is why remote failures are diagnosable at all — screens show one
line of text, the stack trace only reaches logcat under the `Skiff` tag. Keep new catch sites
calling it.

Two grants can be set from adb instead of tapping through Settings:

```bash
adb -s <serial> shell appops set com.naki.skiff MANAGE_EXTERNAL_STORAGE allow
adb -s <serial> shell pm grant com.naki.skiff android.permission.POST_NOTIFICATIONS
```

Screenshots on a foldable need an explicit display id, otherwise `screencap` writes a warning
to stdout instead of a PNG:
`adb -s <serial> shell dumpsys SurfaceFlinger --display-id`, then `screencap -d <id> -p`.
Only the display that is on answers; the other returns a uniform image of a few hundred bytes, so
check the file size rather than trusting the capture.

**On the foldable's cover display, `screencap` does not capture the WebView.** It returns a blank
or half-drawn frame often enough that a screenshot cannot be used to tell what the page is showing,
while the inner display captures normally. Read the DOM over DevTools instead. To tap something
there, native views (dialogs) come from `adb shell uiautomator dump /sdcard/ui.xml`, which prints
screen bounds; the page's own buttons do not appear in that dump, so take their
`getBoundingClientRect()`, multiply by `devicePixelRatio`, and add the WebView's y offset on screen
(the `android.webkit.WebView` node's bounds in the same dump — 110 on the cover display, since the
page's coordinates start below the status bar).

**A fold or unfold re-enumerates the device over USB**, so adb drops for a second or two and every
command in flight fails with "device not found". Wrap device work in a retry rather than reading
one failure as a result.

### Skiff Code (`:code`)

```bash
./gradlew :code:installDebug                 # runs buildWeb first; needs npm on PATH
./gradlew :code:testDebugUnitTest
./gradlew :code:lintDebug
adb -s <serial> shell am start -n com.naki.skiff.code/.ui.MainActivity
adb -s <serial> logcat -s SkiffCode:V        # bridge traffic and the page's console.log
```

The page is the viewer: it shows whatever document `MainActivity` holds, and is empty until a
link opens one. A link goes through `ui/OpenFlow` (dialogs, connection, `stat`, `TextLoader`), and
the page picks the result up over the bridge:

```bash
adb -s <serial> shell am start -a android.intent.action.VIEW -d "'skiffcode://user@host/path?line=3'"
```

The inner single quotes keep the device shell from splitting the link at `&`. A local file
needs the all-files grant (`appops set com.naki.skiff.code MANAGE_EXTERNAL_STORAGE allow`) and
takes `skiffcode:///storage/emulated/0/…`; a `content://` link needs `-t text/plain` and
`-n com.naki.skiff.code/.ui.MainActivity`.

To inspect the page itself, forward Chrome DevTools to the WebView:

```bash
PID=$(adb -s <serial> shell pidof com.naki.skiff.code)
adb -s <serial> forward tcp:9333 localabstract:webview_devtools_remote_$PID
# then http://127.0.0.1:9333/json for the page's webSocketDebuggerUrl
```

## Architecture

### Three modules

`:core` is a `com.android.library` holding everything both apps need: `fs/` and its two
implementations, `data/crypto/SecretStore`, and the `ServerProfile`/`KnownHost` models. `:app`
is Skiff, `:code` is Skiff Code. The packages stayed `com.naki.skiff.*` across the split, so
`:core` and `:app` share some of them — only the module a file sits in tells them apart.

`:core` keeps itself free of either app's storage and UI. `HostKeyGate` is written against the
`KnownHostStore` interface, which `:app`'s `SkiffStore` and `:code`'s `SkiffCodeStore` each
implement over their own file; `SshConnection` and `SshClientFactory` take a password rather than reaching
for the Keystore, which is also what lets them be tested on a plain JVM.

What stayed in `:app`: `transfer/` (the queue and its foreground service are Skiff's), `data/`'s
`SourceRegistry` and `SkiffStore`, and all of `ui/`.

`:core` publishes okio, sshj and kotlinx-serialization as `api` because they are in its own
signatures, so `:app` does not redeclare them. It also publishes `SftpTestServer` as a test
fixture, which is how `:app`'s `SftpTransferTest` drives `CopyEngine` across a real server
without a second copy of the server.

### `FileSystem` is the spine

`:core`'s `fs/FileSystem.kt` is the one abstraction everything else is written against. Panes
render it, `transfer/CopyEngine` copies between two of them, and the planned preview viewer will
read through it. Nothing above `fs/` knows whether it is looking at the phone or an SSH server —
which is what makes device↔server, server↔device and server↔server a single code path.

Two implementations: `fs/local/LocalFileSystem` (`java.io.File`) and `fs/sftp/SftpFileSystem`.
Streams are okio `Source`/`Sink` on both sides so `CopyEngine` never branches on which is which.
All paths are absolute POSIX and go through `fs/FsPath`. Implementations translate their native
exceptions into `fs/FsError`, so UI code never matches on `SFTPException` or `IOException`.

**When adding an operation, add it to the interface and both implementations.** A capability
that only exists on one side breaks the symmetry the whole design rests on.

### The SFTP side, and what it must not do

Only the SFTP subsystem that OpenSSH already ships. **Skiff (`:app`) has no `exec` channel and
must not grow one** — no `rm -rf`, no `cp -r`, no `du`. Recursive delete lives in
`SftpFileSystem.deleteTreeBlocking`, recursive copy in `CopyEngine.walk`, both client-side.

What that buys is an account with no shell: Skiff works against `internal-sftp`, where an `exec`
request is refused outright. It is a separate thing from "nothing to install on the server",
which `exec` does not violate on its own — running a `git` the server already has installs
nothing. Earlier revisions of this file gave the second reason for the first rule; the rule
stands, the reason was wrong.

Skiff Code (`:code`) is allowed `exec`, because git and language servers need it. It stays
confined: M5 puts it in `RemoteExec` alone, on its own `SSHClient`, in project mode only, with
every argument quoted through `ShellQuote`, and a refusal is handled by opening the project
without git and LSP rather than failing. There is no `exec` in the tree yet — M0 reached a real
language server through one and then deleted the harness. **The first one to land belongs in
`RemoteExec`; a call site anywhere else is a design change, not an implementation detail.**

`SFTPClient` is not thread safe, so `SshConnection` pins every call to a single-threaded
dispatcher it owns. Each profile gets **two** connections, browse and transfer, so a large
upload cannot block a directory listing.

`SFTPException extends IOException`. `SshConnection.withSftp` catches `IOException` to drive
reconnect-and-retry and **must keep re-throwing `SFTPException` untouched** — a status reply
(no such file, already exists, permission denied) is the server answering, not the link dying.
Catching it here churns the session on every ordinary error and reports the wrong one.

Host keys are trust-on-first-use through `fs/sftp/HostKeyGate`, which sshj calls from its
transport thread and expects a blocking answer, so the suspending prompt is bridged with
`runBlocking`. Never replace the verifier with `PromiscuousVerifier`. Anything that gate reads
must be a real read — `SkiffStore.knownHost` uses `data.first()`, not `updateData`, because
DataStore's write path taken from a blocked transport thread is a stall waiting to happen.
Fingerprints are SHA256 (`fs/sftp/HostKeyFingerprint`) because that is what `ssh` and
`ssh-keygen` print; sshj's own helper returns MD5 hex, which nobody can compare.

### Ownership: process vs screen

`SkiffContainer` (built in `SkiffApplication.onCreate`, reached via `Application.skiff`) owns
`SkiffStore`, `SourceRegistry`, `HostKeyPrompter` and `TransferQueue`. These are deliberately
**not** in a ViewModel: a transfer has to survive the screen going away, so `WorkspaceViewModel`
must never close the registry in `onCleared`. `TransferService` is a foreground service that
carries the progress notification; the queue runs one job at a time on purpose.

`HostKeyPrompter` is process-scoped for the same reason — a background transfer can meet an
unknown key with no screen on top, and the answer arrives when the UI returns.

### UI

`WorkspaceViewModel` holds two `PaneController`s (A and B). Split view is **the user's choice**,
not a window-size decision: enabled, direction and ratio are all explicit and persisted. A
single full-screen pane is the default. Server profiles, known hosts and layout settings all
live in one JSON `SkiffData` blob behind `data/store/SkiffStore` — there is no Room and no KSP.

Both apps open their blob through `:core`'s `jsonDataStore`, once per process. **A file that no
longer decodes is copied to `<name>.corrupt-<millis>` before the store starts over empty.** Before
that copy existed, the next write destroyed every profile and every accepted host key, so each
server came back as *new* rather than *changed* and the host key prompt asked to trust a key it
should have warned about. The realistic trigger is ours, not the disk's: the JSON names
`AuthMethod`'s subclasses by their full class name, so renaming one, or changing a field's type,
turns every stored file into one that does not decode. Adding a field is safe.

Passwords are encrypted by `data/crypto/SecretStore` with an Android Keystore key and decrypted
per connect. `SshConnection` takes host/port/user and a password *supplier*, deliberately
knowing nothing about the Keystore; `SourceRegistry` owns the profile→connection wiring. That
separation is what lets the SFTP layer be tested on a plain JVM. `SourceRegistry` is held to
the same rule for the same reason — it takes the local source's name and a factory for the host
key gate, not a `Context` and the store — and it publishes the source list itself rather than
letting a second reader derive one from the store. Two readers of `store.profiles` drifted
apart once already, and a descriptor the registry cannot resolve is an `error()` when picked,
not a stale menu entry. Its map and profile list are guarded by one lock, because `getOrPut` is
not atomic and two panes opening one server at the same moment would otherwise log in twice.
**Never hold that lock across `close()`** — closing disconnects on the calling thread, so the
doomed filesystems are collected under it and closed outside.

**Do not swallow `CancellationException`.** `runCatching` around a suspending call catches it
too, which renders a cancelled load's cancellation message as a user-facing error. Catch
`CancellationException` and rethrow before the general handler.

### Links that arrive from outside

`skiffcode://` is `BROWSABLE`, so any app — and any web page — can send one. **A link's path is
never opened without the user seeing it, unless the sender is Skiff.** Matching an `alias` decides
only *which server*; the path stays the link's own, so a link that guesses a profile's name would
otherwise reach any file on that server with the stored credentials. `OpenFlow.confirmPath` is the
gate, and it lists the `OpenRequest` kinds one by one rather than falling through an `else`, so a
new kind has to answer the question. `content://` is not in scope: it is a grant the sending app
handed us, not a path we chose.

**Identify the sender with `ComponentCaller`, never `getReferrer()`.** The caller fills in
`EXTRA_REFERRER` itself, so any app can claim to be Skiff; `ComponentCaller.getPackage()` and
`getLaunchedFromPackage()` are answered by the framework, and a caller chooses only *whether* to
reveal itself (`ActivityOptions.setShareIdentityEnabled`), never *what* it is — which is why Skiff
passes those options when it starts Skiff Code, and why the check still ends in `checkSignatures`.
`onNewIntent` must read `getCurrentCaller()`, not `getInitialCaller()`: a link arriving in a
running instance is exactly where the two differ, and the launcher's trust must not carry over to
it. Below Android 15 there is no `ComponentCaller` and every link is confirmed.

## Toolchain constraints

These are non-obvious and each one has already broken the build or the app:

- **AGP 9 has Kotlin built in.** Applying `org.jetbrains.kotlin.android` fails the build. Only
  the `compose` and `serialization` plugins are applied.
- **KSP/Room are not usable** at Kotlin 2.4.20; that is why storage is kotlinx.serialization.
- `compileSdk = 37` with `compileSdkMinor = 1`, because the installed platform is `android-37.1`.
- **sshj requires slf4j at runtime** — it resolves a logger inside `DefaultConfig`'s
  constructor. Excluding it makes the first connection die with `NoClassDefFoundError`.
- **Test dependencies must not include a library the app lacks.** The slf4j omission above
  survived a full integration suite because the tests pulled `slf4j-simple` in themselves.
- **`ACCESS_LOCAL_NETWORK` is required on Android 17** to reach private-range addresses.
  Without it the connection is dropped rather than refused: a 15 second stall and a timeout
  with nothing pointing at a permission. `fs/LocalNetworkAccess` gates the request on the host
  actually being local.
- BouncyCastle is re-registered in `SkiffApplication` (and `SkiffCodeApplication`) because Android
  ships a cut-down provider under the same `"BC"` name.
- **Do not authenticate with sshj's `authPassword`.** After a refused password it tries
  keyboard-interactive on the same connection, which macOS's sshd never answers, so a mistyped
  password waited out the 30 second read timeout. `SshClientFactory` uses keyboard-interactive only
  when the server does not offer the password method at all.
- **sshj bounds the key exchange with the transport's timeout, not the socket's.**
  `SSHClient.setTimeout` (the `timeout` property) is `SocketClient`'s and reaches only
  `SO_TIMEOUT`; `KeyExchanger.waitForDone` asks `TransportImpl.getTimeoutMs()`, which defaults
  to 30 seconds no matter what the socket was given. Since `HostKeyGate` holds the transport
  thread while the user answers, that default is also how long the host key dialog has to live:
  past it the connection dies, the dialog disappears and the user is told the connection was
  lost. `SshClientFactory` therefore raises `client.transport.timeoutMs` for the connect and
  puts it back to the read timeout once the key exchange is through.
- **A refused host key arrives as a plain `TransportException`,** whose message is
  "Could not verify `<type>` host key with fingerprint ... ". `SshConnection.withSftp` must
  translate before it decides whether to reconnect — a check written against the raw
  `IOException` never matches `FsError.HostKeyRejected`, and the retry then puts the same
  dialog up a second time.

These apply to `:code` only:

- **The Gradle daemon must have `npm` on its PATH.** `:code:preBuild` depends on `buildWeb`, which
  shells out to npm. node is installed with fnm, so the path differs per shell and is deliberately
  not written into the build script. A daemon started from a terminal is fine; Android Studio is
  not, and needs its own answer.
- **ktoml needs the serialization compiler plugin, not KSP**, which is why it works here when Room
  does not. `:code` applies `kotlin-serialization` for it.
- **TypeScript 7's `tsc` treats a file with no import or export as a global script**, so a
  top-level `status` collides with `window.status`.
- **`@codemirror/view` 6.43.12 grows `viewportLineBlocks` to thousands of off-screen lines** when a
  block widget sits on a line boundary, which every gutter then renders. `code/web/src/diff.ts`
  patches the prototype at runtime for the spike; M5 does it with `patch-package`. Check
  `HeightMapBranch.forEachLine` for a clamp when raising the CodeMirror version, and drop the patch
  once it is there.
- **A layer is an extension bundle in a `Compartment`, never its own `EditorView`.** Swapping one
  keeps every state field that stays in the configuration (`StateField.slot(...).reconfigure` hands
  the old value over) and creates only what the layer being entered adds, so the document, the parse
  tree, the height map and the undo history are shared — `history()` therefore lives outside the
  compartment. `unifiedMergeView` is a plain extension array whose `ChunkField.init` builds against
  `state.doc`, so the diff layer joins the same view and compares against the buffer being edited.
  Measured on the tablet with a 2 MB file: switching layers leaves the JS heap at 23.1 MB, unchanged
  within `performance.memory`'s 0.1 MB resolution. Separate views would mean a parse tree and height
  map per layer, multiplied again by M3's LRU of open files.
- **`display: none` does not hide a CodeMirror editor.** Its base theme sets
  `display: flex !important` on `.cm-editor` (and on `.cm-scroller`), so an inline `display: none`
  loses and the editor keeps its height. In the markdown pane, where the rendered document and the
  editor are two DOMs, that left the code standing below the rendered document, reachable by
  scrolling past its end. Hiding one needs
  `dom.style.setProperty('display', 'none', 'important')`, and showing it again `removeProperty`.
- **A focused editor breaks a pinch that listens below the document.** While the caret is in the
  contenteditable, Chrome gives the second finger's `touchstart` to `<html>` instead of the element
  it landed on (the `touchmove`s that follow arrive normally), so a listener on the scroller never
  sees the gesture start and the pinch is dead in the editor layer but fine in the viewer.
  `installPinchZoom` therefore listens on the document, and a pane installs exactly one, asking its
  `hold` which surface is showing.
- **A document shorter than the screen never re-measures when only CSS changes the font size.** The
  measure pass re-reads line heights when the theme facet changed, when a refresh was asked for, or
  when `.cm-content`'s box changed height — and the base theme gives that box `min-height: 100%`
  inside a flex scroller, so a short document holds the scroller's height at every size and none of
  the three fires (`contentDOMHeight` and the rect both stayed 675 across a zoom on the tablet). The
  height map then keeps the old line heights while the DOM grows: content lines are laid out by CSS
  and look right, but anything written from the height map does not. The line-number gutter writes
  its element heights as inline pixels, so it kept the old spacing under numbers that had grown.
  `requestMeasure()` does not help. A long file hides this, since its box does track the font.
  **A font size therefore rides a `Compartment` (`codeFontSize`), not `--code-font-size`** — the
  variable stays for the markdown surface and the menu — and anything else read from the height map
  (folding, the M5 diff gutter) inherits the fix rather than meeting it again.
- **`@codemirror/lsp-client` converts positions as UTF-16 code units and never negotiates
  `positionEncoding`.** It advertises no `general.positionEncodings`, which by the spec obliges the
  server to use UTF-16, and pyright does. A server that counts UTF-8 bytes anyway would put every
  diagnostic in the wrong place on a line with Korean text, so `LspManager` still checks the
  `initialize` reply rather than trusting it.
- **`org.json` is part of the Android framework, so the unit test JVM gets a stub** whose every
  method throws "not mocked". A test that touches it needs `org.json:json` as a test dependency.
- **Android's `EUC-KR` decoder is wider than the JVM's.** It takes UHC's extra hangul, `80` as
  U+0080, and `FF` and the user-defined rows as private-use characters, all of which the JVM
  refuses. `TextLoader` checks EUC-KR's byte shape itself before decoding; a unit test passing on
  the JVM says nothing about what the device's charsets accept.
- **`org.json` writes `/` escaped as `\/`.** Valid JSON, but it means a message carrying an LSP
  method name cannot be matched as a substring — parse it.
- **`Configuration.keyboard` does not see every hardware keyboard.** On the tablet it stayed
  `nokeys` with a Bluetooth keyboard connected and typing (`am get-config` said
  `keysexposed-nokeys` while `dumpsys input` listed the keyboard enabled), so a check written
  against it answers no to a keyboard that is right there. Ask `InputDevice` instead — a device
  that is not `isVirtual`, whose `keyboardType` is `KEYBOARD_TYPE_ALPHABETIC`, and that supports
  `SOURCE_KEYBOARD` — and follow changes with `InputManager.InputDeviceListener`, not
  `onConfigurationChanged`.
- **Connecting a keyboard moves `navigation` as well as `keyboard`**, because the keyboard reports
  a d-pad: `keysexposed-nokeys-navhidden-nonav` became `keysexposed-qwerty-navexposed-dpad`. Both
  are in `configChanges` for that reason.
- **A configuration change the activity does not handle re-opens the link.** The activity is
  destroyed and rebuilt with the same intent, and `onCreate` runs `handleLink` against it again:
  the file opens a second time, reconnecting and asking about the path, and the caller identity
  does not survive, so even a file Skiff sent comes back with the confirmation dialog.
  `onRetainNonConfigurationInstance` now carries the open document across and `onCreate` skips the
  link when it arrives, which makes a recreation survivable — but **only the document is carried,
  not the layer or the scroll**, so `uiMode`, locale and font scale (none of them listed) still
  drop the reader back into the viewer. `fontScale` is the way to trigger one on purpose:
  `adb shell settings put system font_scale 1.3`, then put it back.
- **The WebView multiplies the system font scale into the page.** At the phone's `font_scale` 1.5
  the editor's 14px default computed to 21px, so `MIN_FONT_SIZE`/`MAX_FONT_SIZE` (8–40) are really
  12–60 there. `--code-font-size` still reads 14: the multiplier is `WebSettings.textZoom`, applied
  below the page, and nothing in the page can see it.
- **Entering the editor layer must not focus the view unless a hardware keyboard is attached.**
  Focus is what raises the soft keyboard, and on a phone it covers half the document before the
  user has asked to type. The first tap on the text focuses it. A tablet with an external keyboard
  hides this, which is why it was not found until the foldable.
- **The soft keyboard shrinks the WebView, and CodeMirror does not chase the caret.** The ime inset
  goes into the frame (`MainActivity`), so the viewport gets shorter without the selection moving,
  and CodeMirror only scrolls the caret into view when the selection changes. `pane.ts` listens for
  `resize` and asks with `y: 'nearest'`.

## Testing

`:core`'s `SftpFileSystemTest` runs the production SFTP code against a **real Apache MINA SSH
server** on a random port over a real socket (`SftpTestServer`, a test fixture so `:app` can
reach it too). This is the highest-value test in the repo — it has already caught a protocol
assumption a mock would have agreed with. Prefer extending it over mocking sshj. `:app`'s
`SftpTransferTest` is the same server with `CopyEngine` on top.

`SourceRegistryTest` covers that registry contract on a plain JVM: what the picker offers,
`get` resolves.

`FakeFileSystem` is an in-memory `FileSystem` used to drive `CopyEngine`, including symlink
cases (dereferenced file, followed directory, cycle that must terminate). It follows POSIX
semantics deliberately: `stat` and `list` resolve links, a listing reports the link itself.

End-to-end against a real server means enabling Remote Login on macOS
(System Settings → General → Sharing) and registering it as a profile in the app.

## Before committing

Read the diff before every commit, looking for two things.

**Anything that identifies this machine or its network.** Host names, IP addresses, SSIDs,
account names, passwords, private keys, host key fingerprints, absolute paths carrying a user
name. This has already gone wrong once: a real server and the network around it reached the
repository and had to be taken back out of it. Fixtures and docs use addresses reserved for
documentation (`192.0.2.0/24`, RFC 5737) and throwaway credentials that authenticate nothing
outside the test JVM, the way `SftpTestServer` does. Screenshots, logcat excerpts and dumps of
`SkiffData` carry the same details as plainly as source does. `local.properties` is gitignored;
keep it excluded rather than sanitizing it by hand.

**Whether the change weakens what guards a connection.** The host key gate, `SecretStore`'s
Keystore encryption and the confinement of `exec` are each one edit away from being undone, and
none of them fails a test when they are. A diff touching `fs/sftp/`, `data/crypto/`, a permission
in the manifest, or anything that starts a remote command earns a second read for that reason
alone.

Once both reads come back clean, **ask the user before committing** — every commit, including
documentation-only ones and the hand-off. Say what goes into it (the files and a one-line summary)
and wait for a yes. A "go ahead" for the work itself is not a yes to commit it, and a yes to one
commit does not carry over to the next. The user often steps away, so send the push notification
with the question; do not commit while waiting.

**Ask again before pushing.** A yes to a commit is not a yes to push it. Once commits are made,
say how many the branch is ahead and ask; push only on an explicit yes, and a yes to one push
does not carry over to the next.

## Not yet built

The preview viewer (code/markdown) is deliberately absent from Skiff. It is being built as a
separate app, **Skiff Code** (viewer / editor / diff layers on CodeMirror 6 in a WebView, remote
projects with git and LSP), in this same repository. `fs/FileKind` and the `onOpen` hook in
`PaneScreen` are the seams Skiff keeps for it. Tapping code, text or markdown sends a
`skiffcode://` link built by `:core`'s `link/SkiffCodeLink` (`SkiffCodeLinkTest` in `:code` parses
it back); anything else, or everything when Skiff Code is not installed, goes to an external app,
local files only. Key and keyboard-interactive auth are extension points on `AuthMethod` — only password is
implemented.

Skiff Code relaxes one rule above, under the terms written there: `exec` is Skiff Code's to use
and never `:app`'s.

**M0 settled that all of it fits in one WebView**, on the tablet, against a 2 MB file: scrolling
holds 86–91 fps, a pinch-zoom step costs 3 ms to dispatch and 8 ms to measure, a line-level
unified diff takes about 240 ms, and `@codemirror/lsp-client` runs over the Kotlin bridge with
diagnostics 252 ms after `didOpen`. The measurements and what they changed in the design are in
`plan.md`. What is left of the spike under `:code/` is `web/src/diff.ts` and `web/src/lsp.ts`,
kept for M5 and M6 and no longer loaded by the page. The versions it pinned stand, with what M2
added:

- Kotlin: `androidx.webkit` 1.17.0, `com.akuleshov7:ktoml-core` 0.7.1
- Build: `vite` 8.3.0, `typescript` 7.0.2
- Page: `markdown-it` 15.0.2 (ships its own types), `@codemirror/language-data` 6.5.2 (every
  language's parser as its own chunk, loaded by file name when first needed)
- `@codemirror/`: `view` 6.43.12, `state` 6.7.5, `language` 6.12.4, `lang-javascript` 6.2.5,
  `merge` 6.12.2, `lsp-client` 6.3.0, `lint` 6.9.7, and `commands` 6.11.1, which M3 added for
  `history()` and the default keymap

The bridge's shape is settled too, and the security rules on it are in `plan.md`: one
`WebViewCompat.addWebMessageListener` named `skiffBridge`, `https://appassets.androidplatform.net`
as the only allowed origin, no `addJavascriptInterface`, the bundle served only through
`WebViewAssetLoader`, and JSON-RPC both ways — a message without an `id` is a notification, which
is what carries LSP diagnostics and file changes.

That shape is now `:code`'s `bridge/WebBridge` and `web/src/bridge.ts`, no longer spike code.
**Kotlin cannot reach the page until the page has spoken**: a reply proxy only arrives with a
message from it, so `bridge.ts` sends a `ready` notification as it loads and `WebBridge` holds
anything sent earlier until then. `WebBridgeTest` runs on a plain JVM, which is why `:code` has
`org.json:json` as a test dependency and `WebBridge` takes its logger as a parameter — `Log` is a
stub that throws there too.

`plan.md` holds the next round of work in more detail, in Korean. Its top section, `# Skiff
Code`, is the design and a checklist split into session-sized items — **a new session picks up at
the first unchecked `- [ ]`**, ticks it in the same commit as the work, and follows the
hand-off rules written there. The original brief is `skiff.code.plan.md`, and the hand-drawn
menu layout is `menu.layout.jpg` (transcribed in `plan.md`). The rest of `plan.md` is the
remaining Skiff work.

**Report and notify after every piece of work.** Each time a checklist item or other requested task
is finished (or blocked), report the result to the user in Korean — what was done, what was
verified and how, what is left — and send a push notification with a one-line summary. The user
often steps away while work runs, so the notification is not optional.

**Read `HANDOFF.md` at the start of a session, and overwrite it at the end.** It carries what the
checklist cannot: what the last session did, why the decisions went the way they did, which
assumptions the user has not confirmed yet, and what is still unverified. It is a snapshot of
the current state, not a log — the history lives in git. **When the user says they are starting a new
session, do this before anything else:** overwrite `HANDOFF.md` (and check `plan.md`'s
checklist) so both describe the state as it is, down to what is and is not pushed; ask before
committing it; then ask whether to push.

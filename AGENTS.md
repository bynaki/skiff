# AGENTS.md

Guidance for coding agents working in this repository.

Skiff is an Android SSH file manager. The original brief: ordinary file operations (create,
move, delete), file transfer between phone and server, and a split screen with one side on each
so moving things between them is direct. A code/markdown preview viewer comes later. The
constraint that shapes every decision below is **nothing may be installed on the server**.

## Commands

`java` is not on PATH. Every Gradle invocation needs `JAVA_HOME`, or it fails with
"Unable to locate a Java Runtime":

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17

./gradlew :app:assembleDebug
./gradlew :app:installDebug          # pushes to the connected device
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug             # report: app/build/reports/lint-results-debug.xml
```

Single test class, or one method (backticked names are passed with their spaces):

```bash
./gradlew :app:testDebugUnitTest --tests "com.naki.skiff.FsPathTest"
./gradlew :app:testDebugUnitTest --tests "com.naki.skiff.CopyEngineTest.a symlink cycle terminates instead of recursing forever"
```

Gradle prints only a pass/fail summary. For per-test results, parse
`app/build/test-results/testDebugUnitTest/*.xml`.

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

## Architecture

### `FileSystem` is the spine

`fs/FileSystem.kt` is the one abstraction everything else is written against. Panes render it,
`transfer/CopyEngine` copies between two of them, and the planned preview viewer will read
through it. Nothing above `fs/` knows whether it is looking at the phone or an SSH server —
which is what makes device↔server, server↔device and server↔server a single code path.

Two implementations: `fs/local/LocalFileSystem` (`java.io.File`) and `fs/sftp/SftpFileSystem`.
Streams are okio `Source`/`Sink` on both sides so `CopyEngine` never branches on which is which.
All paths are absolute POSIX and go through `fs/FsPath`. Implementations translate their native
exceptions into `fs/FsError`, so UI code never matches on `SFTPException` or `IOException`.

**When adding an operation, add it to the interface and both implementations.** A capability
that only exists on one side breaks the symmetry the whole design rests on.

### The SFTP side, and what it must not do

Only the SFTP subsystem that OpenSSH already ships. **There is no `exec` channel anywhere and
there must not be one** — no `rm -rf`, no `cp -r`, no `du`. Recursive delete lives in
`SftpFileSystem.deleteTreeBlocking`, recursive copy in `CopyEngine.walk`, both client-side. That
is what makes the app work against an `internal-sftp` account with no shell, which is the point
of "nothing to install on the server".

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

Passwords are encrypted by `data/crypto/SecretStore` with an Android Keystore key and decrypted
per connect. `SshConnection` takes host/port/user and a password *supplier*, deliberately
knowing nothing about the Keystore; `SourceRegistry` owns the profile→connection wiring. That
separation is what lets the SFTP layer be tested on a plain JVM.

**Do not swallow `CancellationException`.** `runCatching` around a suspending call catches it
too, which renders a cancelled load's cancellation message as a user-facing error. Catch
`CancellationException` and rethrow before the general handler.

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
- BouncyCastle is re-registered in `SkiffApplication` because Android ships a cut-down provider
  under the same `"BC"` name.

## Testing

`SftpFileSystemTest` runs the production SFTP code against a **real Apache MINA SSH server** on
a random port over a real socket (`SftpTestServer`). This is the highest-value test in the repo
— it has already caught a protocol assumption a mock would have agreed with. Prefer extending it
over mocking sshj.

`FakeFileSystem` is an in-memory `FileSystem` used to drive `CopyEngine`, including symlink
cases (dereferenced file, followed directory, cycle that must terminate). It follows POSIX
semantics deliberately: `stat` and `list` resolve links, a listing reports the link itself.

End-to-end against a real server means enabling Remote Login on macOS
(System Settings → General → Sharing) and registering it as a profile in the app.

## Not yet built

The preview viewer (code/markdown) is deliberately absent. `fs/FileKind` and the `onOpen` hook
in `PaneScreen` are the seams left for it; tapping a file currently hands it to an external app.
Key and keyboard-interactive auth are extension points on `AuthMethod` — only password is
implemented.

`plan.md` holds the next round of work in more detail, in Korean.

# Skiff

*English · [한국어](README.ko.md)*

An Android SSH file manager. Browse your phone and your servers side by side, and move files
between them.

**Nothing is installed on the server.** Skiff speaks only the SFTP subsystem that every OpenSSH
install already ships. There is no agent, no helper binary, and no shell commands — directory
recursion for copy and delete happens on the phone. That means it also works against an account
locked to `internal-sftp` with no shell at all.

> Early version (0.1.0). Password authentication only; the preview viewer is not built yet.
> See [Status](#status).

## Features

- **Browse** the device and any number of SSH servers, each with its own saved start directory.
- **Split view**, when you want it. A single full-screen pane is the default; you turn the split
  on, choose horizontal or vertical, and drag the divider. Each pane picks its own source, so
  device↔server and server↔server sit side by side equally well.
- **Transfers** in every direction, including server to server. They run in a foreground service
  with a progress notification, so leaving the app does not kill a large upload.
- **File operations**: new folder, new file, rename, delete (recursive), properties.
- **Sort** by name, size or date; show or hide dotfiles.
- **Host key verification** on first connect, with the SHA256 fingerprint shown in the same form
  `ssh` and `ssh-keygen` print, so you can actually compare it. A changed key gets a distinctly
  louder warning rather than silent acceptance.
- **Korean and English**, following the system language.

## Requirements

- Android 11 (API 30) or newer.
- A server running OpenSSH with the SFTP subsystem enabled — which is the default. Nothing else.

## Building

Needs JDK 17 and the Android SDK. Point `local.properties` at your SDK:

```properties
sdk.dir=/path/to/android-sdk
```

Then:

```bash
./gradlew :app:assembleDebug     # build
./gradlew :app:installDebug      # install on a connected device
./gradlew :app:testDebugUnitTest # tests
```

The test suite includes an in-process Apache MINA SSH server, so the SFTP code is exercised
against a real server over a real socket rather than a mock.

## Permissions, and why

A file manager asks for a lot, so here is what each one is for:

| Permission | Why |
|---|---|
| `INTERNET` | Connecting to your servers. |
| `ACCESS_LOCAL_NETWORK` | Android 17 gates private-range addresses separately. Without it a connection to a server on your own LAN is silently dropped and looks like a timeout. Skiff only asks when the server you are opening is actually a local address. |
| `MANAGE_EXTERNAL_STORAGE` | Browsing, moving and deleting arbitrary files on the device. Scoped storage cannot express this — a file manager has to see the whole tree. |
| `FOREGROUND_SERVICE_DATA_SYNC` | Keeping a transfer running when the app is not in front. |
| `POST_NOTIFICATIONS` | The transfer progress notification. |

Server passwords are encrypted with a key held in the Android Keystore that never leaves it, and
are decrypted per connection rather than kept in memory. Host keys are stored on first use and
checked on every connect afterwards.

## Status

Working and used, but young. Not yet built:

- **Preview viewer** for code and markdown — the next planned feature.
- **Key-based and keyboard-interactive authentication.** Password only for now.
- **Transfer conflict choices.** When a name already exists on the far side, Skiff currently
  always keeps both (`file (1).txt`). Overwrite and skip exist in the engine but are not yet
  reachable from the UI.
- Search, drag-and-drop between panes, archive extraction.

`plan.md` describes the next round in detail (in Korean). `AGENTS.md` is the architecture and
contributor guide.

## License

[MIT](LICENSE).

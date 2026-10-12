# 모듈

[목차](../skiffcode.spec.md)

```
settings.gradle.kts   include(":core", ":app", ":code")
:core  (com.android.library, 패키지명은 기존 그대로)
       fs/, fs/local/, fs/sftp/(SftpFileSystem, SshConnection, HostKeyGate, HostKeyPrompter, HostKeyFingerprint),
       data/crypto/SecretStore, ServerProfile·KnownHost 모델, FsError, FsPath, FileKind, LocalNetworkAccess
       + KnownHostStore 인터페이스 (HostKeyGate가 SkiffStore 대신 이것에 의존)
       + SshClientFactory (SshConnection.ensureConnected의 접속·인증·호스트키 부분)
:app   Skiff. SkiffStore가 KnownHostStore를 구현. + ProfileProvider
:code  Skiff Code (applicationId com.naki.skiff.code, logcat 태그 SkiffCode)
  src/main/java/com/naki/skiff/code/
    data/      SkiffCodeStore(프로필, knownHosts, 최근 파일을 JSON 하나에)
    intent/    SkiffCodeUri 파서, OpenRequest 해석(로컬/원격/프로필/프로젝트)
    session/   RemoteSessions(프로필→SftpFileSystem), RemoteExec, ShellQuote
    doc/       TextLoader, DocumentSaver, FileWatcher
    project/   ProjectStore, ProjectTree, GitScopeFinder, GitService
    lsp/       LspProcess, LspManager
    settings/  settings.toml, themes/*.toml, import/export
    bridge/    WebBridge (JSON-RPC)
    ui/        MainActivity(WebView 하나), 네이티브 다이얼로그(호스트키, 비밀번호, 알 수 없는 서버)
  web/         Vite + TypeScript + CodeMirror 6 → 빌드 결과를 src/main/assets/web/ 에 둔다 (gitignore)
    bridge.ts panes.ts layers/{viewer,editor,diff}.ts markdown.ts zoom.ts theme.ts fuzzy.ts
    chrome/{topbar,sidebar,palette,commandButton}.ts gitGutter.ts lsp.ts
```

**Kotlin과 Web의 역할.** 바이트, SSH, git, LSP 프로세스는 Kotlin이 맡는다. 문서 상태, 렌더링,
diff 계산, 퍼지 검색은 Web이 맡는다. Web은 원격 호출을 모두 `bridge.ts`의 RPC 하나로 한다.

**브리지는 양쪽 다 알림을 보낼 수 있어야 한다.** id가 있는 요청/응답만으로는 LSP 진단이나 파일
변경처럼 Kotlin이 먼저 말하는 것을 실을 수 없다. id가 없는 메시지는 알림으로 보고, 받는 쪽이
method로 구독한다(M0에서 확인).

## 성공 조건

이 주제가 됐다고 말할 수 있는 조건과 확인 방법이다(`AGENTS.md`). ✅는 충족, ⬜는 아직이다. 확인한 때와 자세한 내역은
[`skiffcode.plan.done.md`](../skiffcode.plan.done.md)의 그 마일스톤에 있다.

- ✅ `:core`·`:app`·`:code` 세 모듈이 따로 빌드되고 각자의 테스트와 린트가 통과한다 — 확인: `./gradlew :core:testDebugUnitTest :app:testDebugUnitTest :code:testDebugUnitTest :code:testWeb` 와 각 모듈의 `lintDebug`.
- ✅ M1에서 `:core`로 옮긴 뒤 Skiff의 동작이 그대로다 — 확인: 테스트 52개가 `:core` 28 + `:app` 24로 갈려 통과, 실기기에서 탐색·분할 화면·양방향 전송과 MD5 일치(M1).
- ✅ 브리지가 요청·응답과 알림을 양쪽으로 나른다 — 확인: `WebBridgeTest`, LSP 진단과 파일 변경 알림이 기기에서 도착(M0, M3, M6).

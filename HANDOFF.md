# 인수인계

다음 세션이 이어받기 위한 문서다. **세션을 끝낼 때마다 이 파일을 현재 상태로 덮어쓴다.**
계속 쌓는 기록이 아니다. 지난 기록은 git 히스토리에 남는다.

- 할 일, 아직 확인하지 않은 것, 알려진 문제: [`docs/skiffcode.plan.md`](docs/skiffcode.plan.md)
- 설계와 사용자와 정한 것: [`docs/skiffcode.spec.md`](docs/skiffcode.spec.md)
- 기기와 환경: [`docs/devices.md`](docs/devices.md)
- 규칙, 툴체인, 커밋 전 점검, 보고와 알림 규칙: [`AGENTS.md`](AGENTS.md)
- 이 문서에 담는 것: 위 문서들에 없는 **직전 세션의 맥락**(무엇을 했고, 무엇이 남았는지)뿐이다.
  세션이 지나도 남는 것은 위 문서들의 제자리에 적는다.

---

## 마지막 세션 (2026-10-05)

### 지금 상태
- 브랜치 `plan/skiffcode`(git worktree). M0~M5 완료, **M6 ①②완료**(`LspProcess`, `LspManager`).
- M6 진행 순서를 사용자와 정했다(2026-10-05): ① `LspProcess`+왕복 테스트 ② `LspManager` ③ 브리지와 editor
  ④ viewer ⑤ `@` 심볼 ⑥ 확인과 데몬 판정 측정. 계획 문서의 M6 체크리스트가 이 순서다.
- 사용자가 LSP의 앱 부하를 물었다(2026-10-05). 서버는 원격에서만 돌고 로컬 파일에는 LSP가 없다고 답했다.
  더 줄이는 제안(화면의 파일만 `didOpen`, 상한 등)은 **넣지 않기로 했다**("원래대로").
- ①: `lsp/LspFraming.kt`, `lsp/LspProcess.kt`, `ExecChannel.stderr`. 커밋 `0e2452c`.
- ②: `lsp/LanguageServer.kt`(서버 종류와 기본 명령), `lsp/LspManager.kt`(로그인 셸 감싸기, `command -v` 탐지,
  `initialize`가 띄움, 유휴 종료, `positionEncoding` 확인, `stopAll`, `LspEnd`), `ProjectSession.languageServers()`.
  - 테스트: `LspManagerTest` 10개. 스텁 서버는 `StubLanguageServer.kt`로 빼서 두 테스트가 같이 쓴다.
  - 재연결 후 `didOpen`은 페이지의 `LSPClient.connect`가 다시 보낸다(라이브러리 소스에서 확인).
    그래서 Kotlin은 문서를 들고 있지 않다.
- 린트 에러 0(경고 11, 기준과 같다), JVM code 257, vitest 121. core·app은 건드리지 않았다.
- 기기에는 설치하지 않았다. 앱 동작은 바뀌지 않았다(아직 아무도 `languageServers()`를 부르지 않는다).
- 탭의 확인용 찌꺼기는 그대로다(프로젝트 `skiff-layer-check`, 열린 파일 `a.py`·`b.md`, 컨테이너 계정 프로필 셋).
- 기기 확인용 도구: 탭의 맥 프로필은 `dev-mac`(LAN 주소)이고 Tailscale 주소가 아니다. 프로젝트로 열리려면
  저장소가 홈 아래에 있어야 한다(`GitScopeFinder`). 이 셸의 `grep`은 함수로 덮여 있어 일부 파일에서 결과가
  비므로 `command grep`을 쓴다.

### 다음 세션이 할 일
1. `AGENTS.md`를 읽는다.
2. 계획의 첫 `- [ ]`는 M6 ③ 브리지와 editor 연결이다. 할 것:
   - 시작 전에 이 맥에 pyright를 설치해도 되는지 사용자에게 묻는다.
   - `LspManager.listener`를 브리지에 붙이고(`lspMessage`/`lspSend`, 서버 종류를 같이 싣는다), 페이지는 `LspEnd`를 보고
     다시 `initialize`할지 정한다(`Failed`가 바로 되풀이되면 멈출 것).
   - `MainActivity.onStop`에서 시간을 두고 `stopAll()`. **몇 분으로 할지 사용자에게 묻는다.**
   - `settings.toml`의 LSP 명령과 요청 타임아웃 키(`SettingsToml.KEYS`). 알려진 문제 10(`col`)도 여기서.
3. 보지 않은 것: 2MB 파일에서 diff 레이어에 들어가는 시간, 큰 프로젝트의 walk 시간, `project_unchecked` 배너.
4. 블로그를 Hugo로 다시 배포하는 일이 생기면, 그 뒤 `https://bynaki.github.io/.well-known/assetlinks.json`이 200인지 본다.

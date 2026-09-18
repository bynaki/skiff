# 인수인계

다음 세션이 이어받기 위한 문서다. **세션을 끝낼 때마다 이 파일을 현재 상태로 덮어쓴다.**
계속 쌓는 기록이 아니다. 지난 기록은 git 히스토리에 남는다.

- 할 일 목록과 설계: [`plan.md`](plan.md)의 `# Skiff Code` 섹션
- 규칙, 툴체인, 커밋 전 점검, 보고와 알림 규칙: [`AGENTS.md`](AGENTS.md)
- 이 문서에 담는 것: 위 두 문서에 없는 **직전 세션의 맥락**(무엇을 했고, 왜 그렇게 정했고, 무엇이 아직 확인되지 않았는지)

---

## 마지막 세션 (2026-09-18): M0 완료

### 지금 상태
- 브랜치 `plan/skiffcode`(git worktree). `origin/plan/skiffcode`에 push했고(원격 저장소는 공개), **main에는 합치지 않았다.** 작업 트리는 깨끗하다.
- **M0이 끝났다. 다음은 M1(`:core` 추출)이다.** 확정한 버전과 결론은 `AGENTS.md`에 옮겨 적었고, 설계에 반영한 것은 `plan.md` 본문에 있다. 이 문서는 그것을 다시 적지 않는다.
- 탭에는 `com.naki.skiff.code`(debug)가 설치돼 있다.
- `:code` 모듈은 전부 **스파이크 코드**다. M2에서 정식으로 다시 만든다. 지금 들어 있는 것:
  - `ui/MainActivity.kt`: WebView 하나, `WebViewAssetLoader`, `addWebMessageListener("skiffBridge")`. RPC는 `sampleText` 하나, 알림은 `lspSend`/`lspMessage`다. 실행 인자는 KDoc에 적혀 있다.
  - `lsp/StubLsp.kt`: 가짜 LSP 서버. exec 금지 원칙 때문에 실제 원격 서버를 띄울 수 없어서 쓴다.
  - `code/web/src/`: `bridge.ts`(RPC + 알림), `zoom.ts`, `diff.ts`, `lsp.ts`, `main.ts`
  - `code/src/test/.../KtomlSpikeTest.kt`: ktoml 확인용. M4가 진짜 `SettingsTomlTest`를 쓴다.
- `:code:lintDebug`는 경고 10개로 통과한다: `RequiresFeature` 4, `MissingOnRenderProcessGone` 4, `SetJavaScriptEnabled` 1, `DataExtractionRules` 1. 앞의 둘은 M2에서 `WebBridge`를 정식으로 만들 때 처리한다(렌더러 프로세스가 죽으면 WebView를 다시 만들고, 기능 확인은 `if`로 분기).

### 스파이크 앱 띄우기와 측정
`am start -n com.naki.skiff.code/.ui.MainActivity`에 붙이는 인자:
- `--ez selftest true`: 스크롤 180프레임, 중간 점프, 줌 36단계를 자동으로 돌린다. `selftest done`이 찍히면 끝이다.
- `--ei bytes N`: 파일 크기. 기본 2MB다.
- `--es layer diff`: unified diff. `--es alpha 0.3`은 배경 투명도, `--es diff char|line`은 알고리즘(기본 line), `--ez nopatch true`는 CM6 버그 우회를 끈다.
- `--es layer lsp`: 스텁 LSP 서버에 붙어 핸드셰이크, 진단, hover, 완성, 편집 후 재동기화를 스스로 확인한다. `--ez fullsync true`면 서버가 incremental 대신 전체 동기화를 요구한다.

결과는 HUD와 logcat(`SkiffCode`)에 찍힌다. 페이지는 `window.view`(EditorView)와 `window.zoomTest()`를 내놓는다. CDP 붙이는 방법은 `AGENTS.md`에 있다. 번들이 압축돼 있어서 프로파일의 함수 이름은 번들 파일에서 `function 이름(`으로 찾는다.

측정 보조 스크립트(`selftest.sh`, `profile.mjs`, `eval.mjs`)는 세션 스크래치패드에만 있었고 **레포에는 없다.** `eval.mjs`는 `Runtime.evaluate` 한 번 보내는 20줄이라 다시 쓰면 된다.

### 측정 결과 (갤럭시탭 S10 FE, 2MB 35,473줄, 90Hz)
- **불러오기:** 브리지 수신 670~940ms. 대부분 스파이크 생성기 비용(약 550~800ms)이고, `org.json` 인코딩 73ms, 전송은 약 60ms다. EditorView 생성 56~68ms, 첫 페인트 12~41ms.
- **스크롤:** 스크립트 스크롤 85~91fps(p95 11.2ms). adb 스와이프 플링 86fps, `gfxinfo` jank 0.5%. 중간으로 점프 25~30ms.
- **줌:** 한 단계 dispatch p50 3ms + measure p50 8ms. 기준 줄 어긋남 최대 0.8px. 파일 크기에 거의 비례하지 않는다. 사용자가 탭에서 직접 스크롤과 핀치 줌을 해 보고 좋다고 확인했다.
- **diff:** 청크 333개(40블록마다 1줄 수정, 2줄 삭제, 3줄 추가).
  - 줄 단위 diff는 약 240ms에 모든 청크가 정확하다.
  - 패키지 기본값은 파일 전체를 청크 1개로 처리하고, 글자 단위로 제한을 풀면 6.7초에 전부 부정확하다.
  - CM6 버그 우회를 넣으면 스크롤 87fps, 줌 3+10ms로 viewer와 같다. 우회가 없으면 줌 한 단계가 57+86ms이고 줌 중 14fps다.
  - +/- 기호, 지운 줄마다 `-` 하나, 바뀐 글자 강조, 지운 줄 구문 하이라이팅, 투명도 변경을 탭 스크린샷으로 확인했다.
- **LSP:** initialize 4ms. didOpen(199만자, Kotlin 파싱 56ms) 후 진단 252ms. hover 4ms, 완성 3ms. 진단 50개의 범위가 모두 의도한 글자 위에 있었고 한글 주석도 맞았다(`cm-lintRange-info`가 정확히 `원격 파일을 읽어`를 덮는다). 편집 후 재동기화는 incremental 591ms 대 full 744ms(둘 다 `autoSync`의 500ms 디바운스 포함), didChange 크기는 246자 대 199만자다.

### 사용자와 정한 것, 그리고 이유
다시 논의하지 말고 이대로 진행한다.

| 결정 | 고른 것 | 이유 |
|---|---|---|
| 레포 구조 | 같은 레포, `:core` / `:app` / `:code` 멀티모듈 | 별도 레포는 SFTP 계층을 복사하게 되고, 두 사본이 따로 바뀌면서 달라진다 |
| 원격 실행 | 데몬 없이 SSH exec (프로젝트 모드에서만) | 자동 설치 데몬은 아키텍처별 빌드, 업데이트, 자원 관리를 처음부터 떠안게 된다 |
| UI | 전부 WebView 하나 (TS + CodeMirror 6) | Compose와 WebView를 섞으면 테마를 두 곳에 적용해야 하고, 스크롤에 따른 메뉴 숨김도 브리지를 거친다 |
| 서버 정보 공유 | Skiff가 서명 보호 Provider로 프로필만 공유, 비밀번호는 각자 저장 | Keystore 키는 앱마다 따로라 암호문을 공유할 수 없고, 비밀번호를 넘기면 평문이 IPC를 지나간다 |
| 실기기 | 갤럭시탭 S10 FE(SM-X526N, Android 16/API 36, WebView 152) | 연결된 기기가 이것이다. **폴더블 폰도 지원 대상**이라 폴더블 관련 문서와 코드는 지우지 않는다 |
| 예전 시도 | 로컬 브랜치 `claude/next-steps-2afbda`와 기기의 `com.naki.skiffcode`를 삭제 | 사용자 지시. 없었던 것으로 친다. 참고하거나 되살리지 않는다 |
| CM6 버그 처리 | **upstream에 올리지 않는다.** M5 diff 레이어에서 `patch-package`로 두 줄을 패치한다 | CodeMirror는 AI가 쓴 코드를 받지 않고 이슈는 자체 트래커에서만 받는다. 사용자는 리포트를 올리지 않기로 했다 |
| TOML | ktoml을 쓴다. `smol-toml`로 옮기지 않는다 | M0에서 확인했다. 컴파일러 플러그인만 필요하고 KSP를 안 써서 Room과 다르다 |
| exec | `:app`은 금지, `:code`는 허용. 근거는 "서버에 설치하지 않기 위해"가 아니라 **"셸 없는 `internal-sftp` 계정을 지원하기 위해"** 다 | 사용자가 금지의 출처를 물었고, 사용자가 정한 것이 아니라 AI가 잘못 유도한 것으로 드러났다. 규칙은 남기고 이유를 고쳤다 |
| M0 스파이크 코드 | 확인이 끝나면 지운다. exec 하네스도 지웠다 | 사용자 지시. M0은 버리는 코드로 확인만 하는 단계다 |
| 보고 방식 | 작업마다 한국어로 보고하고 푸시 알림을 보낸다 | 사용자가 자리를 비울 때가 많다. `AGENTS.md`에 적었다 |

### 사용자 확인을 아직 받지 않은 가정
해당 단계에 들어가기 전에 한 번 물어본다.
1. diff와 git 거터의 기본 비교 대상은 **HEAD와 현재 버퍼**다. "직전 커밋과 HEAD"는 두 번째 옵션이다. (M5 전)
2. 로컬 파일은 `skiffcode:///경로`(MANAGE_EXTERNAL_STORAGE)와 `content://`(ACTION_VIEW/EDIT) 두 경로로 받는다. (M2 전)
3. 원격 LSP 서버는 자동 설치하지 않고 PATH에서 찾는다. (M6 전)

### 실제 LSP 서버 확인 (exec 규칙을 고치고 추가로 한 것)

**사용자가 exec 금지의 근거를 물었고, 문서가 틀렸다는 것이 드러났다.** "서버에 아무것도 설치하지
않는다"는 사용자가 정한 제약이지만, "그러므로 exec를 두지 않는다"는 AI가 유도한 것이고 논리가
맞지 않는다(서버에 이미 있는 `git`을 exec로 돌리는 것은 아무것도 설치하지 않는다). 실제로 그 규칙이
지키는 것은 **셸 없는 `internal-sftp` 계정 지원**이다. `AGENTS.md`를 그렇게 고쳤고, `:code`는
exec를 쓸 수 있다고 명시했다. `:app`은 여전히 금지다.

사용자가 스파이크에서 exec를 허용해서 실제 서버로 확인했다. **하네스는 사용자 지시로 지웠다.**
M6에서 다시 만들 때 필요한 것은 이것뿐이다:
- 서버 쪽: MINA `SshServer`에 `commandFactory = ProcessShellCommandFactory.INSTANCE`.
  `SftpTestServer`(`:app`)와 같은 구성이고 subsystem 대신 commandFactory를 둔다.
- 클라이언트 쪽: sshj `session.exec("/bin/sh -lc 'cd <root> && exec <command>'")`.
- 프레이밍: 헤더는 `\r\n\r\n`까지 한 바이트씩, 본문은 `Content-Length`만큼 채워 읽는다.
- 언어 서버: `npm i pyright` → `node_modules/.bin/pyright-langserver --stdio`. 레포에 넣지 않았다.
- **`org.json`은 유닛 테스트 JVM에서 스텁이라 전부 던진다.** `org.json:json`을 테스트 의존성으로
  넣어야 한다. Gradle의 `-D`는 테스트 JVM에 전달되지 않으니 `systemProperty`로 넘겨야 한다.
- 결과: initialize 98ms, 진단까지 346ms(로컬 루프백). `positionEncoding`은 응답에 없고(= `utf-16`),
  `textDocumentSync`는 2(incremental). `값 = "한글" + 1`의 진단이 char 4..12로 UTF-16 인덱스와
  정확히 맞았다(UTF-8이면 4..18이다).

### 아직 확인하지 않은 것
안 되는 게 나오면 `plan.md`의 설계를 먼저 고친다.
- **실기기에서 원격 서버로는 아직 안 해 봤다.** 위 확인은 이 맥 안의 MINA 루프백이라 네트워크 지연, 재접속, 끊김이 빠져 있다. 탭에서 실제 SSH 서버로 붙는 것은 M6에서 처음이다.
- 진단이 수백~수천 개일 때의 비용. 스텁은 50개, pyright는 1개였다.
- 마크다운 렌더링(`markdown-it`), lezer 심볼 추출, 테마 CSS 변수, SAF import/export는 라이브러리 버전도 고르지 않았다.
- 큰 파일에서 편집 중 동기화 비용. 2MB에서 incremental이 훨씬 싸다는 것만 알고, 실제 서버가 어디서 버거워하는지는 모른다.
- pyright 말고 다른 서버(typescript-language-server, marksman)는 확인하지 않았다. TypeScript 7은 네이티브 재작성이라 `tsserver.js`가 없고, `typescript-language-server`가 그 위에서 도는지 모른다.

### 다음 세션이 할 일
1. `AGENTS.md`를 읽는다. 특히 Toolchain constraints, Before committing, 보고와 알림 규칙을 본다.
2. `plan.md`의 첫 `- [ ]`인 **M1 첫째 항목(`:core` 라이브러리 모듈 추가)**부터 시작한다.
3. 세션을 끝낼 때 체크리스트를 갱신하고 이 파일을 덮어쓴다.

### 주의할 점
- **M1은 Skiff를 건드리는 리팩터링이다.** 옮기기 전과 후에 `:app` 테스트가 모두 통과해야 하고(지금 52개), 실기기에서 SFTP 탐색과 전송이 이전과 같은지 확인한다. 패키지명은 유지한다.
- **exec 규칙은 이미 고쳤다.** `:app`은 여전히 금지, `:code`는 허용이다. M5의 체크리스트에서 `AGENTS.md` 수정은 빠졌고 `RemoteExec` 구현만 남았다. 자세한 것은 위 "실제 LSP 서버 확인" 절과 `AGENTS.md`의 "The SFTP side, and what it must not do"에 있다.
- `:code`에 kotlinx.serialization 플러그인이 붙어 있는데 main 소스에서는 아직 안 쓴다. ktoml이 요구한다. M2에서 `SkiffCodeStore`가 쓴다.
- `npm install` 때 fsevents install 스크립트는 npm 11 기본 정책으로 실행되지 않는다. macOS용 선택 의존성이라 영향이 없다.
- 기기:
  - 시리얼은 `adb devices`로 얻고 레포에 적지 않는다.
  - 화면은 2분 뒤 꺼지고, 꺼진 화면에서 `screencap`은 검은 화면을 찍는다. 측정 전에 `adb shell input keyevent KEYCODE_WAKEUP`을 보내고 `dumpsys window | grep mCurrentFocus`로 앱이 앞에 있는지 확인한다.
  - 가로 방향(2304x1440)이고 화면은 하나라 `screencap`에 display id가 필요 없다.
  - 아래 가장자리에서 시작하는 스와이프는 시스템 제스처에 먹히므로 y 250~1100 사이에서 한다.
  - adb로는 멀티터치를 만들 수 없으니, 핀치 같은 제스처는 사용자에게 손으로 확인을 부탁한다.
- 환경:
  - `node`(v24)와 `npm`은 PATH에 있다.
  - `java`는 PATH에 없어서 `JAVA_HOME=/opt/homebrew/opt/openjdk@17`이 필요하다.
  - `adb`도 PATH에 없고 `/opt/homebrew/share/android-commandlinetools/platform-tools/adb`에 있다.
  - worktree에는 gitignore된 `local.properties`가 따로 있어야 한다(메인 체크아웃에서 복사했다).

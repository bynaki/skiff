# 인수인계

다음 세션이 이어받기 위한 문서다. **세션을 끝낼 때마다 이 파일을 현재 상태로 덮어쓴다.**
계속 쌓는 기록이 아니다. 지난 기록은 git 히스토리에 남는다.

- 할 일 목록과 설계: [`plan.md`](plan.md)의 `# Skiff Code` 섹션
- 규칙, 툴체인, 커밋 전 점검, 보고와 알림 규칙: [`AGENTS.md`](AGENTS.md)
- 이 문서에 담는 것: 위 두 문서에 없는 **직전 세션의 맥락**(무엇을 했고, 왜 그렇게 정했고, 무엇이 아직 확인되지 않았는지)

---

## 마지막 세션 (2026-09-17): M0 첫째~넷째 항목

### 지금 상태
- 브랜치 `plan/skiffcode`(git worktree). `origin/plan/skiffcode`에 push했고(원격 저장소는 공개), **main에는 합치지 않았다.** 작업 트리는 깨끗하다.
- M0 체크리스트: 1~4번 완료. **다음은 5번(`@codemirror/lsp-client` Transport를 브리지로 대체할 수 있는지)**이다.
- 탭에는 `com.naki.skiff.code`(debug)가 설치돼 있다.
- `:code` 모듈(`com.naki.skiff.code`, minSdk 30, Compose 없음)은 전부 **스파이크 코드**다. M2에서 정식으로 다시 만든다.
  - `ui/MainActivity.kt`: WebView 하나를 둔다.
    - 번들은 `WebViewAssetLoader`(`https://appassets.androidplatform.net/assets/`)로 불러온다.
    - 브리지는 `addWebMessageListener("skiffBridge")`이고, 허용 origin은 appassets 하나다.
    - RPC 메서드는 `sampleText` 하나다. 한글 주석이 섞인 TypeScript 모양 텍스트를 만들어 JSON-RPC로 돌려준다.
    - debug 빌드에서만 `WebView.setWebContentsDebuggingEnabled(true)`를 켠다.
  - `code/web`(Vite 8.3.0 + TypeScript 7.0.2):
    - `bridge.ts`: id로 응답을 짝짓는 `rpc()`
    - `zoom.ts`: 핀치 줌
    - `diff.ts`: unified diff와 CM6 버그 우회
    - `main.ts`: viewer/diff 화면, HUD, 자기 측정
    - 빌드 결과는 `code/src/main/assets/web/`에 생기고 gitignore된다.
  - Gradle은 `npmCi` → `buildWeb` → `preBuild` 순서로 돈다. 입출력을 지정해서 바뀐 게 없으면 둘 다 UP-TO-DATE다.
- 고정한 버전:
  - `androidx.webkit` 1.17.0
  - `vite` 8.3.0, `typescript` 7.0.2
  - `@codemirror/view` 6.43.12, `state` 6.7.5, `language` 6.12.4, `lang-javascript` 6.2.5, `merge` 6.12.2

### 스파이크 앱 띄우기와 측정
`am start -n com.naki.skiff.code/.ui.MainActivity`에 붙이는 인자:
- `--ez selftest true`: 스크롤 180프레임, 중간 점프, 줌 36단계를 자동으로 돌리고 결과를 HUD와 logcat(`SkiffCode`)에 찍는다. `selftest done`이 찍히면 끝이다.
- `--ei bytes N`: 파일 크기. 기본 2MB다.
- `--es layer diff`: unified diff. `--es alpha 0.3`은 배경 투명도, `--es diff char|line`은 알고리즘(기본 line), `--ez nopatch true`는 CM6 버그 우회를 끈다.

측정 보조 스크립트(`selftest.sh`, CDP용 `profile.mjs`/`eval.mjs`)는 세션 스크래치패드에만 있었고 **레포에는 없다.** 필요하면 아래 방법으로 다시 만든다.
- CDP: `adb forward tcp:9333 localabstract:webview_devtools_remote_$(adb shell pidof com.naki.skiff.code)`로 포트를 연다. `http://127.0.0.1:9333/json`에서 페이지의 `webSocketDebuggerUrl`을 얻고, Node 24 전역 `WebSocket`으로 `Runtime.evaluate`, `Profiler.start/stop`을 보낸다.
- 페이지는 `window.view`(EditorView)와 `window.zoomTest()`를 내놓는다.
- 번들이 압축돼 있다. 프로파일에 나온 함수 이름은 번들 파일에서 `function 이름(`로 찾는다.

### 측정 결과 (갤럭시탭 S10 FE, 2MB 35,473줄, 90Hz)
- **불러오기:** 브리지 수신 750~940ms. 대부분 스파이크 생성기 비용(약 800ms)이고, `org.json` 인코딩 73ms, 전송은 약 60ms다. EditorView 생성 56ms, 첫 페인트 12ms.
- **스크롤:** 스크립트 스크롤 85~91fps(p95 11.2ms). adb 스와이프 플링 86fps, `gfxinfo` jank 0.5%. 중간으로 점프 25~30ms.
- **줌:** 한 단계 dispatch p50 3ms + measure p50 8ms. 기준 줄 어긋남 최대 0.8px. 파일 크기에 거의 비례하지 않는다. 사용자가 탭에서 직접 스크롤과 핀치 줌을 해 보고 좋다고 확인했다.
- **diff:** 청크 333개(40블록마다 1줄 수정, 2줄 삭제, 3줄 추가).
  - 줄 단위 diff는 약 240ms에 모든 청크가 정확하다.
  - 패키지 기본값은 파일 전체를 청크 1개로 처리하고, 글자 단위로 제한을 풀면 6.7초에 전부 부정확하다.
  - CM6 버그 우회를 넣으면 스크롤 87fps, 줌 3+10ms로 viewer와 같다. 우회가 없으면 줌 한 단계가 57+86ms이고 줌 중 14fps다.
  - +/- 기호, 지운 줄마다 `-` 하나, 바뀐 글자 강조, 지운 줄 구문 하이라이팅, 투명도 변경을 탭 스크린샷으로 확인했다.

### 이번 세션에 알게 된 것 (설계에 반영함)
- **줌 기준 줄 고정은 `EditorView.scrollIntoView(pos, {y: 'start', yMargin})`로 한다.**
  - 실패한 방법: `requestMeasure`의 write에서 `scrollTop`을 쓰면 CM6 measure 루프의 스크롤 앵커 보정과 겹친다. "Viewport failed to stabilize" 경고가 나고 12만 px 어긋났다.
  - 보정: `yMargin`은 caret 사각형 기준이라, 줄 상자 안에서 caret이 시작하는 비율을 줌 시작 때 잰다.
- **diff는 줄 단위를 먼저 하고, 바뀐 줄 범위 안에서만 글자 단위로 한다(`diffConfig.override`).**
- **`@codemirror/view` 6.43.12 버그:**
  - 증상: 블록 위젯이 줄 경계에 있으면 `HeightMapBranch.forEachLine`(`break == 0` 분기)이 `mid.to + 1`/`mid.from - 1`을 요청 범위로 clamp하지 않는다. 그래서 `viewportLineBlocks`가 화면 밖 수천 줄이 되고, 모든 거터가 그만큼 요소를 만든다.
  - 확인: GitHub `main`에도 그대로다. CodeMirror만 쓴 최소 스크립트로 데스크톱 Chrome에서도 재현했다(20,000줄, 뷰포트 1~36줄에서 `viewportLineBlocks` 9,999개).
  - 스파이크: `diff.ts`의 `patchViewportLineBlocks`로 prototype을 런타임에 바꿔 우회한다.

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
| 보고 방식 | 작업마다 한국어로 보고하고 푸시 알림을 보낸다 | 사용자가 자리를 비울 때가 많다. `AGENTS.md`에 적었다 |

### 사용자 확인을 아직 받지 않은 가정
해당 단계에 들어가기 전에 한 번 물어본다.
1. diff와 git 거터의 기본 비교 대상은 **HEAD와 현재 버퍼**다. "직전 커밋과 HEAD"는 두 번째 옵션이다. (M5 전)
2. 로컬 파일은 `skiffcode:///경로`(MANAGE_EXTERNAL_STORAGE)와 `content://`(ACTION_VIEW/EDIT) 두 경로로 받는다. (M2 전)
3. 원격 LSP 서버는 자동 설치하지 않고 PATH에서 찾는다. (M6 전)

### 아직 모르는 것 (M0 남은 항목)
안 되는 게 나오면 `plan.md`의 설계를 먼저 고친다.
- `@codemirror/lsp-client` Transport를 WebView 브리지로 대신할 수 있는지 (5번)
- ktoml이 Kotlin 2.4.20 / AGP 9에서 컴파일되는지. KSP와 Room이 이미 안 됐던 환경이다. (6번)
- 위에 적은 것 말고는 라이브러리 버전을 확정하지 않았다. 확인한 최신 안정판을 쓰고, M0 마지막 항목(7번)에서 `AGENTS.md`에 모아 적는다.

### 다음 세션이 할 일
1. `AGENTS.md`를 읽는다. 특히 Toolchain constraints, Before committing, 보고와 알림 규칙을 본다.
2. `plan.md`의 첫 `- [ ]`인 **M0 5번**부터 시작한다.
   - **exec 금지 원칙 때문에 실제 원격 LSP 서버는 아직 띄울 수 없다.** Transport가 브리지 위에서 도는지는 Kotlin이나 Web 쪽의 스텁 LSP 서버(예: `initialize`에 응답하고 진단 하나를 보내는 가짜)로 확인한다.
   - 기존 스파이크 페이지(`main.ts`)에 모드를 하나 더하는 식으로 붙이면 된다.
3. 세션을 끝낼 때 체크리스트를 갱신하고 이 파일을 덮어쓴다.

### 주의할 점
- **exec 금지 원칙은 아직 유효하다.** `AGENTS.md`의 원칙을 바꾸는 것은 M5의 체크리스트 항목이다.
- M1(`:core` 추출)은 Skiff를 건드리는 리팩터링이다. 옮기기 전과 후에 `:app` 테스트가 모두 통과해야 하고, 실기기에서 SFTP 탐색과 전송이 이전과 같은지 확인한다.
- `:code:lintDebug`는 경고 9개로 통과한다: `MissingOnRenderProcessGone` 4, `RequiresFeature` 3, `SetJavaScriptEnabled` 1, `DataExtractionRules` 1. 앞의 둘은 M2에서 `WebBridge`를 정식으로 만들 때 처리한다(렌더러 프로세스가 죽으면 WebView를 다시 만들고, 기능 확인은 `if`로 분기).
- **`:code`를 빌드하려면 Gradle 데몬의 PATH에 `npm`이 있어야 한다.**
  - node는 fnm으로 설치돼 있고 셸마다 경로가 달라서 빌드 스크립트에 경로를 적지 않았다.
  - 터미널에서 띄운 데몬은 문제없고, Android Studio처럼 PATH가 없는 곳에서는 대책이 필요하다.
  - `AGENTS.md`에는 아직 적지 않았다(M0 7번에서 정리).
- CM6 버전을 올릴 때는 `node_modules/@codemirror/view/dist/index.js`의 `HeightMapBranch.forEachLine`에 clamp가 들어갔는지 먼저 본다. 들어갔으면 패치를 뺀다.
- TypeScript 7의 `tsc`는 import/export가 없는 파일을 전역 스크립트로 본다. 그래서 `status` 같은 이름이 `window.status`와 충돌한다.
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

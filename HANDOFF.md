# 인수인계

다음 세션이 이어받기 위한 문서다. **세션을 끝낼 때마다 이 파일을 현재 상태로 덮어쓴다.**
계속 쌓는 기록이 아니다. 지난 기록은 git 히스토리에 남는다.

- 할 일 목록과 설계: [`plan.md`](plan.md)의 `# Skiff Code` 섹션
- 규칙, 툴체인, 커밋 전 점검: [`AGENTS.md`](AGENTS.md)
- 이 문서에 담는 것: 위 두 문서에 없는 **직전 세션의 맥락**(무엇을 했고, 왜 그렇게 정했고, 무엇이 아직 확인되지 않았는지)

---

## 마지막 세션 (2026-09-17): M0 첫째~셋째 항목, WebView 브리지·Vite 빌드·CM6 성능

### 한 일
- **실기기를 확인했다.** 연결된 기기는 Fold 7이 아니라 **갤럭시탭 S10 FE(SM-X526N)**, Android 16 / API 36, arm64, WebView 152다. 사용자가 이 기기를 기준으로 하기로 해서 `plan.md`의 M0 항목을 고쳤다. 화면은 하나라서 `screencap`에 display id가 필요 없다.
- **예전 Skiff Code 시도를 지웠다.** 합치지 않은 로컬 브랜치 `claude/next-steps-2afbda`(9/14, Compose UI, 비밀번호까지 Provider로 공유)가 있었다. 사용자 지시로 브랜치를 삭제하고(원격에는 없었다), 그 브랜치에서 설치했던 `com.naki.skiffcode`도 기기에서 지웠다. **없었던 것으로 친다. 참고하거나 되살리지 않는다.**
- `:code` 모듈을 추가했다(`com.naki.skiff.code`, minSdk 30, Compose 없음, 의존성은 `androidx.webkit` 1.17.0 하나).
  - `ui/MainActivity`: WebView 하나 + `WebViewAssetLoader`(`https://appassets.androidplatform.net/assets/`) + `addWebMessageListener("skiffBridge", 허용 origin은 appassets 하나)`. `org.json`으로 `ping`에 응답한다.
  - `assets/web/index.html`: 손으로 쓴 임시 페이지. 다음 항목에서 Vite 빌드 결과로 바뀐다.
- 기기에서 확인: 요청 → Kotlin 응답 → JS 수신까지 한 번 왕복했고 한글도 깨지지 않았다(logcat `SkiffCode` 태그와 화면 모두).
- `code/web`에 Vite 8.3.0 + TypeScript 7.0.2 프로젝트를 만들었다(정확한 버전으로 고정). 손으로 쓴 `index.html`은 지우고 `src/main.ts`로 옮겼다.
  - 빌드 결과는 `code/src/main/assets/web/`에 생기고 gitignore된다. `node_modules`도 gitignore.
  - Gradle: `npmCi`(package.json, lock → node_modules) → `buildWeb`(web 소스 → assets/web) → `preBuild`.
  - 확인한 것: node_modules와 assets가 없는 상태에서 `:code:assembleDebug` 한 번에 빌드된다. 바뀐 게 없으면 두 태스크 모두 UP-TO-DATE다. `main.ts`를 바꾸면 `buildWeb`이 다시 돌고, 결과물이 달라질 때만 `mergeDebugAssets`와 `packageDebug`가 다시 돈다. 기기에서 새 번들로 왕복을 다시 확인했다.
  - `val x by tasks.registering(...)`은 Gradle 9.7에서 폐기 경고가 떠서 `tasks.register<Exec>(...)`로 썼다.
- M0 셋째 항목(CM6 2MB 스크롤·핀치 줌)은 **자동 측정까지 끝났고 사용자의 손 확인을 기다린다.** 체크하지 않았다.
  - 구성: Kotlin `sampleText`가 한글 주석이 섞인 TypeScript 모양의 텍스트를 만들어 브리지로 보낸다(기본 2MB, `--ei bytes N`). 웹은 읽기 전용 CM6(줄 번호, `lang-javascript` 하이라이팅)로 보여준다. 오른쪽 위 HUD와 logcat(`SkiffCode`)에 수치를 찍는다. `--ez selftest true`로 띄우면 스크롤·점프·줌 측정을 자동으로 돌린다.
  - CM6 패키지(정확한 버전으로 고정): `@codemirror/view` 6.43.12, `state` 6.7.5, `language` 6.12.4, `lang-javascript` 6.2.5. 번들 374KB(gzip 128KB).
  - 결과(2MB, 35,473줄, 화면 90Hz):
    - 불러오기: 브리지 수신 약 750~940ms. 대부분 스파이크용 생성기 비용(약 800ms)이고, `org.json` 인코딩 73ms, 전송은 약 60ms다. EditorView 생성 약 56ms, 첫 페인트 12ms.
    - 스크립트 스크롤(프레임당 150px): 85~91fps, p95 11.2ms.
    - adb 스와이프 플링: JS 기준 1158프레임, 86fps, p95 11.2ms, 20ms 넘는 프레임 4.5%. `dumpsys gfxinfo` 기준 jank 0.5%.
    - 중간으로 점프: 25~30ms.
    - 줌 36단계(14→40→8→14px): 기준 줄 어긋남 최대 0.8px. 한 단계 비용은 dispatch p50 3ms + measure p50 8ms(p90 15ms). 최대 100ms가 한 번 있었다. 20KB 파일과 비용이 비슷하다. 즉 파일 크기에 거의 비례하지 않는다.
  - **처음 구현은 실패했다.** `requestMeasure`의 write에서 `scrollTop`을 쓰면 CM6 measure 루프의 스크롤 앵커 보정과 겹친다. 그래서 "Viewport failed to stabilize" 경고가 나고 12만 px 어긋났다. `EditorView.scrollIntoView(pos, {y: 'start', yMargin})`로 바꿔서 해결했다. `yMargin`은 caret 사각형 기준이다. 그래서 줄 상자 안에서 caret이 시작하는 비율(`glyph`)을 줌 시작 때 재서 보정한다. 이 내용은 `plan.md` 설계의 viewer 항목에도 적었다.
  - adb로는 멀티터치를 만들 수 없다. 그래서 실제 핀치 제스처(`touchmove` 경로)는 아직 아무도 확인하지 않았다. 자동 측정은 같은 `zoomTo`를 직접 부른 것이다.

### 사용자와 정한 것, 그리고 이유
질문으로 정했고 사용자는 네 가지 모두 추천안을 골랐다. 다시 논의하지 말고 이대로 진행한다.

| 결정 | 고른 것 | 고르지 않은 것과 이유 |
|---|---|---|
| 레포 구조 | 같은 레포, `:core` / `:app` / `:code` 멀티모듈 | 별도 레포는 SFTP 계층을 복사하게 되고, 두 사본이 따로 바뀌면서 달라진다 |
| 원격 실행 | 데몬 없이 SSH exec (프로젝트 모드에서만) | 자동 설치 데몬은 아키텍처별 빌드, 업데이트, 자원 관리를 처음부터 떠안게 된다. 필요해지는 조건은 `plan.md` "범위 밖"에 적어 뒀다 |
| UI | 전부 WebView 하나 (TS + CodeMirror 6) | Compose 메뉴와 WebView 레이어를 섞으면 TOML 테마를 두 곳에 적용해야 하고, 스크롤할 때 메뉴를 숨기는 동작도 브리지를 거쳐야 한다 |
| 서버 정보 공유 | Skiff가 서명 보호 Provider로 프로필만 공유, 비밀번호는 각자 저장 | Keystore 키는 앱마다 따로라 암호문을 공유할 수 없다. 비밀번호까지 넘기면 평문이 앱 사이 IPC를 지나간다 |

### 사용자 확인을 아직 받지 않은 가정
사용자는 계획을 승인했지만 아래 세 가지에 대해서는 따로 답하지 않았다. 해당 단계에 들어가기 전에 한 번 물어보는 게 안전하다.
1. diff와 git 거터의 기본 비교 대상은 **HEAD와 현재 버퍼**다. "직전 커밋과 HEAD"는 두 번째 옵션이다. (M5 전에 확인)
2. 로컬 파일은 `skiffcode:///경로`(MANAGE_EXTERNAL_STORAGE)와 `content://`(ACTION_VIEW/EDIT) 두 경로로 받는다. (M2 전에 확인)
3. 원격 LSP 서버는 자동 설치하지 않고 PATH에서 찾는다. (M6 전에 확인)

### 아직 모르는 것 (M0에서 확인할 것)
안 되는 게 나오면 `plan.md`의 설계를 먼저 고친다.
- ktoml이 Kotlin 2.4.20 / AGP 9에서 컴파일되는지. KSP와 Room이 이미 안 됐던 환경이다.
- `@codemirror/merge`로 원하는 모양(+/- 기호, 초록/빨강 배경의 읽기 전용 unified diff)이 나오는지.
- `@codemirror/lsp-client` Transport를 WebView 브리지로 대신할 수 있는지.
- 실기기(갤럭시탭 S10 FE)에서 손으로 했을 때 CM6 핀치 줌과 2MB 파일 스크롤이 쓸 만한지. 수치는 위에 있다.
- `androidx.webkit` 1.17.0, Vite 8.3.0, TypeScript 7.0.2, 위 CM6 패키지 말고는 라이브러리 버전을 확정하지 않았다. 실제로 확인한 최신 안정판을 쓰고, M0 마지막 항목에서 `AGENTS.md`에 모아 적는다.

### 다음 세션이 할 일
1. `AGENTS.md`를 읽는다. 특히 Toolchain constraints와 Before committing을 본다.
2. `plan.md`에서 처음 나오는 `- [ ]`부터 시작한다. 지금은 **M0 셋째 항목**이다. 사용자가 탭에서 직접 스크롤과 핀치 줌을 만져 본 결과를 먼저 묻는다. 쓸 만하다고 하면 체크하고 넷째 항목(`@codemirror/merge` unified diff)으로 간다. 문제가 있다고 하면 그 증상부터 고친다.
3. 세션을 끝낼 때 체크리스트를 갱신하고 이 파일을 덮어쓴다.

### 주의할 점
- **exec 금지 원칙은 아직 유효하다.** `AGENTS.md`의 원칙을 바꾸는 것은 M5의 체크리스트 항목이다. 그 전에는 `:code`에도 exec를 넣지 않는다.
- M1(`:core` 추출)은 Skiff를 건드리는 리팩터링이다. 옮기기 전과 후에 `:app` 테스트가 모두 통과해야 하고, 실기기에서 SFTP 탐색과 전송이 이전과 같은지 확인한다.
- M0 코드는 스파이크다. `:code:lintDebug`는 경고 9개로 통과한다(`MissingOnRenderProcessGone` 4, `RequiresFeature` 3, `SetJavaScriptEnabled` 1, `DataExtractionRules` 1). 앞의 둘은 M2에서 `WebBridge`를 정식으로 만들 때 처리한다(렌더러 프로세스가 죽으면 WebView를 다시 만들고, 기능 확인은 `if`로 분기).
- adb 명령에 기기 시리얼이 필요하면 `adb devices`로 얻는다. 시리얼은 레포에 적지 않는다.
- 기기 화면이 꺼져 있으면 `screencap`이 검은 화면을 찍는다. `adb shell input keyevent KEYCODE_WAKEUP` 뒤에 찍는다.
- **이제 `:code`를 빌드하려면 Gradle 데몬의 PATH에 `npm`이 있어야 한다.** node는 fnm으로 설치돼 있고 셸마다 경로가 달라서 빌드 스크립트에 경로를 적지 않았다. 터미널에서 띄운 데몬은 문제없다. Android Studio처럼 PATH가 없는 곳에서 빌드하려면 대책이 필요하다. `AGENTS.md`의 Commands와 Toolchain 섹션에는 아직 적지 않았다(M0 마지막 항목에서 정리한다).
- 기기 화면은 2분(120000ms) 뒤 꺼진다. adb 측정 전에 `KEYCODE_WAKEUP`을 보내고 `dumpsys window | grep mCurrentFocus`로 앱이 앞에 있는지 확인한다. 기기는 가로 방향(2304x1440)이다. 아래 가장자리에서 시작하는 스와이프는 시스템 제스처에 먹히므로 y 250~1100 사이에서 한다.
- `npm install` 때 fsevents의 install 스크립트는 npm 11 기본 정책 때문에 실행되지 않는다. macOS용 선택 의존성이라 빌드에는 영향이 없다.
- TypeScript 7의 `tsc`는 import/export가 없는 파일을 전역 스크립트로 본다. 그래서 `status` 같은 이름이 `window.status`와 충돌한다. 모듈로 만들거나 이름을 피한다.
- 환경: 작업 브랜치는 `plan/skiffcode`(git worktree)이고, `node`(v24)와 `npm`은 PATH에 있다. `java`는 PATH에 없어서 `JAVA_HOME`이 필요하다. `adb`도 PATH에 없고 `/opt/homebrew/share/android-commandlinetools/platform-tools/adb`에 있다. worktree에는 `local.properties`가 따로 있어야 한다(메인 체크아웃에서 복사했다).

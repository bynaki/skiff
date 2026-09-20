# 인수인계

다음 세션이 이어받기 위한 문서다. **세션을 끝낼 때마다 이 파일을 현재 상태로 덮어쓴다.**
계속 쌓는 기록이 아니다. 지난 기록은 git 히스토리에 남는다.

- 할 일 목록과 설계: [`plan.md`](plan.md)의 `# Skiff Code` 섹션
- 규칙, 툴체인, 커밋 전 점검, 보고와 알림 규칙: [`AGENTS.md`](AGENTS.md)
- 이 문서에 담는 것: 위 두 문서에 없는 **직전 세션의 맥락**(무엇을 했고, 왜 그렇게 정했고, 무엇이 아직 확인되지 않았는지)

---

## 마지막 세션 (2026-09-20 낮): 레이어 구조를 정하고 **M3 첫 항목(editor 레이어) 완료**

### 지금 상태
- 브랜치 `plan/skiffcode`(git worktree). 지난 인수인계에 "push 안 한 커밋 3개"라고 적혀 있었지만
  **이미 전부 push돼 있었다.** 지금은 **이번 작업이 커밋되지 않은 채 워킹 트리에 있다**(아래 "바뀐 것").
  커밋할지, main에 합칠지(`main` 대비 47커밋 앞) 아직 답을 못 받았다.
- **M3 첫 항목이 끝났다. 다음은 `DocumentSaver`다**(`plan.md`의 첫 `- [ ]`).
- 테스트: `:core` 35개, `:app` 24개, `:code` 92개. 실패 없음. lint 경고 `:app` 4개, `:code` 11개.
  **전부 지난 세션과 같은 수다.** 웹은 `npx tsc --noEmit` 통과.
- 탭에는 두 앱의 debug 빌드가 깔려 있다. 확인하느라 `/sdcard/Download/skiffcode-test/`의 파일을
  화면에서 고쳤지만 **저장 기능이 아직 없어 디스크는 그대로다.**

### 이번 세션에서 한 것과 내린 판단

**레이어 구조 (사용자 질문에서 시작했다)**
- 질문은 "읽기 전용 / 편집 / diff 레이어를 따로 둘까, 하나로 둘까"였다. 레이어마다 다른 기능을 달고
  싶은데 자원이 아깝다는 것이었다.
- **두 요구는 충돌하지 않는다.** 나누는 단위가 `EditorView`가 아니라 **compartment에 담는 확장 묶음**이면
  된다. CM6 소스에서 확인한 근거 세 가지는 `AGENTS.md`에 적었다(state field가 재설정을 건너 살아남는 것,
  `unifiedMergeView`가 문서를 건드리지 않는 순수 확장 배열인 것, 뷰를 나누면 state가 갈라져 파싱 트리와
  높이맵이 레이어 수만큼 생기는 것).
- **정한 것:** 뷰 하나 + 파일당 state 하나. 스크롤은 "레이어별로 따로 기억"이 아니라 **보던 줄을 잇는다**
  (사용자 결정). `plan.md`의 문구도 그렇게 고쳤다.

**editor 레이어 (`code/web/src/layers/pane.ts`, 새 파일)**
- `main.ts`가 문서마다 뷰를 만들고 버리던 자리를 `Pane`이 가져갔다. `layers/viewer.ts`는 없어지고
  마크다운 표면은 `markdown.ts`로, 코드 표면은 `pane.ts`로 들어갔다.
- base(항상 켜짐): 줄 번호, 하이라이팅, 언어 compartment, 테마, **`history()`**. history를 compartment
  밖에 둔 것이 핵심이다 — 뷰어를 다녀와도 undo가 이전 편집까지 닿는다. 기기에서 두 번 왕복 뒤에도
  닿는 것을 확인했다.
- `BUNDLES.viewer` = readOnly + non-editable, `BUNDLES.editor` = `defaultKeymap` + `historyKeymap`,
  `BUNDLES.diff` = readOnly 자리만(M5).
- **`drawSelection()`과 `highlightActiveLine()`은 넣지 않았다.** 계획에는 적었지만 없어도 되는 것이라
  뺐다. 필요하면 나중에 `BUNDLES.editor`에 한 줄이다.
- 마크다운은 레이어가 진짜 두 DOM이다. 렌더된 블록의 `data-line`(이미 `markdown.ts`에 있었다)과 소스 줄을
  서로 옮겨서 양쪽 다 보던 자리를 지킨다. CM6 뷰는 **처음 editor로 들어갈 때** 만든다.

**기기에서 찾아 고친 것 세 가지 — 셋 다 코드를 읽어서는 안 나왔다**
1. **`view.focus()`만 하면 커서가 문서 맨 앞에 놓인다.** 300번째 줄을 보다가 ③을 누르고 한 글자 치면
   화면이 1번째 줄로 튀었다. 지금은 포커스 전에 **화면 맨 윗줄로 selection을 옮긴다.**
2. **포커스된 에디터에서는 핀치 줌이 죽는다.** 커서가 contenteditable에 있으면 Chrome이 **두 번째
   손가락의 `touchstart`를 `<html>`로** 준다(뒤따르는 `touchmove`는 정상으로 온다). 그래서 스크롤러에
   달린 리스너는 제스처의 시작을 보지 못한다. 블러시키면 멀쩡히 동작하는 것으로 원인을 확정했다.
   `installPinchZoom`을 **document에 달고, pane당 하나만** 달도록 고쳤다(전에는 표면마다 하나씩이었다).
   `AGENTS.md`에 적었다.
3. **`display: none`으로는 CodeMirror가 숨겨지지 않는다.** 기본 테마가 `.cm-editor`에
   `display: flex !important`를 건다. 마크다운 뷰어에서 문서 끝을 지나 계속 스크롤하면 그 아래에 코드
   뷰가 이어서 나왔다(**사용자가 발견했다**). `setProperty('display','none','important')`로 고쳤다.
   `AGENTS.md`에 적었다.

**상단 메뉴를 불투명 막대로 (세션 끝에 사용자가 요청했다)**
- `code/web/index.html`의 `#topbar`가 배경(`#fff`)과 아래 테두리를 갖는다. 막대가 실체를 얻었으므로
  버튼마다 있던 **테두리·배경·그림자·둥근 모서리를 전부 걷어냈다.** 안 그러면 막대 위에 상자가 얹힌다.
- `pointer-events: none`도 없앴다. 전에는 두 버튼 묶음 사이의 빈 곳으로 터치가 통과해야 했다.
  핀치는 이제 `document`에서 듣기 때문에 막대 위에서 시작해도 동작한다.
- **아침의 "떠 있는 버튼을 유지한다" 결정을 같은 날 뒤집은 것이다.** 아래 결정 표에 이유를 적었다.

**Kotlin 쪽**
- `labels`에 `layerViewer/layerEditor/layerDiff`를 넣고 `menu_layer` 문자열을 셋으로 나눴다.
  ③의 아이콘과 이름은 **현재 레이어**를 가리킨다.
- **IME 인셋을 넣었다**(`MainActivity`). 전에는 `systemBars() or displayCutout()`만 잡고
  `WindowInsets.CONSUMED`를 돌려줘서, 키보드가 뜨면 화면 아래를 덮은 채 뷰 높이가 그대로였다.
  읽기만 할 때는 문제가 아니었다.

### 기기에서 확인한 것 (갤럭시탭 S10 FE)
- `big.ts`(2MB) 300줄: ③ → **맨 윗줄 그대로**, 키보드가 커서를 가리지 않음, 타이핑이 그 줄에 들어감.
- 뷰어를 두 번 왕복한 뒤 Ctrl+Z 두 번이 **첫 편집까지** 되돌림.
- `readme.md`: 렌더 ↔ 소스가 같은 줄에서 이어지고, 소스를 고친 뒤 뷰어로 가면 다시 렌더됨.
- 핀치와 ②가 코드/마크다운 × viewer/editor 네 경우 모두 동작(14px ↔ 37.3px ↔ 14px).
  **확인할 때 메뉴가 숨어 있으면 ②와 ③ 탭이 그냥 사라진다.** 아래로 스크롤하면 메뉴가 숨는 것은 의도된
  동작이다. `#topbar`의 `hidden` 클래스를 먼저 보는 게 빠르다.
- 마크다운 뷰어에서 문서 끝을 지나 스크롤해도 아무것도 없다(`document.body.scrollHeight`가 화면 높이와 같다).
- 힙: 2MB 파일에서 레이어를 네 번 오가도 **23.1MB 그대로**(`performance.memory` 해상도 0.1MB).
- 불투명 막대: 코드와 마크다운 둘 다 깔끔하고, 아래/위 스크롤에 숨고 나타나며, ③이 동작한다.
  **스와이프 직후에는 관성이 남아 탭이 먹지 않는다.** 확인할 때는 스와이프 대신
  `.cm-scroller`의 `scrollTop`을 직접 주는 편이 재현이 정확하다.

### 바뀐 것 (커밋되지 않았다)
```
새 파일  code/web/src/layers/pane.ts
지움    code/web/src/layers/viewer.ts
고침    code/web/index.html  (상단 메뉴 불투명 막대)
고침    code/web/src/{main.ts, markdown.ts, zoom.ts, chrome/topbar.ts, package.json, package-lock.json}
고침    code/src/main/java/com/naki/skiff/code/ui/MainActivity.kt
고침    code/src/main/res/values{,-ko}/strings.xml
고침    plan.md, AGENTS.md, HANDOFF.md
```
`@codemirror/commands` 6.11.1을 새로 넣었다(`history()`와 기본 키맵).

### 다음 세션이 할 일
1. `AGENTS.md`를 읽는다. 특히 툴체인 제약, Before committing(**커밋마다, push마다 사용자에게 먼저 묻는다**),
   보고와 알림 규칙.
2. **이번 변경을 커밋할지 먼저 묻는다.**
3. `plan.md`의 첫 `- [ ]`인 **`DocumentSaver`**를 한다. 시작하기 전에 아래를 먼저 다룬다:
   - **`링크가 아는 alias의 임의 경로를 연다`는 문제를 저장 기능보다 먼저 본다.** 읽기만 할 때는 문제가
     아니었지만 쓰기가 생기면 열린다. alias가 맞으면 host는 프로필 것을 쓰지만 **경로는 링크의 것**이다.
   - 저장이 생기면 편집 중 나가기, 저장 안 된 버퍼 표시(사이드바 항목과 겹친다)도 같이 생각한다.

### 사용자와 정한 것, 그리고 이유
다시 논의하지 말고 이대로 진행한다.

| 결정 | 고른 것 | 이유 |
|---|---|---|
| 레포 구조 | 같은 레포, `:core` / `:app` / `:code` 멀티모듈 | 별도 레포는 SFTP 계층을 복사하게 되고, 두 사본이 따로 바뀌면서 달라진다 |
| 원격 실행 | 데몬 없이 SSH exec (프로젝트 모드에서만) | 데몬은 exec 없이 뜨지도 못하고(SFTP는 실행을 못 한다), 남에게 배포하는 앱이 남의 서버에 상주 프로세스를 심는 것은 "서버에 아무것도 설치하지 않는다"는 제약과 충돌한다. 임의의 서버가 대상이라 단일 산출물도 없다. 자세한 것과 데몬을 다시 꺼내는 조건은 `plan.md`의 "정한 것" 2번과 "범위 밖" |
| UI | 전부 WebView 하나 (TS + CodeMirror 6) | Compose와 WebView를 섞으면 테마를 두 곳에 적용해야 하고, 스크롤에 따른 메뉴 숨김도 브리지를 거친다 |
| **레이어 구조 (2026-09-20)** | **뷰 하나 + 파일당 state 하나. 레이어는 `Compartment`의 확장 묶음** | 레이어마다 기능을 달리 다는 것은 compartment가 해 주고, 뷰를 나누면 파싱 트리·높이맵이 레이어 수만큼 생긴다. 근거는 `AGENTS.md`의 CM6 관찰 |
| **레이어 전환 시 스크롤 (2026-09-20)** | **보던 줄을 잇는다.** 레이어별로 따로 기억하지 않는다 | 사용자 결정. 같은 코드를 다른 방식으로 본다는 감각에 맞는다 |
| 서버 정보 공유 | Skiff가 서명 보호 Provider로 프로필만 공유, 비밀번호는 각자 저장 | Keystore 키는 앱마다 따로라 암호문을 공유할 수 없고, 비밀번호를 넘기면 평문이 IPC를 지나간다 |
| 프로필 합치기 (2026-09-19) | Skiff id → 이름 순으로 찾아 Skiff의 주소·시작 경로를 받고 자기 id와 비밀번호는 유지. host(대소문자 무시)·port·user가 바뀌면 비밀번호를 지운다. 없으면 비밀번호 없이 추가. **지우지는 않는다** | 사용자 결정. 비밀번호가 다른 기계로 따라가지 않게 하려는 것이다. Skiff Code에만 있는 프로필을 Skiff가 지웠다고 없애면 사용자가 잃는다 |
| 호스트키 합치기 (2026-09-19) | 그 host:port에 **없을 때만** 받는다. 있는 것은 Skiff와 달라도 덮어쓰지 않는다 | 사용자 결정. 어느 쪽이 맞는지는 서버가 키를 내밀 때 `HostKeyGate`의 경고 창이 판단할 일이지, 동기화가 조용히 정할 일이 아니다 |
| 받는 쪽 서명 확인 (2026-09-19) | Skiff는 링크를 보내기 전에, Skiff Code는 provider를 읽기 전에 서로 같은 키로 서명됐는지 본다 | 사용자 결정. 패키지 이름만 흉내 낸 앱에 서버 주소와 경로가 가거나, 그 앱이 우리에게 호스트키를 심는 것을 막는다 |
| 실기기 | 갤럭시탭 S10 FE(SM-X526N, Android 16/API 36, WebView 152) | 연결된 기기가 이것이다. **폴더블 폰도 지원 대상**이라 폴더블 관련 문서와 코드는 지우지 않는다 |
| 예전 시도 | 로컬 브랜치 `claude/next-steps-2afbda`와 기기의 `com.naki.skiffcode`를 삭제 | 사용자 지시. 없었던 것으로 친다 |
| CM6 버그 처리 | **upstream에 올리지 않는다.** M5 diff 레이어에서 `patch-package`로 두 줄을 패치한다 | CodeMirror는 AI가 쓴 코드를 받지 않고 이슈는 자체 트래커에서만 받는다 |
| TOML | ktoml을 쓴다. `smol-toml`로 옮기지 않는다 | M0에서 확인했다 |
| exec | `:app`은 금지, `:code`는 허용. 근거는 **"셸 없는 `internal-sftp` 계정을 지원하기 위해"** 다 | 금지의 출처가 사용자가 아니라 AI의 잘못된 유도였던 것이 드러났다. 규칙은 남기고 이유를 고쳤다 |
| 로컬 파일 경로 | `skiffcode:///경로`(MANAGE_EXTERNAL_STORAGE)와 `content://`(ACTION_VIEW/EDIT) 둘 다 | `plan.md` "정한 것" 5번 |
| 상단 메뉴 | ① 사이드바, ② 원래 크기, ③ 레이어 토글, ④ 더보기. editor 하단 ±는 없다 | 확대/축소는 핀치로 한다. `menu.layout.jpg`와 번호가 다르다 |
| 상단 메뉴 배경 (2026-09-20, **하루 만에 뒤집혔다**) | **불투명 막대.** 버튼만 떠 있던 모양은 버렸다 | 사용자 결정. 아침에는 "떠 있는 버튼을 유지한다"였고 커밋 `e1fa7b3`이 그렇게 기록했지만, editor 레이어가 생긴 뒤 같은 날 막대로 바꾸기로 했다. 버튼 하나하나의 테두리·그림자가 없어지고 막대 하나가 되면서, 스크롤한 코드 위에서 ① 버튼이 줄 번호를 가리던 것도 같이 없어졌다 |
| 링크로 여는 대화상자 | "서버로 저장"은 기본 켬. 빈 필수 칸이 있으면 연결 버튼을 끈다. 여는 중에 새 링크가 오면 이전 흐름을 취소한다 | 전에는 빈 비밀번호가 저장·시도됐고, 창이 하나 더 쌓였다 |
| 보고 방식 | 작업마다 한국어로 보고하고 푸시 알림을 보낸다 | 사용자가 자리를 비울 때가 많다. `AGENTS.md`에 적었다 |

### 사용자 확인을 아직 받지 않은 가정
해당 단계에 들어가기 전에 한 번 물어본다.
1. diff와 git 거터의 기본 비교 대상은 **HEAD와 현재 버퍼**다. "직전 커밋과 HEAD"는 두 번째 옵션이다. (M5 전)
2. 원격 LSP 서버는 자동 설치하지 않고 PATH에서 찾는다. (M6 전)

### 실제 LSP 서버 확인 (M0에서 한 것, M6에서 다시 필요하다)
스파이크 하네스는 사용자 지시로 지웠다. 다시 만들 때 필요한 것은 이것뿐이다.
- 서버 쪽: MINA `SshServer`에 `commandFactory = ProcessShellCommandFactory.INSTANCE`.
  `SftpTestServer`(`:core`의 testFixtures)와 같은 구성이고 subsystem 대신 commandFactory를 둔다.
- 클라이언트 쪽: sshj `session.exec("/bin/sh -lc 'cd <root> && exec <command>'")`.
- 프레이밍: 헤더는 `\r\n\r\n`까지 한 바이트씩, 본문은 `Content-Length`만큼 채워 읽는다.
- 언어 서버: `npm i pyright` → `node_modules/.bin/pyright-langserver --stdio`. 레포에 넣지 않았다.
- **`org.json`은 유닛 테스트 JVM에서 스텁이라 전부 던진다.** `org.json:json`을 테스트 의존성으로 넣는다.
  Gradle의 `-D`는 테스트 JVM에 전달되지 않으니 `systemProperty`로 넘긴다.
- 결과: initialize 98ms, 진단까지 346ms(로컬 루프백). `positionEncoding`은 응답에 없고(= `utf-16`),
  `textDocumentSync`는 2(incremental). 한글이 든 줄의 진단이 UTF-16 인덱스와 정확히 맞았다.

### 아직 확인하지 않은 것
안 되는 게 나오면 `plan.md`의 설계를 먼저 고친다.
- **손가락으로 하는 핀치.** 이번 확인은 전부 CDP `Input.dispatchTouchEvent`로 만든 터치였다. 위에서 찾은
  "포커스된 에디터의 두 번째 touchstart" 문제도 CDP가 만든 것이 아니라 실제 hit-test 결과로 보이지만
  (블러하면 같은 CDP 터치가 정상 동작한다), **진짜 손가락으로 한 번 해 봐야 한다.**
- **editor의 손 사용감** 전반: 선택 핸들, 길게 누르기, 커서 옮기기.
- **undo에 닿을 UI가 아직 없다.** 키맵은 하드웨어 키보드용이고, 화면 키보드에는 undo가 없다. 커맨드
  팔레트(M4)가 생기기 전까지는 외장 키보드 없이 undo를 할 수 없다. `adb`로는 조합키를 못 보낸다
  (멀티터치와 같은 제약) — DevTools의 `Input.dispatchKeyEvent`로 보내야 한다.
- **바뀐 호스트키 경고 창**을 기기에서 본 적이 없다. 서버의 키를 일부러 바꿔 한 번 봐야 한다.
- **서명이 다른 앱이 끼어드는 경로**(가짜 Skiff Code, 가짜 provider)는 그런 앱을 만들지 않아 보지 못했다.
- **링크가 아는 alias의 임의 경로를 연다.** 읽기만 하는 동안은 문제가 아니었다. **저장을 만들기 전에 본다.**
- Android 17의 로컬 네트워크 권한 요청(탭은 Android 16이라 요청하지 않는다).
- release APK의 배포용 서명. 지난번에는 debug 키로 서명해서 확인만 했다.
- **탭에서 실제 SSH 서버로 LSP를 띄워 본 적은 없다.** M0의 LSP 확인은 이 맥 안의 MINA 루프백이라
  네트워크 지연, 재접속, 끊김이 빠져 있다.
- 진단이 수백~수천 개일 때의 비용. 스텁은 50개, pyright는 1개였다.
- lezer 심볼 추출, 테마 CSS 변수, SAF import/export는 라이브러리 버전도 고르지 않았다.
- 큰 파일에서 편집 중 동기화 비용. 2MB에서 incremental이 훨씬 싸다는 것만 안다.
- pyright 말고 다른 서버(typescript-language-server, marksman)는 확인하지 않았다.

### 주의할 점
- **세션을 끝낼 때 `plan.md` 체크리스트를 갱신하고 이 파일을 덮어쓴다.**
- **레포에 기기나 네트워크를 알아볼 수 있는 것을 남기지 않는다.** 호스트 이름, IP, 계정, 지문이 문서와
  테스트에 들어가기 쉽다. 픽스처와 문서는 `192.0.2.0/24`(RFC 5737)와 `.example`을 쓴다.
- **exec 규칙:** `:app`은 금지, `:code`는 허용. `AGENTS.md`의 "The SFTP side, and what it must not do" 참고.
- `npm install` 때 fsevents install 스크립트는 npm 11 정책으로 실행되지 않는다. macOS용 선택 의존성이라 영향 없다.
- 기기:
  - 시리얼은 `adb devices`로 얻고 레포에 적지 않는다.
  - **화면은 2분 뒤 꺼지고, 꺼진 화면에서 `screencap`은 검은 화면을 찍는다.** 탭 전에
    `input keyevent KEYCODE_WAKEUP` → 잠금 해제 스와이프(`input swipe 1152 1300 1152 300 200`) →
    `dumpsys window | grep mCurrentFocus`로 앱이 앞에 있는지 확인한다. 이걸 빼먹으면 탭이 그냥 사라진다.
  - 가로 방향(2304x1440)이고 화면은 하나라 `screencap`에 display id가 필요 없다.
  - 상단 메뉴 좌표(가로, 2304x1440): ② `tap 2088 117`, ③ `tap 2160 117`.
  - 아래 가장자리에서 시작하는 스와이프는 시스템 제스처에 먹히므로 y 250~1100 사이에서 한다.
  - **adb로는 멀티터치도 조합키도 만들 수 없다.** 둘 다 DevTools로 한다:
    `adb forward tcp:9222 localabstract:webview_devtools_remote_$(adb shell pidof com.naki.skiff.code)`
    → `curl -s localhost:9222/json`에서 `webSocketDebuggerUrl` → `Input.dispatchTouchEvent`(터치 점 두
    개, CSS px 기준)와 `Input.dispatchKeyEvent`(`modifiers: 2`가 Ctrl, `char` 이벤트는 보내지 않는다).
    노드에 `ws`만 있으면 된다. `performance.memory`도 여기서 읽는다.
  - **외장 키보드(Corne)가 연결돼 있으면 화면 키보드가 뜨지 않는다.** `dumpsys input | grep Corne`.
    editor를 확인할 때는 빼야 한다.
  - Skiff Code 저장 파일을 adb로 고칠 때는 먼저 `am force-stop`한다. `run-as … cat`으로 읽고,
    `adb push`로 `/data/local/tmp`에 둔 뒤 `run-as … sh -c 'cat /data/local/tmp/x > files/datastore/skiffcode.json'`으로 쓴다.
  - provider 확인: `adb shell content query --uri content://com.naki.skiff.profiles/profiles`는
    **거부되는 것이 정상이다**(shell은 권한이 없다). 권한 부여는
    `dumpsys package com.naki.skiff.code | grep READ_PROFILES`로 본다.
  - 확인용 파일이 `/sdcard/Download/skiffcode-test/`에 있다(`big.ts` 2MB, `hello.py`, `readme.md`, 인코딩·크기 거부용).
- 환경:
  - `node`(v24)와 `npm`은 PATH에 있다.
  - `java`는 PATH에 없어서 `JAVA_HOME=/opt/homebrew/opt/openjdk@17`이 필요하다.
  - `adb`도 PATH에 없고 `/opt/homebrew/share/android-commandlinetools/platform-tools/adb`에 있다.
    `zipalign`과 `apksigner`는 `/opt/homebrew/share/android-commandlinetools/build-tools/37.0.0/`에 있다.
  - release APK를 기기에 넣을 때는 debug 키로 서명한다(`~/.android/debug.keystore`, 비밀번호 `android`,
    별칭 `androiddebugkey`). 그래야 기존 debug 앱 위에 덮여 앱 데이터가 남는다. 확인이 끝나면
    `installDebug`로 되돌린다.
  - worktree에는 gitignore된 `local.properties`가 따로 있어야 한다(메인 체크아웃에서 복사했다).

# 인수인계

다음 세션이 이어받기 위한 문서다. **세션을 끝낼 때마다 이 파일을 현재 상태로 덮어쓴다.**
계속 쌓는 기록이 아니다. 지난 기록은 git 히스토리에 남는다.

- 할 일 목록과 설계: [`plan.md`](plan.md)의 `# Skiff Code` 섹션
- 규칙, 툴체인, 커밋 전 점검, 보고와 알림 규칙: [`AGENTS.md`](AGENTS.md)
- 이 문서에 담는 것: 위 두 문서에 없는 **직전 세션의 맥락**(무엇을 했고, 왜 그렇게 정했고, 무엇이 아직 확인되지 않았는지)

---

## 마지막 세션 (2026-09-20 밤): 링크 경로 구멍을 막고, 줌에서 줄 번호가 어긋나던 것을 고쳤다

### 지금 상태
- 브랜치 `plan/skiffcode`(git worktree). **커밋 2개를 push까지 끝냈다.**
  - `a9b18b8` Confirm a link's path unless Skiff sent it
  - `cda5163` Tell CodeMirror the font size through a theme, not a CSS variable
  - 문서 갱신(`plan.md`, `AGENTS.md`, 이 파일)은 세 번째 커밋이다.
- **M3는 여전히 `DocumentSaver`가 다음이다**(`plan.md`의 첫 `- [ ]`). 이번 세션은 그 앞을 치웠다.
- **`main`에는 아직 합치지 않았다.** 사용자가 "내가 언급할 때 하자"고 했으므로 **먼저 꺼내지 않는다.**
- 테스트: `:core` 35개, `:app` 24개, `:code` 92개. 실패 없음. lint 경고 `:app` 4개, `:code` 11개.
  **전부 지난 세션과 같은 수다.** 웹은 `npx tsc --noEmit` 통과.
- 탭에 두 앱의 debug 빌드가 깔려 있다. 저장 기능은 아직 없다.

### 이번 세션에서 한 것과 내린 판단

**1. 링크가 준 경로를 확인한다 (`a9b18b8`)**

지난 세션이 "저장을 만들기 전에 본다"고 남긴 항목이다. 실제 모양은 이랬다.

- `skiffcode://`는 `BROWSABLE`이라 **아무 앱이나, 웹 페이지도** 링크를 보낼 수 있다. alias가 맞으면
  host는 프로필 것을 쓰지만 **경로는 링크의 것**이라, 프로필 이름만 맞히면 저장된 자격증명으로 그
  서버의 아무 파일이나 **대화상자 없이** 열렸다. `LocalPath`도 마찬가지(MANAGE_EXTERNAL_STORAGE).
- 읽기만 할 때는 화면에 뜰 뿐이라(공격자가 내용을 가져갈 수 없다) 문제가 아니었다. **저장이 생기면
  사용자의 타이핑이 어느 파일에 떨어지는지를 링크가 고른다.**
- **고른 방법(사용자 결정):** 보낸 앱이 Skiff면 지금처럼 조용히 열고, 아니면 서버와 경로를 보여주고
  묻는다. 검토한 대안은 `startPath` 하위로 제한(정상 흐름도 걸린다)과 저장할 때만 묻기(여는 구멍이
  남는다)였다.
- **핵심은 보낸 앱을 어떻게 아느냐였다.** `getReferrer()`는 호출자가 `EXTRA_REFERRER`를 직접 채워
  **위조된다.** `ComponentCaller.getPackage()`는 프레임워크가 답하고, 호출자는 **밝힐지 여부만**
  고른다(`ActivityOptions.setShareIdentityEnabled`) — 그래서 사칭이 불가능하다. 대신 **Skiff 쪽도
  고쳐야 했다**(안 켜면 Skiff 탭도 확인창이 뜬다).
- `onNewIntent`는 **`getCurrentCaller()`**를 읽는다. `getInitialCaller()`였다면 Skiff가 띄운 앱에
  뒤이어 들어온 악성 링크가 **Skiff의 신뢰를 물려받았을** 것이다. AOSP 소스로 확인했다
  (`performNewIntent`가 호출 전후로 `mCurrentCaller`를 세우고, 두 인자 `onNewIntent`의 기본 구현이
  한 인자짜리를 부른다). 모든 경로에서 세워지는 것은 아니라 `IllegalStateException`을 잡아 null로 본다.
- Android 15 미만은 `ComponentCaller`가 없어 **전부 묻는다**(`minSdk`는 30, 탭은 36).
- `content://`(Claude 앱, 파일 매니저)는 **대상이 아니다.** 경로가 아니라 보낸 앱이 준 권한이다.
- Robolectric이 없어 이 경로는 유닛 테스트가 닿지 않는다. 대신 `confirmPath`의 `when`을 sealed 타입에
  **망라적**으로 써서, `OpenRequest` 종류가 늘면 빌드가 깨지게 했다.

**2. 확대할 때 줄 번호 간격이 따라오지 않던 것 (`cda5163`, 사용자가 발견했다)**

"hello.md 같이 화면보다 짧은 파일을 editor에서 확대하면 숫자만 커지고 간격은 그대로"였다.

- **거터만의 문제가 아니었다.** CM6의 높이 맵이 통째로 낡아 있었다. 40px에서 `defaultLineHeight`가
  24.1, `contentHeight`가 253인데 DOM의 줄 높이는 69였다. 본문 줄은 CSS로 그려져 멀쩡해 보이고,
  거터 칸 높이는 높이 맵에서 **인라인 px로 박히기** 때문에 낡은 것이 눈에 보였다.
- **짧은 파일에서만인 이유:** 측정 관문이 `theme facet 변경 || refresh || contentDOMHeight != rect`인데,
  기본 테마가 `.cm-content`에 `min-height: 100%`를 걸어 **짧은 문서는 박스 높이가 화면 높이에 고정**된다.
  기기에서 확대 전후 모두 675였다. 긴 파일은 내용이 화면보다 높아 박스가 글꼴을 따라가므로 매 프레임
  다시 재고, 그래서 멀쩡했다. `requestMeasure()`로는 안 고쳐지고, `mustMeasureContent`를 손으로 켜니
  즉시 정상화되는 것으로 원인을 확정했다.
- **고친 방법:** 글꼴 크기를 `--code-font-size`가 아니라 **`Compartment`가 나르는 테마**로 옮겼다
  (`codeFontSize`). theme facet 변경이 CM6가 지원하는 유일한 "글꼴이 움직였다" 신호다. CSS 변수는
  마크다운 표면과 메뉴용으로 남는다. 핀치도 ②도 `zoomTo` 하나를 지난다.
- **고치다가 스스로 만든 회귀를 하나 막았다:** 마크다운 뷰어에서 확대한 뒤 editor로 넘어가면, 전에는
  테마가 CSS 변수를 읽어 저절로 따라왔지만 compartment는 낡은 채 남는다. `applyFontSize`가 코드
  표면이 돌아올 때 맞춘다. 숨은 뷰의 재측정도 이것으로 되므로 `requestMeasure()` 호출을 대신했다.
- jsdom은 레이아웃을 하지 않아 줄 높이가 전부 0이다. **vitest로는 이 버그를 볼 수 없다.**

### 기기에서 확인한 것 (갤럭시탭 S10 FE)
- 경로 확인: Skiff에서 탭 → 대화상자 없이(`open LocalPath from com.naki.skiff`). adb 링크 → 확인창,
  취소하면 안 열리고 "열기"면 열린다. **Skiff가 띄운 앱에 들어온 adb 링크도 확인창**(신뢰를 물려받지
  않는다). `attacker@192.0.2.1/etc/shadow?alias=<실제 프로필 이름>`이 **링크의 주소가 아니라 프로필의
  주소**와 `/etc/shadow`를 보여주며 **연결·비밀번호·호스트키 창 전에** 멈췄다.
- 줌: hello.md(8줄) 40px에서 거터/본문 **69/69**(전에는 24.1/69), ② 원래 크기로 24.1/24.1,
  big.ts(2MB)도 69/69, 뷰어에서 크기를 바꾼 뒤 **기존 뷰**로 다시 들어가도 맞는다.
- 확인하는 동안 기기 `screen_off_timeout`을 30분으로 올렸다가 **2분으로 되돌려 놓았다.**

### 다음 세션이 할 일
1. `AGENTS.md`를 읽는다. 특히 툴체인 제약, Before committing(**커밋마다, push마다 사용자에게 먼저
   묻는다**), 보고와 알림 규칙.
2. **`plan.md`의 첫 `- [ ]`인 `DocumentSaver`**를 한다. 앞을 막던 경로 검증은 끝났다.
3. 저장이 생기면 같이 생각할 것: 편집 중 나가기, 저장 안 된 버퍼 표시(사이드바 항목과 겹친다).

### 사용자와 정한 것, 그리고 이유
다시 논의하지 말고 이대로 진행한다.

| 결정 | 고른 것 | 이유 |
|---|---|---|
| 레포 구조 | 같은 레포, `:core` / `:app` / `:code` 멀티모듈 | 별도 레포는 SFTP 계층을 복사하게 되고, 두 사본이 따로 바뀌면서 달라진다 |
| 원격 실행 | 데몬 없이 SSH exec (프로젝트 모드에서만) | 데몬은 exec 없이 뜨지도 못하고(SFTP는 실행을 못 한다), 남에게 배포하는 앱이 남의 서버에 상주 프로세스를 심는 것은 "서버에 아무것도 설치하지 않는다"는 제약과 충돌한다. 임의의 서버가 대상이라 단일 산출물도 없다. 자세한 것과 데몬을 다시 꺼내는 조건은 `plan.md`의 "정한 것" 2번과 "범위 밖" |
| UI | 전부 WebView 하나 (TS + CodeMirror 6) | Compose와 WebView를 섞으면 테마를 두 곳에 적용해야 하고, 스크롤에 따른 메뉴 숨김도 브리지를 거친다 |
| 레이어 구조 (2026-09-20) | 뷰 하나 + 파일당 state 하나. 레이어는 `Compartment`의 확장 묶음 | 레이어마다 기능을 달리 다는 것은 compartment가 해 주고, 뷰를 나누면 파싱 트리·높이맵이 레이어 수만큼 생긴다. 근거는 `AGENTS.md`의 CM6 관찰 |
| 레이어 전환 시 스크롤 (2026-09-20) | 보던 줄을 잇는다. 레이어별로 따로 기억하지 않는다 | 사용자 결정. 같은 코드를 다른 방식으로 본다는 감각에 맞는다 |
| **링크가 준 경로 (2026-09-20)** | **보낸 앱이 Skiff가 아니면 서버와 경로를 보여주고 묻는다.** 판별은 `ComponentCaller` | 사용자 결정. alias는 어느 서버인지만 정하고 경로는 링크의 것이라, 이름만 맞히면 아무 파일이나 열렸다. `startPath` 제한은 정상 흐름을 막고, 저장할 때만 묻기는 여는 구멍을 남긴다 |
| **`main` 병합 (2026-09-20)** | **사용자가 언급할 때까지 꺼내지 않는다** | 사용자 지시 |
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
| 상단 메뉴 배경 (2026-09-20, 하루 만에 뒤집혔다) | **불투명 막대.** 버튼만 떠 있던 모양은 버렸다 | 사용자 결정. 버튼마다의 테두리·그림자가 없어지면서, 스크롤한 코드 위에서 ① 버튼이 줄 번호를 가리던 것도 같이 없어졌다 |
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
- **손가락으로 하는 핀치.** 지금까지 확인은 전부 CDP `Input.dispatchTouchEvent`로 만든 터치였다.
  "포커스된 에디터의 두 번째 touchstart" 문제도 CDP가 만든 것이 아니라 실제 hit-test 결과로 보이지만
  (블러하면 같은 CDP 터치가 정상 동작한다), **진짜 손가락으로 한 번 해 봐야 한다.**
- **editor의 손 사용감** 전반: 선택 핸들, 길게 누르기, 커서 옮기기.
- **undo에 닿을 UI가 아직 없다.** 키맵은 하드웨어 키보드용이고, 화면 키보드에는 undo가 없다. 커맨드
  팔레트(M4)가 생기기 전까지는 외장 키보드 없이 undo를 할 수 없다.
- **바뀐 호스트키 경고 창**을 기기에서 본 적이 없다. 서버의 키를 일부러 바꿔 한 번 봐야 한다.
- **서명이 다른 앱이 끼어드는 경로**(가짜 Skiff Code, 가짜 provider)는 그런 앱을 만들지 않아 보지 못했다.
- **Android 15 미만에서 링크가 전부 확인창을 받는 것**은 그런 기기가 없어 보지 못했다. 폴더블 폰이
  구버전이면 체감이 달라진다.
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
  테스트에 들어가기 쉽다. 픽스처와 문서는 `192.0.2.0/24`(RFC 5737)와 `.example`을 쓴다. **프로필
  이름도 마찬가지다** — 확인하느라 실제 이름을 쓰더라도 문서에는 `<실제 프로필 이름>`으로 적는다.
- **exec 규칙:** `:app`은 금지, `:code`는 허용. `AGENTS.md`의 "The SFTP side, and what it must not do" 참고.
- `npm install` 때 fsevents install 스크립트는 npm 11 정책으로 실행되지 않는다. macOS용 선택 의존성이라 영향 없다.
- 기기:
  - 시리얼은 `adb devices`로 얻고 레포에 적지 않는다.
  - **화면은 2분 뒤 꺼지고, 꺼진 화면에서는 탭이 그냥 사라지고 `screencap`은 검은 화면을 찍는다.**
    긴 확인을 시작하기 전에 `input keyevent KEYCODE_WAKEUP` → 잠금 해제 스와이프
    (`input swipe 1152 1300 1152 300 200`) → `dumpsys window | grep mCurrentFocus`로 앱이 앞에 있는지
    본다. 오래 걸릴 것 같으면 `settings put system screen_off_timeout 1800000`으로 올렸다가
    **끝나고 120000으로 되돌린다.**
  - 가로 방향(2304x1440)이고 화면은 하나라 `screencap`에 display id가 필요 없다.
  - 상단 메뉴 좌표(가로, 2304x1440): ② `tap 2088 117`, ③ `tap 2160 117`.
    확인 대화상자의 버튼: 취소 `tap 973 1307`, 열기 `tap 1326 1308`(한 줄짜리 본문이면 조금 위).
  - 아래 가장자리에서 시작하는 스와이프는 시스템 제스처에 먹히므로 y 250~1100 사이에서 한다.
  - **adb로는 멀티터치도 조합키도 만들 수 없다.** 둘 다 DevTools로 한다:
    `adb forward tcp:9222 localabstract:webview_devtools_remote_$(adb shell pidof com.naki.skiff.code)`
    → `curl -s localhost:9222/json`에서 `webSocketDebuggerUrl` → `Input.dispatchTouchEvent`(터치 점 두
    개, CSS px 기준. 화면 2304x1440은 CSS로 1152x675다)와 `Input.dispatchKeyEvent`(`modifiers: 2`가
    Ctrl, `char` 이벤트는 보내지 않는다). **`ws` 패키지는 설치돼 있지 않다** — Node 24의 내장
    `WebSocket`으로 충분하다. `performance.memory`도 여기서 읽는다.
  - **CM6 뷰에 DevTools에서 닿는 법:** `document.querySelector('.cm-content').cmTile.root.view`.
    높이 맵이 낡았는지 볼 때 쓴다(`view.contentHeight`, `view.defaultLineHeight`).
  - **외장 키보드(Corne)가 연결돼 있으면 화면 키보드가 뜨지 않는다.** `dumpsys input | grep Corne`.
    editor를 확인할 때는 빼야 한다.
  - Skiff Code 저장 파일을 adb로 고칠 때는 먼저 `am force-stop`한다. `run-as … cat`으로 읽고,
    `adb push`로 `/data/local/tmp`에 둔 뒤 `run-as … sh -c 'cat /data/local/tmp/x > files/datastore/skiffcode.json'`으로 쓴다.
  - provider 확인: `adb shell content query --uri content://com.naki.skiff.profiles/profiles`는
    **거부되는 것이 정상이다**(shell은 권한이 없다). 권한 부여는
    `dumpsys package com.naki.skiff.code | grep READ_PROFILES`로 본다.
  - 확인용 파일이 `/sdcard/Download/skiffcode-test/`에 있다(`big.ts` 2MB, `hello.py`, `readme.md`,
    인코딩·크기 거부용). **`hello.md`는 거기가 아니라 `/sdcard/Download/` 바로 아래에 있다** —
    화면보다 짧은 파일이라 줌·거터를 볼 때 쓴다.
- 환경:
  - `node`(v24)와 `npm`은 PATH에 있다.
  - `java`는 PATH에 없어서 `JAVA_HOME=/opt/homebrew/opt/openjdk@17`이 필요하다.
  - `adb`도 PATH에 없고 `/opt/homebrew/share/android-commandlinetools/platform-tools/adb`에 있다.
    `zipalign`과 `apksigner`는 `/opt/homebrew/share/android-commandlinetools/build-tools/37.0.0/`에 있다.
  - macOS에는 `timeout`이 없다. 오래 걸릴 수 있는 node 스크립트는 끝에 `process.exit(0)`를 둔다.
  - AOSP 소스를 봐야 할 때는
    `curl -s "https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android15-release/<경로>?format=TEXT" | base64 -d`.
    developer.android.com의 레퍼런스는 WebFetch로 본문이 안 나온다.
  - release APK를 기기에 넣을 때는 debug 키로 서명한다(`~/.android/debug.keystore`, 비밀번호 `android`,
    별칭 `androiddebugkey`). 그래야 기존 debug 앱 위에 덮여 앱 데이터가 남는다. 확인이 끝나면
    `installDebug`로 되돌린다.
  - worktree에는 gitignore된 `local.properties`가 따로 있어야 한다(메인 체크아웃에서 복사했다).

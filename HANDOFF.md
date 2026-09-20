# 인수인계

다음 세션이 이어받기 위한 문서다. **세션을 끝낼 때마다 이 파일을 현재 상태로 덮어쓴다.**
계속 쌓는 기록이 아니다. 지난 기록은 git 히스토리에 남는다.

- 할 일 목록과 설계: [`plan.md`](plan.md)의 `# Skiff Code` 섹션
- 규칙, 툴체인, 커밋 전 점검, 보고와 알림 규칙: [`AGENTS.md`](AGENTS.md)
- 이 문서에 담는 것: 위 두 문서에 없는 **직전 세션의 맥락**(무엇을 했고, 왜 그렇게 정했고, 무엇이 아직 확인되지 않았는지)

---

## 마지막 세션 (2026-09-20 밤 ~ 09-21 새벽): 폴더블 폰이 들어왔고, 그 폰이 찾아낸 것 세 가지를 고쳤다

### 지금 상태
- 브랜치 `plan/skiffcode`(git worktree). 커밋 `b67c58c` "Keep the soft keyboard out of the way of the
  document" 하나와 문서 커밋 하나. **push까지 끝냈다.**
- **M3는 여전히 `DocumentSaver`가 다음이다**(`plan.md`의 첫 `- [ ]`). 이번 세션은 계획에 없던
  버그 세 개를 치웠다.
- **`main`에는 아직 합치지 않았다.** 사용자가 "내가 언급할 때 하자"고 했으므로 **먼저 꺼내지 않는다.**
- 테스트: `:core` 35개, `:app` 24개, `:code` 92개. 실패 없음. lint 경고 `:app` 4개, `:code` 11개.
  **전부 지난 세션과 같은 수다.** 웹은 `npx tsc --noEmit` 통과.
- **기기가 두 대다.** 둘 다 같은 빌드가 깔려 있다.
  - 갤럭시탭 S10 FE (SM-X526N, Android 16/API 36) — Corne 외장 키보드를 붙였다 뗐다 할 수 있다.
  - **폴더블 (SM-F971N, Android 17/API 37, 120Hz, WebView 151)** — 새로 들어왔다. 자세한 것은 아래.

### 새 기기: 폴더블 (SM-F971N)

`plan.md`의 "실기기" 항목이 탭 하나였는데, 이제 둘이다. 폴더블이 지원 대상이라는 결정은 그대로다.

- 화면 둘. **내부** `2448x1848`(933x704dp), **커버** `1248x1972`(475x751dp). display id는 시리얼과
  같이 레포에 적지 않는다 — `dumpsys SurfaceFlinger --display-id`로 얻고, 먼저 나오는 것이 내부다.
  가로/세로가 아니라 **접힘 상태**가 어느 쪽을 켤지 정한다. `cmd device_state state 0`(CLOSED) / `3`(OPENED) / `reset`으로 바꿀 수 있다.
- **Android 17이라 이 폰에서만 볼 수 있는 것이 있다.** `ACCESS_LOCAL_NETWORK`가 런타임 권한이고
  지금 `granted=false`다. `AGENTS.md`가 "없으면 15초 멈춘 뒤 타임아웃"이라 적어 둔 그 경로를
  **아직 아무도 본 적이 없다.** SSH 서버가 있어야 한다.
- 시스템 글꼴 배율이 1.5다. 그래서 에디터 기본 14px이 21px로 나온다(`AGENTS.md`의 WebView textZoom).
- **`screencap`으로 커버 화면의 WebView를 찍을 수 없다.** 빈 프레임이나 반쯤 그려진 프레임이 나온다.
  화면을 믿지 말고 DevTools로 DOM을 읽는다. 두드릴 좌표를 얻는 법은 `AGENTS.md`의 On-device에 적었다.
- **접었다 펴면 USB가 재열거되어 adb가 1~2초 끊긴다.** 이번 세션 내내 수십 번 끊겼다. 명령 하나가
  "device not found"로 실패한 것을 결과로 읽지 말고 재시도해야 한다.
- 확인용 파일을 `/sdcard/Download/skiffcode-test/`에 두었다(`hello.md` 짧은 것, `big.ts` 3MB는
  상한 초과 확인용, `near2mb.ts` 1.9MB/24,167줄). 탭의 같은 디렉토리와 내용이 다르다.

### 이번 세션에서 한 것

**0. 두 앱을 폴더블에서 한 바퀴 돌렸다 (고칠 것을 찾으려고)**

정상이던 것: 설치와 권한(서명 보호 `READ_PROFILES` 포함), 로컬 탐색, 분할 화면(커버 화면에서도
동작한다), 마크다운 뷰어, 레이어 전환, 크기 상한 거부(3MB → "2 MB보다 큰 파일은 열지 않습니다"),
`?line=` 점프, **접었다 펴도 문서·레이어·거터 정렬이 그대로 따라온다**.

- **링크 경로 확인이 Android 17에서도 맞게 동작한다.** adb 링크는 확인창(`open LocalPath from an app
  that did not say`), Skiff에서 탭하면 확인창 없이(`from com.naki.skiff`).
- **지난 세션의 줌 고침이 이 폰에서도 유효하다.** 8줄짜리 짧은 파일을 핀치로 확대해도 거터 89.25 /
  본문 89.25 / `defaultLineHeight` 89.25가 일치한다. ② 원래 크기로 31.5/31.5.
- **성능은 탭보다 낫다.** 1.9MB/24,167줄에서 스크롤 프레임 중앙값 8.3ms = **120fps**(p95 8.4ms,
  최악 16.7ms), JS 힙 48.1MB. 탭은 86~91fps였다.

**1. 에디터 레이어에 들어가면 화면 키보드가 문서를 가렸다**

`setLayer`가 에디터에 들어갈 때 `view.focus()`를 불렀고, 그것이 곧 화면 키보드였다. **탭에서는 외장
키보드 때문에 화면 키보드가 아예 뜨지 않아 보이지 않던 문제다.**

- 사용자 결정: 처음에는 "포커스를 아예 주지 말라"였고, 그러면 외장 키보드에서 한 번 탭해야
  타이핑이 시작된다고 알린 뒤 **"하드웨어 키보드가 붙어 있을 때만 포커스"**로 정해졌다.
- **`Configuration.keyboard`로 짰다가 틀렸다.** 탭에 Corne가 붙어 타이핑이 되는데도 그 필드는
  `nokeys`였다(`am get-config`가 `keysexposed-nokeys`, `dumpsys input`은 같은 시각에 그 키보드를
  `Enabled: true`로 표시). 한 번 true가 나왔던 관찰이 있었는데 재현되지 않아 버렸다.
- `InputDevice`에 직접 묻는 것으로 바꿨고 그제서야 양쪽 분기가 실기기에서 재현됐다.

**2. 설정이 바뀌면 링크를 다시 열었다 (사용자가 발견했다)**

"하드웨어 키보드를 끊었을 때 다시 여는 문제가 있어."

- **키보드를 켜면 `keyboard`만이 아니라 `navigation`까지 바뀐다.** 감시기로 잡았다:
  `keysexposed-nokeys-navhidden-nonav` → `keysexposed-qwerty-navexposed-dpad`. 키보드가 d-pad로도
  등록되기 때문이다. `navigation`이 `configChanges`에 없어 액티비티가 재생성됐다.
- 재생성되면 `onCreate`가 **같은 intent로 `handleLink`를 다시 돌린다.** 보낸 앱 정보도 살아남지 않아
  Skiff가 보낸 파일인데도 확인창이 다시 떴다.
- 두 가지를 다 했다. `configChanges`에 `keyboard|navigation`을 넣어 이 경우 재생성을 막았고,
  `onRetainNonConfigurationInstance`로 **열린 문서를 넘겨** 재생성 일반을 견디게 했다. 뒤엣것은
  `fontScale`을 두 번 바꿔 재생성을 두 번 일으켜 검증했다(문서가 살아남고 링크는 다시 열리지 않았다).

**3. 화면 하단을 탭해 키보드를 올리면 캐럿이 가려졌다 (사용자가 발견했다)**

WebView가 줄어드는 것까지는 되는데(ime inset이 프레임에 들어간다), **선택이 움직이지 않으므로 CM6가
캐럿을 다시 불러오지 않았다.** 1601줄 파일에서 뷰포트 675→337, 캐럿은 y 590에 남았다. `pane.ts`가
`resize`에서 `scrollIntoView(y: 'nearest')`를 건다. 고친 뒤 같은 탭에서 캐럿 y 262(보이는 범위 0~337).

### 기기에서 확인한 것 (두 대 모두)

| | 탭 (Corne 있음) | 탭 (Corne 뺐을 때) | 폴더블 (키보드 없음) |
|---|---|---|---|
| ③로 에디터 진입 | 포커스 잡음, 캐럿 화면 맨 위 줄 | 포커스 안 잡음 | 포커스 안 잡음 |
| 본문 탭 | — | 포커스 + 캐럿이 탭한 자리 | 포커스 + 캐럿이 탭한 자리 |
| 열어 둔 채 키보드 뺐다 꽂기 | 링크 다시 안 열림, WebView 1개, 문서 유지 | — | (키보드 없음) |
| 하단 탭 → 키보드 | — | 캐럿 y 262 (범위 0~337) | 캐럿 y 371 (범위 0~451) |
| 접었다 펴기 | — | — | 링크 다시 안 열림, 24,167줄 문서 유지 |

### 다음 세션이 할 일
1. `AGENTS.md`를 읽는다. 특히 툴체인 제약(이번에 `:code` 항목이 여섯 개 늘었다), Before committing
   (**커밋마다, push마다 사용자에게 먼저 묻는다**), 보고와 알림 규칙.
2. **`plan.md`의 첫 `- [ ]`인 `DocumentSaver`**를 한다.
3. 저장이 생기면 같이 생각할 것: 편집 중 나가기, 저장 안 된 버퍼 표시(사이드바 항목과 겹친다),
   그리고 **재생성 때 레이어와 스크롤도 넘길지**(아래 "고치지 않고 둔 것" 1번).

### 고치지 않고 둔 것 (이번 세션에서 드러났다)
사용자에게 알렸고, 범위 밖이라 손대지 않았다.

1. **재생성 때 문서만 넘기고 레이어와 스크롤은 넘기지 않는다.** `uiMode`(다크 모드), 언어, 글꼴 크기는
   `configChanges`에 없어서, 바꾸면 보던 레이어가 뷰어로 돌아간다. 무엇까지 넘길지는 M3 저장과 같이
   정하는 게 맞아 보인다. 일부러 재생성시키려면 `settings put system font_scale 1.3` 후 되돌린다.
2. **줄바꿈이 없어 좁은 화면에서 문장이 잘린다.** 커버 화면(475dp)에서 마크다운 본문 한 줄이 오른쪽으로
   사라진다. `plan.md`의 `settings.toml`에 항목으로 잡혀 있지만 **기본값을 무엇으로 둘지**가 폴더블
   때문에 실제 문제가 됐다.
3. **경로 빵부스러기가 좁은 화면에서 오른쪽으로 잘린다**(Skiff, 커버 화면). 현재 폴더 쪽이 보이는 게
   맞을 듯하다.
4. **시스템 글꼴 배율이 그대로 곱해진다.** 폰의 1.5 때문에 14px이 21px이 된다. 따를지 지울지 정해야 한다.
5. 한 번 봤지만 재현하지 못한 것: Skiff 화면 아래 절반이 **빈 키보드 창**에 먹혀 있었다
   (`mInputShown=true`, 창 부모 프레임이 1848이 아니라 1012px). 접기/펴기 직후였다. 같은 순서로 세 번
   더 해봤지만 재현되지 않아 확정된 버그로 적지 않는다.

### 사용자와 정한 것, 그리고 이유
다시 논의하지 말고 이대로 진행한다.

| 결정 | 고른 것 | 이유 |
|---|---|---|
| 레포 구조 | 같은 레포, `:core` / `:app` / `:code` 멀티모듈 | 별도 레포는 SFTP 계층을 복사하게 되고, 두 사본이 따로 바뀌면서 달라진다 |
| 원격 실행 | 데몬 없이 SSH exec (프로젝트 모드에서만) | 데몬은 exec 없이 뜨지도 못하고(SFTP는 실행을 못 한다), 남에게 배포하는 앱이 남의 서버에 상주 프로세스를 심는 것은 "서버에 아무것도 설치하지 않는다"는 제약과 충돌한다. 자세한 것은 `plan.md`의 "정한 것" 2번 |
| UI | 전부 WebView 하나 (TS + CodeMirror 6) | Compose와 WebView를 섞으면 테마를 두 곳에 적용해야 하고, 스크롤에 따른 메뉴 숨김도 브리지를 거친다 |
| 레이어 구조 | 뷰 하나 + 파일당 state 하나. 레이어는 `Compartment`의 확장 묶음 | 뷰를 나누면 파싱 트리·높이맵이 레이어 수만큼 생긴다. 근거는 `AGENTS.md`의 CM6 관찰 |
| 레이어 전환 시 스크롤 | 보던 줄을 잇는다. 레이어별로 따로 기억하지 않는다 | 사용자 결정 |
| 링크가 준 경로 (2026-09-20) | 보낸 앱이 Skiff가 아니면 서버와 경로를 보여주고 묻는다. 판별은 `ComponentCaller` | 사용자 결정. alias는 어느 서버인지만 정하고 경로는 링크의 것이라, 이름만 맞히면 아무 파일이나 열렸다 |
| **에디터 진입 시 포커스 (2026-09-21)** | **하드웨어 키보드가 붙어 있을 때만 포커스한다** | 사용자 결정. 없으면 화면 키보드가 문서의 절반을 가린다. 처음 지시는 "아예 포커스하지 말라"였고, 외장 키보드에서 한 번 탭해야 한다는 점을 알린 뒤 조건부로 정해졌다 |
| **`main` 병합 (2026-09-20)** | **사용자가 언급할 때까지 꺼내지 않는다** | 사용자 지시 |
| 서버 정보 공유 | Skiff가 서명 보호 Provider로 프로필만 공유, 비밀번호는 각자 저장 | Keystore 키는 앱마다 따로라 암호문을 공유할 수 없고, 비밀번호를 넘기면 평문이 IPC를 지나간다 |
| 프로필 합치기 (2026-09-19) | Skiff id → 이름 순으로 찾아 Skiff의 주소·시작 경로를 받고 자기 id와 비밀번호는 유지. host·port·user가 바뀌면 비밀번호를 지운다. **지우지는 않는다** | 사용자 결정 |
| 호스트키 합치기 (2026-09-19) | 그 host:port에 **없을 때만** 받는다 | 사용자 결정. 어느 쪽이 맞는지는 `HostKeyGate`의 경고 창이 판단할 일이다 |
| 받는 쪽 서명 확인 (2026-09-19) | 서로 같은 키로 서명됐는지 본다 | 사용자 결정 |
| 실기기 | **갤럭시탭 S10 FE와 폴더블(SM-F971N) 두 대** | 폴더블도 지원 대상이라 관련 문서와 코드는 지우지 않는다 |
| CM6 버그 처리 | **upstream에 올리지 않는다.** M5 diff 레이어에서 `patch-package`로 패치한다 | CodeMirror는 AI가 쓴 코드를 받지 않는다 |
| TOML | ktoml을 쓴다 | M0에서 확인했다 |
| exec | `:app`은 금지, `:code`는 허용. 근거는 **"셸 없는 `internal-sftp` 계정을 지원하기 위해"** 다 | 금지의 출처가 AI의 잘못된 유도였던 것이 드러났다. 규칙은 남기고 이유를 고쳤다 |
| 상단 메뉴 | ① 사이드바, ② 원래 크기, ③ 레이어 토글, ④ 더보기. 불투명 막대 | 확대/축소는 핀치로 한다 |
| 보고 방식 | 작업마다 한국어로 보고하고 푸시 알림을 보낸다 | 사용자가 자리를 비울 때가 많다 |

### 사용자 확인을 아직 받지 않은 가정
해당 단계에 들어가기 전에 한 번 물어본다.
1. diff와 git 거터의 기본 비교 대상은 **HEAD와 현재 버퍼**다. (M5 전)
2. 원격 LSP 서버는 자동 설치하지 않고 PATH에서 찾는다. (M6 전)

### 실제 LSP 서버 확인 (M0에서 한 것, M6에서 다시 필요하다)
스파이크 하네스는 사용자 지시로 지웠다. 다시 만들 때 필요한 것은 이것뿐이다.
- 서버 쪽: MINA `SshServer`에 `commandFactory = ProcessShellCommandFactory.INSTANCE`.
- 클라이언트 쪽: sshj `session.exec("/bin/sh -lc 'cd <root> && exec <command>'")`.
- 프레이밍: 헤더는 `\r\n\r\n`까지 한 바이트씩, 본문은 `Content-Length`만큼 채워 읽는다.
- 언어 서버: `npm i pyright` → `node_modules/.bin/pyright-langserver --stdio`. 레포에 넣지 않았다.
- **`org.json`은 유닛 테스트 JVM에서 스텁이라 전부 던진다.** `org.json:json`을 테스트 의존성으로 넣고
  Gradle에서는 `systemProperty`로 넘긴다(`-D`는 테스트 JVM에 전달되지 않는다).
- 결과: initialize 98ms, 진단까지 346ms(로컬 루프백). `positionEncoding`은 응답에 없고(= `utf-16`).

### 아직 확인하지 않은 것
안 되는 게 나오면 `plan.md`의 설계를 먼저 고친다.
- **SSH를 쓰는 것 전부를 이번 세션에 한 번도 확인하지 못했다.** 두 기기 모두 프로필이 0개다. 그래서
  아래가 통째로 미확인이다: 호스트키 TOFU 창, 비밀번호 저장, Skiff→Skiff Code 프로필 공유,
  그리고 **Android 17의 `ACCESS_LOCAL_NETWORK` 요청**(폴더블에서만 볼 수 있다). 이 맥의 원격 로그인을
  켜고 기기에 프로필을 등록하면 된다 — **비밀번호 입력은 사용자가 직접 해야 한다.**
- **손가락으로 하는 핀치.** 지금까지 확인은 전부 CDP `Input.dispatchTouchEvent`로 만든 터치였다.
- **editor의 손 사용감** 전반: 선택 핸들, 길게 누르기, 커서 옮기기.
- **undo에 닿을 UI가 아직 없다.** 커맨드 팔레트(M4) 전까지 외장 키보드 없이 undo를 할 수 없다.
- **바뀐 호스트키 경고 창**을 기기에서 본 적이 없다.
- **서명이 다른 앱이 끼어드는 경로**(가짜 Skiff Code, 가짜 provider)는 그런 앱을 만들지 않아 보지 못했다.
- **Android 15 미만에서 링크가 전부 확인창을 받는 것.** 두 기기 다 15 이상이다.
- release APK의 배포용 서명. 지난번에는 debug 키로 서명해서 확인만 했다.
- **실제 SSH 서버로 LSP를 띄워 본 적은 없다.** M0의 확인은 이 맥 안의 MINA 루프백이다.
- 진단이 수백~수천 개일 때의 비용. 큰 파일에서 편집 중 동기화 비용.
- lezer 심볼 추출, 테마 CSS 변수, SAF import/export는 라이브러리 버전도 고르지 않았다.

### 주의할 점
- **세션을 끝낼 때 `plan.md` 체크리스트를 갱신하고 이 파일을 덮어쓴다.**
- **레포에 기기나 네트워크를 알아볼 수 있는 것을 남기지 않는다.** 호스트 이름, IP, 계정, 지문이 문서와
  테스트에 들어가기 쉽다. 픽스처와 문서는 `192.0.2.0/24`(RFC 5737)와 `.example`을 쓴다. **프로필
  이름도 마찬가지다.** 주변기기 이름도 일반화한다(커밋에서 "Corne"를 "a Bluetooth keyboard"로 바꿨다).
- **exec 규칙:** `:app`은 금지, `:code`는 허용.
- `npm install` 때 fsevents install 스크립트는 npm 11 정책으로 실행되지 않는다. macOS용 선택 의존성이라 영향 없다.
- 기기 공통:
  - 시리얼은 `adb devices`로 얻고 레포에 적지 않는다. 두 대가 붙어 있으므로 `ANDROID_SERIAL` 또는
    `adb -s`를 **항상** 쓴다. Gradle도 `ANDROID_SERIAL`을 본다.
  - **adb로는 멀티터치도 조합키도 만들 수 없다.** 둘 다 DevTools로 한다:
    `adb forward tcp:9222 localabstract:webview_devtools_remote_$(adb shell pidof com.naki.skiff.code)`
    → `curl -s localhost:9222/json`에서 `webSocketDebuggerUrl` → `Input.dispatchTouchEvent`(터치 점 두
    개, CSS px 기준)와 `Input.dispatchKeyEvent`(`modifiers: 2`가 Ctrl). **`ws` 패키지는 설치돼 있지
    않다** — Node 24의 내장 `WebSocket`으로 충분하다. `performance.memory`도 여기서 읽는다.
  - **DevTools 타깃이 여러 개일 수 있다.** 액티비티가 재생성되면 죽은 WebView의 타깃이 남아서, `[0]`을
    집으면 엉뚱한 페이지를 읽는다(이번에 "문서가 비어 있다"는 잘못된 값을 한 번 읽었다).
    `document.visibilityState === 'visible'`인 타깃을 골라야 한다. 타깃 개수는 재생성 횟수를 세는
    데에도 쓸 수 있고, `performance.now()`가 초기화됐는지로도 같은 것을 본다.
  - **CM6 뷰에 DevTools에서 닿는 법:** `document.querySelector('.cm-content').cmTile.root.view`.
  - Skiff Code 저장 파일을 adb로 고칠 때는 먼저 `am force-stop`한다. `run-as … cat`으로 읽고,
    `adb push`로 `/data/local/tmp`에 둔 뒤 `run-as … sh -c 'cat … > files/datastore/skiffcode.json'`.
  - provider 확인: `adb shell content query --uri content://com.naki.skiff.profiles/profiles`는
    **거부되는 것이 정상이다**(shell은 권한이 없다). 권한 부여는
    `dumpsys package com.naki.skiff.code | grep READ_PROFILES`로 본다.
- 탭 (SM-X526N):
  - 가로 방향(2304x1440), 화면 하나라 `screencap`에 display id가 필요 없다.
  - 상단 메뉴 좌표: ② `tap 2088 117`, ③ `tap 2160 117`. 확인 대화상자: 취소 `tap 973 1307`,
    열기 `tap 1326 1308`.
  - **화면은 2분 뒤 꺼진다.** 긴 확인 전에 `settings put system screen_off_timeout 1800000`으로 올렸다가
    **끝나고 120000으로 되돌린다.** 깨우기는 `input keyevent KEYCODE_WAKEUP` → `input swipe 1152 1300 1152 300 200`.
  - **외장 키보드(Corne)가 연결돼 있으면 화면 키보드가 뜨지 않는다.** `dumpsys input | grep Corne`은
    떼어낸 뒤에도 항목이 남아 믿을 수 없다 — `am get-config`의 `nokeys`/`qwerty`로 본다.
- 폴더블 (SM-F971N):
  - 화면 두 개, 접힘 상태가 어느 쪽을 켤지 정한다. 좌표는 그때그때 다시 잰다.
  - **커버 화면의 WebView는 `screencap`으로 못 찍는다.** 좌표를 얻는 법은 `AGENTS.md`의 On-device.
  - 내부 화면 상단 메뉴: ② `tap 2162 180`, ③ `tap 2266 180`. 확인 대화상자 열기 `tap 1453 1680`.
  - **화면은 10분 뒤 꺼진다**(기본값 600000). 올렸으면 그 값으로 되돌린다.
  - 접기/펴기마다 adb가 끊긴다. 재시도를 넣는다.
- 환경:
  - `node`(v24)와 `npm`은 PATH에 있다. Gradle 데몬에도 `npm`이 있어야 한다(`:code:preBuild` → `buildWeb`).
  - `java`는 PATH에 없어서 `JAVA_HOME=/opt/homebrew/opt/openjdk@17`이 필요하다.
  - `adb`도 PATH에 없고 `/opt/homebrew/share/android-commandlinetools/platform-tools/adb`에 있다.
    `aapt2`, `zipalign`, `apksigner`는 `…/build-tools/37.0.0/`에 있다.
  - macOS에는 `timeout`이 없다. 오래 걸릴 수 있는 node 스크립트는 끝에 `process.exit(0)`를 둔다.
  - **USB 허브를 지나는 연결이 불안정하다.** 접기/펴기가 아니어도 `unauthorized`로 떨어진 적이 있다
    (기기에서 "USB 디버깅 허용"을 다시 눌러야 했다).
  - AOSP 소스를 봐야 할 때는
    `curl -s "https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android15-release/<경로>?format=TEXT" | base64 -d`.
    developer.android.com의 레퍼런스는 WebFetch로 본문이 안 나온다.
  - release APK를 기기에 넣을 때는 debug 키로 서명한다(`~/.android/debug.keystore`, 비밀번호 `android`,
    별칭 `androiddebugkey`). 확인이 끝나면 `installDebug`로 되돌린다.
  - worktree에는 gitignore된 `local.properties`가 따로 있어야 한다(메인 체크아웃에서 복사했다).

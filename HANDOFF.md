# 인수인계

다음 세션이 이어받기 위한 문서다. **세션을 끝낼 때마다 이 파일을 현재 상태로 덮어쓴다.**
계속 쌓는 기록이 아니다. 지난 기록은 git 히스토리에 남는다.

- 할 일 목록과 설계: [`plan.md`](plan.md)의 `# Skiff Code` 섹션
- 규칙, 툴체인, 커밋 전 점검, 보고와 알림 규칙: [`AGENTS.md`](AGENTS.md)
- 이 문서에 담는 것: 위 두 문서에 없는 **직전 세션의 맥락**(무엇을 했고, 왜 그렇게 정했고, 무엇이 아직 확인되지 않았는지)

---

## 마지막 세션 (2026-09-21 새벽): SSH를 처음 실제로 확인했고, 호스트키 버그 둘을 고치고, `DocumentSaver`를 했다

### 지금 상태
- 브랜치 `plan/skiffcode`(git worktree). 이번 세션 커밋 세 개 — `7236076` 호스트키 두 가지 수정,
  `8baae67` `DocumentSaver`, 그리고 이 인수인계. **push까지 끝냈다.**
- **`plan.md`의 첫 `- [ ]`는 이제 `FileWatcher`다.** `DocumentSaver`는 체크했다.
- **`main`에는 아직 합치지 않았다.** 사용자가 "내가 언급할 때 하자"고 했으므로 **먼저 꺼내지 않는다.**
- 테스트: `:core` 37개(+2), `:app` 24개, `:code` 103개(+11). 실패 없음. lint 경고 `:app` 4개, `:code` 11개로 지난 세션과 같다.
- 기기는 두 대지만 **이번 세션에 붙어 있던 것은 탭뿐이다.**
  - 갤럭시탭 S10 FE (SM-X526N, Android 16/API 36) — 블루투스 키보드를 붙였다 뗐다 할 수 있다.
  - 폴더블 (SM-F971N, Android 17/API 37, 120Hz, WebView 151) — 이번 세션에는 USB에 없었다.

### 이번 세션에서 한 것

**1. 바뀐 호스트키 경고 창을 처음으로 기기에서 봤다 (탭)**

확인 방법: `am force-stop` 뒤 저장소의 그 host:port 지문만 가짜로 바꿔치기하고 adb로 원격 링크를 보낸다.
창은 붉은 제목 "호스트키가 바뀌었습니다", 서버가 제시한 지문과 "이전 지문"을 나란히 보여준다.

- **거부**하면 파일이 열리지 않고 저장된 지문도 그대로다("서버의 호스트키를 신뢰하지 않았습니다.").
- **수락**하면 지문이 갱신되고 원격 파일이 열린다.
- 덤으로, **저장된 비밀번호로 실제 접속이 되는 것까지** 한 바퀴 확인됐다(Keystore 복호화 → 접속 → SFTP 읽기 → 마크다운 렌더, DevTools로 DOM 확인).

**2. 그 과정에서 드러난 버그 두 개를 고쳤다 (`7236076`, `:core`라 두 앱 모두)**

- **창이 30초를 못 넘겼다.** `HostKeyGate`가 sshj 전송 스레드를 붙잡는 동안 키 교환이 타임아웃되면
  창이 사라지고 "연결이 끊어졌습니다"가 뜬다. **진단이 처음엔 틀렸었다** — 그 30초는 우리가 넣은
  `READ_TIMEOUT_MS`가 아니라 `TransportImpl`의 기본값이고, `client.timeout`은 소켓 `SO_TIMEOUT`까지만
  닿는다(값이 우연히 같아서 헷갈렸다). 지금은 접속 구간에만 `client.transport.timeoutMs`를 5분으로
  올리고 키 교환이 끝나면 되돌린다. **대가: 소켓은 받고 말이 없는 서버는 30초가 아니라 5분을 기다린다.**
- **거부하면 한 번 더 물었다.** `withSftp`가 변환 전 `IOException`을 `isFatalAuth`에 넘기고 있었고,
  호스트키 거부는 `TransportException`이라 걸리지 않아 재연결 재시도에 빨려 들어갔다. 변환 후에
  판단한다.
- 기기 확인: 취소 한 번에 끝났고, 창을 **2분 12초** 두었다가 신뢰해도 파일이 열렸다.
- 통제 테스트(`the key exchange still gives up on its own timeout`)가 틀린 진단을 잡아냈다. 타임아웃을
  바꾸는 수정에는 **반대 방향 테스트를 같이 둔다.**

**3. `DocumentSaver` + `DocumentSaverTest` (`8baae67`, M3의 그 항목)**

`TextLoader`의 대칭이다. 쓰기 전 `stat`으로 크기·mtime 비교 → 제자리 truncate 쓰기 → 쓴 뒤 다시 `stat`.
자세한 것과 결정 이유는 `plan.md`의 체크된 항목에 적었다. **아직 앱에서 부를 수 없다** — 저장을 거는
UI는 M4의 커맨드 레지스트리다.

### 지난 인수인계에서 틀렸던 것
- "두 기기 모두 프로필이 0개다"는 **탭에 대해서는 틀렸다.** 탭에는 Skiff에 프로필 2개·호스트키 2개,
  Skiff Code에 프로필 3개·호스트키 3개가 있고 비밀번호도 저장돼 있다. 폴더블은 확인하지 못했다.
- 탭 화면 꺼짐은 2분이 아니라 **5분(300000)** 이다. 올렸으면 그 값으로 되돌린다.

### 다음 세션이 할 일
1. `AGENTS.md`를 읽는다. 특히 툴체인 제약(이번에 sshj 항목이 둘 늘었다), Before committing
   (**커밋마다, push마다 사용자에게 먼저 묻는다**), 보고와 알림 규칙.
2. **`plan.md`의 첫 `- [ ]`인 `FileWatcher`**를 한다. 원격 2초 폴링(별도 연결), `FileObserver`,
   `content://` 폴링. 깨끗한 버퍼는 트랜잭션으로 병합하고 수정 중이면 배너.
   - `DocumentSaver`의 충돌 검사는 **초 단위 mtime**이라 같은 초에 크기까지 같은 변경은 못 본다.
     그 창을 좁히는 것이 이 항목이다.
3. 폴더블을 연결할 수 있으면 SSH 나머지(아래 "아직 확인하지 않은 것" 첫 항목)를 본다.

### 아직 확인하지 않은 것
안 되는 게 나오면 `plan.md`의 설계를 먼저 고친다.
- **폴더블에서는 SSH를 쓰는 것을 하나도 보지 못했다.** 프로필이 0개인지도 확인 못 했다. 거기서만 볼 수
  있는 것이 **Android 17의 `ACCESS_LOCAL_NETWORK` 요청**이다(`AGENTS.md`가 "없으면 15초 멈춘 뒤
  타임아웃"이라 적어 둔 그 경로를 아직 아무도 본 적이 없다). 호스트키 TOFU 창과 Skiff→Skiff Code
  프로필 공유도 거기서는 처음이다. 이 맥의 원격 로그인은 켜져 있다 — **비밀번호 입력은 사용자가 직접 해야 한다.**
- **Skiff(`:app`)의 호스트키 창은 기기에서 보지 않았다.** 로직은 `:core` 공용이라 이번 수정이 이미
  적용됐고, 남은 것은 다이얼로그 UI뿐이다(같은 방법으로 10분이면 본다).
- **`DocumentSaver`를 실제 앱에서 돌려본 적이 없다.** 저장을 거는 UI가 M4라 유닛 테스트뿐이다.
- **손가락으로 하는 핀치.** 지금까지 확인은 전부 CDP `Input.dispatchTouchEvent`로 만든 터치였다.
- **editor의 손 사용감** 전반: 선택 핸들, 길게 누르기, 커서 옮기기.
- **undo에 닿을 UI가 아직 없다.** 커맨드 팔레트(M4) 전까지 외장 키보드 없이 undo를 할 수 없다.
- **서명이 다른 앱이 끼어드는 경로**(가짜 Skiff Code, 가짜 provider)는 그런 앱을 만들지 않아 보지 못했다.
- **Android 15 미만에서 링크가 전부 확인창을 받는 것.** 두 기기 다 15 이상이다.
- release APK의 배포용 서명. 지난번에는 debug 키로 서명해서 확인만 했다.
- **실제 SSH 서버로 LSP를 띄워 본 적은 없다.** M0의 확인은 이 맥 안의 MINA 루프백이다.
- 진단이 수백~수천 개일 때의 비용. 큰 파일에서 편집 중 동기화 비용.
- lezer 심볼 추출, 테마 CSS 변수, SAF import/export는 라이브러리 버전도 고르지 않았다.

### 고치지 않고 둔 것
사용자에게 알렸고, 범위 밖이라 손대지 않았다.

1. **재생성 때 문서만 넘기고 레이어와 스크롤은 넘기지 않는다.** `uiMode`(다크 모드), 언어, 글꼴 크기는
   `configChanges`에 없어서, 바꾸면 보던 레이어가 뷰어로 돌아간다. 일부러 재생성시키려면
   `settings put system font_scale 1.3` 후 되돌린다.
2. **줄바꿈이 없어 좁은 화면에서 문장이 잘린다.** 커버 화면(475dp)에서 마크다운 본문 한 줄이 오른쪽으로
   사라진다. `plan.md`의 `settings.toml`에 항목으로 잡혀 있지만 **기본값을 무엇으로 둘지**가 폴더블
   때문에 실제 문제가 됐다.
3. **경로 빵부스러기가 좁은 화면에서 오른쪽으로 잘린다**(Skiff, 커버 화면). 현재 폴더 쪽이 보이는 게
   맞을 듯하다.
4. **시스템 글꼴 배율이 그대로 곱해진다.** 폴더블의 1.5 때문에 14px이 21px이 된다. 따를지 지울지 정해야 한다.
5. **키 교환 대기가 5분이 됐다.** 위 1번 수정의 대가다. 줄이고 싶으면 `SshClientFactory.KEX_TIMEOUT_MS`다.
6. 한 번 봤지만 재현하지 못한 것: Skiff 화면 아래 절반이 **빈 키보드 창**에 먹혀 있었다(폴더블,
   접기/펴기 직후). 세 번 더 해봤지만 재현되지 않아 확정된 버그로 적지 않는다.

### 사용자와 정한 것, 그리고 이유
다시 논의하지 말고 이대로 진행한다.

| 결정 | 고른 것 | 이유 |
|---|---|---|
| 레포 구조 | 같은 레포, `:core` / `:app` / `:code` 멀티모듈 | 별도 레포는 SFTP 계층을 복사하게 되고, 두 사본이 따로 바뀌면서 달라진다 |
| 원격 실행 | 데몬 없이 SSH exec (프로젝트 모드에서만) | 데몬은 exec 없이 뜨지도 못하고(SFTP는 실행을 못 한다), 남에게 배포하는 앱이 남의 서버에 상주 프로세스를 심는 것은 "서버에 아무것도 설치하지 않는다"는 제약과 충돌한다 |
| UI | 전부 WebView 하나 (TS + CodeMirror 6) | Compose와 WebView를 섞으면 테마를 두 곳에 적용해야 하고, 스크롤에 따른 메뉴 숨김도 브리지를 거친다 |
| 레이어 구조 | 뷰 하나 + 파일당 state 하나. 레이어는 `Compartment`의 확장 묶음 | 뷰를 나누면 파싱 트리·높이맵이 레이어 수만큼 생긴다. 근거는 `AGENTS.md`의 CM6 관찰 |
| 레이어 전환 시 스크롤 | 보던 줄을 잇는다. 레이어별로 따로 기억하지 않는다 | 사용자 결정 |
| 링크가 준 경로 (2026-09-20) | 보낸 앱이 Skiff가 아니면 서버와 경로를 보여주고 묻는다. 판별은 `ComponentCaller` | 사용자 결정. alias는 어느 서버인지만 정하고 경로는 링크의 것이라, 이름만 맞히면 아무 파일이나 열렸다 |
| 에디터 진입 시 포커스 (2026-09-21) | 하드웨어 키보드가 붙어 있을 때만 포커스한다 | 사용자 결정. 없으면 화면 키보드가 문서의 절반을 가린다 |
| **저장 시 끝 줄바꿈 (2026-09-21)** | **버퍼가 정한다. 불러올 때의 상태로 되돌리지 않는다** | 사용자 결정. CM6는 끝 줄바꿈을 저절로 넣지도 빼지도 않으므로 화면에 보이는 것이 그대로 파일이 된다. `TextFormat.finalNewline`은 기록으로만 남는다 |
| **저장 시 인코딩 (2026-09-21)** | **엄격하게 한다. 못 쓰는 글자가 있으면 저장을 거부한다** | 사용자 결정. `?`로 바꾸면 조용한 데이터 손실이고, `TextLoader`가 대체 문자로 읽지 않는 것과 같은 이유다 |
| **`content://` 저장 (2026-09-21)** | **하지 않는다. 읽기 전용이다** | 사용자 결정. `stat`이 없어 충돌 비교가 성립하지 않고 `ContentResolver`가 필요해 성격이 다르다 |
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

### 주의할 점
- **세션을 끝낼 때 `plan.md` 체크리스트를 갱신하고 이 파일을 덮어쓴다.**
- **레포에 기기나 네트워크를 알아볼 수 있는 것을 남기지 않는다.** 호스트 이름, IP, 계정, 지문이 문서와
  테스트에 들어가기 쉽다. 픽스처와 문서는 `192.0.2.0/24`(RFC 5737)와 `.example`을 쓴다. **프로필
  이름도 마찬가지다.** 주변기기 이름도 일반화한다.
- **exec 규칙:** `:app`은 금지, `:code`는 허용.
- `npm install` 때 fsevents install 스크립트는 npm 11 정책으로 실행되지 않는다. macOS용 선택 의존성이라 영향 없다.
- 기기 공통:
  - 시리얼은 `adb devices`로 얻고 레포에 적지 않는다. 두 대가 붙어 있으므로 `ANDROID_SERIAL` 또는
    `adb -s`를 **항상** 쓴다. Gradle도 `ANDROID_SERIAL`을 본다.
  - **네이티브 다이얼로그는 `uiautomator dump`로 읽고 두드린다.** `adb shell uiautomator dump /sdcard/ui.xml`
    → `text`와 `bounds`를 뽑아 중앙을 `input tap`. **앞선 창이 막 닫힌 직후의 탭은 먹히지 않을 때가
    있어** 1초쯤 두고 누른다.
  - **`screencap`이 다이얼로그가 떠 있는 화면을 비게 찍은 적이 있다**(탭, 이번 세션). 화면 대신
    `uiautomator dump`(네이티브)와 DevTools(페이지)를 믿는다.
  - **`adb shell run-as … sh -c '…'`는 명령 전체를 한 번 더 따옴표로 감싸야 한다.** 안 그러면 adb가
    인자를 공백으로 이어 붙여 `sh -c cd`만 실행되고 나머지가 기기 셸에서 돌아, 앱 디렉터리가 아닌 곳에
    리다이렉트하려다 실패한다. 저장 파일을 고칠 때는 먼저 `am force-stop`하고,
    `adb push`로 `/data/local/tmp`에 둔 뒤 `chmod 644`하고 넘긴다.
  - **adb로는 멀티터치도 조합키도 만들 수 없다.** 둘 다 DevTools로 한다:
    `adb forward tcp:9222 localabstract:webview_devtools_remote_$(adb shell pidof com.naki.skiff.code)`
    → `curl -s localhost:9222/json`에서 `webSocketDebuggerUrl` → `Input.dispatchTouchEvent`(터치 점 두
    개, CSS px 기준)와 `Input.dispatchKeyEvent`(`modifiers: 2`가 Ctrl). **`ws` 패키지는 설치돼 있지
    않다** — Node 24의 내장 `WebSocket`으로 충분하다. `performance.memory`도 여기서 읽는다.
  - **DevTools 타깃이 여러 개일 수 있다.** 액티비티가 재생성되면 죽은 WebView의 타깃이 남아서, `[0]`을
    집으면 엉뚱한 페이지를 읽는다. `document.visibilityState === 'visible'`인 타깃을 골라야 한다.
  - **CM6 뷰에 DevTools에서 닿는 법:** `document.querySelector('.cm-content').cmTile.root.view`.
  - provider 확인: `adb shell content query --uri content://com.naki.skiff.profiles/profiles`는
    **거부되는 것이 정상이다**(shell은 권한이 없다). 권한 부여는
    `dumpsys package com.naki.skiff.code | grep READ_PROFILES`로 본다.
- 탭 (SM-X526N):
  - 가로 방향(2304x1440), 화면 하나라 `screencap`에 display id가 필요 없다.
  - 상단 메뉴 좌표: ② `tap 2088 117`, ③ `tap 2160 117`. 확인 대화상자: 취소 `tap 975 1310`,
    열기/신뢰 `tap 1328 1310`.
  - **화면은 5분 뒤 꺼진다(300000).** 긴 확인 전에 `settings put system screen_off_timeout 1800000`으로
    올렸다가 **끝나고 300000으로 되돌린다.** 깨우기는 `input keyevent KEYCODE_WAKEUP`.
  - **외장 키보드가 연결돼 있으면 화면 키보드가 뜨지 않는다.** `dumpsys input`은 떼어낸 뒤에도 항목이
    남아 믿을 수 없다 — `am get-config`의 `nokeys`/`qwerty`로 본다.
- 폴더블 (SM-F971N):
  - 화면 둘. **내부** `2448x1848`(933x704dp), **커버** `1248x1972`(475x751dp). display id는
    `dumpsys SurfaceFlinger --display-id`로 얻고, 먼저 나오는 것이 내부다. 가로/세로가 아니라
    **접힘 상태**가 어느 쪽을 켤지 정한다(`cmd device_state state 0`(CLOSED) / `3`(OPENED) / `reset`).
  - **커버 화면의 WebView는 `screencap`으로 못 찍는다.** 좌표를 얻는 법은 `AGENTS.md`의 On-device.
  - 내부 화면 상단 메뉴: ② `tap 2162 180`, ③ `tap 2266 180`. 확인 대화상자 열기 `tap 1453 1680`.
  - 시스템 글꼴 배율이 1.5라 에디터 기본 14px이 21px로 나온다.
  - **화면은 10분 뒤 꺼진다**(기본값 600000). 올렸으면 그 값으로 되돌린다.
  - **접었다 펴면 USB가 재열거되어 adb가 1~2초 끊긴다.** 명령 하나가 "device not found"로 실패한 것을
    결과로 읽지 말고 재시도한다.
  - 확인용 파일이 `/sdcard/Download/skiffcode-test/`에 있다(`hello.md`, 상한 초과용 3MB `big.ts`,
    1.9MB/24,167줄 `near2mb.ts`). 탭의 같은 디렉토리와 내용이 다르다.
- 환경:
  - `node`(v24)와 `npm`은 PATH에 있다. Gradle 데몬에도 `npm`이 있어야 한다(`:code:preBuild` → `buildWeb`).
  - `java`는 PATH에 없어서 `JAVA_HOME=/opt/homebrew/opt/openjdk@17`이 필요하다.
  - `adb`도 PATH에 없고 `/opt/homebrew/share/android-commandlinetools/platform-tools/adb`에 있다.
    `aapt2`, `zipalign`, `apksigner`는 `…/build-tools/37.0.0/`에 있다.
  - macOS에는 `timeout`이 없다. 오래 걸릴 수 있는 node 스크립트는 끝에 `process.exit(0)`를 둔다.
  - **USB 허브를 지나는 연결이 불안정하다.** 접기/펴기가 아니어도 `unauthorized`로 떨어진 적이 있고,
    이번 세션에는 탭이 명령 도중 한 번 끊겼다(재시도로 넘어갔다).
  - 이 맥의 원격 로그인(SSH)은 켜져 있다. 기기에서 실제 서버로 확인할 때 쓴다.
  - AOSP 소스를 봐야 할 때는
    `curl -s "https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android15-release/<경로>?format=TEXT" | base64 -d`.
    developer.android.com의 레퍼런스는 WebFetch로 본문이 안 나온다.
  - 라이브러리 바이트코드를 확인해야 할 때는 `~/.gradle/caches/modules-2/`의 jar를 풀어 `javap -p -c`.
    이번 세션에 sshj의 타임아웃이 어디서 오는지 이렇게 알아냈다(문서와 이름만으로는 틀렸다).
  - release APK를 기기에 넣을 때는 debug 키로 서명한다(`~/.android/debug.keystore`, 비밀번호 `android`,
    별칭 `androiddebugkey`). 확인이 끝나면 `installDebug`로 되돌린다.
  - worktree에는 gitignore된 `local.properties`가 따로 있어야 한다(메인 체크아웃에서 복사했다).

# 인수인계

다음 세션이 이어받기 위한 문서다. **세션을 끝낼 때마다 이 파일을 현재 상태로 덮어쓴다.**
계속 쌓는 기록이 아니다. 지난 기록은 git 히스토리에 남는다.

- 할 일 목록과 설계: [`plan.md`](plan.md)의 `# Skiff Code` 섹션
- 규칙, 툴체인, 커밋 전 점검, 보고와 알림 규칙: [`AGENTS.md`](AGENTS.md)
- 이 문서에 담는 것: 위 두 문서에 없는 **직전 세션의 맥락**(무엇을 했고, 왜 그렇게 정했고, 무엇이 아직 확인되지 않았는지)

---

## 마지막 세션 (2026-09-19 밤): release 빌드 수정, `ProfileProvider`, **M2 완료**

### 지금 상태
- 브랜치 `plan/skiffcode`(git worktree). **push하지 않은 커밋이 3개다**: `8d9338f`(release 빌드),
  `21b6d88`(`ProfileProvider`), `c681dfc`(M2 확인 체크). push할지 아직 답을 못 받았다. main에는 합치지 않았다.
- **M2가 끝났다. 다음은 M3 첫 항목, editor 레이어다**(`plan.md`의 첫 `- [ ]`).
- 테스트: `:core` 35개, `:app` 24개, `:code` 92개(이번에 `importFromSkiff` 7개 추가). 실패 없음.
- lint 경고: `:app` 4개, `:code` 11개. 지난 세션과 같다.
- **release 빌드가 된다**(`:app` 11MB, 서명 전). debug가 74MB인 것은 R8이 없어 `material-icons-extended`와
  BouncyCastle을 통째로 담기 때문이다. **배포용 서명 설정은 아직 없다.**
- 탭에는 두 앱의 **debug 빌드**가 깔려 있다. 확인 중에 Skiff Code 저장소에 Skiff에서 가져온 `testnas`
  프로필(비밀번호 없음)과 `127.0.0.1:2222` 호스트키가 들어갔다. 이번 확인이 남긴 것이고 정상이다.

### 이번 세션에서 한 것과 내린 판단

**release 빌드 (`8d9338f`, `app/proguard-rules.pro`)**
- R8이 `sun.security.x509.X509Key`를 못 찾아 멈췄다. sshj가 쓰는 `net.i2p.crypto.eddsa.EdDSAEngine`이
  참조하는데 Android에는 없는 클래스다. **바이트코드를 보니 공개키가 `EdDSAPublicKey`가 아닐 때만 가는
  분기**여서(sshj는 항상 `EdDSAPublicKey`를 넘긴다) `-dontwarn` 한 줄로 끝냈다.
- debug 키로 서명해 탭에 넣고 ed25519 호스트키를 쓰는 개발 맥에 실제로 접속되는 것까지 봤다.
  debug 키로 서명하면 기존 debug 앱 위에 덮어 설치돼 프로필과 호스트키가 남는다.
- `:code`는 release에 축소(minify)가 꺼져 있어 같은 문제가 없다. 켜면 같은 규칙이 필요하다.

**`ProfileProvider` (`21b6d88`) — M2 아홉째 항목**
- 계약은 `:core`의 `link/SharedProfiles`에 있다(권한 이름, authority, 열 이름). 권한
  `com.naki.skiff.permission.READ_PROFILES`(signature)는 **두 앱이 모두 선언한다.** 어느 쪽을 먼저
  설치해도 권한이 주어지게 하려는 것이다.
- Skiff의 `data/ProfileProvider`가 `profiles`와 `known_hosts`를 읽기 전용으로 내준다. 비밀번호는 없다.
- Skiff Code는 원격 링크(`Remote`, `UnknownServer`)마다 provider를 읽어 `SkiffCodeStore.importFromSkiff`로
  합치고 링크를 다시 해석한다. 합치는 규칙은 아래 결정 표에 있다.
- **읽기 전에 authority의 주인이 `com.naki.skiff`이고 같은 키로 서명됐는지 본다.** 처음 구현에는 이게
  없었는데, Skiff가 없는 기기에서 다른 앱이 authority를 차지하면 그 앱이 우리가 믿을 호스트키를 정할 수
  있다. 권한은 읽는 쪽이 가진 것이라 이것을 막지 못한다. 호스트키 확인을 통째로 우회하는 구멍이었다.
- Skiff도 링크를 보내기 전에 `checkSignatures`로 받는 쪽을 확인한다(인수인계에 적어 뒀던 보안 메모).
  그래서 `<queries>`가 두 앱 모두에 있다.
- 실기기: 권한 `granted=true`, 권한 없는 adb shell의 `content query`는 `SecurityException`. Skiff에서
  개발 맥의 README.md를 탭하니 대화상자 없이 열렸고, Skiff에만 있던 프로필이 비밀번호 없이, 없던
  호스트키가 들어왔다. 가져온 두 항목을 지우고 소유자 확인이 든 빌드로 다시 해도 같았다.

**M2 마지막 확인 (`c681dfc`)** — 탭에서 세 경로를 모두 봤다.
- adb로 원격 링크(`?alias=`, `line=300`이 그 줄 근처를 맨 위로)와 로컬 링크(`hello.py`, 하이라이팅).
- Skiff에서 원격 파일 탭(서명 확인 → 프로필 가져오기 → 대화상자 없이 열림).
- 삼성 "내 파일"의 "다른 앱에서 열기" → 연결 앱 목록에 Skiff Code → **"한 번만"**으로 `content://`
  마크다운이 열렸다. "항상"은 기기의 기본 앱 설정을 바꾸므로 고르지 않았다.

### 다음 세션이 할 일
1. `AGENTS.md`를 읽는다. 특히 툴체인 제약, Before committing(**커밋마다, push마다 사용자에게 먼저 묻는다**),
   보고와 알림 규칙.
2. `plan.md`의 첫 `- [ ]`인 **M3 editor 레이어**를 한다: `Compartment`로 viewer↔editor 전환, 레이어별
   스크롤 보존, ③ 토글 순환과 아이콘 연결. 지금 ③은 `disabled`다.
   - 문서를 바꿀 때마다 `EditorView`를 새로 만드는 지금 방식은 M3의 "열린 파일 `EditorState` LRU" 항목에서
     하나의 뷰 + 파일별 `EditorState`로 바뀐다. editor 레이어를 만들 때 그 모양을 미리 생각해 둔다.
   - editor가 생기면 **링크가 아는 alias의 임의 경로를 열게 되는 문제**를 다시 본다(읽기만 할 때는
     문제가 아니었다). 아래 "아직 확인하지 않은 것" 참고.

### 사용자와 정한 것, 그리고 이유
다시 논의하지 말고 이대로 진행한다.

| 결정 | 고른 것 | 이유 |
|---|---|---|
| 레포 구조 | 같은 레포, `:core` / `:app` / `:code` 멀티모듈 | 별도 레포는 SFTP 계층을 복사하게 되고, 두 사본이 따로 바뀌면서 달라진다 |
| 원격 실행 | 데몬 없이 SSH exec (프로젝트 모드에서만) | 데몬은 exec 없이 뜨지도 못하고(SFTP는 실행을 못 한다), 남에게 배포하는 앱이 남의 서버에 상주 프로세스를 심는 것은 "서버에 아무것도 설치하지 않는다"는 제약과 충돌한다. 임의의 서버가 대상이라 단일 산출물도 없다. 자세한 것과 데몬을 다시 꺼내는 조건은 `plan.md`의 "정한 것" 2번과 "범위 밖" |
| UI | 전부 WebView 하나 (TS + CodeMirror 6) | Compose와 WebView를 섞으면 테마를 두 곳에 적용해야 하고, 스크롤에 따른 메뉴 숨김도 브리지를 거친다 |
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
| 상단 메뉴 배경 (2026-09-20) | **막대 없이 버튼만 떠 있는 지금 모양을 유지한다.** 불투명 막대로 바꾸지 않는다 | 사용자 결정. 스크롤한 코드 위에서 ① 버튼이 맨 위 줄 번호를 한두 개 가리는 것은 감수한다 |
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
- **바뀐 호스트키 경고 창**을 기기에서 본 적이 없다. 이제 Skiff Code가 Skiff의 호스트키를 가져오므로
  이 경고가 뜨는 경우가 더 줄었다. 서버의 키를 일부러 바꿔 한 번 봐야 한다.
- **서명이 다른 앱이 끼어드는 경로**(가짜 Skiff Code, 가짜 provider)는 그런 앱을 만들지 않아 기기에서 보지 못했다.
- **링크가 아는 alias의 임의 경로를 연다.** alias가 맞으면 host는 프로필 것을 쓰지만 경로는 링크의 것이다.
  읽기만 하는 동안은 문제가 아니고, **editor가 생기면 다시 본다.**
- 손으로 하는 핀치 줌과 상단 메뉴의 손 사용감. adb로는 멀티터치를 못 만든다.
- Android 17의 로컬 네트워크 권한 요청(탭은 Android 16이라 요청하지 않는다).
- release APK의 배포용 서명. 이번에는 debug 키로 서명해서 확인만 했다.
- **탭에서 실제 SSH 서버로 LSP를 띄워 본 적은 없다.** 위 LSP 확인은 이 맥 안의 MINA 루프백이라
  네트워크 지연, 재접속, 끊김이 빠져 있다. exec로 원격 LSP를 띄우는 것은 M6이 처음이다.
- 진단이 수백~수천 개일 때의 비용. 스텁은 50개, pyright는 1개였다.
- lezer 심볼 추출, 테마 CSS 변수, SAF import/export는 라이브러리 버전도 고르지 않았다.
- 큰 파일에서 편집 중 동기화 비용. 2MB에서 incremental이 훨씬 싸다는 것만 안다.
- pyright 말고 다른 서버(typescript-language-server, marksman)는 확인하지 않았다.

### 주의할 점
- **세션을 끝낼 때 `plan.md` 체크리스트를 갱신하고 이 파일을 덮어쓴다.**
- **레포에 기기나 네트워크를 알아볼 수 있는 것을 남기지 않는다.** 호스트 이름, IP, 계정, 지문이 문서와
  테스트에 들어가기 쉽다. 이번에도 plan.md와 테스트에서 기기 이름을 빼고 `build.example`로 바꿨다.
  픽스처와 문서는 `192.0.2.0/24`(RFC 5737)와 `.example`을 쓴다.
- **exec 규칙:** `:app`은 금지, `:code`는 허용. `AGENTS.md`의 "The SFTP side, and what it must not do" 참고.
- `npm install` 때 fsevents install 스크립트는 npm 11 정책으로 실행되지 않는다. macOS용 선택 의존성이라 영향 없다.
- 기기:
  - 시리얼은 `adb devices`로 얻고 레포에 적지 않는다.
  - **화면은 2분 뒤 꺼지고, 꺼진 화면에서 `screencap`은 검은 화면을 찍는다.** 탭 전에
    `input keyevent KEYCODE_WAKEUP` → 잠금 해제 스와이프(`input swipe 1152 1300 1152 300 200`) →
    `dumpsys window | grep mCurrentFocus`로 앱이 앞에 있는지 확인한다. 이걸 빼먹으면 탭이 그냥 사라진다.
  - 가로 방향(2304x1440)이고 화면은 하나라 `screencap`에 display id가 필요 없다.
  - 아래 가장자리에서 시작하는 스와이프는 시스템 제스처에 먹히므로 y 250~1100 사이에서 한다.
  - adb로는 멀티터치를 만들 수 없다. DevTools를 포워딩하고 CDP `Input.dispatchTouchEvent`에 터치 점
    두 개를 주면 핀치를 흉내 낼 수 있다(CSS px 기준, 화면 좌표는 ×2 + 상태 표시줄 60px).
  - **외장 키보드(Corne)가 연결돼 있으면 화면 키보드가 뜨지 않는다.** `dumpsys input | grep Corne`.
  - Skiff Code 저장 파일을 adb로 고칠 때는 먼저 `am force-stop`한다. `run-as … cat`으로 읽고,
    `adb push`로 `/data/local/tmp`에 둔 뒤 `run-as … sh -c 'cat /data/local/tmp/x > files/datastore/skiffcode.json'`으로 쓴다.
  - provider 확인: `adb shell content query --uri content://com.naki.skiff.profiles/profiles`는
    **거부되는 것이 정상이다**(shell은 권한이 없다). 권한 부여는
    `dumpsys package com.naki.skiff.code | grep READ_PROFILES`로 본다.
- 환경:
  - `node`(v24)와 `npm`은 PATH에 있다.
  - `java`는 PATH에 없어서 `JAVA_HOME=/opt/homebrew/opt/openjdk@17`이 필요하다.
  - `adb`도 PATH에 없고 `/opt/homebrew/share/android-commandlinetools/platform-tools/adb`에 있다.
    `zipalign`과 `apksigner`는 `/opt/homebrew/share/android-commandlinetools/build-tools/37.0.0/`에 있다.
  - release APK를 기기에 넣을 때는 debug 키로 서명한다(`~/.android/debug.keystore`, 비밀번호 `android`,
    별칭 `androiddebugkey`). 그래야 기존 debug 앱 위에 덮여 앱 데이터가 남는다. 확인이 끝나면
    `installDebug`로 되돌린다.
  - worktree에는 gitignore된 `local.properties`가 따로 있어야 한다(메인 체크아웃에서 복사했다).

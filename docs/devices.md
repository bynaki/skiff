# 기기와 환경

실기기로 확인할 때와 이 맥에서 빌드할 때 알아둘 것. 세션이 바뀌어도 그대로인 것만 둔다 — 직전 세션의
상태는 [`HANDOFF.md`](../HANDOFF.md)에 있다. 새로 알게 된 것은 이 문서에 더한다.

**레포에 넣으면 안 되는 값**(기기 주소, 무선 디버깅 페어링 이름 등)은 레포 밖 로컬 메모에 두고, 여기에는
`<테일스케일 주소>` 같은 자리표시자만 쓴다.

## 공통

- **레포에 기기나 네트워크를 알아볼 수 있는 것을 남기지 않는다.** 호스트 이름, IP, 계정, 지문이 문서와
  테스트에 들어가기 쉽다. 픽스처와 문서는 `192.0.2.0/24`(RFC 5737)와 `.example`을 쓴다. **프로필
  이름도 마찬가지다.** 주변기기 이름도 일반화한다.
- `npm install` 때 fsevents install 스크립트는 npm 11 정책으로 실행되지 않는다. macOS용 선택 의존성이라 영향 없다.

## 기기 공통

- 시리얼은 `adb devices`로 얻고 레포에 적지 않는다. 두 대가 붙어 있을 수 있으므로 `ANDROID_SERIAL`
  또는 `adb -s`를 **항상** 쓴다. Gradle도 `ANDROID_SERIAL`을 본다.
- **네이티브 다이얼로그는 `uiautomator dump`로 읽고 두드린다.** `adb shell uiautomator dump /sdcard/ui.xml`
  → `text`와 `bounds`를 뽑아 중앙을 `input tap`. **앞선 창이 막 닫힌 직후의 탭은 먹히지 않을 때가
  있어** 1초쯤 두고 누른다. **끝나면 `/sdcard/ui.xml`을 지운다.**
- **페이지의 버튼은 `uiautomator dump`에 나오지 않는다.** CDP `Input.dispatchTouchEvent`는 **CSS px
  그대로** 받으므로 `getBoundingClientRect()` 값을 그대로 넘기면 된다(`devicePixelRatio`도 WebView
  오프셋도 필요 없다). **탭이 한 번 먹히지 않는 일이 있으니** 누른 뒤 결과를 읽어 확인한다.
  상태만 확인하면 될 때는 **DOM의 `.click()`이 더 빠르다** — 열린 파일 메뉴의 행은 `#open-files li .entry`다.
- `Runtime.evaluate`를 여러 번 할 때 **`const`는 전역에 남아** 다음 호출이 "already been declared"로
  죽는다. 식을 `(async () => { … })()`로 감싸면 되고, 그러면 `await`도 쓸 수 있다.
- **탭의 `screencap`이 몇 분 전 화면을 그대로 돌려준 적이 있다**(2026-09-26). 화면은 켜져 있고
  페이지의 `requestAnimationFrame`도 돌았는데 상태 표시줄 시계가 4분째 같았다. 찍은 것을 믿기 전에 그 시계를
  `adb shell date`와 맞춰 본다. 같은 때 CDP `Page.captureScreenshot`은 답이 없었다.
- **`screencap`이 다이얼로그가 떠 있는 화면을 비게 찍은 적이 있다.** 화면 대신
  `uiautomator dump`(네이티브)와 DevTools(페이지)를 믿는다.
- **`adb shell run-as … sh -c '…'`는 명령 전체를 한 번 더 따옴표로 감싸야 한다.** 안 그러면 adb가
  인자를 공백으로 이어 붙여 `sh -c cd`만 실행되고 나머지가 기기 셸에서 돈다. 저장 파일을 고칠 때는
  먼저 `am force-stop`하고, `adb push`로 `/data/local/tmp`에 둔 뒤 `chmod 644`하고 넘긴다.
- **adb로는 멀티터치도 조합키도 만들 수 없다.** 둘 다 DevTools로 한다:
  `adb forward tcp:9333 localabstract:webview_devtools_remote_$(adb shell pidof com.naki.skiff.code)`
  → `curl -s localhost:9333/json`에서 `webSocketDebuggerUrl` → `Input.dispatchTouchEvent`(터치 점 두
  개, CSS px 기준)와 `Input.dispatchKeyEvent`(`modifiers: 2`가 Ctrl). 글자를 넣기만 할 때는
  **`Input.insertText`가 더 간단하다**(포커스가 `.cm-content`에 있어야 한다). **`ws` 패키지는 설치돼
  있지 않다** — Node 24의 내장 `WebSocket`으로 충분하다. `performance.memory`도 여기서 읽는다.
- **DevTools 타깃이 여러 개일 수 있다.** 액티비티가 재생성되면 죽은 WebView의 타깃이 남아서, `[0]`을
  집으면 엉뚱한 페이지를 읽는다. `document.visibilityState === 'visible'`인 타깃을 골라야 한다.
- **CM6 뷰에 DevTools에서 닿는 법:** `document.querySelector('.cm-content').cmTile.root.view`.
- provider 확인: `adb shell content query --uri content://com.naki.skiff.profiles/profiles`는
  **거부되는 것이 정상이다**(shell은 권한이 없다). 권한 부여는
  `dumpsys package com.naki.skiff.code | grep READ_PROFILES`로 본다.

## 탭 (SM-X526N)

- 가로 방향(2304x1440), 화면 하나라 `screencap`에 display id가 필요 없다.
- 상단 메뉴 좌표: ② `tap 2088 117`, ③ `tap 2160 117`. 확인 대화상자: 취소 `tap 975 1310`,
  열기/신뢰 `tap 1328 1310`. 본문에 포커스를 주려면 `tap 600 300`쯤을 누른다.
- **화면은 5분 뒤 꺼진다(300000).** 긴 확인 전에 `settings put system screen_off_timeout 1800000`으로
  올렸다가 **끝나고 300000으로 되돌린다.** 깨우기는 `input keyevent KEYCODE_WAKEUP`.
- **외장 키보드가 연결돼 있으면 화면 키보드가 뜨지 않는다.** `dumpsys input`은 떼어낸 뒤에도 항목이
  남아 믿을 수 없다 — `am get-config`의 `nokeys`/`qwerty`로 본다(이 값도 블루투스 키보드를 못 볼 때가
  있다. 앱은 `InputDevice`에 묻는다). 떼지 않고 화면 키보드를 보려면
  `settings put secure show_ime_with_hard_keyboard 1`로 띄우고 **끝나면 0으로 되돌린다**(되돌려 두었다).
  키보드가 어느 언어인지는 화면으로 못 읽는다 — `AGENTS.md`의 `currentLang` 항목을 본다.
- 확인용 파일이 `/sdcard/Download/skiffcode-test/`에 있다(`hello.py` 32KB, `readme.md` 17KB,
  `Makefile` 20B, `old.txt` 30B, 2MB 바로 아래인 `big.ts`(56677줄), 상한 초과용 3MB `huge.txt`,
  인코딩용 `enc`, `bad.txt`, `image.txt`). **여기에 임시 파일을 만들었으면 끝나고 지운다.**

## 폴더블 (SM-F971N)

지원 대상이므로 지금 연결돼 있지 않아도 남긴다.

- 화면 둘. **내부** `2448x1848`(933x704dp), **커버** `1248x1972`(475x751dp). display id는
  `dumpsys SurfaceFlinger --display-id`로 얻고, 먼저 나오는 것이 내부다. 가로/세로가 아니라
  **접힘 상태**가 어느 쪽을 켤지 정한다(`cmd device_state state 0`(커버) / `3`(내부) /
  `state reset`(기기에 돌려줌) — **`reset` 단독은 "Unknown command"다**).
- **접기/펴기는 액티비티를 재생성하지 않는다**(`configChanges`의 `screenLayout|smallestScreenSize|
  screenSize|density`). 사이드바를 꺼낸 채 접어도 열린 채 넘어간다.
- **커버 화면의 WebView는 `screencap`으로 못 찍는다.** 좌표를 얻는 법은 `AGENTS.md`의 On-device.
- 내부 화면 상단 메뉴: ② `tap 2162 180`, ③ `tap 2266 180`. 확인 대화상자 열기 `tap 1453 1680`.
- 시스템 글꼴 배율이 1.5라 에디터 기본 14px이 21px로 나온다.
- **화면은 10분 뒤 꺼진다**(기본값 600000). 올렸으면 그 값으로 되돌린다.
- **접었다 펴면 USB가 재열거되어 adb가 1~2초 끊긴다.** 명령 하나가 "device not found"로 실패한 것을
  결과로 읽지 말고 재시도한다.
- 프로필 하나(이 맥)와 호스트키 하나가 저장돼 있고 `ACCESS_LOCAL_NETWORK`도 허용돼 있다.
- 확인용 파일이 `/sdcard/Download/skiffcode-test/`에 있다(`hello.md`, 3MB `big.ts`,
  1.9MB/24,167줄 `near2mb.ts`). 탭의 같은 디렉토리와 내용이 다르다.

## 환경

- `node`(v24)와 `npm`은 PATH에 있다. Gradle 데몬에도 `npm`이 있어야 한다(`:code:preBuild` → `buildWeb`,
  그리고 `:code:check` → `testWeb`).
- `java`는 PATH에 없어서 `JAVA_HOME=/opt/homebrew/opt/openjdk@17`이 필요하다.
- `adb`도 PATH에 없고 `/opt/homebrew/share/android-commandlinetools/platform-tools/adb`에 있다.
- **`adb pull`이 이 샌드박스에서 아무 말 없이 실패한다**(종료 코드 1, 출력 없음). 기기의 파일은
  `adb shell base64 <경로> | base64 -d > <파일>`로 가져온다. `adb exec-out screencap -p`는 360바이트짜리
  깨진 PNG를 준 적이 있어 믿지 않는다 — 기기에 `screencap -p <경로>`로 쓰고 base64로 가져오는 쪽이 된다.
- **폴드8은 무선 디버깅으로 붙인다.** 페어링은 이미 돼 있다(기기의 "페어링된 기기"에 이 맥이 있다). 단 **Wi-Fi가 없으면
  포트 자체가 열리지 않는다** — LTE + 테일스케일만으로는 안 된다(기기 화면이 "사용할 수 없음(Wi-Fi 연결
  해제됨)"). Wi-Fi에 붙기만 하면 같은 공유기가 아니어도 되고, 기기의 테일스케일 주소(`<테일스케일 주소>`,
  로컬 메모에 있다)에 그 화면의 포트를 붙여 `adb connect` 한다. mDNS(`adb mdns services`)는 테일스케일을 타지
  않으니 포트는 기기 화면에서 읽는다.
  `aapt2`, `zipalign`, `apksigner`는 `…/build-tools/37.0.0/`에 있다.
- macOS에는 `timeout`이 없다. 오래 걸릴 수 있는 node 스크립트는 끝에 `process.exit(0)`를 둔다.
- **USB 허브를 지나는 연결이 불안정하다.** `unauthorized`로 떨어지거나 명령 도중 끊긴 적이 있다
  (몇 초 기다렸다 `adb forward`부터 다시 걸면 된다).
- 이 맥의 원격 로그인(SSH)은 켜져 있다. 기기에서 실제 서버로 확인할 때 쓴다. **탭에는 이 맥을 가리키는
  프로필이 비밀번호까지 저장돼 있어** 링크 하나로 원격 파일을 열 수 있다(프로필은
  `run-as com.naki.skiff.code cat files/datastore/skiffcode.json`으로 확인하고, **거기서 읽은 것은
  레포에 옮기지 않는다**).
- AOSP 소스를 봐야 할 때는
  `curl -s "https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android15-release/<경로>?format=TEXT" | base64 -d`.
  developer.android.com의 레퍼런스는 WebFetch로 본문이 안 나온다.
- 라이브러리 바이트코드를 확인해야 할 때는 `~/.gradle/caches/modules-2/`의 jar를 풀어 `javap -p -c`.
- release APK를 기기에 넣을 때는 debug 키로 서명한다(`~/.android/debug.keystore`, 비밀번호 `android`,
  별칭 `androiddebugkey`). 확인이 끝나면 `installDebug`로 되돌린다.
- worktree에는 gitignore된 `local.properties`가 따로 있어야 한다(메인 체크아웃에서 복사했다).

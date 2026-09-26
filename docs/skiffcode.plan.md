# Skiff Code 할 일

설계와 정한 것은 [`skiffcode.spec.md`](skiffcode.spec.md)에 있다. 이 문서는 그것을 한 세션 크기의 할 일로 나눈 체크리스트다.

## 이어받는 방법

- 시작 전에 `AGENTS.md`를 읽는다. 툴체인 제약과 "Before committing" 규칙은 여기서도 그대로 적용된다.
- **아래 체크리스트에서 위에서부터 처음 나오는 `- [ ]`가 다음 할 일이다.** 한 항목은 한 세션에서 끝내고 커밋할 수 있는 크기로 잡았다.
- 항목을 끝내면 `[x]`로 바꾸고 그 변경을 **같은 커밋에** 넣는다.
- 막히면 `[ ]`는 그대로 두고 항목 아래에 `  - 막힘: <이유>`를 한 줄 적는다.
- 세션을 시작할 때 [`HANDOFF.md`](../HANDOFF.md)에서 직전 세션의 맥락을 읽고, 끝낼 때 그 파일을 현재 상태로 덮어쓴다.
  **HANDOFF에는 직전 세션만 둔다.** 세션이 지나도 남는 것은 제자리에 적는다 — 결정과 이유는 spec, 확인 못 한 것과 알려진 문제는 이 문서의 아래 절, 기기와 환경은 [`devices.md`](devices.md).
- 각 단계의 마지막 "확인" 항목이 통과해야 다음 단계로 넘어간다.
- 설계와 다르게 구현하게 되면 [`skiffcode.spec.md`](skiffcode.spec.md)도 같은 커밋에서 고친다. 두 문서가 늘 현재 상태를 말해야 한다.

## 할 일

### 0. 문서화
- [x] 이 계획을 `plan.md`(지금의 `docs/skiffcode.spec.md`와 이 문서)에 체크리스트로 적고 `AGENTS.md`에서 가리키게 하기
- [x] `skiff.code.plan.md`(지금의 `docs/my.skiffcode.spec.md`), `menu.layout.jpg`를 레포에 커밋하기 (이 문서가 두 파일을 참조한다)
- [x] 인수인계 문서 `HANDOFF.md` 작성

### M0. 스파이크: 전부 WebView에 붙는지 먼저 확인
버리는 코드로 확인만 한다. 결론과 확정된 라이브러리 버전은 `AGENTS.md`에 남긴다.
- [x] `:code` 최소 앱: WebView 하나 + `WebViewAssetLoader` + `addWebMessageListener`로 JSON-RPC 한 번 왕복
- [x] `code/web` Vite+TS 프로젝트와 Gradle `buildWeb` Exec 태스크(inputs/outputs 지정, `preBuild`에 연결, 증분 빌드 확인)
- [x] CM6로 2MB 파일 스크롤 성능과 핀치 줌(`--code-font-size`, 줌 중심 줄 고정)을 실기기(갤럭시탭 S10 FE, SM-X526N)에서 확인
  - 자동 측정 통과(`HANDOFF.md` 참조), 사용자가 탭에서 직접 스크롤과 핀치 줌을 해 보고 좋다고 확인했다.
- [x] `@codemirror/merge`로 +/- 거터와 초록/빨강 줄의 읽기 전용 unified diff를 그리는 방법 확인
- [x] `@codemirror/lsp-client` Transport를 브리지로 대체할 수 있는지 확인
  - Kotlin 스텁 LSP 서버(`lsp/StubLsp`)를 붙여 실기기에서 initialize 4ms, 2MB didOpen 후 진단 252ms,
    hover 4ms, 완성 3ms, 편집 후 재동기화 591ms를 확인했다. 진단 50개의 범위가 모두 의도한 글자
    위에 있었고 한글 주석도 맞았다. 설계에 반영한 것은 `skiffcode.spec.md`의 "git과 LSP" 절에 있다.
- [x] ktoml이 Kotlin 2.4.20 / AGP 9에서 컴파일되는지 확인. 안 되면 설계의 TOML 줄을 `smol-toml`로 고치기
  - 된다. `KtomlSpikeTest` 3개가 통과하고 APK에 dex까지 들어간다. `smol-toml`로 옮기지 않는다.
    자세한 것은 `skiffcode.spec.md`의 "설정과 테마" 절에 있다.
- [x] **확인:** 위 결과를 `AGENTS.md`에 적고, 막힌 것이 있으면 설계를 고친 뒤 넘어간다
  - `AGENTS.md`에 `:code` 명령, 고정한 버전 표, 브리지의 모양, `:code`에만 해당하는 툴체인 제약을
    적었다. 막힌 것은 없다. 설계에서 고친 것은 줌 기준 줄 고정, diff 알고리즘, CM6 버그 처리,
    LSP 위치 인코딩과 동기화 방식이고 모두 `skiffcode.spec.md` 본문에 반영했다.

### M1. `:core` 추출 (Skiff 동작 변화 없음)
- [x] `:core` 라이브러리 모듈 추가, 공통 코드와 해당 테스트 이동(패키지명 유지, slf4j 런타임 규칙 동일하게 적용)
  - `SftpTestServer`는 `:core`의 testFixtures로 내보냈다. `CopyEngine`은 `:app`에 남아서, 실서버 위에서
    `CopyEngine`을 돌리던 테스트 3개를 `:app`의 `SftpTransferTest`로 옮겼기 때문이다.
- [x] `KnownHostStore` 인터페이스 도입. `HostKeyGate`가 이것에 의존하고 `SkiffStore`가 구현한다(`data.first()`로만 읽는다는 규칙을 KDoc에 옮기기)
- [x] `SshConnection.ensureConnected`의 접속, 인증, 호스트키 부분을 `SshClientFactory`로 분리
- [x] `AGENTS.md`의 명령, 구조, 테스트 경로를 멀티모듈 기준으로 갱신
- [x] **확인:** `./gradlew :core:testDebugUnitTest :app:testDebugUnitTest :app:lintDebug` 통과. 실기기에서 Skiff의 SFTP 탐색과 전송이 이전과 같다
  - 테스트와 린트는 통과한다. 이동 전 52개가 `:core` 28개 + `:app` 24개로 갈렸고 실패는 없다.
  - 실기기에서 실제 SSH 서버로 확인했다: 접속, 홈 디렉터리 목록, 하위 디렉터리 이동, 새로고침
    (서버에서 방금 만든 디렉터리가 바로 보인다), 분할 화면, 양방향 전송. 내려받은 파일과 올린 파일
    모두 MD5가 원본과 같았고(한글 내용 포함), 로그에 경고나 오류가 하나도 없었다.
  - 29.3MB 전송은 **내려받기 7.8 MB/s, 올리기 0.9 MB/s**였다. 비대칭은 설계대로다 —
    `openRead`에만 read-ahead가 붙어 있고(`READ_AHEAD_MAX`), 쓰기는 파이프라이닝이 없다.
    M1이 건드린 곳이 아니고, 내려받기 속도는 read-ahead가 살아 있다는 증거다(없으면 약 1 MB/s).

### M2. 골격 + URI 인텐트 + 단일 파일 viewer
- [x] `SkiffCodeUri` 파서 + `SkiffCodeUriTest`(퍼센트 인코딩, IPv6, 비밀번호 거부, alias 우선순위, 로컬 형식)
  - 파서는 alias와 (user, host, port)를 둘 다 넘기기만 한다. alias 우선으로 프로필을 찾는 것은 프로필 저장소가 있어야 해서 OpenRequest 해석 항목에서 테스트한다.
  - `android.net.Uri`(유닛 테스트에서 스텁)와 `java.net.URI`(인코딩 안 된 한글 경로를 거부)를 쓰지 않고 직접 파싱한다. user 없이 alias만으로 서버를 가리키는 링크도 받는다.
- [x] `SkiffCodeStore`(DataStore JSON): 프로필(비밀번호 암호문), knownHosts, 최근 파일
  - `:core`의 `ServerProfile`, `KnownHost`를 그대로 쓰고 `KnownHostStore`를 구현한다. `Context` 대신 `DataStore`를 받아 JVM에서 테스트한다(`SkiffCodeStoreTest`).
  - DataStore는 `:core`의 `jsonDataStore`로 연다. 읽을 수 없는 파일은 `<이름>.corrupt-<밀리초>`로 복사해 둔 뒤 빈 데이터로 시작한다. Skiff의 `SkiffStore`도 같이 바꿨다(`JsonDataStoreTest`).
  - 최근 파일은 연 링크(`skiffcode://` 또는 `content://`) 문자열과 시각이다. 최신순, 같은 링크는 앞으로 옮기고, 50개를 넘으면 오래된 것부터 버린다.
- [x] 인텐트 필터(`skiffcode` VIEW, `text/*` VIEW/EDIT)와 OpenRequest 해석(alias → (user, host, port) 순으로 프로필 찾기 + 테스트), 알 수 없는 서버 확인창, 비밀번호 입력, 호스트키 다이얼로그
  - `intent/OpenRequest`(`OpenRequestTest`), `ui/OpenFlow`, `ui/Dialogs`, `session/RemoteSessions`, `SkiffCodeApplication`(프로세스 범위 컨테이너).
  - alias가 맞으면 그 프로필의 host를 쓴다. 링크가 아는 alias를 다른 기계로 돌릴 수 없다. user가 없는 링크는 (host, port)가 프로필 하나에만 맞을 때만 그 프로필을 쓴다. host는 대소문자를 무시한다.
  - 알 수 없는 서버는 확인창에서 비밀번호를 받아 프로필로 만든다. "서버로 저장"(기본 켬)을 끄면 프로세스가 끝날 때까지 메모리에만 둔다.
  - 흐름은 파일이 있는지 `stat`하고 최근 파일에 넣는 데서 끝나고, 토스트로 알린다. 읽고 보여 주는 것은 `TextLoader`와 viewer 항목이다.
  - `singleTask`라 두 번째 링크는 `onNewIntent`로 온다. `configChanges`로 회전이나 접기에서 다시 만들어지지 않게 해서 대화상자를 기다리는 흐름이 끊기지 않는다.
- [x] `TextLoader` + `TextLoaderTest`(크기 상한, NUL 판별, EUC-KR 폴백, CRLF와 끝 줄바꿈 기억). MINA로 원격 읽기(한글 파일 포함)
  - `doc/TextLoader`. `load(fs, path)`는 `FileSystem`이면 로컬과 원격 모두 읽는다. `content://`는 바이트를 직접 읽어 `decode`에 넘기면 된다(viewer 항목에서 연결한다).
  - `TextLoaderTest` 11개(디코딩 규칙)와 `TextLoaderTest$OverSftp` 6개(`:core`의 `SftpTestServer`로 한글 이름의 한글 파일, EUC-KR, 상한 경계, 바이너리, 없는 파일). `:code` 테스트가 `:core`의 testFixtures를 쓰게 됐다.
  - `stat` 뒤에 파일이 커져도 상한+1바이트까지만 읽는다. 아직 `OpenFlow`에는 연결하지 않았다. 여는 흐름은 여전히 토스트에서 끝난다.
- [x] `WebBridge`(Kotlin)와 `bridge.ts` RPC 정식 구현, 보안 규칙(origin, CSP, 링크 가로채기) 적용
  - `bridge/WebBridge`와 `WebBridgeTest` 12개(응답과 id, 오류 코드 -32601/-32602/-32000, 알림, `ready` 전 알림 보관, 다시 불러온 페이지, `/`가 든 LSP 문자열 왕복). 스파이크 페이지의 `sampleText`와 스텁 LSP를 새 브리지로 옮겨 실기기에서 M0과 같은 수치를 확인했다(initialize 4ms, didOpen 후 진단 250ms).
  - 실기기에서 DevTools로 확인: 외부 fetch, 외부 이미지, 인라인 스크립트, iframe이 CSP로 막히고, `web/` 밖의 자산은 403이다. `location.href`로 https에 가면 페이지는 그대로이고 Chrome이 열린다. `intent:` 링크는 버려진다.
  - DevTools에서 만든 `<a>`의 `click()`으로는 https 이동이 일어나지 않았다. 원인은 확인하지 않았다. viewer에서 마크다운 링크를 실제로 탭해 다시 볼 것.
- [x] viewer 레이어: 읽기 전용 CM6(하이라이팅, 줄 번호), 핀치 줌, 마크다운 렌더링(`html: false`)
  - `web/src/layers/pane.ts`(M3에서 `layers/viewer.ts`를 흡수했다), `markdown.ts`, `zoom.ts`, `main.ts`. 하이라이팅은 `@codemirror/language-data` 6.5.2로 파일 이름에서 언어를 찾고, 파서는 언어마다 따로 된 청크를 처음 필요할 때 불러온다. 마크다운은 `markdown-it` 15.0.2이고, 이름이 마크다운이면 렌더링해서 보여 준다(원문은 M3의 editor에서).
  - 흐름: `OpenFlow`가 여는 데서 그치지 않고 `TextLoader`로 읽는다(원격은 세션의 `SftpFileSystem`, 로컬은 `LocalFileSystem`, `content://`는 새 `TextLoader.load(Source)`). `MainActivity`가 결과를 들고 있고, 페이지는 `document` RPC로 가져간다. 새 문서가 열리면 `documentChanged` 알림을 받고 다시 묻는다. 페이지를 다시 불러와도 같은 길이다.
  - 읽기 중의 실패(연결, 권한)는 지금처럼 네이티브 대화상자다. 읽었지만 보여 줄 수 없는 것(`TooLarge`, `Binary`, `UnknownEncoding`)은 페이지에 문서 대신 안내로 나온다. 페이지의 글은 전부 Kotlin의 문자열 리소스(영어, 한국어)에서 온다.
  - `?line=`은 코드에서는 그 줄을 맨 위로, 마크다운에서는 그 줄 이전에서 시작하는 마지막 블록을 맨 위로 둔다(`markdown-it`의 `map`으로 블록마다 `data-line`).
  - 핀치 줌은 코드와 마크다운이 글자 크기 하나를 같이 쓰고, 문서를 바꿔도 유지된다. 마크다운은 손가락 아래 블록을 같은 비율 위치에 둔다.
  - 마크다운 링크: 상대 링크와 `#조각`은 페이지에서 막는다(갈 곳이 없고, 브라우저로 넘기면 `appassets` 주소가 열린다). 나머지는 WebView의 `shouldOverrideUrlLoading`이 기존 규칙대로 처리한다.
  - Android 15의 edge-to-edge 때문에 페이지가 상태 표시줄 밑에 그려져서, WebView를 감싼 프레임에 시스템 바 여백을 준다.
  - M0 스파이크의 Kotlin 쪽(`StubLsp`, `sampleText`, 실행 인자)과 페이지의 측정 코드는 지웠다. `diff.ts`와 `lsp.ts`는 M5·M6을 위해 남겼고 페이지가 불러오지 않는다.
  - 실기기 확인(탭, 로컬 파일과 `content://`): Python·TS·Makefile 하이라이팅, 2MB TS 파일을 30000번째 줄에서 열기(읽고 여는 데 약 50ms), 마크다운의 raw HTML이 글자로 나오고 `javascript:` 링크가 링크가 되지 않고 외부 이미지가 CSP로 막히는 것, `?line=`, 3MB 파일·바이너리·깨진 인코딩의 안내, EUC-KR 파일. **링크를 adb로 실제로 탭해서** https는 Chrome으로, mailto는 메일 앱으로 가고 페이지는 남는 것, `intent:`는 버려지고 상대·조각 링크는 아무 일도 없는 것을 봤다. 핀치 줌은 DevTools 프로토콜로 두 손가락 터치를 만들어 코드(줄 30016)와 마크다운(문단 61) 모두 손가락 아래가 그대로인 것을 봤다. **손으로 하는 핀치와 원격 파일 성공 경로는 아직이다.**
- [x] 상단 메뉴 ①~④ 자리와 스크롤에 따른 숨김/표시, ② 원래 크기 동작(①③④는 이후 단계에서 채운다)
- [x] Skiff 쪽: `onOpen`에서 CODE/TEXT/MARKDOWN이면 `skiffcode://` 인텐트를 보내고, Skiff Code가 없으면 기존 외부 앱으로 열기
- [x] Skiff 쪽: 서명 권한 `ProfileProvider` + Skiff Code가 읽어 프로필과 호스트키에 반영(지문이 다르면 경고 흐름)
  - 계약은 `:core`의 `link/SharedProfiles`(권한, authority, 열 이름). 권한 `com.naki.skiff.permission.READ_PROFILES`(signature)는 **두 앱이 모두 선언한다.** 어느 앱이 먼저 설치되든 부여되게 하려는 것이다. provider는 `profiles`(id, name, host, port, username, start_path)와 `known_hosts`를 읽기 전용으로 내준다. 비밀번호는 없다.
  - Skiff Code는 원격 링크(`Remote`, `UnknownServer`)를 받을 때마다 provider를 읽어 `SkiffCodeStore.importFromSkiff`로 합친 뒤 링크를 다시 해석한다. **읽기 전에 authority의 주인이 `com.naki.skiff`이고 같은 키로 서명됐는지 본다.** Skiff가 없을 때 다른 앱이 authority를 차지하면 우리가 믿을 호스트키를 그 앱이 정할 수 있기 때문이다(권한은 읽는 쪽이 가진 것이라 이것을 막지 못한다). 아니면 건너뛴다.
  - 합치는 규칙(사용자 결정, `SkiffCodeStoreTest`): 프로필은 Skiff id, 그다음 이름으로 찾아 Skiff의 주소와 시작 경로를 받고, 자기 id와 비밀번호는 유지한다. host(대소문자 무시)·port·user가 바뀌면 비밀번호를 지운다. 없으면 비밀번호 없이 추가한다. 지우지는 않는다. 호스트키는 그 host:port에 없을 때만 받고, 있는 것은 Skiff와 달라도 두어 접속할 때 `HostKeyGate`의 경고가 판단하게 한다.
  - Skiff는 링크를 보내기 전에 `checkSignatures`로 Skiff Code가 같은 키로 서명됐는지 본다. 다르면 설치되지 않은 것처럼 외부 앱(로컬만)으로 간다. 그래서 `<queries>`가 두 앱 모두에 있다.
  - 실기기: 권한 `granted=true`, adb shell의 `content query`는 `SecurityException`. Skiff에서 개발 맥 프로필의 README.md를 탭하니 대화상자 없이 열렸고, Skiff에만 있던 프로필이 비밀번호 없이, 없던 호스트키가 들어왔다. 서명이 다른 Skiff Code로 가는 경로는 기기에서 보지 않았다.
- [x] **확인:** `adb shell am start -a android.intent.action.VIEW -d 'skiffcode://…'`, Skiff에서 파일 탭, 파일 매니저의 "다른 앱으로 열기"를 각각 실기기에서 확인
  - 탭(갤럭시탭 S10 FE)에서 확인: adb로 원격 링크(`?alias=`와 `line=300`, 그 줄 근처 블록이 맨 위)와 로컬 링크(`skiffcode:///…/hello.py`, 하이라이팅). Skiff에서 원격 파일 탭(서명 확인과 프로필 가져오기를 거쳐 대화상자 없이 열림). 삼성 "내 파일"의 "다른 앱에서 열기" → 연결 앱 목록에 Skiff Code → "한 번만"으로 `content://` 마크다운이 열림.

### M3. editor, 저장, 실시간 반영, 열린 파일
- [x] editor 레이어: `Compartment`로 viewer↔editor 전환, 레이어를 바꿔도 보던 줄을 잇기, ③ 토글 순환과 아이콘 연결
  - 레이어는 **뷰를 나누지 않는다.** `EditorView` 하나와 파일당 `EditorState` 하나를 두고, 레이어는 `Compartment`가 나르는 확장 묶음이다(`code/web/src/layers/pane.ts`). 근거는 `AGENTS.md`의 CM6 관찰.
  - 스크롤은 "레이어별로 따로 기억"이 아니라 **보던 줄을 잇는다**(2026-09-20 사용자 결정). 코드 파일은 같은 뷰라 저절로 그렇게 되고, 마크다운은 렌더된 블록의 `data-line`과 소스 줄을 서로 옮긴다.
  - ③ 순환에 diff는 아직 없다. 확장 묶음 자리(`BUNDLES.diff`)만 있고 M5에서 채운다.
- [x] 링크가 준 경로 확인: 보낸 앱이 Skiff가 아니면 서버와 경로를 보여주고 묻는다 (저장 기능보다 먼저 막았다)
  - alias가 맞아도 정하는 것은 어느 서버뿐이고 경로는 링크의 것이었다. `skiffcode://`가 `BROWSABLE`이라 웹 페이지도 보낼 수 있어, 프로필 이름만 맞히면 저장된 자격증명으로 그 서버의 아무 파일이나 대화상자 없이 열렸다. 읽기만 할 때는 화면에 뜰 뿐이지만, 저장이 생기면 **사용자의 타이핑이 어느 파일에 떨어지는지를 링크가 고르게 된다.**
  - 보낸 앱은 `ComponentCaller.getPackage()`로 알아낸다. `getReferrer()`는 호출자가 `EXTRA_REFERRER`를 직접 채워 위조되고, `ComponentCaller`는 프레임워크가 답한다 — 호출자는 밝힐지 여부만 고르고(`ActivityOptions.setShareIdentityEnabled`, Skiff가 보낼 때 켠다) 무엇인지는 못 고른다. `onNewIntent`는 `getCurrentCaller()`를 읽는다(`getInitialCaller()`면 Skiff가 띄운 앱이 뒤에 온 악성 링크에 신뢰를 물려준다). Android 15 미만은 전부 묻는다.
  - `UnknownServer`는 이미 같은 경로를 보여주며 묻고, `content://`는 보낸 앱이 준 권한이라 대상이 아니다. `else` 없이 종류를 하나씩 적어 새 `OpenRequest`가 생기면 빌드가 깨진다.
  - 실기기: Skiff 탭은 대화상자 없이(`from com.naki.skiff`), adb 링크는 확인창, **Skiff가 띄운 앱에 들어온 adb 링크도 확인창**, 그리고 `attacker@192.0.2.1/etc/shadow?alias=<실제 이름>`이 링크의 주소가 아닌 프로필의 주소와 `/etc/shadow`를 보여주며 **연결·비밀번호·호스트키 전에** 멈췄다.
- [x] 확대할 때 줄 번호 간격이 따라오지 않던 것(화면보다 짧은 파일)
  - 거터만이 아니라 CM6의 높이 맵이 통째로 낡아 있었다. `.cm-content`의 `min-height: 100%` 때문에 짧은 문서는 글꼴이 커져도 박스 높이가 그대로라, 측정 관문(`theme facet 변경 || refresh || contentDOMHeight != rect`)이 하나도 걸리지 않는다. 본문 줄은 CSS로 그려져 멀쩡해 보이고, 높이 맵에서 인라인 px로 쓰이는 거터만 낡은 채 남았다. 그래서 글꼴 크기를 `--code-font-size`가 아니라 `Compartment`가 나른다(`codeFontSize`). 자세한 것은 `AGENTS.md`의 CM6 관찰.
- [x] 에디터 레이어에 들어갈 때 화면 키보드가 문서를 가리던 것 (폴더블에서 드러났다)
  - ③으로 에디터에 들어가면 `view.focus()`가 곧바로 화면 키보드를 올려 문서의 절반을 가렸다. 탭에는 외장 키보드가 붙어 있어 화면 키보드가 아예 뜨지 않아 보이지 않던 문제다.
  - **하드웨어 키보드가 있을 때만 포커스한다.** 없으면 본문을 처음 탭할 때 포커스가 잡히고 캐럿도 탭한 자리에 선다. 있을 때는 예전 동작 그대로다 — 거기서는 포커스가 곧 타이핑 시작이고 가리는 것도 없다.
  - 키보드 유무는 `Configuration.keyboard`가 아니라 **`InputDevice`에 묻는다.** 탭에서 블루투스 키보드가 붙어 타이핑이 되는데도 그 필드는 `nokeys`였다. 변화는 `InputManager.InputDeviceListener`로 따라간다(`onConfigurationChanged`가 아니다).
  - 페이지는 스스로 알 수 없으므로 브리지의 `hardwareKeyboard` 호출과 `hardwareKeyboardChanged` 알림으로 받아 `web/src/keyboard.ts`가 캐시한다.
- [x] 설정이 바뀌면 링크를 다시 열던 것
  - `configChanges`에 없는 설정이 바뀌면 액티비티가 재생성되고 `onCreate`가 **같은 intent로 `handleLink`를 다시 돌렸다.** 파일이 두 번 열리고, 다시 연결하고 경로를 다시 묻고, 화면에 있던 것을 갈아치웠다. 보낸 앱 정보도 살아남지 않아 **Skiff가 보낸 파일인데도 확인창이 다시 떴다.**
  - 방아쇠는 키보드였다. 키보드를 켜면 `keyboard`뿐 아니라 `navigation`까지 움직인다(키보드가 d-pad로도 등록된다). 둘 다 `configChanges`에 넣었다.
  - 그것과 별개로 `onRetainNonConfigurationInstance`로 **열린 문서를 넘겨** 재생성 자체를 견디게 했다. 대화상자를 기다리던 중이면(문서가 아직 비어 있으면) 넘기지 않고 링크를 다시 돌린다 — 그게 원래 복구 경로다.
  - **남은 것: 문서만 넘기고 레이어와 스크롤은 넘기지 않는다.** `uiMode`(다크 모드), 언어, 글꼴 크기는 아직 목록에 없어서, 그것들을 바꾸면 보던 레이어가 뷰어로 돌아간다. 무엇까지 넘길지는 M3 저장과 같이 정한다.
- [x] 화면 하단을 탭해 키보드를 올리면 캐럿이 가려지던 것
  - 화면 키보드가 WebView를 줄이는 것(`MainActivity`가 ime inset을 프레임에 더한다)까지는 되는데, **선택이 움직이지 않았으므로 CM6가 캐럿을 다시 불러오지 않았다.** 1601줄 파일에서 하단을 탭하니 뷰포트가 675→337로 줄고 캐럿은 y 590에 남았다.
  - `pane.ts`가 `resize`에서 `scrollIntoView(..., { y: 'nearest', yMargin: TOPBAR_SPACE })`를 건다. `nearest`라 키보드가 닫히거나 회전으로 자리가 넓어질 때는 아무 일도 하지 않는다.
- [x] `DocumentSaver` + `DocumentSaverTest`(MINA: mtime 충돌 감지, 인코딩과 CRLF 왕복)
  - 읽을 때의 크기·mtime과 다르면 **아무것도 쓰지 않고** `Conflict`를 돌려준다(지워졌으면 `current`가 null). 같으면 제자리 truncate 쓰기, 그리고 다시 `stat`해서 자기 쓰기를 남의 변경으로 오인하지 않게 한다.
  - **mtime은 초 단위라, 같은 초에 크기까지 그대로인 변경은 보이지 않는다.** 그 창을 좁히는 것은 `FileWatcher`의 몫이고, 이 검사는 열어 둔 사이에 딴 데서 바뀐 파일 위에 저장이 떨어지는 것을 막는 쪽이다.
  - **끝 줄바꿈은 버퍼가 정한다**(사용자 결정 2026-09-21). CM6는 끝 줄바꿈을 저절로 넣지도 빼지도 않으므로 화면에 보이는 것이 그대로 파일이 된다. `TextFormat.finalNewline`은 기록으로만 남는다.
  - **인코딩은 엄격하게 한다**(사용자 결정 2026-09-21). EUC-KR 문서에 그 인코딩으로 못 쓰는 글자가 들어오면 `?`로 바꾸지 않고 `Unencodable`로 거부하고, 어떤 글자가 버퍼 어디에 있는지를 돌려준다. `TextLoader`가 대체 문자로 읽지 않는 것과 같은 이유다. 인코딩을 먼저 하므로 그런 버퍼는 `stat`에도 닿지 않는다.
  - 임시 파일에 쓰고 rename하지 않는다(소유권·하드링크·심링크를 깬다). `openWrite(append = false)`가 양쪽 파일시스템에서 truncate다.
  - `content://` 저장은 이번 항목에서 뺐다(사용자 결정 2026-09-21). `stat`이 없어 충돌 비교가 성립하지 않고 `ContentResolver`가 필요하다 — 지금은 읽기 전용이다.
  - **부르는 것은 M4의 `Save File` 커맨드다.** 기준선은 `OpenDocuments.Entry.base`가 들고 있다.
- [x] `FileWatcher`: 원격 폴링(파일을 연 browse 연결), `FileObserver`, `content://` 폴링. 깨끗한 버퍼는 병합하고 수정 중이면 배너
  - `FileWatcher`는 **무엇을 볼지 모른다.** 보는 대상은 `WatchedFile` 셋이고(`WatchedPath`, `WatchedLocalPath`, `WatchedContent`), 루프가 묻는 것은 "지금 어떻게 생겼나(`stamp`)"와 "다시 읽으면 뭐가 나오나(`read`)" 둘뿐이다. 덕분에 폴링 로직만 평범한 JVM에서 MINA로 시험된다.
  - `Stamped`는 `At`/`Gone`/`Unknown` 셋이다. `Unknown`은 비교할 것이 아무것도 없는 `content://`이고, 그 경우 `watch()`는 돌지 않고 **그냥 돌아온다**. 앱이 앞으로 돌아올 때 부르는 `recheck(currentText)`만이 그런 문서를 본다.
  - **stat이 실패한 것은 변경이 아니다.** 서버가 대답하지 않으면 로그만 남기고 다음 틱에 다시 묻는다. 읽기가 실패해도 기준 stamp를 올리지 않아서 다음 틱이 다시 시도한다. 그 대가로 stat과 read 사이에 또 바뀌면 같은 변경을 두 번 보고하는데, 두 번째는 바꿀 것이 없는 병합이라 화면에 아무 일도 일어나지 않는다.
  - 로컬은 **파일이 아니라 부모 디렉토리**를 `FileObserver`로 본다. 대부분의 편집기는 rename으로 쓰는데, 그러면 inode가 바뀌어 파일에 건 observer가 말없이 죽는다. 디렉토리는 자식 이름을 알려주므로 교체가 보인다. 2초 타임아웃은 그대로 두어, 이벤트를 놓쳐도 늦을 뿐 안 오지는 않게 한다.
  - `FileObserver`는 생성자가 아니라 **첫 `awaitHint`에서 켠다.** 열어 두기만 하고 보지 않는 문서(앱이 뒤에 있는 동안 교체된 것)가 뒤에 아무것도 남기지 않게 한다. 끄는 것은 `watch()`의 `finally`다.
  - **앞에 있을 때만 폰다.** `onStop`에서 폴링을 끊고 `onStart`에서 `recheck` 한 번으로 시작하므로, 아무도 안 보는 화면 때문에 서버에 2초마다 묻지 않는다.
  - 액티비티가 재생성돼도 `FileWatcher`가 그대로 넘어간다(`onRetainNonConfigurationInstance`). 마지막으로 본 stamp를 들고 있으니 **회전이 변경으로 보이지 않는다.**
  - 페이지가 정하는 것: 깨끗하면 말없이 받고, 타이핑한 적이 있으면 배너를 띄운다. Kotlin은 **더티 여부를 모른다** — 아는 쪽이 정한다.
  - 받을 때는 문서를 통째로 갈아끼우지 않고 `@codemirror/merge`의 `diff`로 **다른 구간만 한 트랜잭션에 실어** 커서·선택·스크롤이 매핑을 타고 넘어오게 한다. 통째로 바꾸면 그 셋이 전부 문서 끝으로 간다. `timeout: 250`이라 아주 다른 두 본문에서는 덜 정밀한 알고리즘으로 떨어질 뿐 결과는 맞다.
  - 마크다운은 렌더된 DOM이 버퍼를 따라오지 않으므로 다시 렌더하고 보던 줄로 돌려놓는다. 뷰가 아직 없는 동안(에디터에 한 번도 안 간 마크다운)은 `source`를 갈아두어 나중에 만들어지는 뷰가 새 본문으로 열린다.
  - 배너는 화면 **아래**에 둔다. 위 메뉴는 스크롤에 따라 미끄러져 올라가서 메시지를 데리고 사라진다.
  - 실기기(탭) 확인: 로컬 파일은 `echo >>` 하면 **거의 즉시**(FileObserver) 반영되고, 원격은 **2.05초**만에 반영됐다. 타이핑한 뒤에는 배너가 뜨고 "내 것 유지"는 버퍼를 지켰으며 "다시 불러오기"는 파일 내용을 받았고, 받고 나면 다시 깨끗해져 그다음 변경은 말없이 병합됐다. 지우면 "이 파일이 사라졌습니다"에 닫기 하나. 홈으로 나갔다 들어오면 그동안의 변경이 들어와 있었다. 마크다운은 뷰어에서 다시 렌더되고 에디터로 넘어가도 새 본문이었다.
- [x] 사이드바(①): 슬라이드 인/아웃, 열린 파일 목록과 전환
  - **이 항목에서 문서가 하나에서 여럿이 됐다**(2026-09-21 사용자 결정). "열린 파일 목록과 전환"은 여러 파일을 열어 두는 일을 같이 해야 말이 되고, 전환이 버퍼를 버리면 수정 중인 파일에서 나갔다 오는 순간 타이핑한 것이 사라지기 때문이다. 상한 30과 축출 규칙만 다음 항목에 남겼다.
  - `MainActivity`가 `document` 하나 대신 `OpenDocuments`를 든다. **Kotlin이 갖는 것은 파일이 마지막으로 말한 본문과 그 파일에 건 감시뿐이다.** 버퍼(`EditorState`), 레이어, 보던 줄은 페이지가 갖는다 — CodeMirror가 사는 곳이 거기다.
  - 브리지: `documents`(목록과 활성), `document {id?}`, `activate {id}`, `close {id}`, 알림 `documentsChanged`. `documentChanged`는 없앴다. **목록이나 활성이 움직이는 모든 길이 `documentsChanged` 하나로 모이고**, 페이지는 `reconcile()` 한 가지 방법으로만 화면에 도달한다. 링크든 사이드바 탭이든 페이지 새로고침이든 같은 길이다.
  - `fileChanged`에 `id`가 붙었다. 감시는 **보이는 파일만 폰다.** 전환하면 그 파일을 한 번 보고(`recheck`) 폴링을 시작하고, 앱으로 돌아오면 열린 파일 **전부**를 한 번씩 본다.
  - **배경 파일에 온 변경:** 깨끗하면 그 자리에서 state에 병합하고(뷰가 없어도 `EditorState.update`로 된다), 타이핑한 적이 있으면 들고 있다가 그 파일이 화면에 올 때 배너로 묻는다.
  - **더티 표시를 `StateField`로 옮겼다.** 클로저에 있으면 파일이 배경으로 갈 때 같이 사라진다 — 남는 것은 state뿐이다. 같은 이유로 `Compartment`가 모듈에 하나씩이다: 돌아온 파일은 두고 간 state를 감싼 새 pane이고, `reconfigure`는 그 state를 만든 바로 그 compartment 인스턴스에만 먹는다. compartment 안의 값은 state마다라서 파일별 언어와 레이어는 그대로다.
  - **같은 파일을 가리키는 링크가 다시 오면 열지 않고 전환한다.** 키는 로컬 경로 / 프로필 id + 경로 / `content://` uri다(별칭이나 주소가 아니라 **풀린 프로필의 id**라서, 같은 서버를 어떻게 부른 링크든 한 파일이다). 링크의 `line`은 따라간다.
  - **닫기(×)를 넣었다**(2026-09-21 사용자 결정). 수정 중이면 그 줄에서 묻는다 — 저장이 M4라 "사라집니다"가 정확하다. 활성 파일을 닫으면 그다음, 마지막이었으면 그 앞이 활성화되고, 하나뿐이었으면 빈 화면이다.
  - **사이드바에 제목은 없고, ①을 상단 메뉴의 것과 똑같은 자리에 다시 둔다**(2026-09-21 사용자 결정). 사이드바가 나와 있는 동안 상단 메뉴를 덮으니, 연 자리가 곧 닫는 자리다. 자리를 정확히 맞추려면 상단 메뉴와 같은 1px 아래 테두리가 있어야 한다 — 그게 없으면 `align-items: center`가 0.5px 어긋난다.
  - 경로 줄은 **오른쪽에서 왼쪽으로** 잘린다(LRM을 앞에 붙여 경로 자체는 바로 읽힌다). 좁은 화면에서 살아남아야 하는 것은 파일이 든 디렉토리 쪽이다.
  - 실기기(탭) 확인, 전부 DevTools로 읽었다: 로컬 둘 + 이 맥의 원격 하나를 열어 목록에 셋이 쌓였고, 전환하면 버퍼·편집 레이어·보던 줄이 그대로 돌아왔다. 전환 뒤에 Ctrl+Z가 **전환 전에 친 것을 되돌렸다**(undo 기록이 state를 타고 넘어온다). 배경의 원격 파일을 서버에서 바꿔도 화면은 가만히 있다가 그 파일로 가면 말없이 들어왔고, 배경의 더티 파일은 돌아오니 배너로 물었다. 깨끗한 파일은 ×로 바로 닫히고 더티 파일은 한 번 묻는다(취소하면 줄이 돌아온다). 셋을 연 채 `font_scale`을 흔들어도 목록과 활성 파일이 그대로였다.
- [x] 열린 파일 `EditorState` LRU(기본 30, 수정 중인 파일은 제외) + vitest
  - **버리는 것은 `PaneMemory.state` 하나다**(2026-09-21 사용자 결정). `source`·`layer`·`line`은 남으므로 돌아온 파일은 같은 본문, 같은 레이어, 보던 줄 그대로 열리고 undo 기록과 선택만 사라진다. 본문은 pane이 나가면서 버퍼를 `source`에 써 두니 화면에서 보던 것과 같고, 버리는 대상은 깨끗한 파일뿐이라 파일과도 같다.
  - **더티 파일은 버리지 않지만 30자리 중 하나를 차지한다**(2026-09-21 사용자 결정). 상한이 "살아 있는 `EditorState` 수"를 그대로 뜻하게 하려는 것이다. 버리지 않는 이유는 따로다 — 깨끗한 버퍼는 파일의 본문으로 되살아나지만 친 것은 어디에도 없다.
  - **순서는 `memories` 맵 자체다.** 화면에 오는 파일은 맵에서 빼고(이제 pane의 것이다) 나갈 때 끝에 넣으므로 삽입 순서가 곧 "오래 전에 나간 순"이고, 따로 시각을 적을 것이 없다. **빼는 일이 세는 일보다 먼저여야 한다** — 도착하는 파일이 맵에서 가장 오래된 항목이라, 순서가 바뀌면 지금 열릴 버퍼를 제일 먼저 잃는다.
  - 에디터에 간 적이 없는 마크다운은 `state`가 없어 자리를 차지하지 않는다. 기본값 30은 M4의 `settings.toml`이 사용자 것으로 만들 값이다.
  - **`web/`에 vitest가 처음 들어왔다**(`code/web/test`, `npm test`, `:code:testWeb`, `check`가 같이 돈다). DOM 없이 node에서 돈다 — 여기서 시험하는 것은 페이지가 무엇을 들고 있느냐지 무엇을 그리느냐가 아니다. Kotlin에 닿는 모듈은 전부 `bridge.ts`를 거치는데 그것이 WebView 밖에서 로드를 거부하므로 `test/bridge.ts`가 전역을 먼저 놓는다. 테스트 8개이고, 더티가 자리를 차지한다는 결정은 그중 한 개가 지킨다(반대로 고치면 그것만 깨진다).
  - 실기기(탭) 확인, DevTools로 읽었다: **상한을 1로 낮춘 빌드**로 봤다. `big.ts`를 편집 레이어 500줄에 두고 파일 둘을 더 연 뒤 돌아가니 **레이어와 보던 줄(501)은 그대로였고 커서만 0**이었다 — 버퍼만 버려졌다. 같은 파일에 타이핑한 뒤에는 파일 셋을 거쳐 돌아와도 **친 것도 커서(16787)도 그대로**였다. 30으로 되돌린 빌드에서는 넷을 돌아도 커서가 그대로다.
- (M3의 확인 항목은 **M4로 옮겼다**(2026-09-21 사용자 결정). 앞 절반인 실시간 반영과 배너는 이 마일스톤에서 이미 봤고, 뒤 절반인 저장은 저장을 거는 UI가 M4의 커맨드 레지스트리라 지금은 앱에서 부를 길이 없다.)

### M4. 커맨드 팔레트, 설정, 테마
- [x] 커맨드 버튼 A/B/C 상태 기계 + vitest(취소하면 A, 실행 후 모드 유지)
  - 상태 기계는 `palette.ts`에 DOM 없이 두고(vitest가 보는 것이 그것이다) 그리는 것은 `chrome/palette.ts`다. C에서 무엇이 뜨는지는 `items(mode)` 하나로 밖에서 들어온다 — 그 자리에 다음 항목의 퍼지 점수가, 그다음 항목의 레지스트리가 붙는다.
  - **실행할 것이 있어야 "실행 후 A로"를 볼 수 있으므로** 상단 메뉴가 이미 하는 셋(`Toggle Layer`, `Reset Zoom`, `Toggle Sidebar`)을 이 항목에서 등록한다. 고르는 방법은 아직 부분 문자열이다 — 퍼지 점수가 다음 항목이라 그것 하나만 바뀐다.
  - 모드는 값으로만 있다(기본 `>`). 스와이프로 바꾸는 것은 다음 항목이고, 여기서 모드가 있는 이유는 **실행과 취소를 건너 살아남아야** 하기 때문이다.
  - **누르는 순간이 아니라 탭이 끝날 때 움직인다.** 처음에는 결과 줄을 `pointerdown`에서 실행했는데, 그러면 실행이 화면을 바꾼 뒤에 **뒤따르는 click이 그 새 화면에 떨어진다** — `Toggle Sidebar`를 눌렀더니 사이드바가 열리고, 그 click을 사이드바의 스크림이 받아 곧바로 닫혔다(기기에서 본 것). 지금은 `pointerdown`이 `preventDefault`로 **포커스만 붙잡고**(입력칸이 포커스를 잃으면 키보드가 내려가고, 키보드만큼 짧던 페이지가 손가락 밑에서 자라 누르던 것이 비켜난다) 실행은 `click`에서 한다. 버튼과 스크림도 같다.
  - 팔레트 버튼은 배너 위에 선다(`--banner-space`). 배너가 자기 높이를 그 변수에 적는다 — 아니면 "다시 불러오기"를 버튼이 덮는다.
  - 실기기(탭) 확인, 전부 진짜 터치로: 버튼을 누르면 입력칸이 뜨고 **키보드가 English로** 올라왔다. `tog`를 치니 `*Toggle Layer` / `Toggle Sidebar`가 뜨고, 입력 버튼으로 실행하니 레이어가 편집→읽기로 바뀌며 A로 돌아갔다. 결과 줄을 손으로 눌러도 실행됐고(사이드바가 열린 채 남았다), 빈 곳을 누르면 취소되어 A로 돌아가고 **그 탭이 문서에 닿지 않았다.** 외장 키보드의 ↓와 Enter도 선택을 옮기고 실행했다. 배너가 떠 있을 때 버튼은 배너 위(260 < 276)에 있었다.
- [x] 스와이프 모드 전환(`>` / 🔍 / `@`)과 퍼지 점수(`fuzzy.ts`) + vitest
  - **스와이프 한 번에 한 칸이다.** 24px을 넘으면 돌고, 손가락을 뗄 때까지 더 돌지 않는다. 처음에는 24px마다 계속 돌게 해봤는데, 60px 한 번에 두 칸이 넘어가 **어디에 설지 겨냥해야 하는 제스처**가 됐다. 모드가 셋뿐이라 겨냥할 이유가 없다. 위가 앞(`>`→🔍→`@`), 아래가 뒤이고 양쪽으로 순환한다.
  - **버튼에 `touch-action: none`이 있어야 한다.** 없으면 몇 픽셀 만에 브라우저가 그 제스처를 스크롤로 가져가며 `pointercancel`을 보내고 모드는 영영 돌지 않는다 — 기기에서 이벤트를 찍어 확인했다(`pointerdown` → `pointermove` 한 번 → `pointercancel`).
  - 스와이프로 끝난 제스처는 마지막에 오는 click을 버린다. 안 그러면 모드를 바꾼 손가락이 그대로 무언가를 실행한다.
  - **점수는 `fuzzy.ts` 하나뿐이고 한 번의 스캔이다.** 질의의 글자마다 "여기서 끝난다면 얼마"를 이름의 자리마다 들고 다음 글자로 넘긴다 — 왼쪽에서 오른쪽으로 걸으며 처음 찾은 자리를 쓰면 `Toggle Layer`에서 `tl`의 `l`이 `Toggle`의 것이 되어 `Layer`로 시작하는 쪽과 구별되지 않는다.
  - 점수는 단어 첫 글자(대문자나 구분자 뒤) 8, 앞 글자에 이어지면 8, 이름의 처음 10, 그냥 맞으면 1, 건너뛰면 -2다. **이어지는 것이 단어 첫 글자보다 커야** `togg`가 `The Old Grey Goose`보다 `Toggle Layer`를 고른다(글자마다 단어를 시작하는 이름이 그만큼 세다). 같은 점수는 들어온 순서를 지킨다.
  - 맞은 글자를 굵게 표시하는 것은 하지 않았다. 항목에 없다.
  - 실기기(탭) 확인: 스와이프로 `>`→🔍→`@`→`>`가 한 칸씩 돌고 아래로도 돈다. 질의가 들어 있는 채로 모드를 바꾸면 결과가 그 자리에서 다시 나왔다(🔍는 아직 대상이 없어 `No matches`). `tl`에 `*Toggle Layer`가 먼저 왔고, 입력 버튼 탭으로 실행되어 A로 돌아갔으며 **글리프는 마지막 모드 그대로**였다. 취소하고 다시 열어도 그대로다.
- [x] B에서 최근 실행한 다섯 개를 미리 보여주고, 팔레트 가로를 600px에서 끊는다 (2026-09-21 사용자 요청) + vitest
  - 기억하는 것은 **이름**이다. 항목은 매번 새로 만들어지고 사라질 수도 있어서, 보여줄 때 지금 그 모드가 내놓는 것들 중에서 다시 찾는다. 없으면 조용히 빠진다.
  - 실기기(탭) 확인, 가로: 페이지 1152px에서 팔레트가 **600px**이고 오른쪽 16px, 아래 16px에 붙어 있다. 결과 상자도 같은 600px이다.
  - 실기기(탭) 확인, 아이콘: 세 모드의 버튼을 화면으로 봤다 — `>_`, 돋보기, `@`가 모두 흰 선 하나로 24px에 그려져 있다(채움 없음).
  - 실기기(탭) 확인, 저장: `Toggle Sidebar`를 실행하니 `palette.recent`에 `{"command":["Toggle Sidebar"]}`가 적혔고, **앱을 force-stop 하고 다시 켜니** 팔레트가 그것을 선택된 채로 열었다.
  - 실기기(탭) 확인, 최근 목록: 처음 열면 목록이 아예 없고, `reset`으로 한 번 실행한 뒤 다시 열면 `*Reset Zoom`이 선택된 채로 떠서 **타이핑 없이 그 줄을 눌러 실행**됐다. 하나 더 실행하니 최신이 위로 왔고, 🔍로 스와이프하면 그 모드에는 최근이 없어 비었다가 `>`로 돌아오면 다시 나왔다.
- [ ] 커맨드 레지스트리(레이어 전환, 확대/축소, 저장, 사이드바, 설정 열기 등)와 파일 모드(열린 파일 + 같은 디렉토리), 단일 파일 심볼 모드(lezer)
  - **셋으로 나눠서 한다**(2026-09-21 사용자 결정): ① `>` 커맨드 레지스트리와 저장, ② 파일 모드(🔍), ③ 심볼 모드(`@`). 아래는 ①에 적은 것이고, ②와 ③은 아직 `items(mode)`에 빈 목록을 돌려준다.
  - **`main` 병합은 ①②③과 그다음 확인 항목이 끝난 뒤에 묻는다**(2026-09-21 사용자 결정). 저장 커맨드가 붙기 전까지 main의 Skiff Code는 저장할 수 없는 에디터였다. 브랜치가 앞서 있고 main에만 있는 것이 없어 fast-forward다. worktree에서 `git checkout main`은 하지 않는다.
  - **①에 등록한 커맨드**(`commands.ts`, 이름은 전부 영문): `Save File`, `Toggle Layer`, `Show Editor`, `Show Viewer`, `Undo`, `Redo`, `Zoom In`, `Zoom Out`, `Reset Zoom`, `Toggle Sidebar`, `Reload File`, `Close File`. 사용자가 레이어 직접 전환·Undo/Redo·Reload File을 더했다(2026-09-21).
    - **`Show Diff`는 넣지 않았다**(2026-09-21 사용자 결정). 비교 대상이 M5에 생기므로 지금 들어가면 읽기 전용 버퍼만 보인다. M5에서 더한다.
    - **`Open Settings`도 미뤘다**(2026-09-21 사용자 결정). 열 파일이 없다 — `settings.toml` 항목에서 파일 자리가 정해질 때 같이 넣는다.
    - **목록은 화면에 무엇이 있는지에 따라 매번 새로 만든다.** 열린 파일이 없으면 확대/축소와 사이드바뿐이고, 못 연 파일(너무 큼·바이너리)은 레이어가 없으므로 `Reload File`과 `Close File`만 남는다. `Undo`/`Redo`는 편집 레이어에서만 뜬다 — 눌러도 아무 일도 없는 줄이 목록에 있는 것보다 없는 편이 낫다.
    - `Close File`은 수정 중이면 **사이드바를 열어 그 줄에서 묻는다.** 같은 질문이 이미 거기 있고, 무엇을 닫는지 보인다.
    - `Reload File`은 **감시가 알아차리기를 기다리지 않고 지금 다시 읽는다**(`FileWatcher.reread`). 읽은 것은 여느 변경과 같은 길(`fileChanged`)로 가므로 깨끗한 버퍼는 조용히 받고 타이핑한 버퍼는 배너로 묻는다. 같은 초에 크기까지 같은 변경은 `stat`으로 볼 수 없는데, 이 커맨드가 그 사각을 사람 손으로 메운다.
  - **저장 기준선은 감시의 것과 다르다**(`OpenDocuments.Entry.base`). `FileWatcher`의 `known`은 변경을 **본** 순간 움직인다 — 같은 변경을 두 번 보고하지 않으려는 것이다. 저장이 비교하는 기준선은 **버퍼가 그 변경을 받았을 때** 움직여야 한다. 그래야 "내 것 유지"가 뜻대로 된다(`skiffcode.spec.md`의 2026-09-21 결정): 버퍼는 그대로니 그 위에 저장하면 `Conflict`가 나온다.
    - 받았다고 말하는 것은 **페이지다**(`adopted` 알림). 버퍼가 받았는지 아는 쪽이 페이지뿐이다. Kotlin은 페이지에 밀어 준 본문의 stamp를 `offered`에 들고 있다가 그 말을 들으면 `base`로 옮긴다.
    - 한 왕복 안에 변경이 두 번 오면 `offered`가 두 번째 것이라 `base`가 한 칸 앞설 수 있다. 파일이 몇 밀리초 사이에 두 번 바뀌고 그 사이에 사용자가 타이핑해야 하는 창이고, 값은 막았어야 할 저장 하나가 지나가는 것이다.
    - 우리가 쓴 것은 감시에게 따로 알린다(`FileWatcher.saved`). 아니면 우리 저장이 다음 틱에 남의 변경으로 돌아온다.
  - **저장 결과는 Kotlin이 문장으로 돌려준다**(`{result, message}`). 팔레트 안쪽만 영문이고 배너는 여느 화면과 같이 string resource다. 성공은 2.5초 뒤 스스로 사라지는 배너(`banner.flash`)이고, 나머지(`conflict`/`gone`/`unencodable`/`readonly`)는 닫기가 붙은 배너다. 충돌은 **묻지 않고 알리기만 한다** — 남의 변경은 감시가 데려오고, 묻는 것은 그쪽 배너의 일이다.
    - 쓸 수 없는 글자는 **몇 번째 줄인지까지 말한다**(`DocumentSaver`가 준 버퍼 안 위치를 Kotlin이 줄 번호로 바꾼다).
    - `content://`는 `readonly`로 답한다. 커맨드를 감추지 않는 이유는 왜 안 되는지가 답이기 때문이다.
    - **저장할 것이 없어도 그냥 저장한다.** 더티 여부로 길을 나누지 않는다 — 같은 길 하나가 늘 도는 편이 예측 가능하고, 안 바뀐 본문을 쓰는 값은 mtime 하나다.
  - **확대 배율을 `localStorage`에 넣었다**(2026-09-21 사용자 결정, 키 `zoom.size`). `Zoom In`/`Zoom Out`이 어차피 `zoom.ts`를 건드리는 항목이라 여기서 같이 했다. 한 번에 2px이고, 핀치 중에는 매 프레임이 아니라 **손가락이 멎고 400ms 뒤에** 쓴다. 돌아온 값은 숫자인지 보고 8~40으로 자른다.
    - `recents.ts`가 갖고 있던 `localStorage` 감싸기를 `storage.ts`로 옮겼다. 이제 쓰는 곳이 둘이다.
  - **①의 실기기(탭) 확인, DevTools로 페이지를 몰아서:** `Show Editor`로 레이어가 바뀌고, 친 글자가 들어간 뒤 `Save File`이 **기기 파일에 그대로 떨어졌다**(`DocumentSaver`가 앱에서 처음 돈 자리다). 배너는 "저장했습니다."를 띄우고 **2.5초 뒤 스스로 사라졌다.**
    - **충돌:** 타이핑한 채로 기기 셸에서 `echo >>` 하니 배너가 물었고, **"내 것 유지"를 고른 뒤 저장하니 거부됐다**("…다른 곳에서 바뀌어서 저장하지 않았습니다"). 디스크에는 남의 변경이 그대로 남아 있었다 — 기준선을 감시에서 떼어낸 이유가 이것이다.
    - `Reload File`이 더티 버퍼에 다시 물었고, "다시 불러오기"를 고르니 파일 내용이 들어오면서 기준선도 따라 움직여 **그다음 저장은 통과했다.**
    - `Undo`/`Redo`가 팔레트에서 돌고, `Close File`은 사이드바를 열어 그 줄에서 물었다(취소하면 돌아온다).
    - 확대: `Zoom In` 둘에 14→18px, `Reset Zoom`이 14px, `localStorage`의 `zoom.size`도 따라 움직였고 **force-stop 후 다시 켜니 16px로 열렸다**(CodeMirror 뷰도 16px).
    - B(빈 입력)에 `Save File`·`Reload File` 같은 새 커맨드가 최근 목록으로 떴다.
- [ ] **확인(M3에서 옮겨 왔다):** 서버에서 `echo >> file` 하면 2초 안에 반영되고, 수정 중에는 배너가 뜬다. 저장 후 서버의 `git diff`에 의도한 변경만 보인다
  - **앞 절반은 2026-09-21 세션에 탭에서 이미 봤다** — 원격은 2.05초(폴더블은 1초), 타이핑한 뒤에는 배너가 뜨고 "내 것 유지"와 "다시 불러오기"가 각각 버퍼와 파일을 택했다. 다시 볼 것은 저장 쪽이다.
  - 저장을 거는 커맨드가 바로 위 항목이라 여기에 있다. `DocumentSaver`는 M3에서 다 만들었고 MINA 유닛 테스트만 덮고 있다 — **실제 앱에서 돌려본 적이 없다.**
- [ ] `settings.toml` 로드와 적용 + `SettingsTomlTest`(왕복, 없는 키는 기본값, 잘못된 값)
- [ ] `themes/*.toml` → CSS 변수 매핑(메뉴와 레이어 전체), 다크/라이트 번들, 폴백 + vitest
- [ ] 설정과 테마 import/export(SAF)
- [x] `.jsonl`을 JSON 문법으로 연다 (2026-09-23 사용자 요청) + vitest
  - `FileKind`는 건드리지 않기로 했다(2026-09-23 사용자 결정). Skiff 목록에서 `.jsonl`을 탭하면 여전히 밖으로 나가고, 강조는 링크로 연 Skiff Code 안에서만이다.
- [x] 링크를 여는 동안의 로딩 표시: 위 라인과 문서 모양 (2026-09-24 사용자 요청) + vitest 5개
  - **아직 기기에서 눈으로 못 봤다.** 로컬 파일은 200ms 안에 끝나 설계대로 아무것도 뜨지 않는다 — 실제로 보려면 SFTP 파일을 여는 링크가 필요하다. 탭에 설치는 해두었다.
- [x] 상단바 ① 옆에 파일 이름과 저장 상태 점 (2026-09-24 사용자 요청)
  - 설계는 `skiffcode.spec.md`의 "화면 메뉴"에 적었다. 건드릴 곳은 다섯이다: `chrome/topbar.ts`(`setFile(name, dirty)`와 DOM), `index.html`(말줄임과 점 스타일), `layers/pane.ts`(`openPane`에 `onDirtyChange`), `main.ts`(문서를 그릴 때와 콜백에서 갱신), Kotlin `MainActivity`의 `labels` + `values/strings.xml`·`values-ko/strings.xml`(점의 접근성 이름).
  - 콜백은 뷰가 있어야 도는 것이라 vitest로 잡기 어렵다. `dirtyFlag`의 뒤집힘 자체는 이미 상태 수준에서 덮여 있다.
  - **확인:** 태블릿에서 타이핑하면 점이 켜지고 `Save File` 뒤에 꺼진다. 폴드 커버 화면에서 긴 이름이 오른쪽 버튼들을 밀지 않고 말줄임된다.
  - **2026-09-25 탭에서 DevTools로 확인했다:** 편집 레이어에서 한 글자 치면 점이 켜지고(이름 바로 뒤 8px), `Save File` 뒤 "저장했습니다."와 함께 꺼졌다. 친 파일에서 사이드바로 다른 파일에 갔다 오면 점이 다시 켜져 있다(기억해 둔 state에서 읽는다). 긴 이름은 탭 폭(1152px)에선 다 들어가고, 뷰포트를 커버 화면 폭(475)으로 흉내 내면 이름이 56–323에서 말줄임되고 점 331–339, 버튼 묶음 347–467로 밀리지 않았다. **2026-09-25 폴드8 실기기 커버 화면에서도 사용자가 확인했다** — 긴 이름이 말줄임되고 버튼을 밀지 않는다.
- [x] 상단바 파일 이름을 탭하면 열린 파일 메뉴가 위에서 내려오고, 사이드바에서는 열린 파일을 뺀다 (2026-09-26 사용자 요청) + vitest 9개
  - 설계는 `skiffcode.spec.md`의 "화면 메뉴"에 적었다. 목록 코드는 `chrome/sidebar.ts`에서 `chrome/openfiles.ts`로 **옮겼다**(복사가 아니다). 사이드바는 ①과 "열린 프로젝트가 없습니다" 한 줄만 남은 틀이다.
  - 바를 놓을 때 닫을지는 `dropdown.ts`가 정한다. **속도는 마지막 두 이벤트가 아니라 마지막 100ms로 잰다** — 처음엔 두 이벤트 사이로 쟀는데, adb 너머 합성 터치로 같은 튕김이 한 번은 닫히고 한 번은 안 닫혔다. 손가락은 튕김 끝에서 느려지니 실제 터치에서도 같은 문제다.
  - **2026-09-26 탭에서 CDP 터치로 확인했다(파일 7개):** 닫혀 있을 때 메뉴는 화면 위(bottom 0)에 있고, 이름을 탭하면 56–419px로 내려온다(600px 폭, 5줄 338px + 바 24px, 목록은 134.5px 더 스크롤된다). 목록을 끝까지, 그리고 끝을 넘어 끌어도 문서는 0에서 움직이지 않았다. 바를 30px 천천히 끌면 손가락을 따라 −6…−30px 올라갔다가 돌아오고, 80px을 60ms에 튕기면(1/4인 90px 미만) 닫혔다. 120px 천천히 끌기, 바 탭, 바깥 탭, 이름 다시 탭이 모두 닫았다. 줄 탭으로 전환되고, 메뉴가 열린 채 ①을 누르면 메뉴가 들어가고 사이드바가 "열린 프로젝트가 없습니다."와 함께 나왔다. 깨끗한 파일의 ×는 바로 닫고 메뉴는 열린 채 남는다. 친 파일에 팔레트 `Close File`을 실행하니 메뉴가 열리며 그 줄이 물었고(높이 382, 묻는 줄도 한 줄로 셌다) "닫기"로 닫혔다. 3개로 줄이니 메뉴가 228px로 줄었고, 다 닫으니 "열린 파일이 없습니다." 한 줄에 이름 버튼이 꺼졌다.
  - **사용자 요청으로 둘을 더했다(2026-09-26): 열려 있는 동안 바깥 문서가 스크롤되지 않게, 팔레트가 열리면 닫히게.** 고치기 전에 재 보니 메뉴가 열린 채 흐린 막을 끌면 문서가 507px 움직이며 상단 메뉴가 숨었고, 상단 메뉴를 끌어도 움직였다. 고친 뒤 같은 드래그에 문서는 0에 그대로였고, 닫고 나면 같은 드래그로 다시 스크롤됐다(533px). 메뉴가 열린 채 팔레트 버튼을 누르니 메뉴가 닫히고 팔레트가 열렸다.
  - **바를 짧게 끌었다 놓은 직후의 탭이 먹지 않았다(2026-09-26 고침).** 드래그 뒤 따라오는 click을 삼키려고 세운 플래그가, WebView가 드래그 뒤에 click을 아예 보내지 않아서 다음 진짜 탭을 삼켰다. 손가락이 내려올 때 지운다. 탭에서 `adb shell input swipe`로 짧게 끈 뒤 `input tap` 한 번에 닫히는 것을 봤다.
  - **2026-09-26 사용자가 손으로 확인했다:** 설치한 APK를 실기기에서 써 보고, 폴드 커버 화면에서도 전부 의도한 대로라고 했다 — 내려오고 올라가는 모습, 바 쓸어 올리기, 바깥 스크롤 막기, 팔레트와의 관계까지.
- [ ] **확인:** 실기기에서 팔레트 흐름을 녹화하고, 테마를 바꾸면 메뉴, 사이드바, 팔레트, 세 레이어가 한 번에 바뀐다

### M5. 프로젝트 모드 + git
- [ ] `GitScopeFinder` + `GitScopeFinderTest`(MINA: 홈 경계에서 멈춤, worktree의 `.git` 파일, 홈 밖 경로는 찾지 않음)
- [ ] `ProjectStore`와 파일 여는 흐름 2~3단계(프로젝트 활성화, 묻는 창), 사이드바에 프로젝트 목록과 SFTP 지연 로딩 파일 트리
- [ ] `ShellQuote` + `ShellQuoteTest`(`'; rm -rf ~'`, 줄바꿈, `$()`, 백틱, 작은따옴표가 든 경로)
- [ ] `RemoteExec`(프로젝트 전용 SSHClient, exec 거부 감지). `AGENTS.md`의 exec 원칙 수정은 M0에서 이미 했다
- [ ] `GitService` + `GitServiceTest`(MINA에 `ProcessShellCommandFactory`를 붙여 **실제 `git`**을 임시 레포에 대해 실행)
- [ ] git 거터(viewer, editor, diff 공통)
- [ ] diff 레이어: unified, +/-, 초록/빨강 투명도 설정, 하이라이팅, viewer와 같은 스크롤/줌. ④ 더보기에 비교 대상 선택 추가
- [ ] 🔍 파일 모드를 프로젝트에서 `git ls-files` 캐시로 확장
- [ ] **확인:** 실기기에서 git 레포 안 파일을 열면 묻는 창이 뜨고, 프로젝트를 만든 뒤 거터와 diff가 서버의 `git diff`와 일치한다. internal-sftp 계정에서는 git 없이 열린다

### M6. LSP
실제 LSP 서버로 확인하던 M0 하네스는 사용자 지시로 지웠다. 다시 만들 때 필요한 것은 이것뿐이다.
- 서버 쪽: MINA `SshServer`에 `commandFactory = ProcessShellCommandFactory.INSTANCE`.
- 클라이언트 쪽: sshj `session.exec("/bin/sh -lc 'cd <root> && exec <command>'")`.
- 프레이밍: 헤더는 `\r\n\r\n`까지 한 바이트씩, 본문은 `Content-Length`만큼 채워 읽는다.
- 언어 서버: `npm i pyright` → `node_modules/.bin/pyright-langserver --stdio`. 레포에 넣지 않았다.
- **`org.json`은 유닛 테스트 JVM에서 스텁이라 전부 던진다.** `org.json:json`을 테스트 의존성으로 넣고
  Gradle에서는 `systemProperty`로 넘긴다(`-D`는 테스트 JVM에 전달되지 않는다).
- 결과: initialize 98ms, 진단까지 346ms(로컬 루프백). `positionEncoding`은 응답에 없고(= `utf-16`).

- [ ] `LspProcess` Content-Length 프레이밍 + `LspFramingTest`(분할과 결합, 멀티바이트 길이)
- [ ] `LspManager`: 지연 시작, 유휴와 백그라운드 종료, 재연결 후 `didOpen` 재전송, `command -v` 탐지
- [ ] Web `@codemirror/lsp-client` 연결: editor(진단, 자동완성, hover, 정의), viewer(길게 눌러 hover, 정의로 이동, 다른 파일이면 열기)
- [ ] `@` 심볼 모드를 프로젝트에서 LSP `documentSymbol`/`workspace/symbol`로 확장
- [ ] MINA exec + 로컬 LSP 서버(또는 에코 스텁)로 initialize 왕복 테스트
- [ ] **확인:** 실제 서버의 파이썬 프로젝트에서 정의 이동, 진단, 심볼 검색이 동작하고, 앱을 백그라운드에 오래 두면 원격 LSP 프로세스가 종료된다(`ps`로 확인)
- [ ] **데몬 판정 측정:** 와이파이↔LTE를 전환한 뒤 재접속부터 진단이 다시 뜰 때까지를 재고, 그중
      서버 재인덱싱이 차지하는 몫을 나눠서 `skiffcode.spec.md`의 "범위 밖" 표에 적는다. 데몬 결정의 유일한 실데이터다

## 아직 확인하지 않은 것

세션이 지나도 이어지는 목록이다. 확인하면 지우고, 안 되는 게 나오면 [`skiffcode.spec.md`](skiffcode.spec.md)의 설계를 먼저 고친다.
- **로딩 라인과 스켈레톤을 화면에서 본 적이 없다.** 유닛 테스트와 빌드까지다. 로컬 파일은 설계상 아무것도
  띄우지 않으므로 **SFTP 링크로만 볼 수 있다.** `prefers-reduced-motion` 쪽도 마찬가지다.
- **원격(SFTP) 저장을 기기에서 못 봤다.** 이번에 저장한 것은 로컬 파일이다. `DocumentSaver`의 MINA 유닛
  테스트는 원격 쪽을 덮고 있지만, 앱에서 원격 파일을 저장해 본 적은 없다 — 다음 확인 항목이 그것이다.
- **저장의 `unencodable`(EUC-KR 파일에 없는 글자)과 `readonly`(`content://`) 답을 기기에서 못 봤다.**
  문장과 줄 번호가 맞는지도 아직 눈으로 보지 않았다.
- **기준선이 한 칸 앞설 수 있는 창**(한 왕복 안에 변경이 두 번, 그 사이에 타이핑)은 만들어 보지 못했다.
- **팔레트를 좁은 화면에서 보지 못했다.** 가로 600px 상한은 탭에서만 걸렸다. **스와이프도 손가락이 아니라
  CDP 터치로만 봤다.**
- **다른 키보드에서 `inputmode="email"`이 먹는지 모른다.** 본 것은 탭의 삼성 키보드 하나다.
- **`localStorage`가 꽉 찼을 때**는 코드가 삼키지만 본 적은 없다.
- **열린 파일이 30개를 넘는 것을 실제로 보지 못했다.** 상한은 1로 낮춰서 확인했다.
- **`ACCESS_LOCAL_NETWORK`를 거부했을 때**가 남았다(`pm revoke` 후 다시 열면 된다).
- **Skiff → Skiff Code 프로필 공유를 아직 못 봤다.** 보려면 **Skiff에 서버를 하나 넣고** Skiff에서 파일을
  탭해야 한다. 이 맥의 원격 로그인은 켜져 있다 — **비밀번호 입력은 사용자가 직접 해야 한다.**
- **`content://` 감시를 기기에서 본 적이 없다.** 컬럼을 주는 provider와 안 주는 provider 양쪽 다.
- **Skiff(`:app`)의 호스트키 창은 기기에서 보지 않았다.** 로직은 `:core` 공용이고 남은 것은 다이얼로그 UI뿐이다.
- **손가락으로 하는 핀치.** 확인은 전부 CDP `Input.dispatchTouchEvent`로 만든 터치였다. 사이드바를 손가락으로
  밀어 여는 것도 없다 — 지금은 ①과 바깥 탭뿐이다.
- **editor의 손 사용감** 전반: 선택 핸들, 길게 누르기, 커서 옮기기.
- **서명이 다른 앱이 끼어드는 경로**(가짜 Skiff Code, 가짜 provider)는 그런 앱을 만들지 않아 보지 못했다.
- **Android 15 미만에서 링크가 전부 확인창을 받는 것.** 두 기기 다 15 이상이다.
- release APK의 배포용 서명. 지난번에는 debug 키로 서명해서 확인만 했다.
- **실제 SSH 서버로 LSP를 띄워 본 적은 없다.** M0의 확인은 이 맥 안의 MINA 루프백이다.
- 진단이 수백~수천 개일 때의 비용. 큰 파일에서 편집 중 동기화 비용. **2MB 파일에서 병합 diff가 얼마나
  드는지도 아직 안 쟀다.**
- 테마 CSS 변수와 SAF import/export는 라이브러리 버전도 고르지 않았다. lezer 심볼 추출은 패키지가 이미
  있으니 남은 것은 **무엇을 심볼로 볼지**다.

## 알려진 문제

사용자에게 알렸고, 범위 밖이라 손대지 않고 둔 것이다.

1. **재생성 때 열린 파일 목록과 활성 파일은 넘어가지만, 파일들의 버퍼·레이어·스크롤은 넘어가지 않는다.**
   `uiMode`(다크 모드), 언어, 글꼴 크기는 `configChanges`에 없어서, 바꾸면 보던 레이어가 뷰어로 돌아가고
   **타이핑하던 것이 사라진다.** 일부러 재생성시키려면 `settings put system font_scale 1.3` 후 되돌린다.
   **확대 배율은 이번에 `localStorage`로 넘어갔다** — 같은 길이 나머지에도 답이 될 수 있지만 **버퍼 본문까지
   적을지부터 정해야 한다.**
2. **확인 창이 떠 있는데 새 링크가 오면 앞의 것이 소리 없이 대체된다.** adb로 파일 여럿을 열 때는
   **한 번에 하나씩 열고 "열기"를 누른 뒤 다음을 보내야 한다.**
3. **줄바꿈이 없어 좁은 화면에서 문장이 잘린다.** 커버 화면(475dp)에서 마크다운 본문 한 줄이 오른쪽으로
   사라진다. `settings.toml`에 항목으로 잡혀 있지만 **기본값을 무엇으로 둘지**가 폴더블 때문에 실제 문제가 됐다.
4. **경로 빵부스러기가 좁은 화면에서 오른쪽으로 잘린다**(Skiff, 커버 화면). 열린 파일 메뉴의 경로 줄은 왼쪽이
   잘리게 해 두었으니 Skiff도 같은 방향이 맞을 듯하다.
5. **시스템 글꼴 배율이 그대로 곱해진다.** 폴더블의 1.5 때문에 14px이 21px이 된다. 따를지 지울지 정해야 한다.
6. **키 교환 대기가 5분이다.** 줄이고 싶으면 `SshClientFactory.KEX_TIMEOUT_MS`다.
7. 한 번 봤지만 재현하지 못한 것: Skiff 화면 아래 절반이 **빈 키보드 창**에 먹혀 있었다(폴더블,
   접기/펴기 직후). 세 번 더 해봤지만 재현되지 않아 확정된 버그로 적지 않는다.

## 검증 명령

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17
./gradlew :core:testDebugUnitTest :app:testDebugUnitTest :code:testDebugUnitTest
(cd code/web && npm test)
./gradlew :code:lintDebug :app:installDebug :code:installDebug
adb -s <serial> shell am start -a android.intent.action.VIEW -d 'skiffcode://<user>@<host>:22/<path>'
adb -s <serial> logcat -s SkiffCode:V Skiff:V
```
WebView는 debug 빌드에서 `chrome://inspect`로 디버깅한다.

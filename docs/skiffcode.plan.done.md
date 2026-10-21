# Skiff Code 끝난 할 일

[`skiffcode.plan.md`](skiffcode.plan.md)에서 끝난 마일스톤을 옮겨 둔 기록이다. 세션을 시작할 때 읽지 않는다.
무엇을 왜 그렇게 했는지, 무엇을 어떻게 확인했는지 찾을 때 본다. 마일스톤마다의 **성공 조건**은 2026-10-12에 그 마일스톤의 확인 기록에서 거꾸로 적어 넣었다(그 전에는 성공 조건을 따로 두지 않았다). 설계는 [`skiffcode.spec.md`](skiffcode.spec.md)의 목차에서 찾는다.

### 0. 문서화

**성공 조건**

- ✅ 새 세션이 `AGENTS.md`에서 계획과 설계를 찾아 첫 `- [ ]`부터 이어 갈 수 있다 — 확인: M0부터 세션마다 그렇게 이어 왔다.
- ✅ 원래 요구사항과 손그림이 레포에 있다 — 확인: `docs/my.skiffcode.spec.md`, `docs/menu.layout.jpg`.

- [x] 이 계획을 `plan.md`(지금의 `docs/skiffcode.spec.md`와 이 문서)에 체크리스트로 적고 `AGENTS.md`에서 가리키게 하기
- [x] `skiff.code.plan.md`(지금의 `docs/my.skiffcode.spec.md`), `menu.layout.jpg`를 레포에 커밋하기 (이 문서가 두 파일을 참조한다)
- [x] 인수인계 문서 `HANDOFF.md` 작성

### M0. 스파이크: 전부 WebView에 붙는지 먼저 확인
버리는 코드로 확인만 한다. 결론과 확정된 라이브러리 버전은 `AGENTS.md`에 남긴다.

**성공 조건**

- ✅ WebView 하나에서 CM6가 2MB 파일을 부드럽게 다룬다(스크롤 86–91fps, 핀치 한 단계 3ms+8ms) — 확인: 탭 측정, 사용자가 손으로 보고 좋다고 함.
- ✅ 줄 단위 unified diff가 2MB에서 1초 안(약 240ms) — 확인: 탭 측정.
- ✅ `@codemirror/lsp-client`가 Kotlin 브리지 위에서 동작하고 위치가 맞는다(진단 252ms, 한글 줄 범위 정확) — 확인: 탭의 스텁 서버, 실제 pyright.
- ✅ ktoml이 이 툴체인에서 컴파일되고 APK에 들어간다 — 확인: `KtomlSpikeTest`.
- ✅ 결론과 버전이 `AGENTS.md`에 있다.

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

**성공 조건**

- ✅ `:core`·`:app` 테스트와 `:app` 린트가 통과한다 — 확인: 52개가 28+24로 갈려 모두 통과.
- ✅ 실기기에서 Skiff의 SFTP 탐색, 분할 화면, 양방향 전송이 이전과 같다 — 확인: 실제 SSH 서버, 전송한 파일의 MD5가 원본과 같음.

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

**성공 조건**

- ✅ adb 링크, Skiff에서 파일 탭, 파일 매니저의 "다른 앱으로 열기" 세 길로 파일이 viewer에 열린다 — 확인: 탭.
- ✅ 링크 파싱·프로필 찾기·`TextLoader`·브리지가 테스트로 덮인다 — 확인: `SkiffCodeUriTest`, `OpenRequestTest`, `TextLoaderTest`, `WebBridgeTest`.
- ✅ 보안 규칙(origin 하나, CSP, 링크 가로채기, raw HTML 끔)이 적용된다 — 확인: 코드 읽기, vitest `sanitize.test.ts`.

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

**성공 조건**

- ✅ editor로 바꿔 쳐도 보던 줄과 undo가 이어지고, 하드웨어 키보드 없이는 소프트 키보드가 저절로 뜨지 않는다 — 확인: 탭, 폴더블.
- ✅ 저장이 고친 줄만 바꾸고 충돌을 잡는다 — 확인: `DocumentSaverTest`(MINA), 기기 확인은 M4로 옮겨 마쳤다.
- ✅ 서버의 변경이 약 2초 안에 보이고 수정 중이면 배너가 뜬다 — 확인: `FileWatcherTest`, 탭 2.05초·폴더블 1초.
- ✅ 열린 파일 사이를 오가도 버퍼가 남는다(상한 30, 수정 중은 내리지 않음) — 확인: `OpenDocumentsTest`, vitest `memories.test.ts`.

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

**성공 조건**

- ✅ 팔레트의 세 모드와 최근 목록이 탭에서 동작한다 — 확인: vitest, 탭 녹화.
- ✅ 테마를 바꾸면 메뉴·사이드바·팔레트·레이어가 한꺼번에 바뀐다 — 확인: 탭과 폴드8(`MutationObserver`).
- ✅ 저장한 파일의 서버 `git diff`에 의도한 변경만 보이고 인코딩·줄바꿈이 그대로다 — 확인: 탭(UTF-8·CRLF·EUC-KR), 폴드8(사용자).
- ✅ `settings.toml`의 값이 저장하는 순간 적용되고 잘못된 값은 배너로 알린다 — 확인: `SettingsTomlTest`, 탭(`poll_seconds`, `kept_buffers`).
- ✅ https 링크가 Claude 앱에서 탭 한 번에 열린다 — 확인: 폴드8(사용자).

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
  - **폴드8에서 사용자가 손가락으로 확인했다**(2026-10-06): 모드 버튼을 밀어 모드가 바뀐다. 좁은 화면의 팔레트 표시도 2026-10-06 "의도한 대로"였다.
- [x] B에서 최근 실행한 다섯 개를 미리 보여주고, 팔레트 가로를 600px에서 끊는다 (2026-09-21 사용자 요청) + vitest
  - 기억하는 것은 **이름**이다. 항목은 매번 새로 만들어지고 사라질 수도 있어서, 보여줄 때 지금 그 모드가 내놓는 것들 중에서 다시 찾는다. 없으면 조용히 빠진다.
  - 실기기(탭) 확인, 가로: 페이지 1152px에서 팔레트가 **600px**이고 오른쪽 16px, 아래 16px에 붙어 있다. 결과 상자도 같은 600px이다.
  - 실기기(탭) 확인, 아이콘: 세 모드의 버튼을 화면으로 봤다 — `>_`, 돋보기, `@`가 모두 흰 선 하나로 24px에 그려져 있다(채움 없음).
  - 실기기(탭) 확인, 저장: `Toggle Sidebar`를 실행하니 `palette.recent`에 `{"command":["Toggle Sidebar"]}`가 적혔고, **앱을 force-stop 하고 다시 켜니** 팔레트가 그것을 선택된 채로 열었다.
  - 실기기(탭) 확인, 최근 목록: 처음 열면 목록이 아예 없고, `reset`으로 한 번 실행한 뒤 다시 열면 `*Reset Zoom`이 선택된 채로 떠서 **타이핑 없이 그 줄을 눌러 실행**됐다. 하나 더 실행하니 최신이 위로 왔고, 🔍로 스와이프하면 그 모드에는 최근이 없어 비었다가 `>`로 돌아오면 다시 나왔다.
- [x] 커맨드 레지스트리(레이어 전환, 확대/축소, 저장, 사이드바, 설정 열기 등)와 파일 모드(열린 파일 + 같은 디렉토리), 단일 파일 심볼 모드(lezer)
  - **③은 LSP를 기본으로, lezer를 대체 수단으로 한다**(2026-09-27 사용자 결정). 무엇을 심볼로 보는지와 이유는 `skiffcode.spec.md`의 "심볼 검색"에 있다. M6의 "`@` 심볼 모드를 LSP로 확장"이 LSP 쪽이다.
  - **셋으로 나눠서 한다**(2026-09-21 사용자 결정): ① `>` 커맨드 레지스트리와 저장, ② 파일 모드(🔍), ③ 심볼 모드(`@`). 셋 다 했다(①은 아래, ②와 ③은 그 뒤에).
  - **`main` 병합은 ①②③과 그다음 확인 항목이 끝난 뒤에 묻는다**(2026-09-21 사용자 결정). **2026-09-27에 물어 병합했다** — `origin/main`을 fast-forward. 저장 커맨드가 붙기 전까지 main의 Skiff Code는 저장할 수 없는 에디터였다. 브랜치가 앞서 있고 main에만 있는 것이 없어 fast-forward다. worktree에서 `git checkout main`은 하지 않는다.
  - **①에 등록한 커맨드**(`commands.ts`, 이름은 전부 영문): `Save File`, `Toggle Layer`, `Show Editor`, `Show Viewer`, `Undo`, `Redo`, `Zoom In`, `Zoom Out`, `Reset Zoom`, `Toggle Sidebar`, `Reload File`, `Close File`. 사용자가 레이어 직접 전환·Undo/Redo·Reload File을 더했다(2026-09-21).
    - **`Show Diff`는 넣지 않았다**(2026-09-21 사용자 결정). 비교 대상이 M5에 생기므로 지금 들어가면 읽기 전용 버퍼만 보인다. M5에서 더한다.
    - **`Open Settings`도 미뤘다**(2026-09-21 사용자 결정). 열 파일이 없다 — `settings.toml` 항목에서 파일 자리가 정해질 때 같이 넣는다. **그 항목에서 넣었다**(2026-09-27).
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
  - **② 🔍 파일 모드를 했다(2026-09-26).** 목록은 **다른 열린 파일이 먼저, 그다음 같은 디렉토리의 파일**(이름순)이다. 화면에 떠 있는 파일, 디렉토리, 이미 열린 파일은 디렉토리 쪽에서 뺀다 — 열린 파일인지는 키(`keyOf`)를 아는 Kotlin만 알 수 있어서 Kotlin이 뺀다. 숨김 파일은 넣는다. `content://`는 디렉토리가 없어 열린 파일만 나온다.
    - 브리지: `folder {id}` → `{names}`, `openFromFolder {id, name}`. 디렉토리는 **그 파일을 읽은 파일시스템으로** 읽는다(`Folder`, 원격이면 같은 세션). `RemoteSessions.get`을 다시 부르면 비밀번호가 바뀐 프로필이 다른 파일의 세션을 닫을 수 있다.
    - **페이지는 이름만 보내고, `Folder.request`가 `/`·`.`·`..`·NUL이 든 이름을 거부한다.** 그래서 이 길로 열리는 파일은 사용자가 이미 연 파일과 같은 디렉토리에 있다. **경로 확인 창은 띄우지 않는다** — 사용자가 앱 안에서 고른 것이지 밖에서 온 링크가 아니다(`OpenFlow.open`의 인자를 `fromSkiff`에서 `pathChosen`으로 바꿨다).
    - 여는 길은 링크와 같다(`MainActivity.startOpening`): 로딩 라인, 이미 열린 파일이면 전환, 최근 파일 기록. 최근 파일에는 `linkOf`가 만든 링크가 남는다(원격은 프로필 이름을 `alias`로).
    - **디렉토리는 팔레트가 열릴 때마다 한 번 읽는다**(`files.ts`의 `createFolder`). 팔레트는 글자마다 항목을 다시 묻는데 원격 목록은 왕복이라, 한 번 열린 동안은 들고 있는다. 이름이 도착하면 `Palette.refresh()`로 같은 질의를 다시 찾는다. 읽기가 실패하면 빈 목록으로 두고 글자마다 다시 묻지 않는다. 열린 파일이 바뀌면(`reconcile`) 잊는다.
    - 최근 목록은 모드마다 이름으로 기억하므로, **이름이 같은 파일 둘**(다른 디렉토리의 `main.ts` 둘)은 팔레트에서 구별되지 않는다. 목록에 경로 줄을 더하는 것은 하지 않았다.
    - vitest 7개(`files.test.ts` 6, `palette.test.ts`의 `refresh` 1), `FolderTest` 5개.
    - 실기기(탭) 확인, CDP 터치와 DevTools로: 로컬 `pal/a.md`를 열고 🔍로 스와이프해 `.`을 치니 `b.py`, `c.txt`가 떴다(자기 자신과 `sub/`는 없다). `b.py`를 터치하니 **확인 창 없이** 열려 열린 파일이 둘이 됐다. `b.py`에서 다시 여니 `a.md`(열린 파일)가 한 번만, 그다음 `c.txt`가 나왔고 Enter로 **다시 읽지 않고 전환**됐다. 기기에서 `d.txt`를 만든 뒤 팔레트를 다시 여니 `d`에 `d.txt`가 떴다. 원격(이 맥)의 `x.py`를 링크로 열고 같은 방법으로 `한글 이름.txt`를 열었다 — 본문이 `한글`로 읽혔고, 최근 파일에 퍼센트 인코딩된 링크가 `alias`와 함께 남았다.
    - **2026-09-26 사용자가 폴드8에서 손으로 확인했다** — 설치한 APK로 써 보고 괜찮다고 했다.
  - **③ `@` 심볼 모드를 lezer로 했다(2026-09-27).** 무엇을 심볼로 보는지는 `skiffcode.spec.md`의 "심볼 검색"이다. `symbols.ts`의 `outlineOf(파일 이름, 본문)`이 LSP `DocumentSymbol` 모양(`name`, `kind`, `line`, `children`)으로 돌려주고, `symbols()`가 그것을 팔레트 항목으로 편다(`Class.method`). 고르면 `Pane.goToLine`으로 간다 — 링크의 `?line=`과 같은 길이라 마크다운 viewer(렌더된 DOM)에서도 돈다.
    - **트리는 버퍼에서 따로 파싱한다.** 마크다운 viewer에는 CodeMirror 뷰가 없어서 뷰의 트리를 쓸 수 없다. 언어 청크는 pane이 불러오는 것과 같은 것이고, 전체를 한 번에 파싱한다.
    - **개요는 팔레트가 열릴 때마다 한 번 읽는다.** `createFolder`에 타입 인자를 달아 디렉토리 목록과 같은 캐시를 쓴다. 팔레트가 열린 동안 파일이 바뀌면 이전 파일의 항목이 남을 수 있는데, 고른 순간 화면의 pane이 개요를 만든 pane이 아니면 아무 일도 하지 않는다.
    - vitest 7개(`symbols.test.ts`: TS, JS, Python, Markdown 둘, 규칙 없는 언어, 팔레트 항목).
    - 실기기(탭) 확인, CDP 터치와 DevTools로: `sym.py`에서 `.`에 `Store.get`, `Store.size`, `Store.Meta`, `Store.Meta.order`가, `meta`에 `Store.Meta`와 `Store.Meta.order`가 떴다(함수 안의 `inner`는 없다). 400줄짜리 `long.py`에서 `Point`를 고르니 306행이, `Store.get`은 155행이 화면 맨 위에 왔다. 렌더된 `long.md`에서 `Middle`(`##`)은 303행 블록, `End`(밑줄 제목)는 605행 블록으로 갔다.
    - **2026-09-27 사용자가 폴드8에서 손으로 확인했다** — 무선으로 설치한 APK로 써 보고 좋다고 했다.
- [x] **확인(M3에서 옮겨 왔다):** 서버에서 `echo >> file` 하면 2초 안에 반영되고, 수정 중에는 배너가 뜬다. 저장 후 서버의 `git diff`에 의도한 변경만 보인다
  - **앞 절반은 2026-09-21 세션에 탭에서 이미 봤다** — 원격은 2.05초(폴더블은 1초), 타이핑한 뒤에는 배너가 뜨고 "내 것 유지"와 "다시 불러오기"가 각각 버퍼와 파일을 택했다. 다시 볼 것은 저장 쪽이다.
  - 저장을 거는 커맨드가 바로 위 항목이라 여기에 있다. `DocumentSaver`는 M3에서 다 만들었고 MINA 유닛 테스트가 덮고 있었다.
  - **2026-09-27 탭에서 원격 저장을 확인했다.** 이 맥에 임시 git 레포를 만들고 파일 셋을 커밋한 뒤, 저장된 이 맥 프로필로 SFTP 링크를 열어 CDP로 편집 레이어에서 친 뒤 `Save File`을 실행했다.
    - UTF-8 `save.py`: 2행 끝에 ` + "!"`를 붙이고 저장하니 "저장했습니다."가 뜨고, 서버의 `git diff`는 **그 한 줄만** 바뀌었다(한글 주석, 인코딩, 모드 그대로). 4초 뒤에도 감시가 우리 저장을 남의 변경으로 되돌려 보내지 않았다(배너 없음).
    - CRLF `crlf.txt`: 2행을 고치고 저장하니 그 줄만 바뀌고 **모든 줄이 `\r\n` 그대로**였다.
    - EUC-KR `euckr.txt`: 한글로 읽혔고, 3행에 `😀`를 넣고 저장하니 "이 파일은 EUC-KR이라 3번째 줄의 "😀" 을(를) 쓸 수 없어 저장하지 않았습니다."가 뜨고 서버 파일은 그대로였다. 이모지를 지우고 ` 추가`를 넣어 저장하니 3행만 바뀌고 파일은 여전히 유효한 EUC-KR이었다.
    - `content://`(MediaStore의 `old.txt`)를 편집 레이어에서 친 뒤 저장하니 "다른 앱이 건네준 문서라 저장할 수 없습니다."가 뜨고 기기 파일의 mtime이 그대로였다.
    - **2026-09-27 사용자가 폴드8에서 손으로 확인했다** — SFTP 링크로 이 맥의 `fold.py`를 열어 마지막 줄 끝에 `!!`를 치고 저장하니, 서버의 `git diff`에 그 한 줄만 바뀌었다(UTF-8, 줄바꿈, 마지막 줄바꿈 그대로).
- [x] `settings.toml` 로드와 적용 + `SettingsTomlTest`(왕복, 없는 키는 기본값, 잘못된 값)
  - **2026-09-27 사용자 결정 넷:** 파일은 앱 내부(`filesDir/settings.toml`)에 두고 팔레트의 `Open Settings`로 앱 안에서 연다. 키는 **지금 읽는 곳이 있는 것만** 넣었다(테마는 다음 항목, diff 투명도는 M5, LSP 명령은 M6에서 그 기능과 함께). 줄바꿈 기본값은 **켬**. 시스템 글꼴 배율은 **따른다**(지금처럼 둔다). 자세한 것은 `skiffcode.spec.md`의 "설정과 테마".
  - 키: `[editor]` `font`, `font_size`, `tab_size`, `wrap` / `[files]` `max_size_mb`, `kept_buffers`, `poll_seconds`. 파서는 `settings/Settings.kt`의 `SettingsToml.read`, 파일은 `SettingsFile`(프로세스 범위, `SkiffCodeContainer.settings`).
  - **저장하는 것이 적용이다.** 설정 파일의 `save`가 통과하면 Kotlin이 다시 읽고 `settingsChanged`를 보낸다. 쓸 수 없던 값은 저장 답의 `warning`에 실려 **닫기가 붙은 배너**로 뜬다(따로 알림을 보내면 저장의 2.5초 배너가 덮는다). 앱을 켤 때 파일이 깨져 있으면 조용히 기본값이다 — 그 파일을 만든 저장이 이미 말했다.
  - `Open Settings`는 경로 확인 창도, 모든 파일 권한도, 최근 파일 기록도 없다(`OpenFlow.openSettings`). **폴더도 없다** — 옆에 서버 프로필이 든 store가 있어서 팔레트의 파일 모드가 그것을 내놓으면 안 된다.
  - 적용되는 곳: 글꼴은 `--code-font`, 탭 크기와 줄바꿈은 `settings.ts`의 compartment(배경에서 돌아온 버퍼도 다시 맞춘다), 렌더된 마크다운의 코드 블록은 `--tab-size`와 `.wrap`. `font_size`는 Reset Zoom이 가는 곳이고, **바뀌었을 때와 핀치한 적이 없는 첫 실행에만** 지금 크기도 옮긴다. 크기 상한은 여는 때마다 `TextLoader`에, 폴링 주기는 `FileWatcher`가 기다릴 때마다 묻는다(이미 열린 파일에도 먹는다).
  - 잘못된 값: 범위 밖이나 빈 글꼴은 **그 키만** 기본값이고, 타입이 틀리거나 TOML이 깨졌거나 모르는 키가 있으면 **전부** 기본값이다. 모르는 키를 건너뛰지 않는 이유는 오타가 가장 흔할 것이라서다 — 건너뛰면 설정이 안 먹는 것처럼만 보인다. ktoml의 `UnknownNameException`은 internal이라 메시지에서 키를 뽑는다.
  - `SettingsTomlTest` 11개. "왕복"은 **새로 만드는 파일(`TEMPLATE`)이 기본값 그대로 읽히는 것**으로 했다 — 앱은 설정을 인코딩하지 않고, 손으로 쓴 주석 달린 파일이 기본값과 어긋나는 것이 실제로 생길 수 있는 틀림이다. `FileWatcherTest`는 폴링 주기가 함수가 된 만큼만 고쳤고, vitest는 커맨드 목록과 `storedFontSize`(저장된 것이 없으면 null)를 고쳤다.
  - 실기기(탭) 확인, DevTools로: `Open Settings`가 주석 달린 파일을 열었다. 편집 레이어에서 `font_size` 20, `tab_size` 2, `wrap = false`로 저장하니 "저장했습니다."와 함께 **글자가 20px, 탭 2, 줄바꿈 해제가 한 번에** 먹었다(CodeMirror와 CSS 변수 둘 다). `font_size = 99`는 "editor.font_size = 99 은(는) 8~40 밖이라 기본값 14 을(를) 씁니다."가 떠 있고 14px로 돌아왔으며 탭과 줄바꿈은 그대로였다. `font_szie`는 "settings.toml에 editor.font_szie 라는 키는 없어서 모든 설정을 기본값으로 씁니다."였다. `max_size_mb = 1`로 저장한 뒤 1.6MB 파일을 링크로 여니 "1 MB보다 큰 파일은 열지 않습니다."였다. 끝나고 탭의 `settings.toml`과 시험 파일은 지웠다.
  - **기기에서 보지 못한 것:** `poll_seconds`와 `kept_buffers`는 값을 넘기는 자리만 바뀌었고 기기에서 따로 재지 않았다. 폴드8에서 커버 화면 줄바꿈도 아직 안 봤다.
- [x] `themes/*.toml` → CSS 변수 매핑(메뉴와 레이어 전체), 다크/라이트 번들, 폴백 + vitest
  - **2026-09-28 사용자 결정 셋:** 기본값은 `theme = "system"`(기기 다크 모드를 따름), 바꾸는 방법은 `settings.toml`의 `[editor] theme`과 팔레트의 `Theme: System` / `Theme: Light` / `Theme: Dark`, 이번 항목은 번들 `light`/`dark`만(사용자 테마는 다음 항목의 import에서).
  - 번들은 `assets/themes/{light,dark}.toml`, 표는 `[ui]` `[editor]` `[syntax]` `[diff]`와 최상위 `base`. **읽고 검사하는 것은 Kotlin이다**(`settings/Theme.kt`) — 문제를 알리는 말이 Kotlin 문자열 리소스에 있고 `settings.toml`과 같은 규칙(모르는 키면 통째로 base, 색이 아닌 값은 그 키만 base)을 쓰기 때문이다. 색은 `#rgb`/`#rgba`/`#rrggbb`/`#rrggbbaa`만 받는다. 페이지는 받은 표를 `--섹션-키` 변수로 쓰기만 한다(`theme.ts`). 계획에서는 검사를 TS에 두려 했으나 옮겼다.
  - 키 목록은 `ThemeToml.KEYS` 하나다. `theme.test.ts`가 `index.html`의 `:root`(Kotlin이 답하기 전의 모습)와 `light.toml`이 색마다 같은지, 페이지와 코드가 쓰는 `var(--…)`가 테마 키와 정확히 같은지 본다. `ThemeTomlTest` 9개, `SettingsTomlTest`에 `theme` 셋, vitest 4개.
  - 라이트는 이전 화면과 같은 값이다: 메뉴 색은 `index.html`에 박혀 있던 것, 코드 뷰는 CodeMirror의 `defaultHighlightStyle`과 base theme의 라이트 값을 태그마다 변수로 옮겼다. **다른 점 하나:** 텍스트 선택 색이 브라우저 기본에서 `editor.selection`(`#d7d4f0`, CodeMirror의 라이트 선택색)이 됐다.
  - **다크 모드를 따르는 방법:** WebView의 `prefers-color-scheme`에 기대지 않고 Kotlin이 `uiMode`의 night 비트로 번들을 골라 보낸다. `uiMode`를 `configChanges`에 넣었다 — 없으면 다크 모드를 바꿀 때 액티비티가 다시 만들어져 편집하던 사람이 viewer 맨 위로 돌아간다. 상태바 아이콘과 상태바 아래 프레임 색도 테마를 따른다.
  - 팔레트의 Theme 명령은 `settings.toml`의 `theme =` 줄만 고쳐 쓴다(없으면 `[editor]` 맨 위에, 파일이 없으면 템플릿으로 만든다). 그 파일이 앱에 열려 있으면 감시가 바뀐 것을 가져오고, 친 내용이 있으면 묻는다.
  - 실기기(탭) 확인, DevTools로: 다크 모드에서 `system` 기본으로 열면 상단 메뉴 `rgb(21,27,35)`, 편집기 `rgb(13,17,23)`, 상태바도 어둡고 아이콘이 밝다. 팔레트에서 `theme light`를 실행하니 한 번에 흰 화면이 되고 상태바 아이콘이 어두워졌으며 파일에 `theme = "light"`가 적혔다. `Theme: System`으로 돌리고 편집 레이어에서 `cmd uimode night no`를 하니 **같은 pid, 같은 페이지(심어 둔 전역 값이 남음), 편집 레이어 그대로** 라이트가 됐다. 다크에서 팔레트 결과 목록과 렌더된 마크다운도 어둡게 나왔다.
  - **기기에서 보지 못한 것:** 사이드바와 열린 파일 메뉴, 배너를 다크에서 눈으로 보지 않았다(같은 변수를 쓴다). 탭 아래 내비게이션 줄은 라이트에서도 짙은 회색으로 보였다 — 이번 변경 전에도 그랬는지는 확인하지 않았다. 다크에서 앱을 켤 때 페이지가 답을 받기 전 잠깐 라이트(`:root`)로 그려질 수 있다.
- [x] 설정과 테마 import/export(SAF)
  - **2026-09-28 사용자 결정 셋:** 가져온 테마의 이름은 **파일 이름**이다(`ocean.toml` → `Theme: ocean`, `theme = "ocean"`, 같은 이름이면 덮어쓸지 묻는다). 내보내기는 **설정과 테마를 따로**(`Export Settings`, `Export Theme` = 지금 화면의 테마, 번들 포함). 잘못된 파일은 **저장과 같은 규칙** — 들이고 버린 것은 닫기 붙은 배너로.
  - 팔레트 명령 넷: `Import Settings`, `Export Settings`, `Import Theme`, `Export Theme`. `Theme: …` 명령은 Kotlin이 `settings` 답에 싣는 `themes`(system, 번들, 가져온 것 순)로 만든다.
  - Kotlin: `Themes`가 `filesDir/themes/<이름>.toml`을 함께 본다(이름 규칙은 `Themes.nameOf` — 모든 문자의 글자와 숫자, 공백, `_`, `-`, 괄호, 64자, system/light/dark는 대소문자 없이 거부). 가져온 테마는 **쓴 그대로** 두고 읽을 때 같은 base의 번들로 채운다. `SettingsToml.read`는 알려진 테마 이름을 인자로 받는다. SAF는 `MainActivity`의 `startActivityForResult`/`onActivityResult`이고 **요청 코드만으로** 처리한다. 읽는 파일은 256KB 상한, 결과는 새 알림 `notice`(`lasting`이면 남고 아니면 2.5초)로 배너에 뜬다.
  - `ThemesTest` 8개, `SettingsTomlTest` 1개 추가(JVM 157), vitest `commands` 1개 추가(88).
  - 실기기(탭, 다크 모드) 확인, DevTools와 `uiautomator`로: `Export Theme`에 picker 제목이 `dark.toml`로 채워지고, 저장한 파일이 번들 `dark.toml`과 md5가 같았다. 배경색을 바꾼 `ocean.toml`을 들이니 상단 메뉴·본문·상태바까지 한 번에 바뀌고 팔레트에 `Theme: ocean`, `settings.toml`에 `theme = "ocean"`이 적혔다. 같은 이름을 다시 들이면 "테마를 바꿀까요?"가 뜨고 취소하면 그대로, 바꾸기면 모르는 키(`ui.backgrund`)를 배너로 알리고 dark base 색이 됐다. `Dark.toml`은 이름 규칙 배너로 거부됐다. `Export Settings`는 앱의 파일과 바이트가 같았고, `tab_size = 2`, `font_size = 99`로 고친 파일을 `Import Settings`하니 탭 2가 먹고 99는 배너로 알린 뒤 14였다. 앱을 다시 켜도 `ocean`이 유지됐다. 끝나고 탭은 되돌렸다.
  - **기기에서 보지 못한 것:** 라이트 모드에서의 흐름, 폴드8. picker에서 `settings.toml`을 테마로 고르면 "settings"라는 테마로 들어온다(읽지 못한다는 배너와 light base) — 규칙대로지만 되돌릴 방법(테마 삭제)이 아직 없다 — 바로 아래 항목이다.
- [x] `.jsonl`을 JSON 문법으로 연다 (2026-09-23 사용자 요청) + vitest
  - `FileKind`는 건드리지 않기로 했다(2026-09-23 사용자 결정). Skiff 목록에서 `.jsonl`을 탭하면 여전히 밖으로 나가고, 강조는 링크로 연 Skiff Code 안에서만이다.
- [x] 링크를 여는 동안의 로딩 표시: 위 라인과 문서 모양 (2026-09-24 사용자 요청) + vitest 5개
  - 로컬 파일은 200ms 안에 끝나 설계대로 아무것도 뜨지 않는다 — 보려면 SFTP 파일을 여는 링크가 필요하다.
  - **2026-09-27 사용자가 SFTP 링크로 확인했다** — 라인이 200ms 뒤에 뜨고, 스켈레톤이 함께 떴다가 문서가 그려지며 걷힌다. `prefers-reduced-motion` 쪽은 아직 보지 않았다.
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
- [x] 테마 복사·편집·삭제와 기본 테마 보호 (2026-09-29 사용자 요청) + JVM 테스트 6개, vitest 1개
  - **정한 것 셋(2026-09-29 사용자 결정, 모두 제안대로):** 명령은 **지금 화면의 테마**를 대상으로 한다(`Export Theme`과 같다). 복사본 이름은 **네이티브 입력 창**에서 받고 `<지금 테마> copy`로 채워 두며 `Themes.nameOf`로 검사한다 — 안 되는 이름이면 까닭을 붙여 창을 다시 띄운다. 편집 중 오타 키는 **지금 규칙 그대로**(모르는 키 하나면 테마 전체가 base, 배너가 키를 말한다).
  - 설계는 `skiffcode.spec.md` "설정과 테마"에 적었다. `Themes`에 `file`·`ownAt`·`reread`·`delete`가 생겼고, `OpenFlow.openSettings`는 테마도 여는 `openOwn`이 됐다. `settings` 답에 `ownTheme`(지금 테마가 사람 것인지)를 실어 페이지가 `Delete Theme`을 넣고 뺀다.
  - **고친 것:** `Themes.isImported("system")`이 참이었다 — `system`은 `names`에 있고 번들 맵에는 없어서다. 가져오기는 `nameOf`가 그 이름을 막아 드러나지 않았는데, 삭제 테스트가 잡았다.
  - **2026-09-29 탭에서 DevTools와 `uiautomator`로 확인했다(다크 모드, `system`):** 번들일 때 팔레트에 `Copy Theme`·`Edit Theme`만 있고 `Delete Theme`은 없다. 번들에서 Edit는 "dark 은(는) 앱에 든 기본 테마라 고칠 수 없습니다. 복사본을 만들어 고칠까요?"를 묻고, 복사를 누르면 이름 창이 `dark copy`로 뜬다. `dark`를 넣으면 거절 까닭이 창 위에 붙어 다시 뜨고, `dark copy`는 `files/themes/dark copy.toml`로 만들어져 `theme = "dark copy"`가 되고 편집기로 열렸다. `[ui] background`를 `#335577`로 고쳐 Save File 하니 `--ui-background`가 바로 `#335577`이 됐고, `backgrund`로 오타를 내 저장하니 "테마에 ui.backgrund 라는 색은 없어서 …" 배너와 함께 dark의 `#151b23`으로 돌아갔다. 친 채로 Delete Theme → 삭제하니 파일이 닫히고 폴더가 비고 `theme = "system"`, 팔레트에서 Delete가 빠졌다. 내 테마에서 Edit는 묻지 않고 바로 열고, 있는 이름으로 복사하면 "dark copy 테마가 이미 있습니다. dark copy copy 의 복사본으로 바꿀까요?"를 묻는다.
  - 폴드8과 라이트 모드에서는 보지 않았다.
- [x] 색 문자열 옆에 그 색의 작은 네모 (2026-09-29 사용자 요청) + vitest 5개
  - **정한 것(2026-09-29 사용자 결정):** `.toml`·`.css`·`.json` 파일에서만, **보기만** 한다(누르면 색 선택기가 뜨는 것은 없다). CodeMirror에 그런 기능은 없고, `@replit/codemirror-css-color-picker`는 CSS 문법 트리(`ColorLiteral`)를 읽어서 TOML 문자열 안의 색을 잡지 못한다.
  - `web/src/swatches.ts`: `MatchDecorator`로 `#rgb`·`#rgba`·`#rrggbb`·`#rrggbbaa` 앞에 위젯을 넣는다. 레이어 compartment 밖에 두어 세 레이어에 다 보인다.
  - 알려진 것: CSS의 id 선택자가 16진 글자로만 되어 있으면(`#fade`, `#add`) 색으로 잡힌다.
  - **2026-09-29 탭에서 DevTools로 확인했다:** `swatch.css`에서 `#0b3d5c`, `#fff`, `#e5484d80`(반투명) 앞에 11px 네모가 그 색으로 붙었고, 위의 id 선택자 `#fade`에도 붙었다. `swatch.json`은 `#0b3d5c`·`#abcd` 둘(5자리 `#abcde`는 없음), `swatch.toml`은 `[ui]` 네 줄(`#abcde`와 `x#abc`는 없음), `swatch.py`는 0개였다. 탭의 `screencap`은 이때 WebView를 비워 찍었고 CDP `Page.captureScreenshot`은 답이 없어 모양은 눈으로 보지 못했다.
- [x] https 링크로 Skiff Code 열기 — Claude 앱에서 탭 한 번에 (2026-09-29 사용자 요청)
  - **왜:** 폴드8의 Claude 앱은 `skiffcode://` 링크를 "지원되지 않는 링크"라며 넘기지 않는다. 대신 https 링크는 시스템에 넘긴다 — YouTube 링크를 누르니 YouTube 앱이 열렸다(2026-09-29 사용자 확인). 그러니 Skiff Code가 https 링크 하나를 자기 것으로 받으면 된다.
  - **모양(제안):** `https://<호스트>/skiffcode/open#<skiffcode:// 뒤의 나머지>`. 예: `…/open#alice@host/srv/demo/a.md?line=3`, 로컬은 `…/open#/storage/emulated/0/a.md`. 받은 쪽은 `#` 뒤를 `skiffcode://`에 붙여 **지금의 `OpenRequest.of` 그대로** 태운다 — 파서도 경로 확인 창도 하나다.
    - 경로를 `#` 뒤에 두는 까닭: 앱이 없거나 링크가 브라우저로 새면, `#` 앞까지만 웹 서버로 간다. 서버 이름과 경로가 블로그의 접속 기록에 남지 않는다.
  - **보안:** https 링크는 `skiffcode://`와 똑같이 **밖에서 온 링크**다. 누가 보냈든 Skiff가 아니면 `confirmPath`가 묻는다. `AGENTS.md`의 "Links that arrive from outside"에 입구가 둘이 됐다고 적는다. 받는 경로는 `pathPrefix`로 `/skiffcode/open` 하나로 좁혀, 블로그의 다른 주소는 계속 브라우저로 간다.
  - **정할 것 셋 — 시작 전에 사용자에게 묻는다.** 괄호 안은 제안이다.
    1. 호스트 (**`bynaki.github.io`** — 2026-09-29 사용자 요청으로 사용자 지정 도메인 `paran.blue`를 떼어 되돌렸다. `paran.blue`는 2026-05에 누군가 다시 등록한 도메인이라 쓸 수 없다. 안드로이드의 확인은 넘김을 따라가지 않으니 넘김 없는 주소여야 한다)
    2. 소유 증명 (**`assetlinks.json`을 올린다.** 안 올리면 기기마다 설정 → 앱 → Skiff Code → 기본으로 열기 → 링크 추가에서 직접 켜야 하고, 다른 사람에게 배포할 때도 그렇다)
       - 올린다면: `https://bynaki.github.io/.well-known/assetlinks.json`. 블로그 원본은 `bynaki/paran.blue`(Hugo)이고 빌드 결과가 `public/` 서브모듈로 `bynaki.github.io`에 올라간다. Pages 저장소에 `.nojekyll`이 없어 Jekyll이 점으로 시작하는 폴더를 빼므로, `.nojekyll`이나 `_config.yml`의 `include`가 필요하다. 저장소가 빌드 결과물로 보여(`index.xml`, `posts`) **블로그를 다시 배포할 때 이 파일이 지워지지 않게** 원본 쪽에 넣어야 할 수 있다. 서명 지문은 지금의 디버그 키, 배포할 때의 릴리스 키를 모두 싣는다.
    3. 앱이 없을 때 볼 페이지 (**둔다:** `/skiffcode/open/` 한 장이 `#` 뒤를 읽어 `intent://` 링크와 "Skiff Code가 필요합니다"를 보여준다. 확인이 안 된 기기에서도 한 번 더 누르면 열린다)
  - **바꿀 곳:** 매니페스트에 `https` + 호스트 + `pathPrefix` + `autoVerify="true"` 필터 하나. `OpenRequest.of`(또는 `SkiffCodeUri`) 앞에 https → `skiffcode://` 변환 하나. Pages 저장소에 `assetlinks.json`과 대비 페이지(정할 것 2·3에 따라). 블로그 저장소에 올리는 것은 밖으로 공개되는 일이라 **올리기 전에 사용자에게 묻는다.**
  - 테스트: 변환 — 호스트·경로가 맞는 것만 받고(`/skiffcode/openx`, 다른 호스트, `#`이 없는 것은 거절), `#` 뒤의 `?line=`과 퍼센트 인코딩·한글 경로가 그대로 가는지. 변환된 링크가 `confirmPath`를 거치는지는 `OpenFlow`가 종류마다 답하므로 새 종류를 만들지 않는 것으로 지킨다.
  - **확인:** 탭에서 `adb shell pm get-app-links com.naki.skiff.code`로 확인 상태(`verified`)를 보고, `am start -a android.intent.action.VIEW -d 'https://…'`로 열어 경로 확인 창이 뜨는지 본다. 폴드8의 Claude 앱에서 링크를 눌러 Skiff Code가 바로 열리는지 사용자가 본다. 블로그의 다른 주소는 여전히 브라우저로 열리는지도 본다.
  - **정할 것 셋은 제안대로**(2026-09-29 사용자 결정): 호스트 `bynaki.github.io`, `assetlinks.json`을 올린다, 대비 페이지를 둔다.
  - **앱 쪽 끝(2026-09-29).** 매니페스트는 `pathPrefix` 대신 `path="/skiffcode/open"`으로 좁혔다. 변환(`SkiffCodeUri.fromWebLink`)은 `MainActivity.handleLink`에서 한다 — `OpenRequest.of` 안에서 하면 최근 파일에 https 형태가 남는다. 테스트 둘(`OpenRequestTest`)을 더했다. 탭에서 사용자 선택으로 링크를 켜 두고 `am start`로 열어 경로 확인 창이 뜨고 파일이 열리는 것, 최근 파일에 `skiffcode:///…`로 남는 것, 블로그 첫 화면과 `/skiffcode/openx`는 Chrome만 받는 것을 봤다. 시험 뒤 사용자 선택은 다시 껐다.
  - **블로그에 올림(2026-09-29 사용자 승인).** `.nojekyll`, `.well-known/assetlinks.json`(디버그 키 지문), `skiffcode/open.html`을 Pages 저장소(`bynaki.github.io`)와 원본(`bynaki/paran.blue`의 `static/`)에 커밋했다. 셋 다 200으로 열리고 Google의 Digital Asset Links API도 문구를 읽는다. 탭에서 `pm verify-app-links --re-verify` 뒤 `verified`, 홈 화면에서 연 https 링크가 고르는 창 없이 Skiff Code의 경로 확인 창으로 바로 갔다.
  - **폴드8 확인(2026-09-30 사용자 확인):** Claude 앱에서 https 링크를 눌러 Skiff Code로 원격 파일이 열렸다.
  - **남은 것:** 원본을 Hugo로 다시 빌드했을 때 `static/`의 점 파일이 `public/`에 따라가는지는 이 Mac에 Hugo가 없어 보지 못했다 — 다음 배포 뒤 `assetlinks.json`이 200인지 본다.
- [x] 같은 기기를 호스트 키로 알아보기 — 주소가 달라도 같은 파일은 한 파일로 (2026-09-30 사용자 요청)
  - **왜:** 한 서버를 도메인과 테일스케일 IP 두 주소로 열면, 같은 경로라도 열린 파일 목록에 둘로 뜬다. `keyOf`가 `remote:<프로필 id>:<경로>`인데, 두 주소는 프로필 찾기에서 다른 프로필(Skiff에서 온 것, 알 수 없는 서버로 만든 `user@IP`)로 풀리기 때문이다.
  - **모양(사용자가 B안을 골랐다):** 원격 파일의 키를 `remote:<호스트 키 SHA256 지문>:<경로>`로 바꾼다. 지문은 파일을 열 때 이미 접속해 `HostKeyGate`가 확인한 것이라 새로 묻지 않는다 — 그 `host:port`의 known host에서 읽는다. 사용자 이름은 키에 넣지 않는다(같은 기기의 같은 경로면 같은 파일).
  - 버린 안(A): 링크에 `alias=<폰의 프로필 이름>`을 붙여 한 프로필로 모으기. 앱은 그대로지만 폰의 프로필 이름을 알아야 하고, alias 없는 링크와 이미 생긴 두 프로필은 여전히 갈린다.
  - **주의:** 같은 이미지로 복제한 VM처럼 서로 다른 기기가 같은 호스트 키를 쓰면 한 파일로 합쳐진다(드묾, 알려진 한계로 적는다). 지문을 못 읽는 경우(known host가 없는 경로가 있는지 확인)에는 지금의 프로필 id로 물러선다.
  - **같이 볼 것:** 최근 파일 목록, 페이지가 `localStorage`에 파일마다 두는 것(레이어, 줄, 줌)이 무엇을 키로 쓰는지 — 같은 기준으로 묶이는지 확인하고, 다르면 이 항목에서 맞출지 사용자에게 묻는다. 두 프로필로 이미 열린 파일에서 저장·감시가 어느 세션으로 가는지도 본다.
  - 테스트: `keyOf` — 다른 프로필·같은 지문·같은 경로는 같은 키, 다른 지문은 다른 키, 지문이 없으면 프로필 id. `OpenDocuments.byKey`로 두 번째 링크가 새 항목 대신 기존 파일을 켜는지.
  - **확인:** 폴드8에서 한 서버의 같은 파일을 두 주소의 링크로 차례로 열어, 열린 파일 메뉴에 하나만 있고 두 번째 링크가 그 파일로 옮겨 가는지 본다.
  - **"같이 볼 것"을 본 결과(2026-09-30):** 페이지의 `localStorage`에는 파일마다 두는 것이 없다(`palette.recent`, `zoom.size`뿐). 레이어·줄·버퍼는 페이지 메모리에서 Kotlin 항목의 `id`로 묶이므로 `keyOf`를 따라간다. 최근 파일은 링크 문자열이 키라 기준이 달랐고, 사용자가 그대로 두기로 했다(스펙 "열린 파일"). 합쳐진 파일의 저장·감시는 먼저 연 프로필의 세션으로 간다. 두 번째 링크가 만든 연결은 쓰이지 않고 앱이 끝날 때 닫힌다.
  - **코드 끝(2026-09-30).** `OpenRequest.Remote`에 `hostKey`를 두고, `OpenFlow.openRemote`가 `stat`이 게이트를 지난 뒤 `store.knownHost(host, port)`의 지문을 채운다. `keyOf`는 `remote:<지문 또는 프로필 id>:<경로>`. `Folder.request`가 `copy`로 만들어 옆 파일도 같은 지문을 잇는다. 테스트는 `OpenDocumentsTest` 셋, `FolderTest` 하나.
  - **탭에서 확인(2026-09-30).** 이 맥을 가리키는 두 프로필(LAN IP, 테일스케일 이름 — known host 지문이 같다)로 같은 파일의 링크를 차례로 열었다. 둘 다 경로 확인 창을 거쳐 `opened`까지 갔고, 열린 파일 메뉴에는 먼저 연 주소로 한 줄만 있었다. 최근 파일에는 두 링크가 따로 남았다(결정대로).
  - **폴드8 확인(2026-10-01 사용자 확인):** 같은 파일을 테일스케일 IP와 MagicDNS 이름의 https 링크로 차례로 열어, 열린 파일 메뉴에 한 줄만 있는 것을 사용자가 봤다.
- [x] **확인:** 실기기에서 팔레트 흐름을 녹화하고, 테마를 바꾸면 메뉴, 사이드바, 팔레트, 세 레이어가 한 번에 바뀐다
  - **세 레이어 중 diff는 아직 없다(M5).** 이번에는 viewer, editor, 렌더된 마크다운을 봤다.
  - **탭(2026-10-01, 다크 모드에서 시작):** `screenrecord`로 3분을 녹화하며 진짜 탭(`input tap`)과 팔레트로 `Theme: Light` → `Theme: System`을 돌렸고, 테마마다 viewer·editor·마크다운·열린 파일 메뉴·사이드바·팔레트·배너를 찍었다. 모두 그 테마의 색이었다. 바뀌는 순간은 `documentElement`의 `style`에 `MutationObserver`를 걸어 재 보니, 상단 메뉴·사이드바·열린 파일 메뉴·팔레트·결과 목록·배너·편집기·거터·본문이 **같은 마이크로태스크에서 한꺼번에** 새 색이었다(뷰는 레이어가 공유하는 하나뿐이다). 색 네모는 다크와 라이트 모두 제대로 그려졌다(반투명 `#e5484d80`, 흰 바탕의 `#fff`도 보인다).
  - **폴드8(2026-10-01):** 커버 화면(475px)에서 라이트 → 다크 → 시스템을 돌려 같은 방법으로 쟀고 여덟 곳이 한꺼번에 바뀌었다. 펼친 화면 라이트에서 메뉴·사이드바·열린 파일 메뉴·팔레트를 찍었다. **커버 화면 줄바꿈:** `wrap = true`에서 가로 스크롤 없이(`scrollWidth` = `clientWidth` = 475) 긴 줄의 높이가 펼친 화면의 두 배로 접혔다.
  - **고친 것 셋(2026-10-01):**
    - **라이트 테마에서 내비게이션 줄이 짙은 회색이었다.** 테마가 아니라 시스템이 그 줄에 까는 대비용 막(흰 바탕이 `#343435`, 약 80% 검정) 탓이었다 — 같은 때 설정 앱의 줄은 밝았다. `window.isNavigationBarContrastEnforced = false`로 끄니 탭과 폴드8 모두 줄이 `ui.background`가 되고 손잡이도 보인다.
    - **팔레트에서 Enter로 편집 레이어에 들어가는 명령을 실행하면 문서 맨 앞에 줄바꿈이 들어갔다**(`Show Editor`, `Toggle Layer`, 탭에서 6/6). 하드웨어 키보드가 있으면 명령이 편집기에 포커스를 주는데, 막지 않은 Enter의 기본 동작이 그 뒤 편집기에 떨어졌다. 팔레트가 Enter를 `preventDefault`한다 — 고친 뒤 4/4 그대로였다. adb의 `input keyevent`로 본 것이고 진짜 외장 키보드로는 누르지 않았다.
    - **팔레트 입력칸의 포커스 테두리가 브라우저 것이라 라이트에서 주황이었다.** 사용자 결정(2026-10-01)으로 `ui.accent`를 쓴다 — 다크에서도 흰색이던 것이 강조색이 됐다.
  - **`poll_seconds`(탭):** 로컬 파일은 커널 알림으로 약 50ms에 잡혀 이 값과 관계없다. 이 맥의 원격 파일로 재니 2초에서 0.5~1.9초, `7`로 저장하자 **이미 열린 파일의** 감지 간격이 7.1~7.2초가 됐다.
  - **`kept_buffers`(탭):** 파일 하나를 편집·저장하고 다른 두 파일의 편집 레이어를 거쳐 돌아와 Undo를 했다. `1`이면 되돌릴 것이 없었고, `30`이면 친 것이 되돌아갔다.
  - **라이트 모드의 테마 복사·편집·삭제와 import/export(탭, 폴드8 둘 다):** `Copy Theme`이 `light copy`로 만들어 열고, 배경을 `#fde8c8`로 저장하니 바로 먹었고, `Export Theme`의 파일은 앱의 것과 md5가 같았다. `Delete Theme`이 `system`으로 돌렸고, 내보낸 파일을 `Import Theme`하니 다시 `light copy`가 됐다. 끝나고 두 기기의 설정과 테마는 시험 전으로 되돌렸다(폴드8은 백업과 md5가 같다).
  - **아직 못 본 것:** 다크에서 앱을 켤 때 페이지가 답을 받기 전 잠깐 라이트(`:root`)로 그려지는지. 로딩 표시의 `prefers-reduced-motion` 쪽.

### M5. 프로젝트 모드 + git

**성공 조건**

- ✅ git 레포 안 파일을 열면 묻는 창이 뜨고, 프로젝트를 만든 뒤 거터와 diff가 서버의 `git diff -U0`와 같다 — 확인: 탭.
- ✅ internal-sftp·nologin 계정과 git 없는 서버에서 git 없이 열리고 이유가 배너로 뜬다 — 확인: Docker 계정 셋, 탭.
- ✅ 명령 인자가 모두 따옴표로 감싸이고 exec가 `RemoteExec` 밖에 없다 — 확인: `ShellQuoteTest`, `RemoteExecTest`, 코드 검색.
- ✅ 파일 모드가 프로젝트 전체를 찾는다(git이 없으면 SFTP walk) — 확인: `ProjectTreeTest`, 탭.

- [x] `GitScopeFinder` + `GitScopeFinderTest`(MINA: 홈 경계에서 멈춤, worktree의 `.git` 파일, 홈 밖 경로는 찾지 않음)
- [x] `ShellQuote` + `ShellQuoteTest`(`'; rm -rf ~'`, 줄바꿈, `$()`, 백틱, 작은따옴표가 든 경로)
  - 테스트는 실제 `/bin/sh`로 돌리므로 `rm -rf ~` 대신 같은 모양의 `touch`로 표시 파일이 생기지 않는지 본다. 따옴표를 큰따옴표로 바꾸면 실패하는 것도 확인했다.
- [x] `RemoteExec`(프로젝트 전용 SSHClient, exec 거부 감지). `AGENTS.md`의 exec 원칙 수정은 M0에서 이미 했다
- [x] `ProjectStore`와 파일 여는 흐름 2~4단계(프로젝트 활성화와 `command -v git`, 묻는 창)
  - 활성화가 exec로 git을 확인하므로 `RemoteExec` 뒤에 둔다(2026-10-01 사용자 동의로 순서를 바꿨다).
  - 탭에서 이 맥의 원격 로그인으로 확인했다: worktree(`.git`이 파일) 안 파일에서 묻는 창 → 프로젝트 만들기 → `git Available`, 앱을 다시 띄워 같은 레포의 다른 파일은 묻지 않고 활성화, 다른 레포에서 "파일만 열기"는 프로젝트를 남기지 않음.
- [x] 사이드바에 프로젝트 목록과 SFTP 지연 로딩 파일 트리
  - 정한 것(2026-10-02 사용자 결정): 프로젝트가 트리의 맨 위 줄, 화면의 파일을 따라 펼침, 줄을 길게 눌러 지우기(확인 창, 기록만 지운다), `.git`만 숨김. 자세한 것은 spec의 ① 사이드바.
  - Kotlin: `project/ProjectTree`(상대 경로 검사, 목록, 정렬), `ProjectStore.remove`, `ProjectSessions.close`, 열린 파일이 자기 프로젝트와 그 안의 경로를 든다(`OpenDocuments.Entry.project`). 브리지 `projects`·`projectDir`·`openFromProject`·`removeProject`, 알림 `projectsChanged`.
  - 페이지: 상태는 `tree.ts`(vitest 8), 그리는 것은 `chrome/sidebar.ts`.
  - 탭에서 이 맥의 원격 로그인으로 확인했다: 프로젝트 줄 → 루트 목록 약 1초(접속 포함), 폴더 약 0.1초, 파일을 탭하면 경로 확인 없이 열리고 사이드바가 닫힘, 다시 열면 그 파일까지 펼치고 표시, 길게 누르기(실제 터치) → 확인 창 → 취소면 남고 지우기면 사이드바와 store에서 빠짐, 사이드바를 연 채로 링크가 와도 새 파일을 따라감, 권한 없는 폴더는 "권한이 없습니다: …"를 그 자리에 보여줌. 지운 프로젝트는 링크로 다시 만들어 탭에 남겨 두었다.
  - 폴드8에서도 사용자가 직접 써 보고 확인했다(APK를 보내 손으로 설치).
- [x] `GitService` + `GitServiceTest`(MINA에 `ProcessShellCommandFactory`를 붙여 **실제 `git`**을 임시 레포에 대해 실행)
  - `project/GitService`: `head()`, `show(커밋, 경로, 상한)`, `history(경로)`(`log -n 2`), `files()`(`ls-files -co --exclude-standard -z`). 기준 텍스트는 `cat-file --batch` 하나를 길게 열어 읽고, 60초 쓰지 않으면 닫는다. `RemoteExec.start`가 그 채널을 연다(stdin을 닫지 않는 명령). 아직 어디에도 붙이지 않았다 — 거터 항목에서 `ProjectSession`에 붙이고, 세션을 닫을 때 같이 닫는다.
  - `GitServiceTest` 12: 이 맥의 실제 `git`을 MINA의 `/bin/sh -c`로 돌린다(사용자 git 설정은 `GIT_CONFIG_GLOBAL=/dev/null`로 뺐다). 커밋 전 HEAD, 커밋된 내용과 작업 트리의 차이, 새 파일·디렉토리는 null, 바이트 그대로, **20개 파일을 명령 하나로**, 도는 동안 생긴 커밋을 봄, 상한을 넘는 객체 뒤에도 다음 요청이 됨, 유휴 뒤 다시 시작, `log`의 두 커밋, `*.md`라는 이름이 패턴이 아님(`--literal-pathspecs`), 무시·추적·미추적과 줄바꿈 든 이름, 레포가 아닌 폴더. `RemoteExecTest` +1(`start`로 연 `cat`과 한 줄씩 주고받기), 거부된 계정에서는 `start`도 `ExecRefused`다.
  - MINA는 `ProcessShellCommandFactory` 대신 `CheckGitTest`처럼 `ProcessShellFactory`로 `/bin/sh -c`를 돌렸다 — 줄 앞에 환경 변수를 넣어야 해서다.
- [x] git 거터(viewer, editor, diff 공통)
  - 정한 것(2026-10-02 사용자 결정): 지운 프로젝트의 열린 파일은 거터를 잃는다. HEAD는 전환·앱 복귀·파일 변경 때 다시 묻고 폴링마다 묻지 않는다. 자세한 것은 spec의 "git과 LSP".
  - Kotlin: `ProjectSession.gitService()`(git이 있을 때만, 처음 쓸 때 만든다)와 `close()`(`GitService`와 exec 연결을 같이 닫는다, `ProjectSessions`의 교체와 지우기에서 부른다). `GitService.headText`, `TextLoader.decodeLike`. 브리지 `baseline`, 알림 `gitChanged`(앱 복귀, 프로젝트 지우기).
  - 페이지: `gitGutter.ts`(vitest 12). 레이어 compartment 밖에 두어 세 레이어가 같이 쓴다. `diff.ts`의 `lineDiffConfig`를 쓴다.
  - JVM +3(`TextLoaderTest` 1, `GitServiceTest` 2: EUC-KR+CRLF로 커밋된 파일, HEAD에 없는 파일, 상한, 커밋 전 레포).
  - 탭에서 이 맥의 원격 로그인으로 확인했다. 표시는 모두 서버의 `git diff -U0`와 같았다(줄 번호와 종류를 DevTools로 읽었다).
    - 이 worktree의 `main.ts`(추가 셋), 임시 레포의 `a.txt`(수정, 삭제, 추가)와 EUC-KR+CRLF `euc.txt`(수정, 추가).
    - markdown(`skiffcode.spec.md`)은 렌더된 viewer에서는 거터가 없고 editor에서 같게 보였다.
    - editor에서 타이핑하면 바로 수정으로 바뀌었다.
    - 앱을 내린 채 서버에서 커밋하고 돌아오면 새 HEAD로 다시 그려졌다(남은 것은 저장 안 한 타이핑뿐).
    - 화면의 파일을 바꾸고 커밋하면(pull과 같은 모양) 버퍼가 새 내용을 받고 표시가 없어졌다.
    - 프로젝트를 지우면 열린 파일은 남고 거터만 없어졌다. 임시 레포와 그 프로젝트는 지웠다.
  - 폴드8에서도 사용자가 거터를 확인했다(2026-10-02, 손으로 설치한 커밋 전 빌드).
- [x] git 거터의 수정 색을 테마 키 `diff.changed`로 (2026-10-02 사용자 요청)
  - **정한 것(2026-10-02 사용자 결정): 키는 수정 색 하나만 더한다.** 추가와 삭제는 지금처럼 그 테마의 `diff.added`, `diff.removed`를 따른다. 거터와 diff 레이어의 추가·삭제가 같은 색이어야 읽기 쉽다.
  - 지금 수정 막대는 메뉴 강조색 `ui.accent`를 빌려 쓴다. 키를 `[diff] changed`로 두는 까닭은 둘이다. 추가·삭제 색 옆이라 찾기 쉽고, 다음 항목인 diff 레이어도 수정 색이 필요하면 이 키를 쓸 수 있다.
  - **번들에 값으로 적는다(2026-10-02 사용자 결정).** 그래서 다른 색과 규칙이 같다. 이 키가 없는 테마는 같은 `base`(라이트/다크) 번들의 값을 받는다.
    - 예외 코드가 필요 없고, 복사한 테마에 키가 값으로 보인다.
    - 처음 값은 지금 화면과 같게 각 번들의 `ui.accent`다. 다크는 `#4493f8`, 라이트는 `#0969da`다.
    - 잃는 것: `ui.accent`만 바꾼 테마는 수정 막대가 그 accent를 따라가지 않고 번들 색이 된다. 이미 가져온 테마도 같다. 앞서 정한 "없으면 `ui.accent`" 대체는 이것으로 뺐다.
  - 고친 곳: `ThemeToml.KEYS`, 번들 `light.toml`/`dark.toml`(키 위에 무엇을 칠하는지 주석), `index.html`의 `:root`, `gitGutter.ts`.
  - 테스트: `ThemeTomlTest`에 1개(accent만 바꾼 라이트·다크 테마가 번들의 `diff.changed`를 받는다, 번들 값이 예전 accent와 같다). 번들에 모든 키가 있는지와 `:root`·`light.toml`·`var(--…)`가 같은지는 있던 테스트가 새 키까지 봤다. JVM 218, vitest 114, lint 경고 11(전과 같다).
  - 번들 값이 예전 accent와 같아서 화면은 바뀌지 않는다. 이 빌드를 폴드8에 설치해 사용자가 확인했다(2026-10-03).
- [x] diff 레이어: unified, +/-, 초록/빨강 투명도 설정, 하이라이팅, viewer와 같은 스크롤/줌. ④ 더보기에 비교 대상 선택 추가
  - 정한 것(2026-10-03 사용자 결정 넷): 비교할 것이 없으면 ③이 diff를 건너뛴다. 직전 커밋은 **현재 버퍼와** 비교한다. 투명도는 `[editor] diff_alpha`(퍼센트, 처음 0~50·기본 15에서 폴드8을 보고 0~100·기본 30이 됐다 — 아래). ④의 선택은 파일마다, 열려 있는 동안만. 자세한 것은 spec의 "레이어"와 ④.
  - CM6 `forEachLine` 버그는 `code/web/patches/`의 `patch-package`로 고쳤다(`npm ci`의 `postinstall`). Gradle `npmCi`·`buildWeb`의 입력에 `patches/`를 넣었다. `diff.ts`의 런타임 패치와 스파이크 데이터, `char` 모드는 지웠다. 탭에서 번들의 `forEachLine`에 clamp가 든 것을 DevTools로 봤다.
  - **`lineDiff`의 버그를 고쳤다(거터도 같이 걸렸다).** 줄을 `\n`으로 쪼개고 끝에 `길이+1` 보초를 둬서, 파일 끝이 바뀌면 문서 밖 위치가 나와 `Chunk.build`가 `RangeError`로 죽었다. 그 파일은 거터도 diff도 없었다. 이제 줄이 자기 줄바꿈을 들고(git처럼 끝 줄바꿈 없는 줄은 다른 줄), 위치가 텍스트 길이를 넘지 않는다.
  - 페이지: `pane.ts`(diff 묶음을 그 파일의 비교 대상으로 만들고 바뀌면 다시 구성, `nextLayer`, `compare`/`compareWith`/`comparable`, `PaneMemory.compare`), `chrome/more.ts`(④ 메뉴), `topbar.ts`, `main.ts`(`previous`를 묻고, 기준을 다시 물을 때 같이 묻는다), `settings.ts`(`--diff-alpha`).
  - 줄로 스크롤할 때 그 줄 앞의 지운 줄 블록도 화면에 남긴다(화면 절반까지). 블록 높이는 그려지기 전엔 CM의 추정치라 다음 프레임에 한 번 더 맞춘다. 안 하면 ③으로 diff에 들어갈 때 맨 위의 지운 블록이 메뉴 밑에 가려졌다(탭에서 63px 중 65px 스크롤).
  - Kotlin: `GitService.previousText`(`history`의 두 번째 커밋, `headText`와 같은 디코딩), 브리지 `previous`, `Settings.Editor.diffAlpha`, 문자열 셋.
  - 테스트: vitest +7(`diff.test.ts`: ③ 순서, diff 레이어 청크, 끝이 바뀐 텍스트, 무작위 2000쌍이 정확히 b가 되는지), JVM +1(`GitServiceTest`: 직전 텍스트, 커밋 하나뿐인 파일, 커밋 안 된 파일), `SettingsTomlTest` 2개에 `diff_alpha`. JVM 219, vitest 121, lint 경고 11(전과 같다).
  - 탭에서 이 맥의 원격 로그인으로 확인했다(이 worktree, 이 Mac을 가리키는 프로필).
    - `diff.ts`(작업 트리에서 수정): 거터와 diff 레이어의 청크 12개가 서버의 `git diff -U0`과 같았다. ③ 두 번에 diff, 맨 위 지운 블록이 메뉴 바로 아래 보였다. 60번 줄에서 들어가고 나와도 60번 줄.
    - ④ → "직전 커밋과 비교": 청크 11개가 `git diff -U0 <직전 커밋> -- 파일`과 같았다. 다른 파일에 갔다 와도 diff 레이어와 그 선택이 남았다. 다시 열면 ✓가 그쪽에 있다.
    - 커밋 하나에서만 바뀐 `keyboard.ts`: "이 파일을 바꾼 이전 커밋이 없습니다" 배너. HEAD를 고르면 바뀐 것 없는 diff 레이어.
    - 추적하지 않는 `more.ts`: ③이 읽기↔편집만 돌고 ④의 두 항목이 꺼진다.
    - `settings.toml`에 `diff_alpha = 40`을 넣으니 `--diff-alpha`가 0.4. 탭의 설정 파일은 되돌렸다(md5 같음).
  - 폴드8에서 사용자가 바뀐 줄마다 초록 밑줄을 찾았다(2026-10-03). merge 기본 테마가 `.cm-changedText`에 2px 밑줄을
    클래스 4개 깊이의 선택자로 그려 `diff.ts`의 배경 규칙을 이겼다. 선택자를 같은 깊이로 맞춰 고쳤고, 탭에서
    `getComputedStyle`로 밑줄이 없어지고 배경이 `diff_alpha`×2로 들어간 것을 확인했다.
  - 같은 날 폴드8에서 사용자가 "색이 너무 강하다"고 해서 기본값을 15 → 10으로 낮췄고, 이어서 **글자 배경을 없애고 줄 배경만
    남겼다**(사용자 제안). 겹침의 대부분은 바뀐 글자가 아니라 패키지가 삽입 범위 전체를 감싸는 `ins`에 준 두 배 배경이었다.
  - 보지 않은 것: 큰 파일(2MB)에서 diff 레이어에 들어가는 시간, 마크다운 파일의 diff 레이어.
- [x] `settings.toml`에 빠진 키를 주석 처리된 기본값으로 채우기 (2026-10-03 사용자 요청: 폴드8의 파일에 `diff_alpha`가 없었다)
  - **정한 것(2026-10-03 사용자 결정 셋):** 새로 만드는 파일도 모든 키를 주석으로 둔다. 채우는 때는 `Open Settings`로 열 때다. `diff_alpha`는 0~100, 기본 30(줄 배경만 남겨 옅어졌다). 자세한 것은 spec의 "설정과 테마".
  - Kotlin: 손으로 쓴 `TEMPLATE` 대신 `SettingsToml.KEYS`(표, 키, 기본값, 설명) 하나에서 템플릿과 `withMissingKeys`가 나온다. 범위는 설명에 `FONT_SIZES` 같은 상수에서 들어간다. `SettingsFile.ensureExists` → `ensureComplete`(바뀐 것이 있을 때만 쓴다). `withTheme`은 주석 처리된 `# theme =` 줄을 그 자리에서 푼다.
  - **ktoml은 키가 하나도 없는 표를 거부한다**("missing children in a table"). 전부 주석인 템플릿이 그대로는 읽히지 않아서, `read`가 그런 표의 머리 줄을 빈 줄로 바꾼 뒤 읽는다(줄 번호가 밀리지 않게 지우지 않는다).
  - 테스트: `SettingsTomlTest` +8(주석을 푼 템플릿이 기본값으로 읽힘, `Settings`의 필드와 `KEYS`가 같음, 설명에 기본값, 빠진 키 채우기·두 번 해도 같음·주석으로 둔 키 유지·표가 없을 때·깨진 파일과 빈 파일, 키가 모두 주석인 표, Theme이 주석 줄을 풂). JVM 227.
- [x] 🔍 파일 모드를 프로젝트에서 `git ls-files` 캐시로 확장
  - **기준은 활성 프로젝트이고 하위 폴더까지다**(2026-09-26 사용자 요청). 단일 파일 모드의 "열린 파일과 같은 폴더"는 프로젝트가 없을 때만이다. 항목 이름은 루트 기준 상대 경로다(`skiffcode.spec.md`).
  - 정한 것(2026-10-03 사용자 결정 둘): git을 쓸 수 없으면 SFTP로 하위 폴더를 걷는다. 뺄 폴더와 상한은 `settings.toml`의 `[search] skip_dirs`·`max_files`(기본 `node_modules` 등 8개, 5000). 목록은 팔레트를 열 때마다 한 번 받는다. 자세한 것은 spec의 "커맨드 버튼과 팔레트".
  - Kotlin: `ProjectTree.walk`(링크 폴더는 따라가지 않고, 파일 링크는 `stat`으로 확인, 읽을 수 없는 하위 폴더는 건너뜀, 상한에서 `truncated`). 브리지 `folder`/`openFromFolder`가 열린 파일의 프로젝트가 살아 있으면 그 프로젝트를 기준으로 한다 — 이름은 루트 기준 상대 경로이고 `ProjectTree.resolve`가 막는다(사이드바의 `openFromProject`와 같은 문). `ls-files`가 실패하면 walk로 넘어간다. 열린 파일을 빼는 키에는 `OpenFlow`처럼 호스트 키 지문을 넣는다 — 안 넣으니 화면의 파일이 목록에 남았다(탭에서 찾았다). `Settings.Search`, `SettingsToml.KEYS` +2, 복수형 문자열 `search_truncated`.
  - 페이지: `main.ts`가 `truncated`가 오면 배너로 알린다. `files.ts`는 주석만.
  - 테스트: `ProjectTreeTest` +4(하위 폴더·`skip`·`.git`, 파일 링크는 넣고 폴더 링크는 따라가지 않고 깨진 링크는 뺌, 상한에서 멈춤과 딱 맞을 때, 읽을 수 없는 폴더), `SettingsTomlTest` +1(`skip_dirs`의 `/`와 `max_files` 범위, 빈 목록)과 기존 2개에 `[search]`. JVM 232, vitest 121, lint 경고 11(전과 같다).
  - 탭에서 이 맥의 원격 로그인으로 확인했다.
    - 이 worktree(git): `s`로 200개, 서버의 `git ls-files -co --exclude-standard | grep -ci s`(201)에서 화면의 `files.ts`를 뺀 것과 같다. 고른 `docs/skiffcode.spec.md`는 경로를 묻지 않고 열렸고, 거기서 다시 열면 `files.ts`가 열린 파일(이름만)로 올라오고 `skiffcode.spec.md`는 빠졌다.
    - 홈의 임시 폴더(빈 `.git` 폴더라 `ls-files`가 "not a git repository"로 실패 → walk): `node_modules`·`build`가 빠지고 154개가 약 0.1초에. `max_files = 100`이면 99개(README + `gen` 99개에서 멈추고 화면의 README는 빠짐)와 배너. 배너는 팔레트 아래에 겹치지 않고 보였다.
    - 그 프로젝트를 지우니 남은 README에서 같은 디렉토리 목록으로 돌아갔다. 임시 폴더와 프로젝트는 지웠고 탭의 `settings.toml`은 되돌렸다(md5 같음).
  - 폴드8에서도 사용자가 확인했다(2026-10-03, 커밋 전 빌드).
  - 보지 않은 것: 큰 프로젝트에서 walk에 걸리는 시간. exec가 막힌 계정에서의 walk는 아래 **확인** 항목에서 봤다.
- [x] **확인:** 실기기에서 git 레포 안 파일을 열면 묻는 창이 뜨고, 프로젝트를 만든 뒤 거터와 diff가 서버의 `git diff`와 일치한다. internal-sftp 계정에서는 git 없이 열린다
  - 2026-10-04 탭에서 확인했다. 계정은 이 맥의 Docker 컨테이너로 만들었다(사용자 결정): git을 설치하지 않은 Debian 13에 OpenSSH 10.0, `Subsystem sftp internal-sftp`, 계정 셋(`ForceCommand internal-sftp`, 셸이 `nologin`, 보통 bash). 홈마다 `.git`이 있는 작은 프로젝트를 넣었다. 컨테이너는 확인 뒤 지웠다(Dockerfile은 레포에 넣지 않았다).
  - 셋 다 SFTP만으로 묻는 창이 떴다. `supported()`는 internal-sftp와 nologin에서 `NoExec`, bash 계정에서 `Missing`이었다. 셋 다 walk로 프로젝트 파일 검색이 됐다(`node_modules`·`build`·`.git`이 빠짐). 검색에서 고른 파일은 경로를 묻지 않고 열렸다.
  - **배너가 뜨자마자 사라지는 버그를 찾아 고쳤다.** Kotlin은 `documentsChanged` 바로 뒤에 `notice`를 보내는데, 페이지의 `show()`가 `document` 응답을 기다린 뒤 `banner.hide()`를 불러서 26ms 만에 지웠다. 그래서 `NoExec`/`Missing` 배너를 기기에서 한 번도 보지 못했다. `notice`가 `refresh`와 같은 `queue`에서 차례를 기다리게 했다.
  - 이 맥의 계정(git 있음): 홈의 임시 레포에서 한 줄 바꿈, 세 줄 지움, 두 줄 넣음, 끝에 한 줄 더함. 거터(바뀜 3, 9와 10 사이 지움, 더함 18·19·30)와 diff 레이어가 `git diff -U0`와 같았다. 넣기만 한 곳의 빈 `cm-deletedChunk`는 높이 0이다. 임시 레포는 지웠다.
  - 탭에 컨테이너 계정 프로필 셋과 그 프로젝트가 남았다. 지우는 길은 M7의 store 초기화다.

### M6. LSP
실제 LSP 서버로 확인하던 M0 하네스는 사용자 지시로 지웠다. 다시 만들 때 필요한 것은 이것뿐이다.
- 서버 쪽: MINA `SshServer`에 `commandFactory = ProcessShellCommandFactory.INSTANCE`.
- 클라이언트 쪽: sshj `session.exec("/bin/sh -lc 'cd <root> && exec <command>'")`.
- 프레이밍: 헤더는 `\r\n\r\n`까지 한 바이트씩, 본문은 `Content-Length`만큼 채워 읽는다.
- 언어 서버: `npm i pyright` → `node_modules/.bin/pyright-langserver --stdio`. 레포에 넣지 않았다.
- **`org.json`은 유닛 테스트 JVM에서 스텁이라 전부 던진다.** `org.json:json`을 테스트 의존성으로 넣고
  Gradle에서는 `systemProperty`로 넘긴다(`-D`는 테스트 JVM에 전달되지 않는다).
- 결과: initialize 98ms, 진단까지 346ms(로컬 루프백). `positionEncoding`은 응답에 없고(= `utf-16`).

**성공 조건**

- ✅ 실제 서버의 파이썬 프로젝트에서 정의 이동, 진단, 심볼 검색이 된다 — 확인: 폴드8, 원격 Ubuntu 서버, 실제 pyright.
- ✅ 앱을 백그라운드에 오래 두면 원격 언어 서버가 끝나고, 망이 바뀌어 남은 고아는 다음 시작 때 꺼진다 — 확인: 폴드8에서 `ps`, `LspManagerTest`.
- ✅ 데몬 판정에 쓸 실측(망 전환 뒤 진단까지 3.37초, 재인덱싱 몫 26%)이 spec에 있다 — 확인: 15회 측정, 판정은 exec 유지.

- [x] `LspProcess` Content-Length 프레이밍 + `LspFramingTest`(분할과 결합, 멀티바이트 길이) + MINA exec 위 스텁 서버로 initialize 왕복(`LspProcessTest`)
  - 왕복 테스트를 원래 다섯째 항목에서 여기로 당겼다(2026-10-05 사용자 동의). 프레이밍은 실제 채널 위에서 봐야 하고, `LspManager` 테스트도 같은 하네스를 쓴다.
  - **stderr를 계속 비운다.** stderr는 stdout과 채널 창을 나눠 써서, 첫 응답 전에 sshj의 창(2MB)보다 많이 쓰는 서버는 멈춘다. 비우는 것을 빼면 `LspProcessTest`의 8MB 테스트가 시간 초과로 실패하는 것을 봤다. 마지막 4KB만 남긴다(`stderrTail`, 서버가 왜 안 떴는지 알릴 때 쓴다).
  - 로그인 셸의 프로필이 stdout에 찍는 줄은 콜론이 없으면 `LspProtocolError`로 끝나고, 콜론이 있으면 모르는 헤더로 넘어간다.
- [x] `LspManager`: 지연 시작, 유휴와 백그라운드 종료, 재연결 후 `didOpen` 재전송, `command -v` 탐지
  - 재연결 후 `didOpen`은 페이지의 `LSPClient.connect`가 보낸다. Kotlin은 서버가 끝났다고 알리기만 한다(spec "git과 LSP"의 `LspManager`).
  - 백그라운드 종료는 `stopAll()`까지 만들었다. **`MainActivity`의 `onStop`에 거는 것과 "오래"를 몇 분으로 할지는 다음 항목에서 브리지와 같이 한다.** 리스너도 거기서 붙인다.
  - `LspManagerTest`는 스텁 실행 파일의 폴더를 PATH가 아니라 테스트 HOME의 `.profile`에서만 더한다. `-l`을 빼면 10개 중 8개가 실패하는 것을 봤다.
- [x] Web `@codemirror/lsp-client` 연결: editor(진단, 자동완성, hover, 정의), viewer(두 번 탭해 hover, 정의로 이동, 다른 파일이면 열기)
  - 2026-10-05 사용자 결정 여섯: 이 맥에 `brew install pyright`, 백그라운드 `stopAll`은 10분, 정의 이동과 링크의 `col`은 커서를 줄·열에 두고 줄을 맨 위로, 요청 타임아웃 기본 10초, 서버에 여는 것은 화면의 파일만, 터치는 viewer 길게 누르기 툴팁 안의 `정의로 이동` 버튼과 팔레트 명령. 2026-10-06 사용자 결정: 길게 누르기를 **두 번 탭**으로 바꾸고 길게 누르기는 브라우저의 선택·복사에 돌려준다(폴드8에서 손가락으로 길게 누르니 선택만 됐다 — 그 서버에 언어 서버가 없었던 탓으로 보이지만, 몸짓이 다투는 것도 피한다). 설계는 spec "git과 LSP"의 페이지 쪽.
  - Kotlin: `LanguageServer.forFile`(확장자→서버와 language id), `LspUri`, `Settings.Lsp`(`[lsp] timeout_seconds`, `[lsp.<종류>] command`, `SettingsToml.KEYS` +4), `ProjectSessions`가 설정의 명령을 넘김, `LspManager`의 탐지 캐시를 명령 목록으로, `close`가 `LspEnd.Closed`를 알림. `MainActivity`: `lspSend` 채널, 프로젝트마다 리스너, `openDefinition`, 문서의 `lsp`와 `col`, `documentsChanged`의 `col`, `onStop` 10분 뒤 `stopLanguageServers`.
  - 페이지: `lsp.ts`(M0 스파이크를 갈아엎음), `lspPolicy.ts`, `sanitize.ts`, pane의 LSP compartment와 커서 두기, 팔레트 `Go to Definition`·`Show Hover`.
  - 알려진 문제 10(`col`)을 풀었다. 팔레트 심볼 모드의 이동도 이제 그 줄 첫머리에 커서를 둔다(같은 `goToLine`을 쓴다).
  - 테스트: JVM 265(+8: `LspUriTest` 4, `SettingsTomlTest` +2와 표 구조 테스트 둘 갱신, `LspManagerTest` +2), vitest 129(+8: `lspPolicy` 5, `sanitize` 2, `commands` 1). 린트 경고 11(기준과 같다).
  - **탭에서 확인했다**(이 맥의 원격 로그인, 홈의 임시 레포 `skiff-lsp-check`, 실제 pyright). 진단 둘(`"한글" + 1`의 범위가 UTF-16으로 맞고, `util`에서 가져온 메서드의 인자 타입 오류). 링크 `line=12&col=7`이 커서를 12:7에. CDP 터치 길게 누르기로 hover 셋을 연달아, 툴팁의 버튼으로 같은 파일(4:5)과 다른 파일(`util.py` 1:5). editor 레이어에서 팔레트 UI로 `Show Hover`와 `Go to Definition`. 서버 프로세스를 죽이니 같은 초에 다시 떴고 진단이 돌아왔다. 버퍼를 고치니 그 진단이 사라졌다.
  - 탭에서 찾아 고친 것 둘: CDP 터치 뒤에 오는 호환 mousemove가 라이브러리의 마우스 hover를 띄웠다(막음). 호환 mousedown이 선택을 옮겨 막 뜬 툴팁을 닫았다(선택으로는 닫지 않게).
  - 두 번 탭으로 바꾼 뒤 탭에서 CDP 터치로 다시 봤다(2026-10-06): 120ms 간격 두 탭 → hover, 450ms 간격·60px 떨어진 두 탭·한 번 탭·800ms 길게 누르기 → 없음, 툴팁 뒤 한 번 탭 → 닫힘, 진단 자리 → 진단 문구, 버튼 → 같은 파일 4:5, editor 레이어에서 두 번 탭 → 단어 선택만.
  - 서버가 없을 때 물으면 배너(2026-10-06 사용자 결정). 탭에서 `[lsp.python] command = ["no-such-langserver"]`로 봤다: 파일을 열 때는 배너 없음, 두 번 탭 → "이 프로젝트의 서버에서 Python 언어 서버를 찾지 못했습니다."가 잠깐 뜨고 사라짐. 설정을 되돌리고 앱을 다시 켜니 hover가 돌아왔다.
  - PATH에 프로젝트 `.venv/bin`(앞)과 `~/.local/bin`(뒤)을 붙인다(2026-10-06 사용자 결정). 폴드8에서 원격 Ubuntu 서버의 프로젝트가 "언어 서버를 찾지 못했습니다"였던 원인. `LspManagerTest` 둘(가상환경·`~/.local/bin`에만 있는 명령을 찾음, 가상환경이 로그인 PATH보다 먼저). 그 서버에 같은 스크립트로 `.venv`의 pyright가 잡히고 `initialize`에 답하는 것을 ssh로 봤다.
  - 링크가 끊겨 끝난 서버는 `LspEnd.Disconnected`로 조용히 내려 두고, 앱으로 돌아오면(`lspWake`) 다시 띄운다(2026-10-06, 폴드8을 닫았다 열면 "멈췄습니다 Connection lost"). `LspManagerTest` 둘(돌던 서버 아래 링크가 끊김, 링크가 끊긴 채로 시작). 탭에서 앱을 뒤로 보내고 이 맥에서 그 sshd 세션을 끊은 뒤 돌아오니 배너 없이 pyright가 새로 떠 진단 둘이 돌아왔다. 탭의 Wi-Fi를 40초 껐다 켜는 것으로는 링크가 끊기지 않았다.
  - hover가 떠 있는 동안 그 단어를 선택색으로(2026-10-06 사용자 결정). 탭에서 CDP로: `local_helper`·`"한글"`의 `한글`에 표시, 툴팁 밖 탭으로 닫으면 사라짐, 스크린샷으로 색 확인.
  - **폴드8에서 사용자가 확인했다**(2026-10-06): 원격 Ubuntu 서버의 프로젝트에서 두 번 탭 툴팁과 단어 강조, 폴드8을 닫았다 연 뒤에도 끊기지 않음.
  - **폴드8에서 사용자가 확인했다**(2026-10-06): 손가락으로 길게 누르면 브라우저의 선택·복사가 된다.
  - 보지 않은 것: 자동완성 목록과 서명 도움말을 화면 키보드로, 마우스 hover. 백그라운드에서 끊긴 뒤 다시 띄우기는 아래 **확인** 항목에서 봤다.
  - pyright가 `File or directory "/<default workspace root>" does not exist.`를 로그로 냈다. 라이브러리가 `rootUri`만 보내고 `workspaceFolders`를 보내지 않아서였고, 아래 심볼 항목에서 `initialize`에 워크스페이스 폴더를 더해 사라졌다. `pyrightconfig.json` 같은 것이 루트에서 읽히는지는 보지 않았다.
- [x] `@` 심볼 모드를 프로젝트에서 LSP `documentSymbol`/`workspace/symbol`로 확장
  - **한 목록으로**(2026-10-06 사용자 결정): 이 파일 심볼이 위, 프로젝트 심볼이 경로와 함께 아래. 설계와 이유는 spec "심볼 검색"의 LSP 쪽.
  - 페이지: `symbols.ts`(`outlineFromServer`, `projectSymbols`, `projectItems`, `createProjectSearch`), `lsp.ts`(`documentSymbols`, `workspaceSymbols`, `openInProject`, `withWorkspaceFolder`, 트리 개요 capability), pane의 `serverOutline`·`projectSymbols`·`openSymbol`, `palette.ts`(항목의 `elsewhere`, 소스에 입력을 넘김), `chrome/palette.ts`와 `index.html`(경로를 오른쪽에 흐리게). Kotlin은 바뀌지 않았다.
  - pyright를 이 맥에서 stdio로 직접 물어 정했다: `documentSymbol`이 인자와 지역 변수까지 준다, `workspaceFolders` 없이는 `workspace/symbol`이 비어 있다, 인자·지역 변수·클래스 속성이 모두 Variable이다.
  - 테스트: vitest 146(+17: `symbols` 13, `palette` 2, 새 `lsp` 2). JVM 269 그대로. 린트 경고 11(기준과 같다).
  - **탭에서 확인했다**(이 맥의 원격 로그인, `skiff-lsp-check`, 실제 pyright, CDP 터치로 팔레트를 `@`로). `app.py`에서 `m` → `main`, `LIMIT`(모듈 상수를 잠깐 더해 lezer가 아니라 LSP가 공급원인 것을 봤다, 인자 `name`은 안 나옴), `hel` → `local_helper` 아래 `Greeter.hello ⟶ util.py`. 그것을 고르니 `util.py`가 확인 창 없이 7:9(`hello`)에 열렸다. `util.py`에서 `m` → `main ⟶ app.py`, `LIMIT ⟶ app.py`. 언어 서버가 없는 기기 로컬 `hello.py`는 lezer로 `인사`가 나왔다. 새 빌드에서는 `/<default workspace root>` 로그가 없었다. 스크린샷으로 경로 표시를 봤다.
  - **폴드8에서 사용자가 확인했다**(2026-10-06, APK를 손으로 설치): 원격 서버의 파이썬 프로젝트에서 `@` 모드가 잘 돈다.
  - 보지 않은 것: 큰 프로젝트에서 `workspace/symbol`이 입력을 따라오는 속도, TypeScript 서버의 심볼(`containerName` 모양이 다를 수 있다), 서버가 막 뜨는 중(초기화 몇 초)에 팔레트를 열 때 — 그동안 이 파일 개요는 비어 있다가 답이 오면 찬다.
- [x] **확인:** 실제 서버의 파이썬 프로젝트에서 정의 이동, 진단, 심볼 검색이 동작하고, 앱을 백그라운드에 오래 두면 원격 LSP 프로세스가 종료된다(`ps`로 확인)
  - **폴드8에서 확인했다**(2026-10-06, USB adb와 CDP 터치, 원격 Ubuntu 서버의 파이썬 프로젝트, 실제 pyright):
    정의 이동(`a01 = A()`의 `A`를 두 번 탭 → hover `class A()` → 버튼 → 커서가 `class A: ...`의 4:7), 진단(그 프로젝트는
    pyright로 오류 0개라 editor 버퍼 끝에만 `skiff_check: int = "not an int"`를 넣었다 — 1초 안에 표시, Ctrl+Z로 되돌려
    버퍼가 원래와 같은 것을 비교로 봤고 진단도 사라짐, 저장하지 않음), 심볼 검색(팔레트 버튼을 밀어 `@` 모드, `b0` →
    이 파일의 `b01`·`b02`, `SessionB` → `binder.py`의 `SessionBinder` 등 경로와 함께).
  - **백그라운드: 10분이 아니라 몇 초 만에 끝났다.** 앱을 홈으로 보내고 6초 뒤 Android가 앱(uid)의 TCP 소켓을 닫았고
    (`InetDiagMessage: Destroyed 2 sockets ... uids={<앱 uid>}`), 서버는 같은 순간 sshd 세션을 닫아 pyright도 끝났다. LTE일
    때(21:03)와 와이파이일 때(21:05) 둘 다 같았다. `netpolicy`의 백그라운드 데이터 제한은 꺼져 있었다 — Android가
    백그라운드 앱의 네트워크를 막는 쪽으로 보인다. 이렇게 닫힌 소켓은 서버에 끊김이 닿으므로 고아가 남지 않는다.
    그래서 이 기기에서는 `onStop` 10분의 `stopAll`이 돌 일이 없다(남겨 둔다: 소켓을 닫지 않는 기기나 상태의 몫이고,
    `LspManagerTest`가 덮는다). 앱으로 돌아오면 `lspWake`로 다시 접속해 2초 만에 pyright가 새로 떴다.
  - 그래서 고아가 생기는 것은 소켓을 닫을 틈 없이 망이 사라질 때다(와이파이를 끄거나 범위를 벗어남) — 아래가 그 경우다.
  - **찾아서 고친 것: 망이 바뀌면 원격 서버에 고아 언어 서버가 남았다**(2026-10-06). 폴드8의 앱이 꺼져 있는데
    원격 Ubuntu 서버에 pyright(약 210MB)가 1시간 반째 떠 있었다. sshd 로그로 보니 와이파이(집 IPv4)로 접속해
    pyright를 띄우고 1분 뒤 LTE(IPv6)로 다시 접속했는데, 옛 연결은 끝났다는 신호가 서버에 닿지 않아 sshd가
    세션을 그대로 들고 있었다. 그 서버는 `ClientAliveInterval 0`이라 커널의 TCP keepalive(7200초)만 남는다.
  - 사용자 결정 A(2026-10-06): 다시 띄울 때 옛 것을 끈다. 설계는 spec "git과 LSP"의 `LspManager`.
    `LspManager`(시작 스크립트가 `skiff-lsp-pid <pid>`를 먼저 찍고, `Disconnected`로 끝난 서버를 `orphans`에 두었다가
    다음 시작 전에 `kill`), `LspManagerTest` +3(끊긴 서버를 다음 시작이 끔, 이름이 다른 프로세스는 두고 같은 것만 끔,
    PID를 알리지 않거나 1 이하면 실패). JVM 272, 린트 경고 11(기준과 같다).
  - kill 표기는 원격 서버의 dash에서 정했다: `kill -TERM -- -<pgid>`는 dash가 받지 않고 `kill -s TERM -- -<pgid>`만
    그룹 전체를 끝냈다. 맥의 sh도 같다. 실제 고아 pyright는 python 래퍼가 그룹의 맨 앞이고 node가 같은 그룹이었다.
  - **폴드8에서 확인했다**(USB adb, 원격 Ubuntu 서버의 프로젝트): 와이파이로 연 pyright가 와이파이를 끈 뒤에도
    서버에 남았고(옛 sshd 세션 셋도), 앱을 홈으로 보냈다 돌아오자 LTE로 다시 붙으며 옛 pyright만 사라지고 새
    것 하나가 떴다(옛 sshd 세션은 남음 — 끈 것은 세션이 아니라 kill이다). 새 서버로 두 번 탭 hover(`(module) typing`).
    와이파이를 다시 켜도 LTE 연결이 유지돼 끊김이 없었다.
  - 처음 본 고아(19:08 시작, 고치기 전 빌드라 그대로 두고 지켜봄)는 21:20:26에 sshd 세션 둘과 함께 끝났다. 2시간 12분 —
    keepalive 7200초 뒤 75초 간격 확인 9번(`tcp_keepalive_intvl`·`probes` 기본값)과 맞는다.
  - 남는 것: 옛 sshd 세션(sshd와 sftp-server, 가벼움)은 TCP keepalive가 끊을 때까지 남는다. 앱 프로세스가 죽으면
    고아 PID도 잊는다(메모리에만 둔다). 프로젝트를 지우거나 프로필이 바뀌어 세션을 닫을 때 링크가 이미 죽어 있던
    서버도 남는다.
- [x] **데몬 판정 측정:** 와이파이↔LTE를 전환한 뒤 재접속부터 진단이 다시 뜰 때까지를 재고, 그중
      서버 재인덱싱이 차지하는 몫을 나눠서 `skiffcode.spec.md`의 "범위 밖" 표에 적는다. 데몬 결정의 유일한 실데이터다
  - 결과는 spec "범위 밖"의 표 아래: 합계 중앙값 3.37초, 그중 재인덱싱 몫 0.87초(26%), SSH 재접속 1.60초.
  - 재는 법(2026-10-06 사용자 결정): 백그라운드→복귀와 앱을 보는 중 와이파이 끄기를 각 5회, 구간은 재접속·서버
    시작·`initialize`·첫 진단, 시각은 임시 `Log.i`(태그 `SkiffMeasure`)와 페이지의 `console.log`로 logcat에 남겼다.
    **임시 로그는 측정 뒤 지웠고** 깨끗한 빌드를 폴드8에 다시 설치했다.
  - 진단은 열린 파일의 editor 버퍼 끝에만 오류 줄을 넣어 만들었다. 끝나고 그 글자를 지운 뒤 `Reload File` → `다시 불러오기`로
    파일의 내용을 받아 저장하지 않은 표시를 지웠다(버퍼 해시가 처음과 같다). 서버 파일은 쓰지 않았다.
  - 첫 시도의 (b)는 연결이 처음부터 LTE에 있어 와이파이를 끄고 켜도 끊기지 않았다. 와이파이 위에서 다시 접속하게 한 뒤
    다시 쟀다. 끝난 뒤 서버에 남은 pyright는 마지막 것 하나(고아 없음).
  - 이 맥에서 원격 서버의 프로젝트 파일 목록을 읽는 것은 권한 분류기가 막았다(운영 서버 읽기). 측정 파일은 사용자가 폴드8에서 열었다.
  - **판정: exec 유지**(2026-10-06 사용자 결정). spec "정한 것" 2.
- [x] 옛 서버 `kill`을 새 서버 시작 뒤로(2026-10-06 사용자 결정, 위 측정에서 시작 앞을 0.47초 막던 것)
  - `LspManager.start`가 고아를 꺼내 두고 새 서버를 띄운 뒤(`spawn`) 고아마다 따로 `reap`한다. 새 서버가 옛 PID를 받았으면
    건너뛴다(이름이 같아 `kill`의 이름 확인이 막지 못한다). 뜨지 못하면 고아를 되돌리고, `reap` 중 링크가 끊기면
    `Op.Unreaped`로 되돌린다. 고아는 서버마다 목록이 됐다.
  - `LspManagerTest` +1(새 서버가 옛 PID를 받으면 끄지 않음 — 가드를 빼면 실패하는 것을 봤다), 기존 하나는 이름을
    "새 서버가 뜬 뒤 끈다"로 바꾸고 두 번째 서버가 다른 PID를 알리게 했다. JVM 273, vitest 146, 린트 경고 11(기준과 같다).
  - **폴드8에서 확인했다**(원격 Ubuntu 서버의 프로젝트): 와이파이 위의 연결로 띄운 pyright가 앱을 보는 중 와이파이를 끈 뒤에도
    서버에 남았고, 앱을 홈으로 보냈다 돌아오자 그것은 사라지고 새 pyright 하나만 남았다. 그 사이 서버에 pyright가 하나 더
    잠깐 보였는데(와이파이를 끈 4초 뒤) 왜 떴는지는 보지 못했다 — 임시 로그가 없는 빌드였다. 복귀 뒤에는 그것도 없었다.

### M7. 마무리

**성공 조건**

- ✅ `Reset Data…`가 고른 항목만 지우고, 저장하지 않은 파일이 닫히면 한 번 더 묻고, 취소하면 아무것도 바뀌지 않는다 — 확인: `ResetDataTest`, 탭(DevTools·`uiautomator`), 폴드8(사용자).

- [x] 팔레트에 데이터 초기화 명령 `Reset Data…`(2026-10-01 사용자 요청)
  - 2026-10-07 사용자 결정: 명령 하나, 체크리스트 창 하나. 항목은 서버 프로필, 호스트키, 프로젝트, 열린 파일, 최근 파일, 설정(초기 설정으로), 테마(기본만 남김), 팔레트 최근 기록. 처음엔 모두 꺼짐. 프로필을 켜면 프로젝트가 따라 켜진다. 프로필을 지우면 열린 원격 파일도 닫고, 닫힐 파일 중 저장하지 않은 것이 있으면 한 번 더 묻는다. 원래 제안(`Reset Projects`/`Reset Store` 둘)은 이것으로 바꿨다. 설계는 spec "데이터 초기화".
  - Kotlin: `ui/ResetData.kt`(`ResetItem`, 함께 지울 것, 닫을 파일), `Dialogs.askReset`(`await`에 `shown`), `MainActivity.resetData`·`kindOf`, `SkiffCodeStore.reset`, `SettingsFile.reset`, `Themes.deleteAllImported`, `ProjectSessions.closeAll`, 문자열 ko/en. 페이지: `commands.ts`, `palette.ts`의 `forgetRecent`, `main.ts`(`isFileDirty`로 고친 문서 id를 보냄, `paletteRecentsCleared`).
  - 테스트: JVM 280(+7: `ResetDataTest` 4, `SkiffCodeStoreTest` +1, `ThemesTest` +1, `SettingsFileTest` 1), vitest 148(+2). 린트 경고 11(기준과 같다).
  - **탭에서 확인했다**(2026-10-07, DevTools와 `uiautomator`): 처음 모두 꺼짐·삭제 꺼짐, 프로필 켬 → 프로젝트 켜지고 잠김(눌러도 안 바뀜), 끄면 풀림, 손으로 켜 둔 프로젝트는 프로필을 켰다 꺼도 켜진 채. 취소하면 store·설정·테마·열린 파일 다 그대로. 고친 `hello.py`가 열린 채 열린 파일 → 확인 창에 `hello.py`만(깨끗한 `readme.md`는 없음), 취소하면 그대로. `ocean` 테마를 쓰는 중 테마만 → 폴더가 비고 `theme = "system"`, 다른 것은 그대로, 확인 창 없음. 고친 `settings.toml`이 열린 채 설정만 → 확인 창에 `settings.toml`, 진행하니 그것만 닫히고 파일은 템플릿. 전부 → store 네 항목 0, 설정 템플릿, 테마 폴더 빔, 열린 파일 없음, `palette.recent`가 `{}`, "초기화했습니다." 배너.
  - 탭에서 찾아 고친 것: 열린 파일 전부를 닫을 때 `docs.all`을 돌며 지워 `ConcurrentModificationException`이 났다(복사본을 돈다). 첫 파일을 닫은 뒤 멈춰 store는 그대로였다.
  - 확인 뒤 탭의 store와 `settings.toml`은 백업으로 되돌렸다. 팔레트 최근 기록은 WebView의 것이라 백업하지 못해 비어 있다.
  - 팔레트에 `Reset`만 치면 `Reset Zoom`이 먼저 잡힌다. `Reset Data`까지 쳐야 한다.
  - **폴드8에서 사용자가 확인했다**(2026-10-07, APK를 손으로 설치, 커밋 전 빌드): 커버 화면에서 긴 창이 스크롤되어 버튼까지 닿고, 고친 원격 파일이 열린 채 프로필을 지우면 확인 창이 뜨고 허락하면 닫힌다.

### F1. hover 툴팁 (fix: 01)

읽을 spec: [`lsp.md`](skiffcode.spec/lsp.md)("터치"와 "툴팁의 차례와 스크롤").

원래 요구사항은 [`my.next.spec.md`](my.next.spec.md)의 "fix: 01". 2026-10-12 사용자 결정: M8보다 먼저 하고, 차례는 시그니처 → 버튼 → 진단 → docstring.

**성공 조건**

- ✅ 긴 hover가 화면에서 잘리지 않고 툴팁 안에서 끝까지 스크롤된다. 끌어도 문서와 상단 메뉴가 움직이지 않는다 — 확인: 탭(위쪽·아래쪽 줄), 폴드8 커버 화면.
- ✅ 차례가 시그니처 → `정의로 이동` → 진단 → docstring이고, 시그니처가 없으면 버튼이 맨 위다 — 확인: vitest `splitHover` 7개, 탭.
- ✅ 버튼을 누르면 정의로 간다 — 확인: 탭(9번 줄이 맨 위).
- ✅ vitest·JVM·린트 통과 — 확인: vitest 155, JVM 280, 경고 11.

- [x] hover 툴팁: 스크롤, 버튼 위치
  - 원인(코드로 확인, 기기에서 맞았다): `lspTheme`의 `'.cm-tooltip.cm-tooltip-hover, .cm-tooltip .skiff-hover'`가 우리 툴팁에 걸리지 않는다. `.skiff-hover`는 `.cm-tooltip`과 같은 요소이고 `cm-tooltip-hover`는 `hoverTooltips`의 것이다. 그래서 `maxHeight`도 `overflow`도 없고, 공간이 모자라면 CodeMirror가 인라인 `height`로 줄인 상자 밖으로 내용이 넘친다.
  - 스타일: `.cm-tooltip.skiff-hover`에 `maxHeight`, `overflowY: auto`, `overscrollBehavior: contain`. 상한(지금 40vh)은 기기에서 보고 정한다.
  - `lsp.ts`: hover 내용을 시그니처와 나머지로 나누는 순수 함수(`splitHover`). 마크다운은 맨 앞 펜스 코드 블록까지가 시그니처, 바로 뒤의 `---` 줄은 docstring 쪽 구분선. 배열은 앞쪽의 `{language, value}` 원소들. plaintext나 앞에 코드 블록이 없으면 시그니처 없음. `hoverDom`이 시그니처 → 버튼 → 진단 → (구분선) docstring으로 그린다. 시그니처가 없으면 버튼이 맨 위.
  - vitest: pyright 모양(코드 블록 + `---` + 문서), 코드 블록만, 배열, plaintext, 코드 블록이 앞에 없는 마크다운, 빈 내용.
  - 탭에서 확인(맥의 임시 파이썬 프로젝트, pyright): `json.dumps`처럼 docstring이 긴 이름을 두 번 탭해 — 화면 위쪽 줄과 아래쪽 줄 각각에서 툴팁 안 스크롤이 끝까지 닿고, 끌어도 문서가 안 움직이고, 스크롤해도 툴팁이 안 닫힘. 버튼이 시그니처 바로 밑, 눌러 정의로 감. 진단이 있는 자리에서 진단이 버튼 밑. 시그니처 없는 hover. 폴드8 커버 화면.
  - 한 것: `lsp.ts`의 `splitHover`(내보냄)·`leadingCode`·`partsToHTML`, `hoverDom`의 차례, `lspTheme`의 `.cm-tooltip.skiff-hover`(40vh, `overflowY: auto`, `overscrollBehavior: contain`). 탭에서 툴팁을 스크롤하면 상단 메뉴가 숨는 것을 찾아 `chrome/topbar.ts`가 `.cm-tooltip` 안의 스크롤을 건너뛰게 했다.
  - 테스트: vitest 155(+7, `lsp.test.ts`의 `splitHover`), JVM 280, `:code` 린트 경고 11(기준과 같다).
  - **탭에서 확인했다**(2026-10-12, DevTools와 `adb input swipe`, 맥의 임시 프로젝트와 pyright): `json.dumps`를 화면 위쪽 줄에서 두 번 탭 → 아래로 270px, 내용 818px, 차례가 시그니처·버튼·구분선·docstring. 툴팁 안을 밀면 끝(550px)까지 스크롤되고 문서(`scrollTop` 2)와 상단 메뉴는 그대로, 툴팁도 열린 채. 툴팁 밖을 밀면 툴팁이 닫히고 문서가 스크롤되며 메뉴가 숨는다(전과 같다). 아래쪽 줄의 `json.dumps` → 위로 뜨고 같은 차례. 정의되지 않은 이름 → 시그니처·버튼·진단. 키워드(hover 없음) → 버튼만. `helper`의 버튼 → 9번 줄이 맨 위.
  - **폴드8 커버 화면에서 확인했다**(475×694): 툴팁 폭 428px(90vw), 높이 278px, 내용 1967px을 끝까지 스크롤, 문서와 메뉴 그대로. 확인용으로 만든 프로젝트는 지웠다.
  - 시그니처가 긴 함수(pyright의 `json.dumps`는 14줄)는 버튼이 툴팁 아래 끝에 걸쳐 보인다. 시그니처 바로 밑이라는 차례는 지켰다.

### M8. 전 화면, 앞 화면 (점프 목록)

읽을 spec: [`jumps.md`](skiffcode.spec/jumps.md). 겹쳐 나온 것을 닫는 쪽은 [`top-menu.md`](skiffcode.spec/top-menu.md)(사이드바, 열린 파일 메뉴, 더보기)와 [`palette.md`](skiffcode.spec/palette.md), 정의로 이동은 [`lsp.md`](skiffcode.spec/lsp.md)의 그 항목만.

원래 요구사항은 [`my.next.spec.md`](my.next.spec.md). 2026-10-11 사용자 결정: 닫힌 파일의 칸은 건너뛰고, 레이어는 적지 않고, 가장자리 제스처가 없는 내비게이션 모드는 기기를 본 뒤 정한다. 2026-10-12에 두 기기를 봤다 — 둘 다 제스처(`navigation_mode` 2, 양옆에서 밀기)라 페이지가 가장자리를 직접 받는 일은 하지 않는다.

**성공 조건** (2026-10-12 사용자 결정: 3버튼은 탭에서만 확인, 미는 동안 따로 그리는 것 없음, 제스처는 adb 스와이프로 확인)

- ✅ (2026-10-12, `jumps.test.ts` 15개와 `back.test.ts` 5개) vitest: 점프 목록의 규칙(끝에서 뒤로 가면 지금 자리가 적힘, 뒤로 간 채 새로 적으면 앞 칸이 잘림, 같은 파일·같은 줄 합치기, 100칸 상한, 닫힌 파일의 칸이 빠지고 앞뒤가 이어짐, 비었을 때 null)과 "겹친 것부터 닫기"가 통과한다.
- ✅ (2026-10-12, `BackGestureTest` 7개, 린트 경고 11 그대로) JVM: 가장자리 셋(`EDGE_RIGHT`·`EDGE_NONE` → 전 화면, `EDGE_LEFT` → 앞 화면)과 콜백이 켜질 조건이 통과한다. `:code` 린트에 새 경고가 없다.
- ✅ (탭, 2026-10-12 — 레이어별 경로는 `input keycombination`과 팔레트로, 실제 외장 키보드는 사용자가 눌러 hello.py ↔ readme.md 100 ↔ 40을 오갔다) 탭의 외장 키보드 `Ctrl+O`/`Ctrl+I`와 팔레트 `Go Back`/`Go Forward`로: 같은 파일 안 정의로 갔다 돌아오기, 다른 파일 정의로 갔다 돌아오고 다시 앞으로, 열린 파일 메뉴로 바꾼 것 되돌리기, 중간 파일을 닫은 뒤 그 칸을 건너뜀 — viewer·editor·diff·렌더된 마크다운 각각에서. 돌아간 줄이 화면 맨 위이고 커서도 그 줄이다.
- ✅ (탭·폴드8 펼친 화면·커버 화면, 2026-10-12, adb 스와이프) 탭과 폴드8(펼친 화면, 커버 화면)에서 adb 스와이프로: 오른쪽 가장자리에서 밀면 전 화면, 왼쪽 가장자리에서 밀면 앞 화면, 파일 사이도. 시스템이 adb 스와이프를 뒤로 가기로 받지 않으면 사용자에게 손으로 확인해 달라고 한다.
- ✅ (탭, 2026-10-12, adb 스와이프) 사이드바·팔레트·열린 파일 메뉴·더보기가 나와 있으면 어느 쪽에서 밀든 그것만 닫힌다. 점프 목록이 비어 있으면 시스템의 뒤로 가기(홈)가 그대로다. editor에서 소프트 키보드가 떠 있으면 키보드가 먼저 닫힌다.
- ✅ (탭, 2026-10-12) 탭을 잠시 3버튼 내비게이션으로 바꿔 뒤로 버튼이 전 화면인지 보고, 제스처로 되돌린다.

- [x] `web/src/jumps.ts`와 vitest
  - 순수 모듈(DOM 없음): `record(at)`, `back(now)`, `forward()`, `forget(fileId)`, `canBack`/`canForward`.
  - 테스트: 끝에서 뒤로 가면 지금 자리가 적혀 앞으로 돌아옴, 뒤로 간 채 새로 적으면 앞 칸이 잘림, 같은 파일·같은 줄 합치기, 100칸 상한, 닫힌 파일의 칸이 빠지고 그 앞뒤가 이어짐, 비었을 때 `back`/`forward`가 null.
- [x] 페이지에 연결: 적기, 움직이기, 키, 팔레트
  - `main.ts`의 `reconcile`에서 화면의 파일이 바뀔 때 떠나는 자리(`pane.close()`의 `line`)를 적는다. 같은 파일의 `goToLine`(링크의 줄, 같은 파일의 정의, 심볼 모드) 앞에서도 적는다. 점프 목록으로 움직인 것은 적지 않는다.
  - Kotlin `activate`가 `goToLine`을 받아 `documentsChanged`에 실어 돌려주게 한다.
  - `reconcile`이 목록에서 사라진 파일을 `forget`한다.
  - `Ctrl+O`/`Ctrl+I` 키(`Mod-i`의 `selectParentSyntax`보다 높은 우선순위), 팔레트 `Go Back`/`Go Forward`(`commands.ts`).
  - 여기까지면 키보드와 팔레트로 쓸 수 있다. 탭의 외장 키보드로 확인: 같은 파일 안 정의로 갔다 돌아오기, 다른 파일 정의로 갔다 돌아오고 다시 앞으로, 열린 파일 메뉴로 바꾼 것 되돌리기, 중간 파일을 닫은 뒤 건너뛰기, viewer·editor·diff·마크다운 각각에서.
  - 확인(2026-10-12, 탭, 이 맥의 임시 git 프로젝트와 pyright): 키는 DevTools `Input.dispatchKeyEvent`(Ctrl)로 보냈다 — 탭에 외장 키보드가 잡히지 않았다(`nokeys`). viewer에서 같은 파일 정의(1→200) 왕복, 다른 파일 정의(main 1 ↔ util 120) 뒤로·앞으로, 열린 파일 메뉴로 바꾼 것(notes.md 85 ↔ main.py) 되돌리기, 렌더된 마크다운 안에서 링크의 `?line=` 되돌리기, editor에서 같은 파일·다른 파일과 팔레트 `Go Back`/`Go Forward`, `Ctrl+I`가 선택을 넓히지 않음(선택 비어 있음), diff에서 같은 파일·다른 파일, util.py를 닫은 뒤 notes → main으로 건너뜀. 돌아간 줄이 맨 위였고 커서도 그 줄이었다(렌더된 마크다운에는 커서가 없다). 링크의 줄이 문서 끝 가까이면 스크롤이 끝에서 멈추고, 그 멈춘 줄이 적힌다.
  - 확인 중 고친 것(2026-10-12, 세 번째 항목 뒤): **외장 키보드의 `Ctrl+O`가 페이지에 닿지 않았다.** viewer에서는 페이지에 포커스가 없어(`document.hasFocus()` false) 하드웨어 키가 액티비티에서 멈춘다. DevTools `Input.dispatchKeyEvent`는 페이지에 직접 넣어 이것을 가렸다. 키를 `MainActivity.dispatchKeyEvent`에서 받아 `jump`로 알리게 바꾸고 페이지의 키 리스너는 뺐다. `adb shell input keycombination KEYCODE_CTRL_LEFT KEYCODE_O`(시스템 입력 경로)로 viewer·렌더된 마크다운의 뒤로·앞으로, 포커스 둔 editor의 `Ctrl+I`(선택이 넓어지지 않음)를 봤다.
  - 확인 중 고친 것: diff 레이어 파일로 돌아갈 때 HEAD 사본이 첫 측정보다 먼저 오면 `compareChanged`가 아직 0인 스크롤을 읽어 1줄로 돌아갔다(커서는 199). `pane.ts`의 `topLine()`이 요청한 스크롤이 그려질 때까지 그 줄을 답한다. `requestAnimationFrame`을 300ms 늦춰 고치기 전 빌드에서 재현했고, 고친 빌드에서는 두 번 다 199였다.
- [x] Kotlin: 뒤로 가기 제스처
  - 페이지가 `navigationState {back, forward, overlay}`를 알리고(점프 목록이 바뀔 때, 사이드바·팔레트·메뉴가 열리고 닫힐 때), `MainActivity`가 하나라도 참이면 `onBackInvokedDispatcher`에 콜백을 등록하고 셋 다 거짓이면 내린다.
  - API 34+는 `OnBackAnimationCallback`: `onBackStarted`에서 `swipeEdge`를 들고 있다가 `onBackInvoked`에서 `EDGE_RIGHT`·`EDGE_NONE` → 전 화면, `EDGE_LEFT` → 앞 화면을 페이지에 알린다. `onBackCancelled`면 아무것도 안 한다. 33은 `OnBackInvokedCallback`, 30~32는 `onBackPressed`로 전 화면만.
  - 페이지: 겹쳐 나온 것(사이드바, 팔레트, 열린 파일 메뉴, 더보기)이 있으면 그것부터 닫고, 없으면 방향대로 움직인다. 갈 곳이 없으면 아무것도 안 한다.
  - 테스트: 방향 판별을 `MainActivity` 밖의 작은 함수로 빼서 JVM 테스트(가장자리 셋 × 콜백이 켜질 조건). 페이지 쪽 "겹친 것부터 닫기"는 vitest.
  - 한 것: `ui/BackGesture.kt`(`jumpDirection`, `NavigationState`), 페이지의 `back.ts`(겹친 것부터 닫기, 바뀔 때만 알리기). 네 겹침 요소에 `isOpen()`을 두고, 열리고 닫히는 것은 `MutationObserver`로 본다(각자 바깥 탭으로도 닫히므로). 매니페스트에 `enableOnBackInvokedCallback`(Android 13~15에서도 콜백이 불리게). `EDGE_NONE`은 API 36 상수라 가장자리 없음은 null로 둔다.
  - 확인(2026-10-12, 탭, `navigation_mode` 2, adb `input swipe`): 시스템이 adb 스와이프를 뒤로 가기로 받았다. 오른쪽 가장자리 → 전 화면(readme.md → hello.py), 왼쪽 → 앞 화면(readme.md 100). 사이드바·팔레트·열린 파일 메뉴·더보기를 하나씩 열고 양쪽에서 밀면 그것만 닫히고 파일은 그대로. 앞 화면만 남았을 때 오른쪽에서 밀면 그대로. 파일을 다 닫으면 홈으로 갔다. editor에서 소프트 키보드가 떠 있으면 첫 밀기에 키보드만 닫히고 다음 밀기에 전 화면.
- [x] 확인: 탭과 폴드8
  - 위 성공 조건을 하나씩 확인해 ✅로 바꾼다. 하나라도 ⬜이면 이 항목은 닫지 않는다.
  - 두 기기의 내비게이션 모드는 2026-10-12에 봤다(둘 다 `navigation_mode` 2, `navigation_bar_gesture_detail_type` 1). 확인 전에 바뀌지 않았는지 다시 본다.
  - 3버튼으로 바꾸는 것은 탭에서만, 확인이 끝나면 곧바로 제스처로 되돌린다(`settings get`으로 되돌린 값을 본다).
  - 탭 3버튼(2026-10-12): `cmd overlay enable-exclusive --category com.android.internal.systemui.navbar.threebutton`으로 바꿨다(`navigation_mode` 0). 뒤로 버튼(오른쪽 아래)이 전 화면(readme.md 100 → hello.py), 앞 화면만 남았을 때 한 번 더 누르면 그대로, 팔레트 `Go Forward`로 readme.md 100. 되돌린 값을 처음 읽어 둔 것과 맞춰 봤다(`navigation_mode` 2, 제스처 오버레이, `navigation_bar_gesture_while_hidden` 1). 오버레이를 바꿀 때마다 액티비티가 다시 만들어져, 열린 파일이 없으면 마지막 링크가 다시 확인 창을 띄웠다 — `AGENTS.md`가 말하는, 처리하지 않는 설정 변경의 기존 동작이다.
  - 폴드8(2026-10-12, Wi-Fi 무선 디버깅, `navigation_mode` 2): 로컬 파일 `near2mb.ts`(5000줄)와 `hello.md` 사이에서 펼친 화면·커버 화면(`device_state state 0`) 모두 오른쪽 가장자리 → 전 화면, 왼쪽 → 앞 화면, 두 번씩. 커버 화면에서 사이드바를 연 채 오른쪽에서 밀면 사이드바만 닫혔다. 끝나고 `state reset`, 화면 꺼짐 300000, Claude 앱을 앞으로 되돌렸다.

### F2. 같은 서버의 프로젝트는 하나 (fix: 02)

읽을 spec: [`open-flow.md`](skiffcode.spec/open-flow.md)("같은 서버의 다른 주소"). 열린 파일이 같은 파일을 가리는 것은 [`document-io.md`](skiffcode.spec/document-io.md)의 그 항목만, 지우는 것은 [`reset-data.md`](skiffcode.spec/reset-data.md)의 "서버 프로필" 줄만.

원래 요구사항은 [`my.next.spec.md`](my.next.spec.md)의 "fix: 02". 사용자 결정: 프로젝트의 git·LSP는 만든 프로필로만 접속하고, 이미 있는 중복은 손으로 지운다(2026-10-13). 호스트 키가 같은 다른 기계(같은 이미지의 Docker 컨테이너)를 가를 길을 더한다(2026-10-14).

원인(코드로 확인): `ProjectStore.containing`이 `profileId`가 같은 프로젝트만 찾는다. 한 서버를 세 주소로 열면 프로필이 셋이라 같은 레포가 주소마다 "프로젝트 만들기"를 다시 묻고, 만들면 사이드바에 하나씩 는다. 열린 파일은 M4에서 이미 호스트 키로 가리므로(`keyOf`) 파일은 하나로 열리고 있었다 — 대신 키가 같은 다른 기계의 파일도 하나로 합쳐졌다.

**성공 조건**

- ✅ (2026-10-14, `SameServerTest` 12개, `SkiffCodeStoreTest` +5, `ProjectStoreTest` +2, `OpenDocumentsTest`·`FolderTest`, 린트 경고 11 그대로) JVM: 서버 무리 — 지문이 같고 "같은 서버"로 이어진 프로필이 한 무리, 지문이 없거나 저장하지 않은 프로필은 혼자, 대표는 store 목록에서 먼저인 것, 물을 것은 답이 없는 무리마다 하나, "다른 서버"로 답한 무리는 다시 묻지 않음. store — 답을 남기고, Reset Data의 프로필과 함께 지우고, `importFromSkiff`가 주소를 바꾼 프로필의 답을 뺀다. `containing` — 무리의 어느 프로필의 프로젝트든 찾고, 가장 깊은 루트, 같은 루트면 먼저 만든 것. `keyOf` — 같은 무리의 두 주소는 한 파일, 다른 무리는 같은 지문·같은 경로라도 두 파일. `:code` 린트에 새 경고가 없다.
- ✅ (탭, 2026-10-14, LAN 주소와 테일스케일 이름의 두 프로필) 탭에서 이 맥을 두 주소로(테일스케일 주소와 LAN 주소, 호스트 키가 같다): 두 번째 주소로 처음 열 때 한 번 묻고, `같은 서버`라 답하면 같은 레포의 다른 파일이 "프로젝트 만들기" 없이 그 프로젝트로 열린다. 사이드바의 프로젝트가 하나이고 그 파일 자리로 펼쳐지며 git 거터가 보인다. 같은 파일은 열린 파일 하나로 간다. 다시 열면 묻지 않는다.
- ✅ (탭, 2026-10-14) 탭: 두 번째 주소로 열어도 LSP 프로세스 id가 그대로다(이 맥의 `pgrep`). 정의로 이동이 된다.
- ✅ (탭, 2026-10-14) 탭: 같은 이미지(키를 빌드 때 만든)의 Docker 컨테이너 둘에 같은 경로의 레포를 두고 연다. 두 번째에서 묻고, `다른 서버`라 답하면 프로젝트와 열린 파일이 따로다. 다시 열면 묻지 않는다. 뒤로 가기로 닫으면 따로 열리고 다음에 다시 묻는다.
- ✅ (탭, 2026-10-14) 탭: `Reset Data…`에서 서버 프로필을 지우면 답도 지워진다(프로필을 다시 만들면 다시 묻는다).
- ✅ (폴드8, 2026-10-14, 사용자가 손으로) 폴드8에서 사용자가: 이미 있던 중복을 사이드바에서 지운 뒤 실제 서버를 세 주소로 열어, 묻는 창에 `같은 서버`라 답하고 프로젝트가 하나다. 파일은 열기만 하고 저장하지 않는다.

- [x] 같은 서버 무리: 순수 함수와 store
  - 순수 함수(새 파일, 예: `data/SameServer.kt`): 프로필 목록(순서 그대로), 프로필의 지문(`host:port`로 `knownHosts`, `SkiffCodeStore.knownHost`처럼 그대로 비교), 답 목록을 받아 — 프로필 하나의 무리(대표 id, 이름 = 지문과 대표 id, 구성원 id)와, 그 프로필이 물어야 할 무리들(각 무리의 대표)을 낸다. "같은 서버" 답은 무리를 잇고, "다른 서버" 답은 그 무리 전체와 갈린 것으로 본다.
  - store: `SkiffCodeData`에 답 목록(프로필 id 둘과 같은지)을 더한다. 기본값이 빈 목록이라 옛 JSON도 읽힌다. 답 쓰기, `reset`의 프로필 항목에서 함께 지우기, `importFromSkiff`가 `host`·`port`를 바꾼 프로필의 답 빼기.
  - 테스트: 순수 함수(두 주소 같음, 세 주소에서 무리로 한 번, 다른 서버 둘, 지문 없음, 저장 안 한 프로필, 대표가 안 바뀜), `SkiffCodeStoreTest`(옛 JSON, reset, import).
  - 한 것(2026-10-14): `data/SameServer.kt`의 `SameMachineAnswer`, `Server`(이름 `key:<지문>:<대표 id>`, 혼자면 `profile:<id>`), `ServerGroups`(`serverOf`, `toAsk`). 무리는 "같은 서버" 답으로만 잇고, 지금 지문이 다른 프로필 사이의 옛 "같은 서버" 답은 잇지 않는다. store: `SkiffCodeData.sameMachine`, `servers()`, `answerSameMachine`(같은 짝의 옛 답을 바꾼다), `deleteProfile`도 그 프로필의 답을 뺀다(지워진 프로필의 답이 남지 않게, spec에도 적었다). `importFromSkiff`는 `host`(대소문자 무시)나 `port`가 바뀐 프로필의 답을 뺀다.
  - 테스트: `SameServerTest` 12개, `SkiffCodeStoreTest` +5(20개). JVM `:code` 306, `:core` 38, `:app` 32, vitest 176, `:code` 린트 경고 11(기준과 같다).
- [x] 묻기와 열린 파일
  - `OpenFlow.openRemote`: `stat`이 지난 뒤, 파일을 읽기 전에 물을 무리마다 네이티브 창(`Dialogs.kt`, 문구는 한국어·영어). 답을 store에 남긴다. 뒤로 가기는 이번만 `다른 서버`.
  - `OpenRequest.Remote.hostKey`를 서버 이름(무리의 이름, 저장 안 한 프로필은 프로필 id)으로 바꾸고 `keyOf`가 그것을 쓴다. `MainActivity`가 팔레트 파일 모드에서 키를 만드는 곳(`knownHost(...)`로 지문을 읽는 곳)도 같은 함수를 거친다.
  - 테스트: `OpenDocumentsTest`의 `keyOf`(같은 무리·다른 무리·저장 안 한 프로필).
  - 한 것(2026-10-14): `OpenFlow.serverOf`가 `stat` 뒤에 물을 무리를 하나씩 묻고(답할 때마다 다시 계산 — 한 무리에 들면 다음 무리가 정해질 수 있다), 뒤로 가기로 닫은 무리는 이번 열기에서만 건너뛴다. 창은 `Dialogs.askSameMachine`, 두 프로필을 이름과 `user@host:port`로 보인다. `OpenRequest.Remote.hostKey` → `server`, `keyOf`는 `remote:<server 또는 profile:<id>>:<경로>`. 팔레트 파일 모드도 `servers().serverOf`를 거친다. 기기 확인은 마지막 항목에서 한다.
  - 테스트: `OpenDocumentsTest`(같다고 답한 두 주소는 한 파일, 같은 키라도 아직 안 물었거나 다르다고 답한 둘은 두 파일, 이름 없으면 프로필), `FolderTest`(옆 파일이 서버 이름을 이어받음). JVM `:code` 307, `:code` 린트 경고 11.
- [x] 같은 서버의 프로젝트를 찾기
  - `ProjectStore.containing(profileIds: Set<String>, dir)`: 무리의 구성원 중 어느 프로필의 프로젝트든 후보. 가장 깊은 루트, 같은 깊이면 목록에서 먼저인 것(`maxByOrNull`는 처음 것을 낸다 — 테스트로 못 박는다).
  - `OpenFlow.activateProject`: 찾은 것이 없을 때만 `GitScopeFinder`와 "프로젝트 만들기"로 간다. `add`는 그대로 연 프로필로 만든다.
  - `ProjectSessions.get`에는 연 프로필이 아니라 **프로젝트의 프로필**을 넘긴다(`MainActivity.profileOf`와 같은 찾기). 프로젝트의 프로필이 없으면(지금은 Reset Data가 둘을 함께 지우므로 없을 일이 없다) 연 프로필로 간다.
  - 테스트: `ProjectStoreTest`에 위 경우들.
  - 한 것(2026-10-14): `containing(profileIds, dir)`, `OpenFlow.serverOf`가 `Server`를 내고 `activateProject`가 그 `profileIds`로 찾는다. 세션은 프로젝트의 프로필로(없으면 연 프로필).
  - 테스트: `ProjectStoreTest` +2(다른 주소로 찾기, 무리 안에서 가장 깊은 것·같은 루트면 먼저 만든 것). JVM `:code` 309, `:core` 38, `:app` 32, vitest 176, 린트 경고 11.
- [x] 확인: 탭과 폴드8
  - 이 맥에 임시 git 레포와 pyright(M6, M8과 같은 방법). 탭에 이 맥의 프로필을 두 주소로 둔다. 확인이 끝나면 만든 프로필·프로젝트·답을 지우고 레포도 지운다.
  - 키가 같은 다른 기계는 M5처럼 이 맥의 Docker로: `apt-get install openssh-server`를 빌드 때 하는 이미지 하나로 컨테이너 둘(다른 포트). 두 컨테이너의 `ssh-keygen -lf /etc/ssh/ssh_host_ed25519_key.pub`가 같은지 먼저 본다. 확인 뒤 지우고, Dockerfile은 레포에 넣지 않는다.
  - 폴드8은 사용자에게 손으로 해 달라고 한다: 사이드바에서 중복을 길게 눌러 지우기, 세 주소의 링크로 열고 묻는 창에 답하기.
  - 탭(2026-10-14, USB, 이 맥의 임시 레포 `skiff-f2-check`와 pyright): 탭에 이미 있던 이 맥의 두 프로필(LAN 주소, 테일스케일 이름 — 지문이 같다)로. LAN 주소로 `main.py`를 열자 "같은 서버인가요?"가 떴고, 뒤로 가기로 닫으니 답이 저장되지 않고(`sameMachine` 없음) "프로젝트로 열까요?"로 넘어가 프로젝트를 만들었다(git 거터, pyright 하나). 테일스케일 이름으로 `pkg/util.py`를 열자 다시 물었고 `같은 서버` → "프로젝트 만들기" 없이 그 프로젝트로 열림, 프로젝트 레코드 하나, 사이드바에 하나이고 `pkg/util.py`로 펼쳐짐, pyright pid 그대로. 테일스케일 이름으로 `main.py`를 다시 열면 묻지 않고 LAN 주소로 열려 있던 그 파일로 갔다(열린 파일 둘). `main.py`의 `helper`에서 `Go to Definition` → `util.py` 1줄, pid 그대로.
  - Docker(같은 날): `debian:13`에 `apt-get install openssh-server git`을 빌드 때 한 이미지로 컨테이너 둘(2231, 2232), 두 지문이 같음을 `ssh-keygen -lf`로 봤다. 같은 경로 `/home/tester/app`의 `main.py`를 컨테이너마다 다른 내용으로. 2231을 열어 프로젝트를 만들고, 2232에서 호스트키 창 뒤에 "같은 서버인가요?" → `다른 서버` → "프로젝트로 열까요?"가 따로 떠 프로젝트가 둘, 열린 파일도 둘(내용 one/two). 2231을 다시 열면 묻지 않고 그 파일로 갔다. `Reset Data…`의 서버 프로필 → `sameMachine` 비고 프로젝트도 비었다. 두 컨테이너를 다시 열자 다시 물었다.
  - 폴드8(2026-10-14): 무선 디버깅이 닿지 않아 APK를 보내 사용자가 손으로 설치하고 실제 서버로 확인했다고 알려 왔다.
  - 끝나고: 탭의 store를 확인 전 백업으로 되돌림(`cmp`로 같음), 화면 꺼짐 600000, 컨테이너·이미지·임시 레포 지움. 팔레트 최근 기록은 `localStorage`라 확인 중 쓴 것이 남았다.

### M9. 마크다운 문서 링크 열기

읽을 spec: [`markdown-links.md`](skiffcode.spec/markdown-links.md). 점프를 적는 곳은 [`jumps.md`](skiffcode.spec/jumps.md)의 "적는 때"만, 바꿀 보안 문장은 [`security.md`](skiffcode.spec/security.md)의 브리지 링크 두 줄만.

원래 요구사항은 [`my.next.spec.md`](my.next.spec.md)의 "markdown 문서 링크 열기". 사용자 결정(2026-10-15): 상대 링크는 어디를 가리키든 묻지 않는다, 텍스트면 확장자와 상관없이 앱에서 연다, https `.md`는 읽기 전용이고 GitHub blob 주소는 raw로 바꾼다, 그 밖의 https는 지금처럼 기본 앱으로 바로.

지금(코드로 확인): `markdown.ts`의 클릭 처리가 페이지 origin으로 풀리는 링크(상대 경로와 `#조각`)를 `preventDefault`만 하고 버린다. 나머지는 WebView가 이동하려다 `shouldOverrideUrlLoading` → `openExternally`로 간다.

**성공 조건**

- ✅ JVM(2026-10-15, `MarkdownLinkTest` 13, `WebLoaderTest` 7, `OpenDocumentsTest`의 웹 문서 1, `:code` 린트 경고 11 그대로): 링크 해석 — 상대·하위·`../`·`/`(프로젝트는 루트, 단일 파일은 절대 경로)·퍼센트 인코딩·쿼리 버림·조각 유지·루트 위 `..`와 NUL 거부, `content://` 기준은 무시, https `.md`(대소문자, 쿼리·조각 무시), GitHub blob → raw, https·http·mailto는 기본 앱, 그 밖의 스킴은 무시, 웹 문서 기준의 상대 링크. 웹 문서 받기 — 로컬 HTTP 서버로 `text/html` 거부, 상한 초과는 크기 안내, 404·시간 초과는 문구. `keyOf` — 같은 주소는 한 파일. `:code` 린트에 새 경고가 없다.
- ✅ vitest(2026-10-15, `anchors.test.ts` 10, `diff.test.ts`의 읽기 전용 ③): 제목 앵커가 GitHub 규칙과 같다(한글, 문장부호, 공백, 중복 `-1`). 앵커에서 원본 줄 찾기, 못 찾으면 맨 위.
- ✅ 탭, 이 맥의 프로젝트 레포에서(2026-10-15, adb 탭과 DevTools로 확인. 이미지는 "텍스트 파일이 아닙니다", 폴더는 "…은(는) 폴더입니다", 없는 파일은 "…이(가) 없습니다"): 같은 폴더·하위 폴더·`../`·`/` 링크의 `.md`가 묻지 않고 viewer로 열린다. `.py` 링크는 코드 viewer, 이미지 링크는 바이너리 안내, 폴더 링크는 폴더 오류, 없는 파일은 오류 문구. `#제목`은 같은 문서의 그 제목으로, `a.md#제목`은 열고 그 제목으로 간다(한글 제목 포함). 오른쪽 가장자리에서 밀면 링크를 누르기 전 자리로 돌아온다.
- ✅ 탭(2026-10-15, 단일 파일은 옆·`../`·절대 경로, `content://`는 MediaStore 주소로): 단일 파일(프로젝트가 아닌 폴더)과 로컬 파일(`skiffcode:///storage/…`)에서도 상대 링크가 열린다. `content://`로 연 문서의 상대 링크는 아무것도 하지 않고 `#제목`은 된다.
- ✅ 탭(2026-10-15, markdown-it README와 그 안의 `docs/usage.md`, 없는 raw 주소는 "…이(가) 없습니다", `mailto:`는 Gmail): 공개 레포의 README(raw 주소, GitHub blob 주소 둘 다)가 앱에서 읽기 전용으로 열리고 ③이 레이어를 바꾸지 않는다. 그 안의 상대 `.md` 링크도 앱에서 열린다. 같은 주소를 두 번 열어도 열린 파일은 하나다. 다른 https 링크는 기본 브라우저로, `mailto:`는 메일 앱으로 간다.
- ✅ 폴드8 커버 화면(2026-10-15, Tailscale adb, 폭 475px에서 18×28px 링크를 `input tap`으로): 상대 링크 하나와 https `.md` 하나를 탭해 연다.

- [x] 링크 해석: 순수 함수와 테스트
  - 새 파일(예: `intent/MarkdownLink.kt`): 기준 문서(`OpenRequest`의 종류와 경로, 프로젝트 루트 또는 null, 웹 문서면 주소)와 `href` 원문을 받아 — 파일 열기(경로, 조각), 웹 문서(주소, 조각), 기본 앱(Uri), 무시 중 하나를 낸다. 경로 정규화는 `FsPath`를 쓰되 `..`를 직접 접는다(`ProjectTree.resolve`는 `..`를 거부하므로 쓰지 않는다).
  - GitHub 변환은 `github.com/<o>/<r>/blob/<나머지>` 하나만. 그 밖의 GitHub 주소(`/tree/`, 저장소 첫 화면)는 `.md`가 아니므로 기본 앱으로 간다.
  - 테스트: 성공 조건 JVM의 해석 목록을 그대로.
- [x] 상대 링크와 제목
  - 페이지: `markdown.ts`의 클릭이 `preventDefault`를 늘 하고, `#`만이면 페이지 안에서 제목으로(점프 적기 → `goToLine`), 아니면 `openLink {id, href}`를 보낸다. `href`는 `link.href`(풀린 것)가 아니라 `getAttribute('href')`.
  - 제목 앵커: 렌더할 때 맨 위 제목 블록에 앵커를 계산해 두는 함수, 앵커 → 원본 줄. vitest.
  - Kotlin: `bridge.method("openLink")` — 문서 id로 기준을 찾고 해석 함수를 거쳐, 파일이면 그 문서와 같은 종류·같은 프로필의 `OpenRequest`로 `openFlow().open(…, pathChosen = true)`, 기본 앱이면 `openExternally`. `OpenAt`에 조각을 더하고, 문서 상태로 페이지에 넘겨 페이지가 줄을 찾는다.
  - 프로젝트 안의 파일은 지금 여는 흐름이 그 프로젝트를 찾아 붙인다(따로 할 것 없음 — 확인만).
  - 문서: `security.md`의 브리지 링크 줄과 "원격 파일 내용" 줄, `AGENTS.md`의 "Links that arrive from outside" 끝 문단(넓힌 것이 하나 늘었다), `layers.md`의 마크다운 줄에 이 주제로 가는 링크.
- [x] https 마크다운(웹 문서)
  - `OpenRequest.Web(url)`: `confirmPath`에 한 줄(바깥 링크로 만들 수 없어 묻지 않는다), `keyOf`는 주소, `linkOf`는 없음(최근 파일에 남기지 않는다).
  - 받기(새 파일, 예: `doc/WebLoader.kt`): `HttpURLConnection`, https만, 시간 제한, `text/html` 거부, 상한과 인코딩은 `TextLoader`의 규칙. 테스트는 JDK의 `com.sun.net.httpserver.HttpServer`로(https가 아니므로 받기 함수가 스킴 검사를 밖에서 받게 둔다).
  - 읽기 전용: 문서 상태에 `readOnly`를 더하고 페이지의 ③이 editor·diff를 건너뛴다(diff를 건너뛰는 지금 규칙 옆에). 저장·감시·git·LSP는 붙이지 않는다.
  - 열린 파일 메뉴와 팔레트에 호스트와 경로로 보인다.
- [x] 확인: 탭과 폴드8
  - 이 맥에 임시 git 레포: 여러 폴더의 `.md`, 각 종류의 링크(같은 폴더, 하위, `../`, `/`, `#제목`, `a.md#한글 제목`, `.py`, 이미지, 폴더, 없는 파일, https raw, GitHub blob, 일반 https, `mailto:`)를 담은 `index.md`. 탭에서 프로젝트로, 프로젝트를 지우고 단일 파일로 연다. 끝나면 레포·프로젝트를 지운다.
  - 로컬 파일은 같은 `.md` 몇 개를 기기의 `Documents/`에 `adb push`하고, 끝나면 지운다. `content://`는 파일 앱에서 공유로 연다.
  - 웹 문서는 공개 레포(예: markdown-it)의 README로. 기기의 인터넷 연결이 필요하다.
  - 폴드8 커버 화면에서 링크 하나와 https `.md` 하나를 탭해 본다(작은 화면에서 링크가 눌리는지).
  - 탭(2026-10-15, USB, 탭에 있던 이 맥의 LAN 프로필로): 임시 레포 `skiff-m9-check`를 프로젝트로 열고 링크 종류마다 `input tap`(DevTools로 링크 위치를 읽어)으로 눌렀다. 단일 파일은 git 없는 폴더 하나, `content://`는 MediaStore 주소로 `am start -t text/plain`. 웹 문서는 markdown-it의 README로 — 배지 이미지는 CSP가 막았다(설계대로). 처음 연 주소에서 F2의 "같은 서버인가요?"가 떠 `같은 서버`로 답했다.
  - 폴드8(같은 날, Tailscale adb, 접힌 채 사용자가 잠금을 풀어 줌): 커버 화면(폭 475px)에서 로컬 파일의 상대 링크와 https `.md`를 눌렀다.
  - 끝나고: 탭의 store를 확인 전 백업으로 되돌림(`cmp`로 같음), 임시 레포 둘과 두 기기의 확인용 파일을 지움. 폴드8의 최근 파일에는 지운 확인용 파일이 남았다. `mailto:` 확인에서 연 Gmail 작성 창은 보내지 않고 나왔다.

### M10. 문서 여백과 scrolloff

읽을 spec: [`margins.md`](skiffcode.spec/margins.md). 키를 더하는 곳은 [`settings-theme.md`](skiffcode.spec/settings-theme.md)의 첫 항목만.

원래 요구사항은 [`my.next.spec.md`](my.next.spec.md)의 "feat: 01". 사용자 결정(2026-10-20): scrolloff는 아래만, 문서 끝의 빈 자리는 scrolloff와 같은 줄 수(`bottom_margin`은 두지 않는다), 오른쪽 여백은 px 기본 72(64로 정했다가 버튼이 56px인 것을 기기에서 보고 고쳤다), 세 코드 레이어에 같게 두고 마크다운 viewer는 예외. 오른쪽 여백은 커밋하기 전에 M11의 `sidescrolloff`로 바뀌었다.

지금(코드로 확인): `pane.ts`의 테마가 `.cm-content`에 `paddingTop: var(--topbar-space)`만 둔다. 오른쪽·아래 여백은 없고, `keepCaretVisible`은 `yMargin: TOPBAR_SPACE`로 위아래를 같이 비운다. 설정 compartment(`settings.ts`의 `prefs`)에는 탭 크기와 줄바꿈이 든다.

**성공 조건**

- ✅ (2026-10-20, JVM 330·린트 경고 11 그대로) JVM: `scrolloff`·`right_margin`의 기본값, 범위 밖은 그 키만 기본값, 타입 오류는 전부 기본값, `KEYS`와 `Settings`가 같다 — 확인: `SettingsTomlTest`.
- ✅ (2026-10-20, vitest 190, `margins.test.ts`) vitest: 여백과 scrolloff의 계산(줄 수·줄 높이 → px, 0이면 없음) — 확인: 새 테스트.
- ✅ (탭, 2026-10-20, DevTools로 잼: 커서 아래 5.24줄(키보드 떠 있을 때도), 마지막 줄 아래 5.12줄, 40px로 확대해도 5.08줄, 줄바꿈된 글자 끝 1077.6px < 버튼 왼쪽 1080px, 세 레이어에서 맨 위 줄 140 그대로, 마크다운 여백 그대로, 저장하면 0/0으로 바로 바뀜. diff 레이어는 ± 거터 17px만큼 글자 폭이 좁다 — 여백 때문이 아니라 원래 있던 차이다) 탭: editor에서 화면 맨 아래 줄을 편집하면 커서 아래에 5줄이 남는다(소프트 키보드가 떠 있을 때 포함). 문서 끝 아래에 빈 자리가 있어 마지막 줄도 그렇다. 오른쪽 여백으로 줄바꿈된 글자가 커맨드 버튼에 가리지 않는다. viewer·editor·diff를 오가도 줄바꿈과 맨 위 줄이 그대로다. 마크다운 viewer는 지금과 같다. 설정을 저장하면 바로 바뀐다.
- ✅ (폴드8 커버 화면, 2026-10-20, 글꼴 배율 1.5·`wrap = false`, DevTools로 잼: 처음엔 마지막 줄 아래가 3.3줄이라 `em`을 `lh`로 고친 뒤 5.12줄, 커서 아래 5.17줄(키보드가 떴을 때 6.79줄 — adb 방향키를 보낸 뒤로는 키보드가 다시 뜨지 않아 한 번만 봤다), viewer↔editor 맨 위 줄 3001 그대로, 오른쪽 끝까지 밀면 글자 끝이 버튼 왼쪽보다 안쪽. 고친 뒤 탭에서도 다시 5.12줄) 폴드8 커버 화면: 위의 탭 확인을 한 번 더.

- [x] 설정 키
  - `Settings.Editor`에 `scrolloff: Int = 5`, `@SerialName("right_margin") rightMargin: Int = 72`. `SettingsToml`에 범위 `SCROLLOFFS = 0..50`, `RIGHT_MARGINS = 0..400`, `read`의 `inRange` 두 줄, `KEYS`의 설명 두 줄(줄 단위·px 단위를 설명에 적는다).
  - `MainActivity.pageSettings`가 `scrolloff`, `rightMargin`을 싣는다. 페이지의 `Settings` 인터페이스에도.
  - 테스트: `SettingsTomlTest`에 기본값·범위 밖·`KEYS` 일치.
- [x] 페이지: 여백과 scrolloff
  - `settings.ts`의 `Shown`과 `bundle`에 둘을 더한다: `.cm-content`의 `paddingRight`(px)와 `paddingBottom`(`${scrolloff}lh` — `em`은 `textZoom`이 곱해지지 않아 폴드8에서 모자랐다)을 담은 `EditorView.theme`, `EditorView.scrollMargins.of(view => ({ bottom: scrolloff × view.defaultLineHeight }))`. 테마로 두는 까닭은 CSS 변수만 바꾸면 짧은 문서가 다시 재지 않기 때문이다(`AGENTS.md`의 `codeFontSize`).
  - `pane.ts`의 `keepCaretVisible`이 아래에도 같은 줄 수를 남기는지 본다(`scrollMargins`가 더해지므로 따로 할 일이 없을 것이다).
  - 계산은 순수 함수로 빼서 vitest(`margins.test.ts`).
  - 거슬리면 바꿀 것: hover 툴팁이 아래 N줄을 피한다, 끌어 선택하면 아래 N줄 안에서 자동 스크롤이 시작된다(`margins.md`의 "어떻게 거는가"). 그러면 `EditorView.scrollHandler`로 커서 스크롤에만 건다.
- [x] 확인: 탭과 폴드8 커버 화면
  - 성공 조건의 탭 항목을 차례로. 핀치로 크게 한 뒤에도 아래 5줄이 남는지, `wrap = false`에서 오른쪽 끝까지 스크롤되는지.
  - `settings.toml`에서 `scrolloff = 0`, `right_margin = 0`이면 지금과 같은지.
  - 확인 전 탭의 `settings.toml`을 백업하고 끝나면 되돌린다.

### M11. 가로 scrolloff (`sidescrolloff`)

읽을 spec: [`margins.md`](skiffcode.spec/margins.md)의 오른쪽 항목과 "어떻게 거는가". 키를 바꾸는 곳은 [`settings-theme.md`](skiffcode.spec/settings-theme.md)의 첫 항목만.

M10의 `right_margin`을 바꾼다. 사용자 결정(2026-10-20): 단위는 글자 수, 편집할 때 scrolloff처럼 가로로 스크롤, 오른쪽만, 기본 5, `wrap = true`이면 여백도 스크롤도 없다, 이름은 vim을 따라 `sidescrolloff`.

지금(코드로 확인): `settings.ts`의 `bundle`이 줄바꿈과 상관없이 `.cm-content`에 `paddingRight: ${rightMargin}px`를 두고, `scrollMargins`에는 `bottom`만 있다. Kotlin은 `[editor] right_margin`(0~400, 기본 72)을 읽어 `rightMargin`으로 넘긴다. M10이 아직 커밋되지 않아 `right_margin`은 어느 버전에도 나간 적이 없다 — 옮겨 줄 사람 파일이 없으니 키를 바꾸기만 한다.

**성공 조건**

- ✅ (2026-10-20, JVM 330·린트 경고 11 그대로) JVM: `sidescrolloff`의 기본값, 범위 밖은 그 키만 기본값, `KEYS`와 `Settings`가 같다, `right_margin`은 모르는 키로 읽힌다(`SettingsTomlTest`). `:code` 린트에 새 경고가 없다.
- ✅ (2026-10-20, vitest 193) vitest: 글자 수와 글자 폭 → px, `wrap`이 켜지면 여백과 스크롤 여백이 0.
- ✅ (탭, 2026-10-20, 307·431글자짜리 줄을 버퍼에 잠시 넣고 adb 방향키·`input text`로, DevTools로 잼: 방향키로 오른쪽 끝에 닿으면 5.60글자, 타이핑하면 6.91글자, 줄 끝 5.26글자, 40px로 확대해 줄 끝 5.07·줄 중간 5.22글자, 왼쪽으로 돌아갈 때는 예전처럼 5.6px. 넣은 줄은 지웠고 `settings.toml`은 md5로 되돌렸다) 탭, `wrap = false`: 긴 줄 끝 쪽으로 커서를 옮기거나 타이핑하면 커서 오른쪽에 5글자가 남게 가로로 스크롤된다. 가장 긴 줄 끝에서도 5글자가 남는다. 핀치로 크게 해도 5글자다. 왼쪽으로 돌아갈 때는 지금과 같다.
- ✅ (탭, 2026-10-20, 임시 git 레포: `padding-right` 0, 줄바꿈된 글자가 1136px까지(화면 1152px), viewer·editor·diff에서 맨 위 줄 140과 긴 줄의 3줄 접힘이 그대로, 아래 105px 그대로) 탭, `wrap = true`: 오른쪽 여백이 없다(글자가 화면 오른쪽 끝까지 간다). viewer·editor·diff를 오가도 줄바꿈과 맨 위 줄이 그대로다. 세로 scrolloff(아래 5줄)는 M10 그대로다.
- ✅ (폴드8 커버 화면, 2026-10-20, 글꼴 배율 1.5·`wrap = false`, `near2mb.ts`의 가장 긴 줄(75글자), adb 방향키, DevTools로 잼: `padding-right` 63px = 5 × 12.6px, 오른쪽 끝에 닿으면 5.39글자, 줄 끝 5.14글자, 끝까지 밀어도 가장 긴 줄 뒤 5.14글자) 폴드8 커버 화면(글꼴 배율 1.5, 사용자 설정이 `wrap = false`): 커서 오른쪽 5글자, 가장 긴 줄 끝 5글자.

- [x] 설정 키
  - `Settings.Editor`의 `rightMargin`을 `sidescrolloff: Int = 5`로, `RIGHT_MARGINS`를 `SIDESCROLLOFFS = 0..100`으로, `KEYS`의 설명을 "글자 수, `wrap = true`이면 쓰지 않는다"로. `MainActivity.pageSettings`는 `sidescrolloff`를 싣는다.
  - 테스트: `SettingsTomlTest`의 `right_margin` 자리를 `sidescrolloff`로, `right_margin = 72`가 `UnknownKey`로 읽히는 줄 하나.
- [x] 단위 재기(폴드8이 잠겨 있어 탭의 글꼴 배율을 1.5로 올려 쟀다 — `ch`가 따른다, `margins.md`): `.cm-content` 안에 `10ch` 상자를 두고 `view.defaultCharacterWidth × 10`과 견준다. 같으면 `padding-right`는 `Nch`, 다르면(`em`처럼 3분의 2) `EditorView.contentAttributes`에 함수를 주어 `defaultCharacterWidth`로 px를 적는다. 결과를 `margins.md`에 적는다.
- [x] 페이지
  - `settings.ts`의 `Shown`에 `sidescrolloff`, `bundle`에서 `wrap`이면 오른쪽은 없음, 아니면 `padding-right`와 `scrollMargins`의 `right: sidescrolloff × view.defaultCharacterWidth`. `bottom`(scrolloff)은 그대로 둔다.
  - 계산은 순수 함수로 빼서 `margins.test.ts`에.
  - `pane.ts`의 `keepCaretVisible`(키보드가 뜰 때)은 `y`만 묻는다. 가로도 묻는지 보고, 아니면 그대로 둔다(키보드는 높이만 바꾼다).
- [x] 확인: 탭과 폴드8 커버 화면
  - 성공 조건의 기기 항목을 차례로. 탭은 `wrap = false`로 바꿔 보고 되돌린다(확인 전 `settings.toml` 백업, 끝나면 md5로 맞춘다).
  - 폴드8은 사용자가 실제로 쓰는 기기라 설정을 바꾸지 않는다 — 이미 `wrap = false`다.

### M12. 오른쪽 네비게이션 바

읽을 spec: [`nav-bar.md`](skiffcode.spec/nav-bar.md). 표시의 출처는 [`git.md`](skiffcode.spec/git.md)의 "git 거터", 오른쪽 여백은 [`margins.md`](skiffcode.spec/margins.md)의 오른쪽 항목, 키를 더하는 곳은 [`settings-theme.md`](skiffcode.spec/settings-theme.md)의 첫 항목만.

원래 요구사항은 [`my.next.spec.md`](my.next.spec.md)의 "오른쪽에 가는 막대 네비게이션 바". 사용자 결정(2026-10-21): git 변경과 LSP 진단을 그린다, 보이는 구간 상자를 둔다, viewer·editor·diff에 두고 마크다운 viewer는 뺀다, `settings.toml`의 기본은 끔. 터치를 받지 않는 것은 요구사항이다. `nav-bar.md`에 "제안"으로 적은 것(오류·경고만, 왼쪽 git·오른쪽 진단, 폭 6px 안팎, 표시가 없어도 상자는 있음)은 기기에서 보고 사용자에게 확인받는다.

지금(코드로 확인): `gitGutter.ts`의 `baselineField`가 기준과 청크를 들고, `gitMarks`가 표시할 줄을 낸다(`gitMarksIn`은 테스트용). 진단은 `@codemirror/lint`의 상태에 있고 `lsp.ts`가 `forEachDiagnostic`으로 읽는다. 설정 compartment(`settings.ts`의 `bundle`)에 탭 크기·줄바꿈·scrolloff·sidescrolloff가 든다. Kotlin은 `settings/Settings.kt`의 `Settings.Editor`와 `SettingsToml.KEYS`, `MainActivity.pageSettings`다.

**성공 조건**

- ✅ (2026-10-21, JVM 331·`:code` 린트 경고 11 그대로) JVM: `nav_bar`의 기본값(끔), 타입이 틀리면 전부 기본값, `KEYS`와 `Settings`가 같다 — 확인: `SettingsTomlTest`. `:code` 린트에 새 경고가 없다.
- ✅ (2026-10-21, vitest 208 — `navBar.test.ts` 13, `margins.test.ts`에 2) vitest: 표시를 막대의 픽셀 줄로 바꾸는 계산(git 추가·수정·삭제, 진단 오류·경고, info·hint는 없음, 같은 픽셀 줄은 하나로, 빈 문서와 한 줄 문서), 보이는 구간 상자의 위치와 높이 — 확인: 새 테스트 `navBar.test.ts`.
- ✅ (탭, 2026-10-21, 이 맥의 임시 레포 `skiff-navbar-check`(485줄 `check.py`: 위·가운데의 수정과 추가, 4줄 삭제, 끝의 추가, pyright 오류 2·경고 2)를 프로젝트로, DevTools로 캔버스의 픽셀을 읽어 `lineBlockAt` 비율과 견줌: 151줄 예상 191.8 → 191–193px(왼쪽 추가·오른쪽 경고), 삭제 304.9 → 305–307, 332줄 419.2 → 419–421(오류가 같은 줄의 경고를 이김), 끝 606.4 → 607–612. 100줄에 한 줄을 넣으면 예상 127.5 → 127–129px에 바로 생기고 지우면 사라짐. 상자: scrollTop 5000 → 299.1px, 끝에서 막대 바닥과 맞닿음, 높이 40.4px(= 675/10346 × 619). Zoom In 두 번(14 → 18px)에도 표시 위치 그대로, 상자 31.4px. editor·diff(지운 줄 위젯으로 문서가 168px 길어진 것까지 반영) 둘 다 막대가 있고, 렌더된 마크다운 viewer에서는 숨음. 막대 자리 `input tap 2301 756` → editor 커서가 그 높이의 15줄로. 링크로 300줄에 간 뒤 오른쪽 가장자리 `input swipe` → 뒤로 가기가 그대로 되고 앱이 남음. `wrap = false`: 긴 줄 끝에서 막대 왼쪽까지 5.25글자. `wrap = true`: 줄바꿈된 글자 끝 1136.4px < 막대 왼쪽 1146px) 탭, `nav_bar = true`, git 프로젝트의 파일: 맨 위·가운데·맨 끝의 변경이 막대의 같은 비율 높이에 보이고, 스크롤해 가 보면 git 거터의 표시와 맞는다. LSP 오류·경고가 보인다. 편집하면 막대가 바로 바뀐다. 보이는 구간 상자가 스크롤과 핀치를 따른다. viewer·editor·diff에 있고 마크다운 viewer에는 없다. 막대를 탭하거나 끌면 문서가 받고, 오른쪽 가장자리의 뒤로 가기 제스처는 그대로다. 글자가 막대에 가리지 않는다(`wrap` 양쪽).
- ✅ (탭, 2026-10-21, 앱 안의 `Open Settings`에서 고쳐 `Save File`: 끔 → 막대 0개·`padding-right` 0px, 켬 → 1개·6px) 탭: `nav_bar = false`(기본)이면 지금과 같고, 저장하면 바로 켜지고 꺼진다.
- ✅ (탭, 2026-10-21, 1.9MB·19,503줄 `big.ts`에서 50줄마다 바뀐 389줄, 끔/켬을 두 번씩 번갈아: 프레임마다 40px 스크롤 3초 — 끔 80.8·81.0fps, 켬 81.5·83.2fps, p95 간격 모두 22.1ms. 글자 30개를 하나씩 넣고 그리는 프레임까지 — 중앙값 끔 11.4·11.1ms, 켬 11.1·11.0ms. M0의 86–91fps는 손가락 플링으로 잰 것이라 이 값과 방법이 다르다. 변경이 1.6px 간격이라 git 칸은 한 줄기로 그려졌다) 탭, 2MB 파일: 켠 채로 스크롤이 M0(86–91fps)와 비슷하고, 타이핑이 느려지지 않는다 — DevTools로 잰다.
- ✅ (탭, 2026-10-21, 사용자 요청으로 더함. `big.ts` 가운데에서 `input swipe` 직후 `screencap`: 끔 → x≈1140에 시스템 스크롤바, 켬 → 없고 막대의 상자만. `scrollbar-width: none`과 `::-webkit-scrollbar`는 둘 다 안 먹어(`CSS.supports` false, Chrome 155) 화면 밖으로 밀어 자르는 방법으로 했다. 처음엔 `overflow: hidden`이라 `wrap = false`에서 `.cm-editor`가 24px 밀려(`scrollLeft` 24) 커서 오른쪽이 8.06글자였고, `clip`으로 바꾼 뒤 5.21글자·`scrollLeft` 0. `wrap = true`에서 줄바꿈된 글자 끝 1128px < 막대 1146px, `padding-right` 30px. 끄면 `margin-right` 0·`overflow: visible`로 돌아오고 스크롤바가 다시 보임. `settings.toml`은 md5로 되돌렸다) 탭: `nav_bar = true`이면 스크롤하는 중에도 브라우저의 세로 스크롤바가 보이지 않고, 끄면 다시 보인다. 글자는 여전히 막대에 가리지 않고(`wrap` 양쪽), `wrap = false`의 커서 오른쪽은 막대 앞까지 5글자다.
- ✅ (폴드8 커버 화면, 2026-10-21, 사용자 허락을 받고 Wi-Fi adb로. 사용자가 이미 `nav_bar = true`로 켜 두어 `settings.toml`은 바꾸지 않았다(앞뒤 md5 같음). 글꼴 21px·`wrap = false`, 485줄 파일을 링크로 "파일만 열기": 스크롤러 오른쪽 499px(화면 475 + 24), `padding-right` 93px(= 5 × 12.6 + 30). 가운데(scrollTop 7461)에서 `input swipe` 직후 `screencap`: 밀어 내기를 페이지에서만 되돌리면(`margin-right` 0·`overflow: visible`) 오른쪽 끝에 시스템 세로 스크롤바, 그대로 두면 없고 막대의 상자만. 120줄(302글자) 끝에 커서 → 막대 앞까지 5.15글자, `.cm-editor`의 `scrollLeft` 0. 화면 오른쪽 위의 회색 손잡이는 삼성 엣지 패널(`CocktailBarService`)이라 파일을 열기 전에도 같은 자리에 있다) 폴드8 커버 화면: 위와 같이 스크롤바가 보이지 않는다.
- ✅ (폴드8 커버 화면, 2026-10-21, 사용자 허락을 받고 Wi-Fi adb로, 글꼴 배율 1.5·21px·`wrap = false`·사용자 테마 `ocean copy`: 막대 왼쪽 469.4px·폭 6·높이 638px, 커맨드 버튼 오른쪽 459.4px로 10px 떨어짐. 151줄 예상 196.9 → 197–199px, 332줄 431.7 → 431–433, 끝 626.3 → 626–631. 사용자 테마에 `warning`이 없어 base의 `#d29922`로 그려짐. 긴 줄 끝에서 막대 왼쪽까지 5.15글자, `padding-right` 69.0px(= 5 × 12.6 + 6). 상자 scrollTop 7000 → 288.3px, 끝에서 막대 바닥과 맞닿음. `settings.toml`은 md5로, 화면 꺼짐은 300000으로 되돌렸다) 폴드8 커버 화면(글꼴 배율 1.5, `wrap = false`): 막대가 보이고 글자를 가리지 않는다. 사용자 기기라 `nav_bar`를 켜는 것은 사용자에게 묻고, 끝나면 되돌린다.

- [x] 설정 키
  - `Settings.Editor`에 `@SerialName("nav_bar") navBar: Boolean = false`, `SettingsToml.KEYS`에 설명 한 줄("오른쪽 막대에 문서 전체의 git 변경과 진단, 보이는 구간을 그린다. 마크다운 viewer에는 없다"). `MainActivity.pageSettings`가 `navBar`를 싣고, 페이지의 `Settings` 인터페이스에도 더한다.
  - 경고 색: 테마에 `[syntax] warning`을 더했다(2026-10-21 사용자 결정). `ThemeToml.KEYS`, 번들 두 벌, `index.html`의 기본 라이트 색.
  - 테스트: `SettingsTomlTest`에 기본값·타입 오류·`KEYS` 일치.
- [x] 페이지: 표시 계산(순수 함수)
  - 새 `navBar.ts`. 입력은 git 표시(`gitMarks`의 `[pos, mark]`), 진단(`forEachDiagnostic`의 `from`과 `severity`), 위치 → 높이 함수, 문서 높이, 막대 높이. 출력은 픽셀 줄마다 하나의 표시(git 칸·진단 칸 따로, 오류 > 경고, 삭제·수정·추가의 차례는 정해서 적는다). 보이는 구간 상자는 `scrollTop`·`clientHeight`·`scrollHeight` → 상자의 위·높이(최소 높이를 둔다).
  - `gitGutter.ts`에서 기준의 표시를 상태로 꺼내는 함수(`gitMarksIn`과 같은 것)를 막대도 쓰게 한다 — 따로 다시 계산하지 않는다.
  - vitest `navBar.test.ts`: 성공 조건의 vitest 항목 전부.
- [x] 페이지: 막대 그리기
  - `ViewPlugin`이 `view.dom`에 `<canvas>`와 상자 `<div>`를 둔다(`pointer-events: none`, `right: 0`, 위는 `--topbar-space`, 아래는 `--banner-space`). `devicePixelRatio`만큼 캔버스를 키워 흐리지 않게 한다.
  - 다시 그리는 때: `docChanged`·`geometryChanged`·기준이 바뀜·진단이 바뀜(lint 상태 필드가 바뀐 트랜잭션)·크기 바뀜. 읽기는 `requestMeasure`의 `read`(`lineBlockAt`, `contentHeight`), 그리기는 `write`. 스크롤에는 상자만 `transform`으로 옮긴다.
  - 색은 `getComputedStyle`로 읽고, `settingsChanged`(테마)에 다시 읽는다.
  - `settings.ts`의 `bundle`에 `navBar`가 켜지면 플러그인과, `.cm-content`의 `padding-right`·`scrollMargins.right`에 막대 폭을 더하는 것을 넣는다(`wrap`이면 막대 폭만). 계산은 `margins.test.ts`에 더한다.
  - 마크다운 viewer에서는 CodeMirror가 숨으므로 막대도 같이 숨는지 본다(`view.dom` 안에 있으면 따라 숨는다).
- [x] 확인: 탭과 폴드8 커버 화면
  - 확인 전 탭의 `settings.toml`을 백업하고 끝나면 md5로 되돌린다. 임시 git 레포에 맨 위·가운데·맨 끝을 바꾼 파일과, pyright 오류·경고가 나는 Python 파일을 둔다.
  - 성공 조건의 탭 항목을 차례로: 막대와 거터의 높이 비교는 DevTools로 표시의 `lineBlockAt(pos).top / contentHeight`와 캔버스의 픽셀을 견준다. 탭으로 문서가 받는지는 막대 자리를 adb로 탭해 커서가 옮겨지는지(editor)로 본다.
  - 2MB 파일(`near2mb.ts`)에서 켜고 끈 채로 스크롤 fps와 키 입력 한 번의 `update` 시간을 잰다. 느리면 진단·git 표시를 픽셀 줄로 묶는 것을 디바운스한다.
  - 폴드8은 사용자에게 `nav_bar`를 켜도 되는지 묻고, 끝나면 되돌린다.
  - 탭과 폴드8 모두 끝났다(성공 조건, 스크롤바 숨김 포함). 임시 레포 `~/skiff-navbar-check`와 탭·폴드8 앱의 그 프로젝트는 지웠다(폴드8 것은 사용자가). 스크롤바 확인 때 다시 만든 같은 레포도 지웠고, 폴드8에서 연 파일은 닫았다.
  - "제안"으로 적은 네 가지(폭 6px, 왼쪽 git·오른쪽 진단, info·hint 뺌, 표시 없어도 상자)는 사용자가 그대로 두기로 했다(2026-10-21). `nav-bar.md`에서 "제안"을 지웠다.

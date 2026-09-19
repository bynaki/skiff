# 인수인계

다음 세션이 이어받기 위한 문서다. **세션을 끝낼 때마다 이 파일을 현재 상태로 덮어쓴다.**
계속 쌓는 기록이 아니다. 지난 기록은 git 히스토리에 남는다.

- 할 일 목록과 설계: [`plan.md`](plan.md)의 `# Skiff Code` 섹션
- 규칙, 툴체인, 커밋 전 점검, 보고와 알림 규칙: [`AGENTS.md`](AGENTS.md)
- 이 문서에 담는 것: 위 두 문서에 없는 **직전 세션의 맥락**(무엇을 했고, 왜 그렇게 정했고, 무엇이 아직 확인되지 않았는지)

---

## 마지막 세션 (2026-09-19 새벽): M2 여섯째 항목 viewer 레이어 완료

### 지금 상태
- 브랜치 `plan/skiffcode`(git worktree). **push하지 않은 커밋이 있다**: 지난 세션의 `ef1e6d7`, `dfb5434`,
  `02842c9`와 이번 세션의 viewer 커밋, 이 인수인계 커밋. main에는 합치지 않았다.
- **다음은 M2 일곱째 항목, 상단 메뉴 ①~⑤ 자리와 스크롤에 따른 숨김/표시다**(`plan.md`의 첫 `- [ ]`).
- 모듈은 셋이다: `:core`(라이브러리), `:app`(Skiff), `:code`(Skiff Code). 무엇이 어디 있는지는
  `AGENTS.md`의 "Three modules"에 있다. **`:code`의 페이지는 이제 viewer다**(`web/src/main.ts`,
  `layers/viewer.ts`, `markdown.ts`, `zoom.ts`, `bridge.ts`). M0 스파이크로 남은 것은 `diff.ts`와 `lsp.ts`뿐이고
  페이지가 불러오지 않는다(M5·M6이 다시 쓴다).
- 테스트: `:core` 35개, `:app` 24개, `:code` 81개(이번에 `TextLoaderTest`에 3개 추가). 실패 없음.
  이번 세션은 `:code`만 돌렸다(`:core`, `:app`은 건드리지 않았다).
- lint 경고: `:code` **11개**. 10개는 지난 세션과 같은 종류이고, 새로 `Recycle` 1개가 `OpenFlow`의
  `openInputStream`에 뜬다. 스트림은 `use`로 닫히는데 lint가 `withContext` 너머를 못 보는 오탐이다.

### 이번 세션에서 한 것과 내린 판단
**흐름.** `OpenFlow`가 여는 데서 멈추지 않고 `TextLoader`로 읽는다. `Opened`에 `result: LoadResult`와
`line`이 붙었다. 원격은 세션의 `SftpFileSystem`, 로컬은 `LocalFileSystem`, `content://`는 새
`TextLoader.load(Source)`(크기는 읽은 만큼, mtime은 0 — M3 `FileWatcher`가 `content://` 폴링 방법을 정할 때 볼 것).
`MainActivity`가 문서 상태(JSON)를 들고, 페이지는 `document` RPC로 가져가며 `documentChanged` 알림을
받으면 다시 묻는다. 핸들러가 멈추지 않아 응답이 요청 순서대로 나가므로 마지막 응답이 늘 현재 문서다.
- 읽는 중의 실패(연결, 권한)는 지금처럼 **네이티브 대화상자**다. 읽었지만 못 보여 주는 것(`TooLarge`,
  `Binary`, `UnknownEncoding`)만 페이지에 안내로 나온다. **페이지의 글은 전부 Kotlin 문자열 리소스**
  (영어, 한국어)에서 온다. 웹 쪽에 번역 테이블을 따로 두지 않으려는 것이다.
- 링크 파서의 거부 이유(영어 그대로)는 페이지가 아니라 대화상자에 나오는 것이라 이번에 손대지 않았다.
- "불러오는 중" 상태는 없다. 원격에서 읽는 동안 이전 화면이 남는다.

**viewer.** 언어는 `@codemirror/language-data` 6.5.2로 **파일 이름**에서 찾고, 파서는 언어마다 청크로
나뉘어 처음 필요할 때 불러온다(번들 1.8MB). 이름이 마크다운이면 `markdown-it` 15.0.2(`html: false`,
타입 내장)로 렌더링한다. 원문 보기는 M3 editor의 몫이다. 문서를 바꿀 때마다 `EditorView`를 새로
만든다 — 하나의 뷰에 파일별 `EditorState`를 두는 것은 M3 항목이다.
- `?line=`: 코드는 그 줄을 맨 위로, 마크다운은 그 줄 이전에서 시작하는 마지막 최상위 블록을 맨 위로
  (`markdown-it`의 `map`으로 `data-line`을 붙였다).
- 줌: `zoom.ts`를 `Hold`(제스처 시작 때 붙잡고, 크기를 바꾸며 되돌려 놓는 함수)로 일반화했다. 코드는
  M0의 `scrollIntoView` 방식 그대로, 마크다운은 손가락 아래 블록을 같은 비율 위치에 둔다. 글자 크기는
  하나를 같이 쓰고 문서를 바꿔도 유지된다. 마크다운 본문도 `--code-font-size`를 쓴다(테마는 M4).
- 마크다운 링크: 상대 링크와 `#조각`은 페이지에서 `preventDefault`한다. 브라우저로 넘기면
  `appassets…` 주소가 열리기 때문이다. 다른 파일로 가는 상대 링크는 아직 아무 일도 하지 않는다.
- 스타일은 밝은 색 하나다. 다크와 테마는 M4.
- Android 15의 edge-to-edge로 페이지가 상태 표시줄 밑에 그려져서, WebView를 `FrameLayout`에 넣고
  시스템 바 여백을 줬다(WebView 자신의 padding은 내용에 먹지 않는다). **상단 메뉴 항목은 이 여백
  안쪽에서 시작한다.**
- M0 스파이크의 Kotlin 쪽(`StubLsp`, `sampleText`, 실행 인자)과 페이지의 측정 코드는 지웠다.
- 상한 문구는 "2 MB"로 쓴다. `Formatter`는 1000 단위라 2MiB를 "2.1MB"로 보여 준다.

**실기기에서 찾아 고친 버그 — EUC-KR.** Android의 `EUC-KR` 디코더는 JVM보다 넓다. `A1 41`(UHC 확장
한글)을 U+C8A5로, `80`을 U+0080으로, `FF`와 사용자 정의 행 `FE xx`를 PUA 문자로 받는다. JVM은 전부
거부해서 **단위 테스트는 통과하는데 기기에서는 깨진 파일이 열렸다.** 이제 `TextLoader`가 디코딩 전에
EUC-KR 바이트 모양(ASCII, 또는 두 바이트 모두 A1..FE이고 C9·FE 행이 아닌 것)을 직접 본다. 기기에서
네 경우 모두 `UnknownEncoding`이 되고 정상 EUC-KR 파일은 그대로 열리는 것을 확인했다. `AGENTS.md`
툴체인 제약과 `plan.md` 설계에 적었다. (DevTools로 본 `C9 A1`→U+0261은 버그가 아니라 올바른 UTF-8이었다.)

**실기기 확인(탭, 로컬 파일과 `content://`):** 테스트 파일은 탭의 `Download/skiffcode-test/`에 있다.
Python·TS·Makefile 하이라이팅, 2MB TS를 30000번째 줄로 열기(읽고 여는 데 약 50ms), 마크다운의 raw HTML이
글자로 나오고 `javascript:` 링크가 링크가 되지 않고 외부 이미지가 CSP로 막힘, 3MB·바이너리·깨진 인코딩
안내, `content://media/external/file/<id>`로 EUC-KR 파일 열기. **adb로 링크를 실제로 탭해** https는 Chrome,
mailto는 Gmail로 가고 페이지는 남고, `intent:`는 버려지고 상대·조각 링크는 아무 일도 없었다.
핀치 줌은 DevTools 프로토콜 `Input.dispatchTouchEvent`로 두 손가락을 만들어 코드(줄 30016)와
마크다운(문단 61) 모두 손가락 아래가 그대로인 것을 봤다. **adb는 멀티터치를 못 만들지만 CDP는 된다.**
mailto 확인 때 Gmail 작성 화면이 열렸다가 보내지 않고 닫혔다. Gmail 임시보관함에 빈 메일이 남았을 수 있다.

### M2 넷째·다섯째 항목(`TextLoader`, 브리지)에서 내린 판단
**순서 질문의 답:** 지난 세션에서 답을 못 받은 질문에 사용자가 **"계획 순서대로"**를 골랐다.
`TextLoader` → 브리지 → viewer 다음에 Skiff 쪽(`onOpen`에서 `skiffcode://` 인텐트)을 연결한다.
그래서 Skiff에서 원격 파일을 탭하면 아무 일도 없는 것은 그 항목까지 그대로다(`openExternally`가
`SourceId.Local`이 아니면 돌아간다).

**`doc/TextLoader` (`ef1e6d7`)**
- `load(fs, path)`: `stat` → 상한(기본 2MB) 검사 → 상한+1바이트까지만 읽기 → `decode`. `FileSystem`
  위라 로컬과 원격이 같은 길이다. `content://`는 바이트를 직접 읽어 `decode(bytes, size, mtime)`에 넘긴다.
- **디코딩은 엄격하다.** UTF-8을 REPORT 모드로 시도하고, 실패하면 EUC-KR, 둘 다 실패하면
  `UnknownEncoding`으로 열지 않는다. 대체 문자로 채운 텍스트를 저장하면 원래 바이트가 덮어써진다.
  계획에는 없던 결과 값이라 `plan.md` 설계에 적었다.
- UTF-8 BOM은 떼고 기억한다(`TextFormat.bom`). 줄바꿈은 **첫 줄의 것**으로 정하고 텍스트는 `\n`으로
  바꿔 넘긴다. 섞인 파일은 저장하면 한 가지로 통일된다.
- 결과의 `size`, `modifiedEpochSeconds`는 읽기 전 `stat` 값이다. M3의 `DocumentSaver`가 충돌 비교에 쓴다.
- `:code` 테스트가 `:core`의 testFixtures(`SftpTestServer`)와 `slf4j-simple`을 쓰게 됐다(`:app`과 같은 방식).
- viewer 항목에서 `OpenFlow`에 연결했다. `content://`용으로 `load(Source)`가 생겼다.

**`bridge/WebBridge` + `bridge.ts` (`dfb5434`)**
- `method(name) { params -> result }`, `onNotify(name)`, `notify(method, params)`(아무 스레드에서나).
  오류 코드: 없는 메서드 -32601, `JSONException`은 -32602, 그 밖의 예외는 -32000(메시지 그대로).
  Web에서는 `RpcError(code, message)`로 받는다.
- **`ready` 규칙:** Kotlin은 페이지가 먼저 말해야 답할 통로(reply proxy)를 얻는다. 그래서 `bridge.ts`가
  로드되자마자 `ready` 알림을 보내고, 그 전에 Kotlin이 보낸 알림은 모아 뒀다가 순서대로 넘긴다.
  페이지를 다시 불러오면 새 `ready`의 통로로 바뀐다. **주의:** `ready` 직후 넘어온 알림은 그때까지
  `onNotify`로 구독하지 않은 Web 리스너에게는 전달되지 않는다. viewer는 그래서 페이지가 `rpc`로
  묻는 구조로 갔다.
- 메인 프레임에서 온 메시지만 받는다. `Log`는 JVM 테스트에서 스텁이라 로거를 생성자로 받는다.
- 보안: 자산 로더는 `/assets/web/`만 내주고 나머지 요청은 모두 빈 403. CSP는 `index.html`에 있다
  (`style-src`의 `'unsafe-inline'`은 그 파일의 `<style>` 때문). 링크는 http/https/mailto만 `ACTION_VIEW`로
  넘기고 `intent:` 등은 버린다. 자세한 규칙은 `plan.md` "보안 규칙".
- 스파이크의 `sampleText`와 스텁 LSP는 viewer 항목에서 지웠다.
- 실기기 확인: LSP 스파이크가 M0과 같은 수치(initialize 4ms, 진단 250ms, 재동기화 589ms). DevTools로
  외부 fetch·외부 이미지·인라인 스크립트·iframe이 CSP로 막히고, `web/` 밖 자산이 403이고,
  `location.href = 'https://…'`는 Chrome을 열고 페이지는 남고, `intent:` 링크는 버려지는 것을 봤다.
- DevTools에서 만든 `<a>`의 `click()`으로는 https 이동이 일어나지 않았지만(원인 모름), viewer에서
  **실제로 탭하면** Chrome으로 나간다. 확인 끝.

### 지난 세션(M2 첫~셋째 항목)에서 내린 판단
**`SkiffCodeUri`**
- 직접 파싱한다. `android.net.Uri`는 유닛 테스트 JVM에서 스텁이고, `java.net.URI`는 인코딩되지 않은
  한글 경로를 거부한다.
- user는 없어도 된다(alias만으로 서버를 가리킬 수 있다). 포트 기본 22, host 소문자, IPv6 괄호 제거.
  `+`는 공백으로 바꾸지 않는다. 깨진 퍼센트 인코딩, UTF-8이 아닌 바이트, NUL은 거부한다.

**`SkiffCodeStore`와 `jsonDataStore`**
- Skiff와 같은 모양이고 파일은 따로다(`skiffcode.json`). Keystore 키가 앱마다 따로라 암호문을 공유할 수 없다.
- **읽을 수 없는 저장 파일은 `<이름>.corrupt-<밀리초>`로 복사해 두고 빈 데이터로 시작한다. 두 앱을
  같이 고쳤다**(사용자 지시, `:core`의 `jsonDataStore`). 전에는 첫 쓰기가 원래 파일을 덮어써서
  프로필과 호스트키를 모두 잃었고, 모든 서버가 "바뀐 서버"가 아니라 "처음 보는 서버"로 보였다.
  현실적인 원인은 `AuthMethod` 하위 클래스 이름 변경이다. 실기기의 Skiff에서 기존 `skiff.json`이
  그대로 읽히는 것을 확인했다. "초기화했다"는 알림은 아직 없다.

**인텐트와 여는 흐름**
- **alias가 맞으면 그 프로필의 host를 쓴다.** 링크가 아는 alias를 다른 기계로 돌릴 수 없다. 대신
  경로는 링크의 것이라, 링크를 받은 사람이 자기 서버의 임의 경로를 열게 된다. 읽기만 하는 동안은
  문제가 아니지만 editor가 생기면 다시 볼 것.
- user 없는 링크는 (host, port)가 프로필 하나에만 맞을 때만 쓴다. host 비교는 대소문자를 무시한다.
- 알 수 없는 서버의 확인창은 주소와 경로를 보여 주고 비밀번호를 받는다. "서버로 저장"은 **기본 켬**
  이다(링크로 여는 서버는 다시 열 가능성이 높다고 봤다). 끄면 프로세스가 끝날 때까지 메모리에만 둔다.
  링크에 user가 있으면 확인창에서 바꿀 수 없다.
- 대화상자는 WebView가 아니라 네이티브 `AlertDialog`다. 비밀번호와 호스트키 결정이 원격 내용을
  렌더링하는 페이지를 지나가지 않게 하려는 것이다. 문구는 영어와 한국어 리소스로 뒀다.
- 흐름은 `stat`과 최근 파일 기록에서 끝나고 토스트를 띄운다. viewer가 이어받는다.
- **파서의 거부 이유는 영어 그대로 대화상자에 나온다**(예: "a password in the link is not accepted").
  viewer에서 오류를 페이지에 보여 줄 때 현지화할 것.

**`SshClientFactory` 인증 (별도 커밋 `18347b5`)**
- 실기기 확인 중에 **틀린 비밀번호가 33초 뒤에야 실패하는 것**을 찾았다. sshj의 `authPassword`는
  `password`가 거부되면 같은 연결에서 `keyboard-interactive`를 다시 시도하는데, macOS sshd는 그
  두 번째 시도에 답하지 않는다. OpenSSH 클라이언트도 같은 순서면 멈추는 것을 `expect`로 확인했다.
  이제 `password`만 시도하고, 서버가 `password`를 아예 안 받을 때만 `keyboard-interactive`를 쓴다.
  Skiff도 같이 고쳐진다. 실기기에서 3초로 줄었다.

### M1에서 내린 판단
- **M1의 1번과 2번 항목은 같이 할 수밖에 없다.** `HostKeyGate`를 `:core`로 옮기려면 `SkiffStore` 의존을
  먼저 `KnownHostStore`로 끊어야 컴파일이 된다. 그래서 한 커밋에 넣었다.
- **`SftpTestServer`는 `:core`의 testFixtures로 내보냈다.** `SftpFileSystemTest` 19개 중 3개가
  `CopyEngine`(`:app`에 남는다)을 쓰고 있었다. 그 3개를 `:app`의 `SftpTransferTest`로 옮기고, 서버는
  fixture로 공유한다. 서버를 복사하면 두 사본이 달라진다.
- **`:core`는 okio, sshj, kotlinx-serialization을 `api`로 내보낸다.** 셋 다 `:core`의 시그니처에
  들어가 있어서, `:app`이 다시 선언하지 않아도 되게 했다. `:app`에 남긴 직접 의존은 BouncyCastle
  하나인데, `SkiffApplication`이 직접 provider를 등록하기 때문이다.
- **`SshClientFactory`는 접속·인증·keepalive만 갖는다.** 오류를 `FsError`로 옮기는 것은 `SshConnection`에
  남겼다. 재시도 판단이 거기 있기 때문이다.

### 실서버 확인 결과
사용자가 서버 프로필을 등록해 줘서 탭에서 직접 확인했다. **서버는 개발 맥 자신이라 양쪽을 셸에서
대조할 수 있었다.** 접속, 홈 목록, 하위 디렉터리 이동, 새로고침, 분할 화면, 양방향 전송이 모두
동작하고 MD5가 원본과 같았다. 자세한 것은 `plan.md`의 M1 마지막 항목에 적었다.

**전송 속도의 비대칭을 오해하지 말 것.** 29.3MB에서 내려받기 7.8 MB/s, 올리기 0.9 MB/s다.
`SftpFileSystem.openRead`에만 read-ahead가 있고(`READ_AHEAD_MAX = 16`) 쓰기 쪽은 파이프라이닝이
없어서 그렇다. **버그가 아니고 M1이 만든 것도 아니다.** 올리기가 느린 게 문제가 되면 쓰기 쪽
파이프라이닝은 별개 작업으로 잡는다.

기기에 남아 있던 프로필 `testnas`는 문서용 예약 주소(`192.0.2.0/24`)를 가리켜 응답하지 않는다.
15초 뒤 `FsError.Unreachable`로 끝나는 것까지 확인했다 — 오류 경로도 정상이다.

### 원격 실행 방식을 다시 따져봤다 (코드 변경 없음)
사용자가 "SSH exec가 나은지 데몬이 나은지" 다시 보자고 해서 분석했다. **결론은 exec 유지로 그대로**
이지만 **적혀 있던 근거가 약해서 고쳤다.** "아키텍처별 빌드 부담"은 컴파일된 바이너리 데몬만
반박하고, `sh`나 `python3`로 도는 스크립트 에이전트는 그 비용이 없다. 실제로 성립하는 근거 세 개와
데몬을 다시 꺼내는 조건(M6에서 무엇을 잴지 포함)은 `plan.md`의 "정한 것" 2번과 "범위 밖"에 있다.

덤으로 `GitService` 설계에 `git cat-file --batch`를 길게 연 채널로 두는 안을 넣었다. exec의 약점인
명령당 왕복을 줄이는 자리고, 프레이밍이 `LspProcess`와 같은 모양이다.

### 다음 세션이 할 일
1. `AGENTS.md`를 읽는다. 특히 Toolchain constraints, Before committing(**커밋마다 사용자에게 먼저
   묻는다**), 보고와 알림 규칙을 본다.
2. push하지 않은 커밋이 있다. push할지 사용자에게 묻는다.
3. 사용자에게 **손으로 핀치 줌**(코드와 마크다운)을 부탁하고, 가능하면 맞는 비밀번호로 **원격 파일을
   여는 성공 경로**를 본다.
4. `plan.md`의 첫 `- [ ]`인 **상단 메뉴 ①~⑤ 자리와 스크롤 숨김/표시**를 한다(①④⑤는 자리만).
   - 메뉴는 `index.html`/`main.ts`의 `#viewer` 위에 겹친다. 코드는 `view.scrollDOM`, 마크다운은
     `.markdown-scroller`가 스크롤하므로 두 스크롤러 모두에서 방향을 봐야 한다.
   - ②③(레이어 확대/축소)은 `zoom.ts`의 `setFontSize`와 이어진다. 버튼으로 줌할 때의 기준 줄은
     화면 가운데로 두면 된다(M0의 `zoomTest`가 그렇게 했다, git 히스토리에 있다).
   - 설계 문서의 `chrome/topbar.ts` 자리다.

### 사용자와 정한 것, 그리고 이유
다시 논의하지 말고 이대로 진행한다.

| 결정 | 고른 것 | 이유 |
|---|---|---|
| 레포 구조 | 같은 레포, `:core` / `:app` / `:code` 멀티모듈 | 별도 레포는 SFTP 계층을 복사하게 되고, 두 사본이 따로 바뀌면서 달라진다 |
| 원격 실행 | 데몬 없이 SSH exec (프로젝트 모드에서만) | 2026-09-18에 다시 따져보고 근거를 고쳤다. 데몬은 exec 없이 뜨지도 못하고(SFTP는 실행을 못 한다), 남에게 배포하는 앱이 남의 서버에 상주 프로세스를 심는 것은 "서버에 아무것도 설치하지 않는다"는 근본 제약과 충돌한다. 셋째로 임의의 서버가 대상이라 단일 산출물이 없다. 자세한 것과 데몬을 다시 꺼내는 조건은 `plan.md`의 "정한 것" 2번과 "범위 밖"에 있다 |
| UI | 전부 WebView 하나 (TS + CodeMirror 6) | Compose와 WebView를 섞으면 테마를 두 곳에 적용해야 하고, 스크롤에 따른 메뉴 숨김도 브리지를 거친다 |
| 서버 정보 공유 | Skiff가 서명 보호 Provider로 프로필만 공유, 비밀번호는 각자 저장 | Keystore 키는 앱마다 따로라 암호문을 공유할 수 없고, 비밀번호를 넘기면 평문이 IPC를 지나간다 |
| 실기기 | 갤럭시탭 S10 FE(SM-X526N, Android 16/API 36, WebView 152) | 연결된 기기가 이것이다. **폴더블 폰도 지원 대상**이라 폴더블 관련 문서와 코드는 지우지 않는다 |
| 예전 시도 | 로컬 브랜치 `claude/next-steps-2afbda`와 기기의 `com.naki.skiffcode`를 삭제 | 사용자 지시. 없었던 것으로 친다. 참고하거나 되살리지 않는다 |
| CM6 버그 처리 | **upstream에 올리지 않는다.** M5 diff 레이어에서 `patch-package`로 두 줄을 패치한다 | CodeMirror는 AI가 쓴 코드를 받지 않고 이슈는 자체 트래커에서만 받는다. 사용자는 리포트를 올리지 않기로 했다 |
| TOML | ktoml을 쓴다. `smol-toml`로 옮기지 않는다 | M0에서 확인했다. 컴파일러 플러그인만 필요하고 KSP를 안 써서 Room과 다르다 |
| exec | `:app`은 금지, `:code`는 허용. 근거는 "서버에 설치하지 않기 위해"가 아니라 **"셸 없는 `internal-sftp` 계정을 지원하기 위해"** 다 | 사용자가 금지의 출처를 물었고, 사용자가 정한 것이 아니라 AI가 잘못 유도한 것으로 드러났다. 규칙은 남기고 이유를 고쳤다 |
| M0 스파이크 코드 | 확인이 끝나면 지운다. exec 하네스도 지웠다 | 사용자 지시. M0은 버리는 코드로 확인만 하는 단계다 |
| 로컬 파일 경로 | `skiffcode:///경로`(MANAGE_EXTERNAL_STORAGE)와 `content://`(ACTION_VIEW/EDIT) 둘 다 받는다 | 2026-09-18 사용자가 가정대로 진행하라고 했다. `plan.md` "정한 것" 5번 |
| M2 순서 | 계획 순서대로: `TextLoader` → 브리지 → viewer → Skiff 쪽 연결 | 2026-09-18 사용자 선택. Skiff 쪽을 먼저 붙이면 탭해도 토스트까지만 가고, `ProfileProvider` 전이라 비밀번호와 호스트키를 다시 받아야 한다 |
| 보고 방식 | 작업마다 한국어로 보고하고 푸시 알림을 보낸다 | 사용자가 자리를 비울 때가 많다. `AGENTS.md`에 적었다 |

### 사용자 확인을 아직 받지 않은 가정
해당 단계에 들어가기 전에 한 번 물어본다.
1. diff와 git 거터의 기본 비교 대상은 **HEAD와 현재 버퍼**다. "직전 커밋과 HEAD"는 두 번째 옵션이다. (M5 전)
2. 원격 LSP 서버는 자동 설치하지 않고 PATH에서 찾는다. (M6 전)

### 실제 LSP 서버 확인 (exec 규칙을 고치고 추가로 한 것)

**사용자가 exec 금지의 근거를 물었고, 문서가 틀렸다는 것이 드러났다.** "서버에 아무것도 설치하지
않는다"는 사용자가 정한 제약이지만, "그러므로 exec를 두지 않는다"는 AI가 유도한 것이고 논리가
맞지 않는다(서버에 이미 있는 `git`을 exec로 돌리는 것은 아무것도 설치하지 않는다). 실제로 그 규칙이
지키는 것은 **셸 없는 `internal-sftp` 계정 지원**이다. `AGENTS.md`를 그렇게 고쳤고, `:code`는
exec를 쓸 수 있다고 명시했다. `:app`은 여전히 금지다.

사용자가 스파이크에서 exec를 허용해서 실제 서버로 확인했다. **하네스는 사용자 지시로 지웠다.**
M6에서 다시 만들 때 필요한 것은 이것뿐이다:
- 서버 쪽: MINA `SshServer`에 `commandFactory = ProcessShellCommandFactory.INSTANCE`.
  `SftpTestServer`(이제 `:core`의 testFixtures)와 같은 구성이고 subsystem 대신 commandFactory를 둔다.
- 클라이언트 쪽: sshj `session.exec("/bin/sh -lc 'cd <root> && exec <command>'")`.
- 프레이밍: 헤더는 `\r\n\r\n`까지 한 바이트씩, 본문은 `Content-Length`만큼 채워 읽는다.
- 언어 서버: `npm i pyright` → `node_modules/.bin/pyright-langserver --stdio`. 레포에 넣지 않았다.
- **`org.json`은 유닛 테스트 JVM에서 스텁이라 전부 던진다.** `org.json:json`을 테스트 의존성으로
  넣어야 한다. Gradle의 `-D`는 테스트 JVM에 전달되지 않으니 `systemProperty`로 넘겨야 한다.
- 결과: initialize 98ms, 진단까지 346ms(로컬 루프백). `positionEncoding`은 응답에 없고(= `utf-16`),
  `textDocumentSync`는 2(incremental). `값 = "한글" + 1`의 진단이 char 4..12로 UTF-16 인덱스와
  정확히 맞았다(UTF-8이면 4..18이다).

### 아직 확인하지 않은 것
안 되는 게 나오면 `plan.md`의 설계를 먼저 고친다.
- **여는 흐름에서 실기기로 확인한 것:** 비밀번호가 든 링크 거부, 알 수 없는 서버 확인창, 첫 연결
  호스트키 창(지문을 맥의 `ssh-keygen -lf`와 대조), 틀린 비밀번호 재시도 창, 저장된 프로필을 alias로
  찾기, `onNewIntent`, 로컬 링크의 파일 접근 안내와 설정에서 돌아온 뒤 이어지기, 최근 파일 기록.
- **확인하지 못한 것:** 맞는 비밀번호로 원격 파일까지 여는 성공 경로(사용자 비밀번호가 필요하다),
  **바뀐 호스트키 경고 창**, 파일 매니저의 "다른 앱으로 열기"(`content://` 자체는 MediaStore URI로
  확인했다, M2 마지막 확인 항목), 손으로 하는 핀치 줌, Android 17의
  로컬 네트워크 권한 요청(탭은 Android 16이라 요청하지 않는다). 탭의 Skiff Code에는 `dev-mac`
  프로필이 틀린 비밀번호로 저장돼 있다. 성공 경로를 볼 때 비밀번호 창에 맞는 것을 넣으면 갱신된다.
- **탭에서 실제 SSH 서버로 LSP를 띄워 본 적은 아직 없다.** M1에서 SFTP 탐색과 전송은 실서버로 확인했지만, 위 LSP 확인은 이 맥 안의 MINA 루프백이라 네트워크 지연, 재접속, 끊김이 빠져 있다. exec로 원격 LSP를 띄우는 것은 M6이 처음이다.
- 진단이 수백~수천 개일 때의 비용. 스텁은 50개, pyright는 1개였다.
- lezer 심볼 추출, 테마 CSS 변수, SAF import/export는 라이브러리 버전도 고르지 않았다(마크다운은 `markdown-it` 15.0.2로 정했다).
- 큰 파일에서 편집 중 동기화 비용. 2MB에서 incremental이 훨씬 싸다는 것만 알고, 실제 서버가 어디서 버거워하는지는 모른다.
- pyright 말고 다른 서버(typescript-language-server, marksman)는 확인하지 않았다. TypeScript 7은 네이티브 재작성이라 `tsserver.js`가 없고, `typescript-language-server`가 그 위에서 도는지 모른다.

### 주의할 점
- **세션을 끝낼 때 `plan.md` 체크리스트를 갱신하고 이 파일을 덮어쓴다.**
- **exec 규칙은 이미 고쳤다.** `:app`은 여전히 금지, `:code`는 허용이다. M5의 체크리스트에서 `AGENTS.md` 수정은 빠졌고 `RemoteExec` 구현만 남았다. 자세한 것은 위 "실제 LSP 서버 확인" 절과 `AGENTS.md`의 "The SFTP side, and what it must not do"에 있다.
- `npm install` 때 fsevents install 스크립트는 npm 11 기본 정책으로 실행되지 않는다. macOS용 선택 의존성이라 영향이 없다.
- 기기:
  - 시리얼은 `adb devices`로 얻고 레포에 적지 않는다.
  - 화면은 2분 뒤 꺼지고, 꺼진 화면에서 `screencap`은 검은 화면을 찍는다. 측정 전에 `adb shell input keyevent KEYCODE_WAKEUP`을 보내고 `dumpsys window | grep mCurrentFocus`로 앱이 앞에 있는지 확인한다.
  - 가로 방향(2304x1440)이고 화면은 하나라 `screencap`에 display id가 필요 없다.
  - 아래 가장자리에서 시작하는 스와이프는 시스템 제스처에 먹히므로 y 250~1100 사이에서 한다.
  - adb로는 멀티터치를 만들 수 없다. DevTools를 포워딩하고 CDP `Input.dispatchTouchEvent`에 터치
    점 두 개를 주면 핀치를 흉내 낼 수 있다(CSS px 기준, 화면 좌표는 ×2 + 상태 표시줄 60px). 그래도
    손으로 하는 확인은 사용자에게 부탁한다.
  - 페이지 좌표를 알면 `adb shell input tap`으로 링크를 실제로 탭할 수 있다(위와 같은 변환).
- 환경:
  - `node`(v24)와 `npm`은 PATH에 있다.
  - `java`는 PATH에 없어서 `JAVA_HOME=/opt/homebrew/opt/openjdk@17`이 필요하다.
  - `adb`도 PATH에 없고 `/opt/homebrew/share/android-commandlinetools/platform-tools/adb`에 있다.
  - worktree에는 gitignore된 `local.properties`가 따로 있어야 한다(메인 체크아웃에서 복사했다).

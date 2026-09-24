# Skiff Code 설계

코드 뷰어이자 에디터인 **독립 앱**이다. Skiff에서 "나중에"로 미뤄둔 미리보기 뷰어가 이 앱이 됐다.
원래 요구사항은 [`my.skiffcode.spec.md`](my.skiffcode.spec.md)에, 화면 설계 손그림은
[`menu.layout.jpg`](menu.layout.jpg)에 있다. 이 문서는 두 문서를 설계로 푼 것이고, 할 일 목록은
[`skiffcode.plan.md`](skiffcode.plan.md)에 있다.

## 정한 것

1. **같은 레포, 멀티모듈.** 공통 SSH/FS 계층을 `:core`로 빼고 `:app`(Skiff)과 `:code`(Skiff Code)가 함께 쓴다. 별도 레포면 SFTP 계층을 복사하게 되고, 두 사본이 따로 바뀌면서 달라진다.
2. **원격 데몬 없이 SSH exec로 쓴다.**
   - 단일 파일 모드는 Skiff처럼 SFTP만 쓴다.
   - exec 채널은 사용자가 만든 **프로젝트 안에서만** 열고, 서버에 이미 있는 `git`과 LSP 서버(`--stdio`)를 직접 실행한다.
   - 데몬은 뒤로 미룬다. exec 쪽 클래스는 인터페이스 뒤에 두어 나중에 데몬으로 바꿀 수 있게 한다.
   - **근거 (2026-09-18에 다시 따져보고 이렇게 고쳤다):**
     1. **데몬은 exec의 대안이 아니라 exec + α 다.** SFTP는 파일을 올릴 수는 있어도 실행할 수 없으니,
        데몬을 처음 띄우는 수단이 결국 exec다. 셸 없는 `internal-sftp` 계정에서는 데몬도 똑같이 불가능하다.
     2. **남의 서버에 설치하는 것은 이 프로젝트의 근본 제약과 충돌한다.** "서버에 아무것도 설치하지
        않는다"가 사용자가 정한 제약이고(`AGENTS.md` 첫 문단), 이 앱은 남에게 배포한다. 원래 요구사항의
        "데몬이 필요하다면 자동 설치 필수"는 *남의 서버에 상주 프로세스를 심는 동작*이 되어, 서버
        관리자 눈에는 악성 소프트웨어와 구분되지 않는다. 서버에 이미 있는 `git`을 exec로 돌리는 것은
        아무것도 설치하지 않는다.
     3. **임의의 서버가 대상이라 단일 산출물이 없다.** 바이너리면 아키텍처 × libc 매트릭스, 스크립트면
        런타임 탐지 매트릭스이고, 거기에 버전 스큐와 고아 프로세스 정리가 전부 우리 책임이 된다. exec
        채널은 채널이 닫히면 sshd가 프로세스를 거둔다 — 수명 관리가 우리 코드가 아니라 OS에 있다.

     ("아키텍처별 빌드 부담" 하나만 적혀 있던 예전 근거는 **컴파일된 바이너리 데몬에만** 해당해서,
     `sh`나 `python3`로 도는 스크립트 에이전트를 반박하지 못했다. 위 세 개로 바꿨다.)
3. **UI 전체를 WebView 하나에 올린다.** 상단 메뉴, 사이드바, 커맨드 팔레트, 레이어를 모두 TypeScript와 CodeMirror 6으로 만든다. Kotlin은 파일, SSH, git, LSP를 맡는 백엔드 브리지다. 테마는 CSS 변수 한 벌로 전체에 적용된다. Compose와 WebView를 섞으면 테마를 두 곳에 적용해야 하고, 스크롤에 따른 메뉴 숨김도 브리지를 거친다.
4. **서버 프로필만 공유한다.**
   - Skiff가 서명 보호(`protectionLevel="signature"`) ContentProvider로 비밀이 아닌 정보(별칭, host, port, user, startPath, 알려진 호스트키)를 내보낸다.
   - Keystore 키는 앱마다 따로라서, 비밀번호는 Skiff Code가 처음 한 번 입력받아 자기 Keystore에 저장한다. 암호문은 공유할 수 없고, 비밀번호를 넘기면 평문이 IPC를 지나간다.
5. **로컬 파일은 두 경로로 받는다** (2026-09-18 사용자 확인). Skiff에서는 `skiffcode:///절대경로`로 받고(MANAGE_EXTERNAL_STORAGE 사용), 다른 앱에서는 표준 `ACTION_VIEW`/`ACTION_EDIT`의 `content://`로 받는다. 로컬은 항상 단일 파일 모드다.

**가정** (틀렸으면 여기부터 고칠 것):
- diff와 git 거터의 기본 비교 대상은 **HEAD와 현재 버퍼**다. "이 파일을 건드린 직전 커밋과 HEAD"는 더보기(④) 메뉴에서 바꾸는 두 번째 비교 대상이다.
- LSP 서버는 원격에 **자동 설치하지 않는다.** PATH에서 찾고, 경로는 설정에서 덮어쓸 수 있다.

## 설계

### 모듈

```
settings.gradle.kts   include(":core", ":app", ":code")
:core  (com.android.library, 패키지명은 기존 그대로)
       fs/, fs/local/, fs/sftp/(SftpFileSystem, SshConnection, HostKeyGate, HostKeyPrompter, HostKeyFingerprint),
       data/crypto/SecretStore, ServerProfile·KnownHost 모델, FsError, FsPath, FileKind, LocalNetworkAccess
       + KnownHostStore 인터페이스 (HostKeyGate가 SkiffStore 대신 이것에 의존)
       + SshClientFactory (SshConnection.ensureConnected의 접속·인증·호스트키 부분)
:app   Skiff. SkiffStore가 KnownHostStore를 구현. + ProfileProvider
:code  Skiff Code (applicationId com.naki.skiff.code, logcat 태그 SkiffCode)
  src/main/java/com/naki/skiff/code/
    data/      SkiffCodeStore(프로필, knownHosts, 최근 파일을 JSON 하나에)
    intent/    SkiffCodeUri 파서, OpenRequest 해석(로컬/원격/프로필/프로젝트)
    session/   RemoteSessions(프로필→SftpFileSystem), RemoteExec, ShellQuote
    doc/       TextLoader, DocumentSaver, FileWatcher
    project/   ProjectStore, GitScopeFinder, GitService
    lsp/       LspProcess, LspManager
    settings/  settings.toml, themes/*.toml, import/export
    bridge/    WebBridge (JSON-RPC)
    ui/        MainActivity(WebView 하나), 네이티브 다이얼로그(호스트키, 비밀번호, 알 수 없는 서버)
  web/         Vite + TypeScript + CodeMirror 6 → 빌드 결과를 src/main/assets/web/ 에 둔다 (gitignore)
    bridge.ts panes.ts layers/{viewer,editor,diff}.ts markdown.ts zoom.ts theme.ts fuzzy.ts
    chrome/{topbar,sidebar,palette,commandButton}.ts gitGutter.ts lsp.ts
```

**Kotlin과 Web의 역할.** 바이트, SSH, git, LSP 프로세스는 Kotlin이 맡는다. 문서 상태, 렌더링,
diff 계산, 퍼지 검색은 Web이 맡는다. Web은 원격 호출을 모두 `bridge.ts`의 RPC 하나로 한다.

**브리지는 양쪽 다 알림을 보낼 수 있어야 한다.** id가 있는 요청/응답만으로는 LSP 진단이나 파일
변경처럼 Kotlin이 먼저 말하는 것을 실을 수 없다. id가 없는 메시지는 알림으로 보고, 받는 쪽이
method로 구독한다(M0에서 확인).

### URI

```
원격  skiffcode://alice@192.0.2.10:22/home/alice/demo/a.md?alias=home-server&line=42&layer=viewer
로컬  skiffcode:///storage/emulated/0/Documents/a.md
```
- 경로는 퍼센트 인코딩한다. 선택 쿼리는 `alias`, `line`, `col`, `layer`(viewer|editor|diff)다.
- **`user:password@`는 거부한다.**
- 프로필은 `alias` 일치 → `(user, host, port)` 일치 순으로 찾는다. 없으면 "알 수 없는 서버" 확인창에서 열기 여부, 필요한 정보(비밀번호), 프로필로 저장할지를 묻는다.
- **보낸 앱이 Skiff가 아니면 서버와 경로를 보여주고 묻는다**(2026-09-20). alias가 맞아도 정하는 것은 *어느 서버*뿐이고 경로는 링크의 것이라, 이름만 맞히면 저장된 자격증명으로 그 서버의 아무 파일이나 열렸다. 보낸 앱은 `ComponentCaller`로 알아낸다(`getReferrer()`는 호출자가 위조한다). 자세한 것은 `AGENTS.md`의 "Links that arrive from outside".
- 호스트키는 `HostKeyGate`(TOFU)를 그대로 쓴다. **PromiscuousVerifier는 쓰지 않는다.**
- 다른 앱에서 띄우기:
  - 노트나 터미널 앱의 링크를 누르면 커스텀 스킴이 열린다.
  - Chrome은 사용자가 직접 누른 링크만 열리고, `intent://` 형식이 더 안정적이다.
  - https 앱링크는 검증된 도메인이 필요해서 범위 밖이다.

### 파일을 여는 흐름

1. 로컬이면 → 단일 파일로 연다.
2. 원격이고 저장된 프로젝트의 루트 아래에 있으면 → 그 프로젝트를 활성화하고 연다.
3. 원격이면 `GitScopeFinder`가 **SFTP만으로** 파일의 디렉토리부터 위로 `.git`(worktree/submodule이면 파일)을 찾는다. **홈 디렉토리(`canonicalize(".")`)에서 멈추고, 홈 밖의 파일은 찾지 않는다.**
   - `.git`을 찾으면 → "단일 파일로 열기 / 프로젝트 만들기(루트 = git 최상위)"를 묻는다.
   - 못 찾으면 → 묻지 않고 단일 파일로 연다.
4. 프로젝트를 활성화할 때 exec로 `command -v git`을 확인한다. exec가 거부되면(internal-sftp 계정) git/LSP 없이 열고 이유를 알린다.

### 문서 읽기, 저장, 감시

- **읽기 (`TextLoader`):**
  - 먼저 `stat`한다. 크기 상한은 설정값이고 기본 2MB다.
  - 바이너리 여부는 **앞 8KB에 NUL 바이트가 있는지**로 판별한다. 확장자는 믿지 않는다.
  - UTF-8로 **엄격하게** 디코딩하고(깨진 바이트를 U+FFFD로 바꾸지 않는다), 실패하면 **EUC-KR로 폴백**한다. EUC-KR은 디코더에 맡기기 전에 바이트 모양(ASCII, 또는 두 바이트 모두 A1..FE이고 사용자 정의 행 C9·FE가 아닌 것)을 직접 확인한다. Android의 `EUC-KR` 디코더는 UHC 확장 한글, `80`, `FF`, 사용자 정의 행까지 받아서 JVM과 결과가 다르다(M2 viewer에서 실기기로 확인). 둘 다 실패하면 열지 않는다(`UnknownEncoding`). 대체 문자로 채운 텍스트를 저장하면 원래 바이트를 덮어쓰기 때문이다. 파일에 원래 들어 있던 U+FFFD는 정상 UTF-8이라 폴백하지 않는다.
  - 원래 인코딩, UTF-8 BOM, CRLF, 끝 줄바꿈을 기억해 두고 저장할 때 되돌린다. 텍스트의 줄바꿈은 `\n`으로 바꿔 넘긴다(CM6가 갖는 형태). 줄바꿈은 첫 줄의 것으로 정하므로, 섞인 파일은 저장하면 한 가지로 통일된다.
  - 읽기 전에 한 `stat`의 크기와 mtime을 결과에 담아 `DocumentSaver`가 비교에 쓴다.
- **저장 (`DocumentSaver`):**
  - 쓰기 직전에 `stat`해서 불러올 때의 mtime/size와 비교한다. 다르면 충돌 배너를 띄운다.
  - 같으면 제자리에 truncate 쓰기를 한다. 임시 파일에 쓰고 rename하는 방식은 소유권, 하드링크, 심링크를 깨서 쓰지 않는다.
  - 쓴 뒤 다시 `stat`해서 자기 쓰기를 외부 변경으로 오인하지 않게 한다.
- **감시 (`FileWatcher`):**
  - 원격: 보이는 파일은 약 2초마다 SFTP `stat`으로 폴링한다. 나머지 열린 파일은 활성화되거나 앱으로 돌아올 때 확인한다. **폴링은 파일을 연 그 `SftpFileSystem`의 browse 연결로 한다**(2026-09-21 사용자 결정, 원래는 별도 연결이었다). `stat`은 browse, 읽기와 저장은 transfer라 둘이 부딪히지 않고, `:code`에는 아직 디렉토리 탐색이 없다. 프로필마다 SSH 로그인이 하나 더 생기는 값을 그 때문에 치르지 않는다. M5의 사이드바 파일 트리가 browse를 쓰기 시작하면 다시 본다.
  - 로컬 경로는 `FileObserver`, `content://`는 폴링한다. `content://`는 `stat`이 없으므로 provider에게 크기와 수정 시각 **컬럼**을 묻고, 둘 다 없으면 폴링을 접고 **앱으로 돌아올 때만** 다시 읽어 본문을 비교한다(2026-09-21 사용자 결정). 값이 없는 provider에서 2MB 스트림을 2초마다 다시 읽는 것을 피한다.
  - 버퍼가 깨끗하면 diff를 트랜잭션으로 적용해서 커서와 스크롤을 유지한다. 수정 중이면 "다시 불러오기 / 내 것 유지" 배너를 띄운다.
  - **"내 것 유지"를 고르면 저장 기준선은 그대로 둔다**(2026-09-21 사용자 결정). 그 버퍼를 저장할 때 `DocumentSaver`가 `Conflict`를 돌려주므로, 남의 변경 위에 덮어쓰기 직전에 한 번 더 확인하게 된다. "내 것 유지"는 "지금은 안 불러온다"는 뜻이지 "덮어써도 좋다"는 뜻이 아니다. 이 기준선을 들고 있는 곳은 M4의 커맨드 레지스트리에서 생겼다 — `OpenDocuments.Entry.base`이고, 페이지가 `adopted`라고 말할 때만 움직인다.
- **열린 파일:**
  - EditorView는 하나만 두고, 파일마다 `EditorState`와 레이어별 스크롤 위치를 보관한다. 전환은 즉시 되고 undo 기록도 남는다.
  - 상한은 설정값이고 기본 30개다. 넘으면 가장 오래된 *깨끗한* 파일부터 내리고, 수정 중인 파일은 내리지 않는다.

### 레이어

- `pane`은 파일과 1:1이고 전체화면이다. viewer, editor, diff가 레이어로 겹쳐 있다.
- **레이어는 뷰가 아니라 확장 묶음이다.** `pane` 하나에 `EditorView` 하나와 `EditorState` 하나를 두고, 레이어를 바꾸는 것은 `Compartment`에 든 확장 묶음을 바꾸는 것이다. 그래서 문서, 파싱 트리, 높이맵, undo 기록이 세 레이어에 하나씩만 있고, 나간 레이어의 state field는 CM6가 알아서 버린다(2026-09-20에 정했다. 근거는 `AGENTS.md`의 CM6 관찰). 마크다운 viewer만 CM6가 아닌 별도 DOM이라 예외다.
- **viewer:**
  - 읽기 전용 CM6에 하이라이팅, 줄 번호, git 거터를 보여준다.
  - **문법은 이름으로 고른다** — `LanguageDescription.matchFilename`이 `Makefile` 같은 이름과 확장자를 함께 안다. 내용은 보지 않는다.
    - **`.jsonl`은 language-data가 모른다**(JSON 항목이 `json`과 `map`만 claim한다). 한 줄에 하나씩 든 JSON이라 같은 문법이 맞고, 이름의 끝 `l`을 떼어 JSON 항목이 답하게 한다(2026-09-23 사용자 요청). 문서 전체로는 값이 여러 개라 파서가 오류를 내지만 **줄바꿈마다 복구해서 모든 줄이 그대로 강조된다**(파서를 직접 돌려 확인: 3줄 중 3줄이 Object, 최상위 오류 노드 2개). 뷰어에 JSON 린터가 없어 그 오류가 화면으로 새지도 않는다.
  - 마크다운은 `markdown-it`(`html: false`)으로 렌더링하고 테마를 적용한다.
  - 스크롤과 확대/축소를 가장 먼저 잘 만든다. 핀치 줌은 CSS 변수 `--code-font-size`를 바꾸고, 손가락 사이의 줄이 제자리에 있게 한다.
  - 줌 중심 줄 고정은 CM6의 `EditorView.scrollIntoView(pos, {y: 'start', yMargin})` 효과로 한다. CM6가 줄 높이를 다시 잰 뒤 적용하기 때문이다. `requestMeasure`에서 `scrollTop`을 직접 쓰면 CM6의 스크롤 앵커와 싸워서 수만 px씩 어긋난다(M0에서 확인).
- **editor:**
  - viewer와 같은 `EditorState`를 쓰고, `Compartment`로 readOnly와 확장만 바꾼다.
  - 글자 크기는 viewer와 같이 핀치로 바꾸고, 상단 ②로 원래 크기에 돌아온다. 하단 ± 메뉴는 두지 않는다(아래 "화면 메뉴").
  - LSP 진단과 자동완성을 붙인다.
- **diff:**
  - 읽기 전용 unified 뷰다. 현재 버퍼를 문서로 두고 `@codemirror/merge`의 `unifiedMergeView({original, mergeControls: false})`로 비교 대상과 비교한다. 지운 줄은 블록 위젯으로 끼워진다.
  - **diff 알고리즘은 줄 단위로 먼저 하고, 바뀐 줄 범위 안에서만 글자 단위로 한다(`diffConfig.override`).** 패키지 기본값(`scanLimit` 500)은 큰 파일에서 파일 전체를 청크 하나로 포기하고, 제한을 풀면 2MB에 6.7초가 걸리며 그래도 부정확하다. 줄 단위는 2MB에 약 240ms다(M0에서 확인). git 거터의 `Chunk.build`도 같은 설정을 쓴다.
  - +/- 기호는 별도 거터로 그린다. 추가된 줄은 `lineMarker`, 지운 줄 블록은 `widgetMarker`에 지운 줄 수만큼 `-`를 쌓는다. 초록/빨강 배경의 투명도는 CSS 변수 `--diff-alpha`로 조절한다. 하이라이팅도 한다.
  - **`@codemirror/view` 6.43.12에 버그가 있다.** 블록 위젯이 줄 경계에 있으면 `HeightMapBranch.forEachLine`이 범위를 clamp하지 않아서 `viewportLineBlocks`가 화면 밖 수천 줄로 늘어난다. 모든 거터가 그만큼 요소를 만들어서 줌 한 단계가 5배 이상 느려진다. 두 곳에 `Math.max(from, …)`/`Math.min(to, …)`를 넣으면 고쳐진다. **정한 것:** upstream에는 올리지 않고(CodeMirror는 AI가 쓴 코드를 받지 않는다), M5 diff 레이어에서 `patch-package`로 이 두 줄을 패치한다. CM6 버전을 올릴 때마다 이 함수에 clamp가 들어갔는지 확인하고, 들어갔으면 패치를 뺀다.
  - 스크롤과 줌은 viewer 코드를 그대로 쓴다.

### 화면 메뉴 (`menu.layout.jpg`를 글로 옮김)

**상단 메뉴** (왼쪽 ①, 오른쪽 묶음 ②③④)
- ① 사이드바 버튼. 사이드바가 왼쪽에서 밀려 나온다. 열린 파일, 프로젝트 목록, 프로젝트 파일 트리를 보여준다. 사이드바 버튼이나 바깥을 누르면 밀려 들어간다. **사이드바가 ①을 같은 자리에 다시 갖는다**(2026-09-21 사용자 결정): 나와 있는 동안 상단 메뉴를 덮으므로, 연 손가락이 있던 그 자리가 닫는 자리가 된다. 사이드바에 제목은 두지 않는다.
- **①과 ② 사이에 지금 보고 있는 파일 이름, 그 옆에 저장 상태 점** (2026-09-24 사용자 요청). 이름은 남는 자리를 다 쓰고(`flex: 1; min-width: 0`) 넘치면 끝을 말줄임한다 — 사이드바의 경로와 달리 왼쪽을 지우지 않는다. 파일 이름은 앞이 구별하는 쪽이다.
  - **점은 버퍼에 타이핑이 있을 때만 있다**(2026-09-24 사용자 결정). 저장된 상태에는 아무것도 없다. 늘 점을 두고 색으로 나누는 쪽도 봤지만, 아무 일도 없는 동안 화면에 하나 더 있는 것보다 조용하다.
  - **바뀌었다고 알려주는 신호가 지금은 없다.** `dirtyFlag`는 `EditorState`에 있고 `pane.dirty`로 물어볼 수만 있으며, 사이드바도 닫을 때 물어볼 뿐 표시하지 않는다. 그래서 `openPane`이 콜백을 하나 받고 뷰의 `updateListener`에서 **값이 뒤집힐 때만** 부른다. 타이핑, 저장(`saved()`), 밖에서 온 변경을 받는 것(`adopt`)이 전부 트랜잭션이라 셋이 같은 한 곳으로 들어온다.
  - 열린 문서가 없으면 이름도 점도 없고, 못 연 파일(너무 큼·바이너리)은 **이름만** 둔다 — 버퍼가 없으니 저장 상태라는 것이 없다.
  - 점의 접근성 이름은 Kotlin의 string resource에서 온다(페이지의 다른 글자와 같다). 파일 이름 자체는 데이터라 그 대상이 아니다.
  - 레이어를 아래로 스크롤하면 메뉴와 함께 이름도 숨는다. 지금 메뉴가 하는 그대로다.
- ② 원래 크기. 핀치로 바꾼 글자 크기를 설정의 기본 글자 크기로 되돌린다(`settings.toml`이 생기기 전까지는 14px). 화면 가운데 줄을 제자리에 둔다.
- ③ viewer → editor → diff 순환 토글. 아이콘이 현재 레이어에 맞게 바뀐다.
- ④ 더보기. 지금은 자리만 만들어 둔다(비교 대상 선택 등이 들어갈 곳).
- **그림과 번호가 다르다** (2026-09-19 사용자 결정). 그림의 ② 확대와 ③ 축소를 빼고 ② 원래 크기 하나로 바꿨고, 뒤의 번호를 당겼다(그림의 ④ 토글 → ③, ⑤ 더보기 → ④). 확대/축소는 핀치로 하고, 원래 요구사항(`my.skiffcode.spec.md`)의 "글자 크기 확대 축소는 아래 메뉴에 배치"도 같은 결정으로 뺐다. 커맨드 팔레트의 확대/축소 명령은 남긴다.
- 레이어를 아래로 스크롤하면 메뉴가 위로 숨고, 위로 스크롤하면 다시 나타난다.

**기다리는 동안** (2026-09-24 사용자 요청)
- 링크에서 문서까지의 기다림은 **전부 Kotlin 쪽에서 일어난다** — 묻는 창, 접속, `stat`, 읽기. 페이지는 끝나야 `documentsChanged`를 받으므로, 시작과 끝을 알리는 `openingChanged` 알림 하나를 새로 둔다. 새 링크가 앞의 것을 밀어내면 밀린 쪽의 "끝났다"는 버린다(`opening === job`일 때만 보낸다) — 아니면 새 링크의 표시를 그것이 꺼버린다.
- **화면 맨 위 가장자리의 3px 라인**이다. 메뉴 아래가 아닌 이유는 메뉴가 스크롤을 따라 올라가며 라인을 데려가기 때문이다 — 배너를 아래에 둔 것과 같은 이유다.
- **화면에 문서가 없을 때만** 그 아래에 문서 모양(회색 줄들)을 함께 둔다. 문서가 떠 있는데 회색 줄로 덮으면 있던 것보다 덜 말한다. 문서가 그려지는 순간 모양은 라인의 최소 표시 시간과 무관하게 걷힌다.
- **200ms 안에 끝나면 아무것도 뜨지 않고, 한 번 뜨면 300ms는 남는다**(`loading.ts`). 로컬 파일은 대개 전자라 아무것도 보지 않는다 — 열 때마다 번쩍이는 라인은 라인이 없는 것보다 나쁘다. 나가는 중에 새 링크가 오면 라인 하나가 그대로 이어진다.
- 언제 보일지의 규칙은 DOM 없이 `loading.ts`에 두고 그리는 것은 `chrome/loading.ts`다(팔레트와 같은 나눔이라 vitest가 규칙만 본다). `prefers-reduced-motion`에서는 흐르지 않고 가만히 있는 표시로 바뀐다.

**커맨드 버튼과 팔레트** (VS Code 커맨드 팔레트 방식)
- **A:** 우하단 버튼. 평소에는 50% 투명도이고, 누르면 100%가 되며 B로 간다.
- **B:** 입력 커서가 활성화되고 버튼은 "입력 버튼"이 된다.
  - 빈 곳을 누르는 등으로 취소하면 A로 돌아간다.
  - 입력 버튼을 위아래로 스와이프하면 모양과 모드가 바뀐다: `>` 커맨드 검색, 🔍 파일 검색, `@` 심볼 검색.
    - **셋 다 글자가 아니라 선 아이콘으로 그린다** (2026-09-21 사용자 요청). 상단 메뉴와 같은 방식(획 하나, `currentColor`, 채움 없음, 24px)이고, `topbar.ts`의 `svg()`를 함께 쓴다. 글자로 두면 🔍는 시스템이 제 색과 제 굵기로 칠하는 이모지라 혼자 튀고, `>`와 `@`는 폰트가 주는 굵기로 폰트의 베이스라인 자리에 앉는다 — 버튼 한가운데가 아니다.
    - `>`는 명령을 치는 줄까지 같이 그린 프롬프트(`>_`), `@`는 안쪽 원과 그 둘레를 돌다 멈추는 획이다.
- **C:** 입력을 시작하면 퍼지 검색 결과가 뜨고 맨 위 항목이 기본 선택이다.
  - Enter나 입력 버튼으로 실행하고 A로 돌아간다.
  - A로 돌아갈 때 버튼 모양은 마지막 모드를 유지한다.
- **B는 비어 있지 않다** (2026-09-21 사용자 요청): 아무것도 치기 전에 **그 모드에서 마지막으로 실행한 다섯 개**를 보여주고, 타이핑 없이 거기서 바로 실행할 수 있다. 모드마다 따로 세고, 같은 것을 다시 실행하면 목록에서 한 자리만 차지하며 맨 위로 올라온다. 한 번도 실행한 적이 없는 모드에서는 아무것도 뜨지 않는다.
  - **페이지의 `localStorage`에 남는다**(`recents.ts`, 키는 `palette.recent`). 앱을 껐다 켜도, 액티비티가 재생성돼도 그대로다. `settings.toml`이 아닌 이유는 그 파일이 **사람이 열어 고치는 것**이기 때문이다 — 최근 목록은 편집할 설정이 아니라 쓰다 보면 쌓이는 흔적이라 페이지가 자기 쪽에 둔다.
  - 그러려면 `MainActivity`가 `domStorageEnabled`를 켜야 한다. **기본값이 꺼짐이고, 그때 `localStorage`는 null이라** `.setItem`이 `TypeError`로 떨어진다(기기에서 확인). 저장소는 이 앱의 origin 아래 앱 전용이고 앱 데이터를 지우면 같이 지워진다.
  - **저장소가 없어도 팔레트는 돌아간다.** 읽기와 쓰기 모두 실패를 삼키고 빈 목록으로 간다 — 저장소가 꺼진 빌드, 꽉 찬 저장소, 그리고 DOM이 없는 vitest가 전부 그 경로다. 남아 있던 것이 우리가 적은 모양이 아니면 없는 것으로 친다.
- **가로는 600px에서 멈추고 구석에 남는다** (2026-09-21 사용자 요청). 탭에서는 좌우 16px만 남기고 1120px까지 늘어나 있었다 — 한 줄이 화면을 가로지르면 엄지가 있는 버튼에서 멀고, 이름은 그렇게 길지 않다. 좁은 화면은 상한 아래라 전처럼 화면 너비를 다 쓴다(`width: calc(100% - 32px); max-width: 600px`).
- **결과 개수에는 상한을 두지 않는다**(2026-09-21 사용자 결정). 여덟 줄로 끊는 것을 넣었다가 뺐다. 길면 목록이 스크롤된다(`max-height: 40vh`).
- 파일 검색: 프로젝트에서는 `git ls-files` 캐시를, 단일 파일에서는 열린 파일과 같은 디렉토리를 대상으로 한다.
- 심볼 검색: 단일 파일에서는 lezer 구문 트리를, 프로젝트에서는 LSP를 쓴다.
- **팔레트 안쪽은 전부 영문이다** (2026-09-21 사용자 결정). 커맨드 이름(`Save File`), placeholder, "결과 없음"까지 팔레트라는 표면 하나가 통째로 영문이다. 커맨드 이름만 영문이면 한 줄 안에서 언어가 섞이고, 퍼지 검색의 대상도 영문 이름 하나가 된다(한글로 쳐서 커맨드를 찾는 길은 만들지 않는다).
  - 그래서 팔레트의 글자는 **Kotlin의 string resource를 거치지 않고 페이지 안에 있다.** 페이지의 다른 글자와 다른 유일한 자리다 — 지역화하지 않기로 한 것이라 `values-ko`가 들고 있을 것이 없다.
- **팔레트가 열릴 때 소프트 키보드를 영문으로** (2026-09-21): 강제하는 API는 없고 힌트뿐이다. **페이지만으로 됐다 — `inputmode="email"`이다.** 탭(삼성 키보드, 한국어와 English 둘 다 추가돼 있음)에서 잰 것:

  | 입력칸 | `EditorInfo` | 키보드 |
  |---|---|---|
  | `type=text` + `lang="en"` | `inputType=0x8c0a1`, **`hintLocales=null`** | 한국어 |
  | `type=url` / `inputmode=url` | `inputType=0x11` | 한국어 |
  | **`inputmode=email`** | `inputType=0xd1` | **English** |

  - **`lang="en"`은 IME에 닿지도 않는다.** Chromium이 `hintLocales`로 옮겨주지 않아 `null`이다. 그래서 페이지의 `lang`으로는 아무것도 못 한다.
  - 이메일 칸이 아니지만 **이메일 칸이 요구하는 모양이 커맨드 이름의 모양이다** — 라틴 문자, 자동수정 없음, 첫 글자 대문자 없음, 그리고 `@` 키(심볼 모드의 글리프이기도 하다).
  - **문서 쪽은 그대로 한국어다.** 문서를 탭하면 한국어, 팔레트를 열면 English로 바뀌는 것을 같은 세션에서 확인했다.
  - 다른 키보드가 무시하면 남은 길은 Kotlin의 `EditorInfo.hintLocales`(API 24+)다. WebView를 서브클래싱해 `onCreateInputConnection`의 `outAttrs`에 넣되, 그 자리가 페이지 전체에 하나뿐이라 **팔레트가 포커스를 잡고 놓을 때 브리지로 알려** 켜고 끄고 `imm.restartInput(webView)`로 다시 읽게 해야 한다.
  - **키보드 언어는 화면으로 못 읽는다**(삼성 키보드의 자판 영역은 `screencap`에 흰색으로 나온다). `adb shell dumpsys activity service com.samsung.android.honeyboard | grep currentLang`이 읽어 준다. 탭은 외장 키보드 때문에 소프트 키보드가 뜨지 않으므로 `settings put secure show_ime_with_hard_keyboard 1`로 띄우고 **끝나면 0으로 되돌린다.**

### 상태 저장

**앱이 쓰이면서 쌓이는 것은 페이지의 `localStorage`에 둔다** (2026-09-21 사용자 결정). 팔레트의 최근 목록, 확대 배율, 파일이 있던 레이어와 줄, 사이드바가 나와 있었는지 같은 것들이다. 브리지를 건너 Kotlin에 맡기지 않는다 — 그것을 아는 쪽이 페이지이고, 페이지가 다시 열릴 때 필요한 것도 페이지다.

- **`settings.toml`과 다르다.** 그 파일은 사람이 열어 고치는 것이고(폰트, 탭 크기, 테마, 상한, LSP 명령), 여기 있는 것은 **사람이 고를 것이 아니라 앱이 알아차린 것**이다. 기본 글꼴 크기는 설정이고, 지금 보고 있는 배율은 상태다.
- 키는 `팔레트.무엇` 식으로 앞을 붙인다(`palette.recent`).
- **읽기와 쓰기는 전부 감싼다.** `domStorageEnabled`가 꺼진 빌드에서는 `localStorage`가 null이고, vitest에는 아예 없다. 없으면 없는 대로 돌아가야 한다.
- **돌아온 값은 믿기 전에 확인한다.** 적은 것은 페이지지만 그 사이 무엇이 들어 있을지는 모른다.
- **서버나 사람을 알아볼 수 있는 것은 넣지 않는다.** 프로필, 비밀번호, 호스트키는 Kotlin 쪽 store와 Keystore에 남는다.

### 설정과 테마

- `settings.toml`: 폰트, 폰트 크기, 탭 크기, 줄바꿈, 테마, diff 투명도, 크기 상한, 열린 파일 상한, 폴링 주기, LSP 명령.
  - **줄바꿈의 기본값은 폴더블이 생기면서 실제 문제가 됐다.** 지금은 `lineWrapping`이 아예 없어 가로로 스크롤하는데, 커버 화면(475dp)에서는 마크다운 본문 한 줄이 오른쪽으로 사라진다. 넓은 화면에서는 보이지 않던 것이다.
  - 폰트 크기에는 **기기의 시스템 글꼴 배율이 그대로 곱해진다.** `font_scale` 1.5인 폰에서 기본 14px이 21px로 나왔다(WebView의 `textZoom`, 페이지 아래에서 적용되어 페이지가 볼 수 없다). 설정의 상한 8~40도 그만큼 달라진다. 배율을 따를지 지울지 정해야 한다.
- `themes/*.toml`: `[ui]`(메뉴, 사이드바, 팔레트), `[editor]`, `[syntax]`, `[diff]`. Web에서 CSS 변수로 바꾸고 **메뉴와 레이어에 똑같이** 적용한다. 다크와 라이트를 기본 번들한다. 잘못된 값은 기본값으로 폴백하고 오류를 알린다.
- 설정과 테마의 import/export는 SAF(`ACTION_OPEN_DOCUMENT` / `ACTION_CREATE_DOCUMENT`)로 한다.
- TOML 파싱은 Kotlin의 ktoml(`com.akuleshov7:ktoml-core` 0.7.1)로 한다. M0에서 확인했다.
  - 중첩 테이블, 문자열 리스트, 언어로 키를 삼은 테이블(`[lsp.python]` → `Map<String, …>`),
    빠진 키의 생성자 기본값이 모두 된다.
  - `:code`에 kotlinx.serialization 플러그인이 필요하다. **KSP는 아니라서 Room과 달리 문제가 없다.**
  - ktoml은 kotlinx-serialization-core 1.9.0으로 빌드됐는데, M2에서 `:code`에 serialization-json
    1.11.0이 들어오면 core가 1.11.0으로 올라간다. 그 조합으로도 테스트가 통과한다.
  - `kotlinx-datetime`이 함께 들어온다. 설정에 날짜가 없어도 APK에 들어간다.

### git과 LSP (프로젝트 모드)

- **`RemoteExec`:**
  - 프로젝트별로 exec 전용 SSHClient를 둔다(`SshClientFactory`). SFTP의 단일 스레드 dispatcher와 분리해서 오래 도는 채널이 탐색을 막지 않게 한다.
  - **명령은 argv 리스트로만 만들고 `ShellQuote`로 인자마다 작은따옴표로 감싼다.** 경로는 URI에서 오는 외부 입력이다.
- **`GitService`:**
  - `git -C <root> show HEAD:<rel>`: 비교 기준 텍스트.
  - `git log -n 2 --format=%H -- <rel>`: 두 번째 비교 대상.
  - `git ls-files -co --exclude-standard -z`: 파일 검색.
  - 결과는 HEAD 해시를 키로 캐시한다.
  - **기준 텍스트는 `git -C <root> cat-file --batch` 채널 하나를 길게 열어 읽는다.** exec의 진짜
    약점은 명령마다 채널 왕복인데, 파일마다 `show`를 부르면 왕복이 파일 수만큼 는다. `--batch`는
    stdin에 `HEAD:<rel>`을 쓰면 `<sha> <type> <size>\n<내용>`을 돌려주므로 왕복이 사라진다.
    길이를 먼저 읽고 그만큼 채워 읽는 모양이라 `LspProcess`의 프레이밍과 같고, 유휴일 때
    `LspManager`처럼 닫는다. 데몬 없이 데몬의 이점 하나를 가져오는 자리다.
- **git 거터:** Web에서 `Chunk.build(기준, 버퍼)`로 추가, 수정, 삭제를 줄 번호 옆의 가는 세로줄로 표시한다. 세 레이어 모두에 적용한다.
- **`LspProcess`:**
  - exec로 `cd <root> && exec <command>`를 `$SHELL -lc`로 감싸 실행한다. 사용자의 PATH를 쓰기 위해서다.
  - stdio에 Content-Length 프레이밍을 한다.
  - 메시지는 브리지로 Web의 `@codemirror/lsp-client` Transport에 넘긴다. **Transport는 `send`,
    `subscribe`, `unsubscribe` 셋뿐이고 프레이밍을 모른다.** 그래서 브리지에는 LSP JSON을
    **문자열 그대로** 실어 보낸다. Kotlin은 그 문자열의 바이트에 헤더만 붙여 stdout에 쓰면 되고
    파싱할 일이 없다(M0에서 확인).
- **위치는 UTF-16 코드 단위다.** `@codemirror/lsp-client`는 CM6 오프셋을 그대로 LSP 위치로 쓰고
  `positionEncoding`을 협상하지 않는다. 다만 `general.positionEncodings`를 광고하지도 않으니
  명세상 서버는 UTF-16을 써야 한다. **실제 pyright를 SSH exec로 띄워 확인했다**(M0):
  `positionEncoding`이 응답에 없고(= `utf-16`), `값 = "한글" + 1`의 진단 범위가 char 4..12로
  UTF-16 인덱스와 정확히 맞았다(UTF-8이면 4..18이다). 그래도 `LspManager`는 `initialize` 응답을
  확인하고, `utf-16`이 아니면 그 서버를 끄고 이유를 알린다.
- **동기화는 incremental(`textDocumentSync: 2`)을 쓰는 서버만 값이 있다.** Full이면 편집이 멈출
  때마다 문서 전체가 브리지와 SSH를 지나간다. 2MB 파일에서 didChange가 246자 대 199만자, 편집에서
  진단까지 591ms 대 744ms였다(둘 다 `autoSync`의 500ms 디바운스 포함, M0에서 확인). pyright는
  incremental을 준다. Full만 하는 서버는 큰 파일에서 편집 중 동기화를 끄는 것을 검토한다.
- **exec + Content-Length 프레이밍은 동작한다.** MINA의 `ProcessShellCommandFactory`를 서버로,
  sshj의 exec 채널을 클라이언트로 두고 실제 pyright를 띄워 initialize 98ms, 진단까지 346ms였다
  (로컬 루프백이라 네트워크 지연은 없다, M0에서 확인). 이 하네스가 M6의 시작점이다.
- **요청 기본 타임아웃은 3초다.** 원격 서버에는 짧으니 `LSPClient`의 `timeout`을 설정에서 올린다.
- **`LspManager`:**
  - (프로젝트, 언어)마다 서버 하나를 둔다. 그 언어 파일을 처음 열 때 띄운다.
  - 유휴 10분이 지나거나, 앱이 오래 백그라운드에 있거나, 프로젝트가 비활성화되면 `shutdown`→`exit` 후 채널을 닫는다.
  - 연결이 끊기면 다시 띄우고 열린 문서에 `didOpen`을 다시 보낸다.
- **기본 LSP 명령:** python은 `pyright-langserver --stdio`, 없으면 `pylsp`. js/ts는 `typescript-language-server --stdio`, markdown은 `marksman server`. `command -v`로 없으면 조용히 끈다. 문서 URI는 `file://<원격 절대경로>`다.
- **Web 쪽 붙이는 방법:** `new LSPClient({rootUri, extensions: languageServerExtensions()})`를
  만들고 `LSPPlugin.create(client, uri, languageID)`를 에디터 확장에 넣는다. `languageServerSupport`는
  deprecated이고 진단이 빠져 있다. 진단은 `languageServerExtensions()`에 든 `serverDiagnostics`가
  `@codemirror/lint`로 넣으므로 lint 확장을 따로 넣을 필요는 없다.

### 보안 규칙 (커밋 전 두 번째 읽기 대상)

- **브리지:**
  - `WebViewCompat.addWebMessageListener`의 허용 origin은 `https://appassets.androidplatform.net` 하나뿐이다.
  - `addJavascriptInterface`는 쓰지 않는다.
  - 번들은 `WebViewAssetLoader`로만 로드하고 file:// 접근은 끈다. 로더는 `/assets/web/` 아래만 내주고, 그 밖의 요청은 모두 빈 403으로 막는다(외부 네트워크에 닿지 않는다).
  - CSP 메타 태그를 넣는다: `default-src 'none'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:`. 마크다운 속 외부 이미지(추적 픽셀)도 여기서 막힌다.
  - 메시지는 메인 프레임에서 온 것만 받는다.
  - 페이지는 다른 곳으로 이동하지 않는다. 링크는 `shouldOverrideUrlLoading`에서 가로채 http, https, mailto만 다른 앱으로 넘기고, `intent:` 같은 나머지는 버린다.
  - `setWebContentsDebuggingEnabled`는 debug 빌드에서만 켠다.
- **원격 파일 내용은 권한 있는 브리지와 같은 페이지에 렌더링된다.** 그래서 마크다운의 raw HTML은 끄고, 링크는 `shouldOverrideUrlLoading`에서 외부 브라우저로 넘긴다.
- **exec:**
  - `:core`, `:app`, Skiff Code의 단일 파일 모드에는 지금처럼 **exec가 없다.**
  - exec는 `:code`의 `session/RemoteExec` 한 곳에만 있고, 사용자가 만든 프로젝트에서만 열린다.
- **ProfileProvider:** 서명 권한으로 보호하고 비밀번호는 절대 내보내지 않는다.

## 범위 밖

원격 데몬, LSP 서버 자동 설치, 키/keyboard-interactive 인증, https 앱링크, 분할 pane,
git 커밋/스테이징 같은 쓰기 작업, 로컬 프로젝트.

**데몬을 다시 꺼내는 조건.** 데몬이 exec보다 실제로 나은 것은 아래 셋뿐이다(2026-09-18 분석).
서술로는 판정할 수 없으니 **M6에서 실기기로 재고 그 수치를 여기에 적는다.** 임계값은 실측 전에
정하지 않는다. 데몬으로 가게 되면 그때 전제는 사용자당 에이전트 하나다.

| 데몬이 이기는 것 | M6에서 잴 것 |
|---|---|
| 네트워크가 바뀌어도 LSP가 살아남는다 (가장 큰 명분, exec로는 대체 없음) | 와이파이↔LTE 전환 후 재접속부터 진단이 다시 뜰 때까지 걸린 시간, 그중 서버 재인덱싱이 차지하는 몫 |
| inotify 파일 감시 | 열린 파일 N개일 때 2초 폴링이 쓰는 왕복 수와 배터리, N이 늘 때의 기울기 |
| 트리 탐색 왕복 감소 | 프로젝트 트리를 펼칠 때 디렉터리당 SFTP `ls` 왕복 시간 |

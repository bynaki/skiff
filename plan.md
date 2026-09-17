# Skiff Code

코드 뷰어이자 에디터인 **독립 앱**이다. Skiff에서 "나중에"로 미뤄둔 미리보기 뷰어가 이 앱이 됐다.
원래 요구사항은 [`skiff.code.plan.md`](skiff.code.plan.md)에, 화면 설계 손그림은
[`menu.layout.jpg`](menu.layout.jpg)에 있다. 이 섹션은 두 문서를 설계로 풀고 할 일 목록으로 나눈 것이다.

## 이어받는 방법

- 시작 전에 `AGENTS.md`를 읽는다. 툴체인 제약과 "Before committing" 규칙은 여기서도 그대로 적용된다.
- **아래 체크리스트에서 위에서부터 처음 나오는 `- [ ]`가 다음 할 일이다.** 한 항목은 한 세션에서 끝내고 커밋할 수 있는 크기로 잡았다.
- 항목을 끝내면 `[x]`로 바꾸고 그 변경을 **같은 커밋에** 넣는다.
- 막히면 `[ ]`는 그대로 두고 항목 아래에 `  - 막힘: <이유>`를 한 줄 적는다.
- 세션을 시작할 때 [`HANDOFF.md`](HANDOFF.md)에서 직전 세션의 맥락을 읽고, 끝낼 때 그 파일을 현재 상태로 덮어쓴다.
- 각 단계의 마지막 "확인" 항목이 통과해야 다음 단계로 넘어간다.
- 설계와 다르게 구현하게 되면 이 문서의 설계 부분도 같은 커밋에서 고친다. 이 문서가 늘 현재 상태를 말해야 한다.

## 정한 것

1. **같은 레포, 멀티모듈.** 공통 SSH/FS 계층을 `:core`로 빼고 `:app`(Skiff)과 `:code`(Skiff Code)가 함께 쓴다.
2. **원격 데몬 없이 SSH exec로 시작한다.**
   - 단일 파일 모드는 Skiff처럼 SFTP만 쓴다.
   - exec 채널은 사용자가 만든 **프로젝트 안에서만** 열고, 서버에 이미 있는 `git`과 LSP 서버(`--stdio`)를 직접 실행한다.
   - 데몬은 뒤로 미룬다. exec 쪽 클래스는 인터페이스 뒤에 두어 나중에 데몬으로 바꿀 수 있게 한다.
3. **UI 전체를 WebView 하나에 올린다.** 상단 메뉴, 사이드바, 커맨드 팔레트, 레이어를 모두 TypeScript와 CodeMirror 6으로 만든다. Kotlin은 파일, SSH, git, LSP를 맡는 백엔드 브리지다. 테마는 CSS 변수 한 벌로 전체에 적용된다.
4. **서버 프로필만 공유한다.**
   - Skiff가 서명 보호(`protectionLevel="signature"`) ContentProvider로 비밀이 아닌 정보(별칭, host, port, user, startPath, 알려진 호스트키)를 내보낸다.
   - Keystore 키는 앱마다 따로라서, 비밀번호는 Skiff Code가 처음 한 번 입력받아 자기 Keystore에 저장한다.

**가정** (틀렸으면 여기부터 고칠 것):
- diff와 git 거터의 기본 비교 대상은 **HEAD와 현재 버퍼**다. "이 파일을 건드린 직전 커밋과 HEAD"는 더보기(⑤) 메뉴에서 바꾸는 두 번째 비교 대상이다.
- 로컬 파일은 두 경로로 받는다. Skiff에서는 `skiffcode:///절대경로`로 받고(MANAGE_EXTERNAL_STORAGE 사용), 다른 앱에서는 표준 `ACTION_VIEW`/`ACTION_EDIT`의 `content://`로 받는다. 로컬은 항상 단일 파일 모드다.
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

### URI

```
원격  skiffcode://alice@192.0.2.10:22/home/alice/demo/a.md?alias=home-server&line=42&layer=viewer
로컬  skiffcode:///storage/emulated/0/Documents/a.md
```
- 경로는 퍼센트 인코딩한다. 선택 쿼리는 `alias`, `line`, `col`, `layer`(viewer|editor|diff)다.
- **`user:password@`는 거부한다.**
- 프로필은 `alias` 일치 → `(user, host, port)` 일치 순으로 찾는다. 없으면 "알 수 없는 서버" 확인창에서 열기 여부, 필요한 정보(비밀번호), 프로필로 저장할지를 묻는다.
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
  - UTF-8로 읽다가 대체 문자가 나오면 **EUC-KR로 폴백**한다.
  - 원래 인코딩, CRLF, 끝 줄바꿈을 기억해 두고 저장할 때 되돌린다.
- **저장 (`DocumentSaver`):**
  - 쓰기 직전에 `stat`해서 불러올 때의 mtime/size와 비교한다. 다르면 충돌 배너를 띄운다.
  - 같으면 제자리에 truncate 쓰기를 한다. 임시 파일에 쓰고 rename하는 방식은 소유권, 하드링크, 심링크를 깨서 쓰지 않는다.
  - 쓴 뒤 다시 `stat`해서 자기 쓰기를 외부 변경으로 오인하지 않게 한다.
- **감시 (`FileWatcher`):**
  - 원격: 보이는 파일은 약 2초마다 SFTP `stat`으로 폴링한다. 나머지 열린 파일은 활성화되거나 앱으로 돌아올 때 확인한다. 폴링은 browse 연결을 막지 않도록 별도 연결에서 한다.
  - 로컬 경로는 `FileObserver`, `content://`는 폴링한다.
  - 버퍼가 깨끗하면 diff를 트랜잭션으로 적용해서 커서와 스크롤을 유지한다. 수정 중이면 "다시 불러오기 / 내 것 유지" 배너를 띄운다.
- **열린 파일:**
  - EditorView는 하나만 두고, 파일마다 `EditorState`와 레이어별 스크롤 위치를 보관한다. 전환은 즉시 되고 undo 기록도 남는다.
  - 상한은 설정값이고 기본 30개다. 넘으면 가장 오래된 *깨끗한* 파일부터 내리고, 수정 중인 파일은 내리지 않는다.

### 레이어

- `pane`은 파일과 1:1이고 전체화면이다. viewer, editor, diff가 레이어로 겹쳐 있다.
- **viewer:**
  - 읽기 전용 CM6에 하이라이팅, 줄 번호, git 거터를 보여준다.
  - 마크다운은 `markdown-it`(`html: false`)으로 렌더링하고 테마를 적용한다.
  - 스크롤과 확대/축소를 가장 먼저 잘 만든다. 핀치 줌은 CSS 변수 `--code-font-size`를 바꾸고, 손가락 사이의 줄이 제자리에 있게 한다.
  - 줌 중심 줄 고정은 CM6의 `EditorView.scrollIntoView(pos, {y: 'start', yMargin})` 효과로 한다. CM6가 줄 높이를 다시 잰 뒤 적용하기 때문이다. `requestMeasure`에서 `scrollTop`을 직접 쓰면 CM6의 스크롤 앵커와 싸워서 수만 px씩 어긋난다(M0에서 확인).
- **editor:**
  - viewer와 같은 `EditorState`를 쓰고, `Compartment`로 readOnly와 확장만 바꾼다.
  - 글자 크기 ±는 하단 메뉴에 둔다.
  - LSP 진단과 자동완성을 붙인다.
- **diff:**
  - 읽기 전용 unified 뷰다. 현재 버퍼를 문서로 두고 `@codemirror/merge`의 `unifiedMergeView({original, mergeControls: false})`로 비교 대상과 비교한다. 지운 줄은 블록 위젯으로 끼워진다.
  - **diff 알고리즘은 줄 단위로 먼저 하고, 바뀐 줄 범위 안에서만 글자 단위로 한다(`diffConfig.override`).** 패키지 기본값(`scanLimit` 500)은 큰 파일에서 파일 전체를 청크 하나로 포기하고, 제한을 풀면 2MB에 6.7초가 걸리며 그래도 부정확하다. 줄 단위는 2MB에 약 240ms다(M0에서 확인). git 거터의 `Chunk.build`도 같은 설정을 쓴다.
  - +/- 기호는 별도 거터로 그린다. 추가된 줄은 `lineMarker`, 지운 줄 블록은 `widgetMarker`에 지운 줄 수만큼 `-`를 쌓는다. 초록/빨강 배경의 투명도는 CSS 변수 `--diff-alpha`로 조절한다. 하이라이팅도 한다.
  - **`@codemirror/view` 6.43.12에 버그가 있다.** 블록 위젯이 줄 경계에 있으면 `HeightMapBranch.forEachLine`이 범위를 clamp하지 않아서 `viewportLineBlocks`가 화면 밖 수천 줄로 늘어난다. 모든 거터가 그만큼 요소를 만들어서 줌 한 단계가 5배 이상 느려진다. 두 곳에 `Math.max(from, …)`/`Math.min(to, …)`를 넣으면 고쳐진다. 사용자는 "upstream에 알리고 고쳐지기 전까지 `patch-package`로 패치"를 골랐다. 그런데 CodeMirror는 AI가 쓴 코드를 받지 않아서 PR은 보낼 수 없고, 이슈는 `code.haverbeke.berlin/codemirror/dev`에서만 받는다. 사용자가 직접 이슈를 올릴지는 **아직 확인받지 않았다.** `patch-package` 패치는 M5 diff 레이어에서 적용한다.
  - 스크롤과 줌은 viewer 코드를 그대로 쓴다.

### 화면 메뉴 (`menu.layout.jpg`를 글로 옮김)

**상단 메뉴** (왼쪽 ①, 오른쪽 묶음 ②③④⑤)
- ① 사이드바 버튼. 사이드바가 왼쪽에서 밀려 나온다. 열린 파일, 프로젝트 목록, 프로젝트 파일 트리를 보여준다. 사이드바 버튼이나 바깥을 누르면 밀려 들어간다.
- ② 레이어 확대, ③ 레이어 축소.
- ④ viewer → editor → diff 순환 토글. 아이콘이 현재 레이어에 맞게 바뀐다.
- ⑤ 더보기. 지금은 자리만 만들어 둔다(비교 대상 선택 등이 들어갈 곳).
- 레이어를 아래로 스크롤하면 메뉴가 위로 숨고, 위로 스크롤하면 다시 나타난다.

**커맨드 버튼과 팔레트** (VS Code 커맨드 팔레트 방식)
- **A:** 우하단 버튼. 평소에는 50% 투명도이고, 누르면 100%가 되며 B로 간다.
- **B:** 입력 커서가 활성화되고 버튼은 "입력 버튼"이 된다.
  - 빈 곳을 누르는 등으로 취소하면 A로 돌아간다.
  - 입력 버튼을 위아래로 스와이프하면 모양과 모드가 바뀐다: `>` 커맨드 검색, 🔍 파일 검색, `@` 심볼 검색.
- **C:** 입력을 시작하면 퍼지 검색 결과가 뜨고 맨 위 항목이 기본 선택이다.
  - Enter나 입력 버튼으로 실행하고 A로 돌아간다.
  - A로 돌아갈 때 버튼 모양은 마지막 모드를 유지한다.
- 파일 검색: 프로젝트에서는 `git ls-files` 캐시를, 단일 파일에서는 열린 파일과 같은 디렉토리를 대상으로 한다.
- 심볼 검색: 단일 파일에서는 lezer 구문 트리를, 프로젝트에서는 LSP를 쓴다.

### 설정과 테마

- `settings.toml`: 폰트, 폰트 크기, 탭 크기, 줄바꿈, 테마, diff 투명도, 크기 상한, 열린 파일 상한, 폴링 주기, LSP 명령.
- `themes/*.toml`: `[ui]`(메뉴, 사이드바, 팔레트), `[editor]`, `[syntax]`, `[diff]`. Web에서 CSS 변수로 바꾸고 **메뉴와 레이어에 똑같이** 적용한다. 다크와 라이트를 기본 번들한다. 잘못된 값은 기본값으로 폴백하고 오류를 알린다.
- 설정과 테마의 import/export는 SAF(`ACTION_OPEN_DOCUMENT` / `ACTION_CREATE_DOCUMENT`)로 한다.
- TOML 파싱은 Kotlin의 ktoml로 한다. M0에서 컴파일이 안 되면 Web의 `smol-toml`로 옮긴다.

### git과 LSP (프로젝트 모드)

- **`RemoteExec`:**
  - 프로젝트별로 exec 전용 SSHClient를 둔다(`SshClientFactory`). SFTP의 단일 스레드 dispatcher와 분리해서 오래 도는 채널이 탐색을 막지 않게 한다.
  - **명령은 argv 리스트로만 만들고 `ShellQuote`로 인자마다 작은따옴표로 감싼다.** 경로는 URI에서 오는 외부 입력이다.
- **`GitService`:**
  - `git -C <root> show HEAD:<rel>`: 비교 기준 텍스트.
  - `git log -n 2 --format=%H -- <rel>`: 두 번째 비교 대상.
  - `git ls-files -co --exclude-standard -z`: 파일 검색.
  - 결과는 HEAD 해시를 키로 캐시한다.
- **git 거터:** Web에서 `Chunk.build(기준, 버퍼)`로 추가, 수정, 삭제를 줄 번호 옆의 가는 세로줄로 표시한다. 세 레이어 모두에 적용한다.
- **`LspProcess`:**
  - exec로 `cd <root> && exec <command>`를 `$SHELL -lc`로 감싸 실행한다. 사용자의 PATH를 쓰기 위해서다.
  - stdio에 Content-Length 프레이밍을 한다.
  - 메시지는 브리지로 Web의 `@codemirror/lsp-client` Transport에 넘긴다.
- **`LspManager`:**
  - (프로젝트, 언어)마다 서버 하나를 둔다. 그 언어 파일을 처음 열 때 띄운다.
  - 유휴 10분이 지나거나, 앱이 오래 백그라운드에 있거나, 프로젝트가 비활성화되면 `shutdown`→`exit` 후 채널을 닫는다.
  - 연결이 끊기면 다시 띄우고 열린 문서에 `didOpen`을 다시 보낸다.
- **기본 LSP 명령:** python은 `pyright-langserver --stdio`, 없으면 `pylsp`. js/ts는 `typescript-language-server --stdio`, markdown은 `marksman server`. `command -v`로 없으면 조용히 끈다. 문서 URI는 `file://<원격 절대경로>`다.

### 보안 규칙 (커밋 전 두 번째 읽기 대상)

- **브리지:**
  - `WebViewCompat.addWebMessageListener`의 허용 origin은 `https://appassets.androidplatform.net` 하나뿐이다.
  - `addJavascriptInterface`는 쓰지 않는다.
  - 번들은 `WebViewAssetLoader`로만 로드하고 file:// 접근은 끈다.
  - CSP 메타 태그를 넣는다.
  - `setWebContentsDebuggingEnabled`는 debug 빌드에서만 켠다.
- **원격 파일 내용은 권한 있는 브리지와 같은 페이지에 렌더링된다.** 그래서 마크다운의 raw HTML은 끄고, 링크는 `shouldOverrideUrlLoading`에서 외부 브라우저로 넘긴다.
- **exec:**
  - `:core`, `:app`, Skiff Code의 단일 파일 모드에는 지금처럼 **exec가 없다.**
  - exec는 `:code`의 `session/RemoteExec` 한 곳에만 있고, 사용자가 만든 프로젝트에서만 열린다.
- **ProfileProvider:** 서명 권한으로 보호하고 비밀번호는 절대 내보내지 않는다.

---

## 할 일

### 0. 문서화
- [x] 이 계획을 `plan.md`에 체크리스트로 적고 `AGENTS.md`에서 가리키게 하기
- [x] `skiff.code.plan.md`, `menu.layout.jpg`를 레포에 커밋하기 (이 문서가 두 파일을 참조한다)
- [x] 인수인계 문서 `HANDOFF.md` 작성

### M0. 스파이크: 전부 WebView에 붙는지 먼저 확인
버리는 코드로 확인만 한다. 결론과 확정된 라이브러리 버전은 `AGENTS.md`에 남긴다.
- [x] `:code` 최소 앱: WebView 하나 + `WebViewAssetLoader` + `addWebMessageListener`로 JSON-RPC 한 번 왕복
- [x] `code/web` Vite+TS 프로젝트와 Gradle `buildWeb` Exec 태스크(inputs/outputs 지정, `preBuild`에 연결, 증분 빌드 확인)
- [x] CM6로 2MB 파일 스크롤 성능과 핀치 줌(`--code-font-size`, 줌 중심 줄 고정)을 실기기(갤럭시탭 S10 FE, SM-X526N)에서 확인
  - 자동 측정 통과(`HANDOFF.md` 참조), 사용자가 탭에서 직접 스크롤과 핀치 줌을 해 보고 좋다고 확인했다.
- [x] `@codemirror/merge`로 +/- 거터와 초록/빨강 줄의 읽기 전용 unified diff를 그리는 방법 확인
- [ ] `@codemirror/lsp-client` Transport를 브리지로 대체할 수 있는지 확인
- [ ] ktoml이 Kotlin 2.4.20 / AGP 9에서 컴파일되는지 확인. 안 되면 설계의 TOML 줄을 `smol-toml`로 고치기
- [ ] **확인:** 위 결과를 `AGENTS.md`에 적고, 막힌 것이 있으면 설계를 고친 뒤 넘어간다

### M1. `:core` 추출 (Skiff 동작 변화 없음)
- [ ] `:core` 라이브러리 모듈 추가, 공통 코드와 해당 테스트 이동(패키지명 유지, slf4j 런타임 규칙 동일하게 적용)
- [ ] `KnownHostStore` 인터페이스 도입. `HostKeyGate`가 이것에 의존하고 `SkiffStore`가 구현한다(`data.first()`로만 읽는다는 규칙을 KDoc에 옮기기)
- [ ] `SshConnection.ensureConnected`의 접속, 인증, 호스트키 부분을 `SshClientFactory`로 분리
- [ ] `AGENTS.md`의 명령, 구조, 테스트 경로를 멀티모듈 기준으로 갱신
- [ ] **확인:** `./gradlew :core:testDebugUnitTest :app:testDebugUnitTest :app:lintDebug` 통과. 실기기에서 Skiff의 SFTP 탐색과 전송이 이전과 같다

### M2. 골격 + URI 인텐트 + 단일 파일 viewer
- [ ] `SkiffCodeUri` 파서 + `SkiffCodeUriTest`(퍼센트 인코딩, IPv6, 비밀번호 거부, alias 우선순위, 로컬 형식)
- [ ] `SkiffCodeStore`(DataStore JSON): 프로필(비밀번호 암호문), knownHosts, 최근 파일
- [ ] 인텐트 필터(`skiffcode` VIEW, `text/*` VIEW/EDIT)와 OpenRequest 해석, 알 수 없는 서버 확인창, 비밀번호 입력, 호스트키 다이얼로그
- [ ] `TextLoader` + `TextLoaderTest`(크기 상한, NUL 판별, EUC-KR 폴백, CRLF와 끝 줄바꿈 기억). MINA로 원격 읽기(한글 파일 포함)
- [ ] `WebBridge`(Kotlin)와 `bridge.ts` RPC 정식 구현, 보안 규칙(origin, CSP, 링크 가로채기) 적용
- [ ] viewer 레이어: 읽기 전용 CM6(하이라이팅, 줄 번호), 핀치 줌, 마크다운 렌더링(`html: false`)
- [ ] 상단 메뉴 ①~⑤ 자리와 스크롤에 따른 숨김/표시(①④⑤는 이후 단계에서 채운다)
- [ ] Skiff 쪽: `onOpen`에서 CODE/TEXT/MARKDOWN이면 `skiffcode://` 인텐트를 보내고, Skiff Code가 없으면 기존 외부 앱으로 열기
- [ ] Skiff 쪽: 서명 권한 `ProfileProvider` + Skiff Code가 읽어 프로필과 호스트키에 반영(지문이 다르면 경고 흐름)
- [ ] **확인:** `adb shell am start -a android.intent.action.VIEW -d 'skiffcode://…'`, Skiff에서 파일 탭, 파일 매니저의 "다른 앱으로 열기"를 각각 실기기에서 확인

### M3. editor, 저장, 실시간 반영, 열린 파일
- [ ] editor 레이어: `Compartment`로 viewer↔editor 전환, 레이어별 스크롤 보존, ④ 토글 순환과 아이콘 연결, 하단 글자 크기 ±
- [ ] `DocumentSaver` + `DocumentSaverTest`(MINA: mtime 충돌 감지, 인코딩과 CRLF 왕복)
- [ ] `FileWatcher`: 원격 폴링(별도 연결), `FileObserver`, `content://` 폴링. 깨끗한 버퍼는 병합하고 수정 중이면 배너
- [ ] 사이드바(①): 슬라이드 인/아웃, 열린 파일 목록과 전환
- [ ] 열린 파일 `EditorState` LRU(기본 30, 수정 중인 파일은 제외) + vitest
- [ ] **확인:** 서버에서 `echo >> file` 하면 2초 안에 반영되고, 수정 중에는 배너가 뜬다. 저장 후 서버의 `git diff`에 의도한 변경만 보인다

### M4. 커맨드 팔레트, 설정, 테마
- [ ] 커맨드 버튼 A/B/C 상태 기계 + vitest(취소하면 A, 실행 후 모드 유지)
- [ ] 스와이프 모드 전환(`>` / 🔍 / `@`)과 퍼지 점수(`fuzzy.ts`) + vitest
- [ ] 커맨드 레지스트리(레이어 전환, 확대/축소, 저장, 사이드바, 설정 열기 등)와 파일 모드(열린 파일 + 같은 디렉토리), 단일 파일 심볼 모드(lezer)
- [ ] `settings.toml` 로드와 적용 + `SettingsTomlTest`(왕복, 없는 키는 기본값, 잘못된 값)
- [ ] `themes/*.toml` → CSS 변수 매핑(메뉴와 레이어 전체), 다크/라이트 번들, 폴백 + vitest
- [ ] 설정과 테마 import/export(SAF)
- [ ] **확인:** 실기기에서 팔레트 흐름을 녹화하고, 테마를 바꾸면 메뉴, 사이드바, 팔레트, 세 레이어가 한 번에 바뀐다

### M5. 프로젝트 모드 + git
- [ ] `GitScopeFinder` + `GitScopeFinderTest`(MINA: 홈 경계에서 멈춤, worktree의 `.git` 파일, 홈 밖 경로는 찾지 않음)
- [ ] `ProjectStore`와 파일 여는 흐름 2~3단계(프로젝트 활성화, 묻는 창), 사이드바에 프로젝트 목록과 SFTP 지연 로딩 파일 트리
- [ ] `ShellQuote` + `ShellQuoteTest`(`'; rm -rf ~'`, 줄바꿈, `$()`, 백틱, 작은따옴표가 든 경로)
- [ ] `RemoteExec`(프로젝트 전용 SSHClient, exec 거부 감지) + `AGENTS.md`의 exec 원칙을 "프로젝트 모드의 RemoteExec 한 곳만" 허용으로 수정
- [ ] `GitService` + `GitServiceTest`(MINA에 `ProcessShellCommandFactory`를 붙여 **실제 `git`**을 임시 레포에 대해 실행)
- [ ] git 거터(viewer, editor, diff 공통)
- [ ] diff 레이어: unified, +/-, 초록/빨강 투명도 설정, 하이라이팅, viewer와 같은 스크롤/줌. ⑤ 더보기에 비교 대상 선택 추가
- [ ] 🔍 파일 모드를 프로젝트에서 `git ls-files` 캐시로 확장
- [ ] **확인:** 실기기에서 git 레포 안 파일을 열면 묻는 창이 뜨고, 프로젝트를 만든 뒤 거터와 diff가 서버의 `git diff`와 일치한다. internal-sftp 계정에서는 git 없이 열린다

### M6. LSP
- [ ] `LspProcess` Content-Length 프레이밍 + `LspFramingTest`(분할과 결합, 멀티바이트 길이)
- [ ] `LspManager`: 지연 시작, 유휴와 백그라운드 종료, 재연결 후 `didOpen` 재전송, `command -v` 탐지
- [ ] Web `@codemirror/lsp-client` 연결: editor(진단, 자동완성, hover, 정의), viewer(길게 눌러 hover, 정의로 이동, 다른 파일이면 열기)
- [ ] `@` 심볼 모드를 프로젝트에서 LSP `documentSymbol`/`workspace/symbol`로 확장
- [ ] MINA exec + 로컬 LSP 서버(또는 에코 스텁)로 initialize 왕복 테스트
- [ ] **확인:** 실제 서버의 파이썬 프로젝트에서 정의 이동, 진단, 심볼 검색이 동작하고, 앱을 백그라운드에 오래 두면 원격 LSP 프로세스가 종료된다(`ps`로 확인)

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

## 범위 밖

원격 데몬, LSP 서버 자동 설치, 키/keyboard-interactive 인증, https 앱링크, 분할 pane,
git 커밋/스테이징 같은 쓰기 작업, 로컬 프로젝트. 데몬은 다음 중 하나가 실제로 문제가 되면
따로 계획한다(그때 전제는 사용자당 에이전트 하나):
- 열린 파일이 많아 폴링 부하가 크다.
- 네트워크가 바뀔 때마다 LSP가 재시작되고 인덱싱 비용이 크다.
- 파일 트리 탐색이 느리다.

---

# Skiff 다음 계획

## 배경

Skiff는 처음 요구사항을 거의 다 구현한 상태다. 남은 건 처음부터 "나중에"로 잡아뒀던
**미리보기 뷰어** 하나다. 탐색, 생성·이름변경·삭제, 사용자가 켜고 방향을 고르는
2분할, 그리고 모든 방향의 파일 전송은 구현이 끝났고 실기기에서 실제 OpenSSH 서버를 상대로
검증까지 마쳤다.

이 계획은 뷰어와 함께, 1차 구현이 남기고 간 구멍들을 다룬다. 그중 셋은 **설계는 해놓고
배선을 안 한 자리**라, 그 위에 기능을 더 얹기 전에 닫는 게 맞다.

시작하기 전에 `AGENTS.md`를 먼저 읽을 것 — 거기 적힌 툴체인 제약들은 하나같이 실제로 빌드를
한 번씩 깨뜨린 것들이다.

---

## 1. 미리보기 뷰어 → Skiff Code로 넘어감

뷰어는 Skiff 안에 넣지 않고 독립 앱 **Skiff Code**로 만든다. 설계와 할 일은 이 문서 맨 위
`# Skiff Code` 섹션에 있다. 여기 있던 읽기 규칙(크기 상한, NUL 바이트로 바이너리 판별, EUC-KR
폴백)은 그 섹션의 `TextLoader`로 옮겼다. Skiff 쪽에 남는 일은 `onOpen`에서 Skiff Code로
인텐트를 보내는 것 하나다(Skiff Code M2).

---

## 2. 충돌 정책이 설계돼 있는데 도달할 수가 없다

`ConflictPolicy`에는 값이 네 개 있다. 그런데 `TransferQueue.runJob`이 두 군데
(`transfer/TransferQueue.kt:91,101`)에서 `KEEP_BOTH`를 하드코딩하고 있어서, `ASK`와
`OVERWRITE`는 **프로덕션에서 죽은 값**이다.

지금은 같은 이름의 파일이 있으면 무조건 조용히 `file (1).txt`가 만들어진다. 안전한 기본값이긴
하지만, 파일 매니저가 **오직 그것만** 할 수 있어서는 안 된다.

`CopyEngine`은 대화형이 아닌 세 정책을 이미 구현했고 테스트도 있다. 그래서 남은 건 UI와
배선뿐이다:

- 덮어쓰기 / 건너뛰기 / 둘 다 유지를 고르는 충돌 다이얼로그. "나머지에도 적용" 체크박스 포함.
- `ASK`는 엔진이 실행 도중에 멈춰서 답을 기다려야 한다. **`HostKeyPrompter`와 같은 패턴**으로
  간다 — `CompletableDeferred`를 쓰는 프로세스 범위 프롬프터. 전송이 충돌을 만났을 때 화면이
  떠 있지 않을 수 있기 때문이다.
- 고른 정책을 `TransferJob`에 실어둔다. 그래야 대기 중이거나 재개된 작업이 답을 기억한다.

---

## 3. 쓰이지 않는 표면: 연결하든 지우든 결정할 것

존재하는데 아무도 안 쓰는 게 둘 있다. **각각 배선하거나 삭제해야 한다 — 지금처럼 두는 게 둘
중 어느 쪽보다도 나쁘다.**

- **`FileSystem.freeSpace`** — 호출자가 0개다. 원래 의도는 사전 점검이었다. 큰 전송이 90%에서
  실패하는 대신 시작하자마자 실패하도록. 참고로 `SftpFileSystem.freeSpace`는 의도적으로
  null을 반환한다(SFTP 기본 프로토콜에 해당 호출이 없다). 따라서 이 점검은 **로컬이 목적지일
  때만** 동작하는데, 그게 곧 흔한 다운로드 경우이므로 여전히 값어치가 있다.
  `CopyEngine.plan`에 넣고, 바이트가 움직이기 전에 `FsError.NoSpace`를 던지게 한다.
- **`androidx.window`** — `libs.versions.toml`에 선언돼 있는데 import한 곳이 한 군데도 없다.
  원래는 분할선을 폴드 힌지에 스냅시키려던 것이었다. `ui/workspace/SplitContainer.kt`에서
  `WindowInfoTracker`/`FoldingFeature`로 구현하거나, 아니면 의존성을 뺀다. 폴더블 폰도 지원 대상이라
  가치는 있다. 다만 지금 실기기는 힌지가 없는 갤럭시탭 S10 FE라서 폴더블 기기 없이는 동작을
  확인할 수 없다 — 어느 쪽이든 **안 쓰는 의존성이 카탈로그에 남아 있어선 안 된다**.

---

## 4. 자잘한 것들

- **`.kotlin/`이 gitignore에 없다.** 빌드 산출물이니 추가할 것.
- **원격 심링크 아이콘이 틀렸다.** `SftpFileSystem`은 모든 항목에
  `linkTargetIsDirectory = false`를 넣는다. 링크마다 해석하면 행당 왕복이 한 번씩 더 들기
  때문이다. 탭했을 때 제대로 들어가는 건 앞서 고쳤지만, 심링크된 디렉토리가 여전히 파일
  아이콘으로 보인다. 선택지: 행이 화면에 들어올 때 지연 해석하거나, 심링크를 별도로 표시하고
  타입을 암시하는 걸 그만두거나.
- **권한 편집이 없다.** `setPermissions`는 원래 설계에 있었는데 빠졌다. `FileNode`는 이미
  `mode`를 갖고 있고 속성 다이얼로그가 표시도 한다. 편집 가능하게 만드는 건 인터페이스에
  메서드 하나 추가 + `SFTPClient.chmod` / `Files.setPosixFilePermissions` 정도다.
- **원격 속성에 소유자·그룹이 없다.** SFTP는 uid/gid를 알려주는데 다이얼로그가 안 보여준다.

---

## 검증 방법

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17
./gradlew :app:testDebugUnitTest
./gradlew :app:installDebug
```

- **뷰어**: Skiff Code 섹션의 단계별 "확인" 항목으로 옮겼다.
- **충돌 정책**: `CopyEngine`은 덮어쓰기 / 건너뛰기 / 둘 다 유지에 대해 이미 가짜 파일시스템
  기반 테스트가 있다. 여기에 전송 도중 `ASK`에 답한 결과가 나머지에도 적용되는 케이스를 더한다.
- **실기기**: 반대편에 이미 같은 이름이 있는 파일을 전송해서 세 선택지가 각각 제대로
  동작하는지 확인한다. 마크다운 파일, 소스 파일, 큰 로그를 양쪽 패널에서 열어본다.
  `adb logcat -s Skiff:V`를 켜둘 것 — **UI가 메시지 한 줄로 삼킨 실패는 전부 여기 찍히므로,
  조용하다는 게 곧 신호다.**

## 이번 계획에서 명시적으로 빼는 것

키 기반 인증과 keyboard-interactive 인증(`AuthMethod`에 확장 지점은 있고 지금은 예외를
던진다), 뷰어 안에서의 편집, 실패한 전송의 재개, 검색, 패널 간 드래그 앤 드롭, 압축 해제.
각각이 별도 계획이 필요한 크기다.

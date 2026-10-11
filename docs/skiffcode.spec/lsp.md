# LSP (프로젝트 모드)

[목차](../skiffcode.spec.md)

- **`LspProcess`:**
  - 열린 exec 채널만 받는다. 명령 줄은 `LspManager`가 만든다.
  - stdio에 Content-Length 프레이밍을 한다(`LspFraming`). 헤더 줄이 아닌 것(콜론 없는 줄)이 오면 `LspProtocolError`로 끝낸다 — 로그인 셸의 프로필이 stdout에 무언가 찍는 경우다. 연결은 멀쩡하므로 `IOException`이 아니다.
  - **stderr를 계속 읽어 버리고 마지막 4KB만 남긴다.** stderr는 stdout과 채널 창을 나눠 쓰므로, 읽지 않으면 로그를 많이 쓰는 서버는 창이 차서 응답을 멈춘다. 남긴 끝부분은 서버가 왜 안 떴는지 알릴 때 쓴다.
  - 보낼 메시지는 큐에 넣고 쓰기 코루틴 하나가 순서대로 쓴다. 그래서 메인 스레드에서 보내도 막히지 않는다.
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
- **요청 타임아웃은 `settings.toml`의 `[lsp] timeout_seconds`, 기본 10초다**(2026-10-05 사용자 결정, 범위 1~120). 라이브러리 기본 3초는 원격에 짧다. `initialize`만은 60초다 — 프로젝트의 첫 `initialize`는 접속, exec 확인, `command -v`를 다 거친 뒤에야 서버가 뜨고, 거기서 시간 초과가 나면 클라이언트가 못 쓰게 되고 서버를 다시 띄우게 된다. 라이브러리가 타임아웃을 private으로 들고 있어 초기화가 끝난 뒤 바꿔 넣는다(`lsp.ts`).
- **`LspManager`:**
  - (프로젝트, 서버 종류)마다 서버 하나를 둔다(`LanguageServer`: Python, TypeScript(js/ts/jsx/tsx), Markdown). 프로젝트 세션이 하나씩 갖고(`ProjectSession.languageServers()`), **exec가 안 되는 계정(`NoExec`)에는 없다** — 강제 `internal-sftp`는 `command -v`에도 빈 출력과 0으로 답할 수 있어서 탐지만으로는 못 가른다.
  - 명령은 **계정의 로그인 셸로** 돌린다: `sh -c 'exec "${SHELL:-/bin/sh}" -lc "$1"' sh '<스크립트>'`. sshd의 `-c`만으로는 프로필을 읽지 않아 nvm·pyenv 같은 버전 관리자의 PATH가 빠진다. 탐지(`command -v <이름>`)와 실행(`cd <root> && exec <명령>`)이 같은 셸을 거친다. 탐지 결과는 서버 종류마다 한 번 묻고 기억한다(실패한 확인은 다시 묻는다).
  - **서버는 페이지의 `initialize`가 띄운다. 다른 메시지는 서버를 띄우지 않고, 돌지 않는 서버로 가는 메시지는 버린다.** 서버가 끝나면 페이지에 이유(`LspEnd`)를 알리고, 페이지는 다시 `initialize`로 시작한다. 이미 도는 서버에 `initialize`가 오면 갈아 끼운다 — 페이지가 다시 만들어진 경우다.
  - **재연결 후 `didOpen` 재전송은 페이지의 클라이언트가 한다.** `LSPClient.connect`는 `initialize`를 보내고 곧 `Workspace.connected()`로 열린 파일마다 지금 버퍼로 `didOpen`을 보낸다(`@codemirror/lsp-client` 6.3.0 소스에서 확인). 그래서 Kotlin은 문서 내용을 들고 있지 않는다.
  - 메시지를 해석하는 것은 `initialize`와 그 응답(`positionEncoding` 확인)과 우리가 보낸 `shutdown`의 응답뿐이다. `initialize`는 문자열에 `"initialize"`가 있을 때만 파싱해서 고른다.
  - 끝나는 이유(`LspEnd`): `Idle`(유휴 10분), `Stopped`(`stopAll`, 앱이 오래 떠나 있었을 때), `Closed`(프로젝트를 지웠거나 프로필이 바뀌어 세션이 닫힘), `NotInstalled`, `Unsupported`(utf-16이 아닌 위치 단위), `Failed`(스스로 끝남, 끊김, 시작 실패. stderr 끝부분을 싣는다).
  - **앱이 백그라운드에 10분 있으면 `stopAll`한다**(2026-10-05 사용자 결정). `MainActivity.onStop`이 시계를 걸고 `onStart`가 끈다. 유휴 종료와 같은 10분이라, 앱이 떠나 있는 동안 페이지가 아무것도 보내지 않으면 대개 유휴 쪽이 먼저 온다.
  - 명령은 `settings.toml`의 `[lsp.python]`·`[lsp.typescript]`·`[lsp.markdown]`의 `command`(프로그램과 인자 배열)다. 기본값은 그 종류의 첫 명령이고, **기본값 그대로면 뒤의 명령도 차례로 찾는다**(python의 `pylsp`). 다른 값을 적으면 그것만 쓴다. 탐지 결과는 명령 목록을 키로 기억하므로 설정을 바꾸면 다시 찾는다. 빈 배열이나 프로그램 이름이 빈 것은 기본값으로 바꾸고 알린다.
  - 프로젝트의 세션을 얻지 못하면(프로젝트·프로필이 없음, `NoExec`) `NotInstalled`로, 예외면 `Failed`로 알린다. **`initialize`에만 알리고 그 뒤 메시지는 버린다** — 메시지마다 알리면 페이지가 다시 띄운 클라이언트를 뒤늦은 알림이 끝낸다.
  - 유휴와 `stopAll`은 `shutdown`(응답을 3초까지 기다림)→`exit`(끝나기를 1초까지 기다림) 후 채널을 닫는다. **프로젝트를 지울 때는 주고받지 않고 바로 닫는다** — 연결이 곧 끊기고 그러면 서버 쪽에서도 끝난다.
  - **링크가 끊겨 끝난 서버는 다음에 그 서버를 띄우기 전에 원격에서 끈다**(2026-10-06 사용자 결정). 폰이 와이파이에서 LTE로 넘어가면 옛 연결의 끝이 서버에 닿지 않아, sshd는 TCP keepalive가 포기할 때까지(리눅스 기본 2시간 남짓) 세션과 언어 서버를 들고 있다. 폴드8에서 원격 서버에 pyright가 1시간 반 남아 있던 것을 보고 정했다. 그래서 시작 스크립트는 `exec` 전에 `echo skiff-lsp-pid $$`를 찍고(`exec`가 PID를 그대로 두므로 서버의 PID다) `LspManager`가 메시지보다 먼저 그 줄을 읽는다. 다른 줄이 먼저 오면 프로필이 stdout에 찍은 것이라 전처럼 실패다. `Disconnected`로 끝난 서버의 PID를 기억했다가 다음 시작 때 같은 연결로 `kill`을 보낸다. **새 서버가 PID를 알린 뒤에 나란히 보낸다**(2026-10-06 사용자 결정) — 앞에 두면 그 왕복(폴드8에서 0.47초)만큼 진단이 늦었다. 새 서버가 옛 PID를 받았으면 보내지 않는다: 이름이 같아 아래 확인이 새 서버를 옛 것으로 안다. 새 서버가 뜨지 못했거나 `kill`하는 사이 링크가 또 끊기면 그 PID는 다음 시작으로 넘어간다. `ps -o args=`에 그 프로그램 이름이 아직 있을 때만, 그 PID가 이끄는 프로세스 그룹 전체(pyright는 python 래퍼가 node를 띄운다)에 `kill -s TERM --`(dash가 받는 표기), 그룹이 아니면 그 PID에만. sshd가 세션의 명령을 그룹의 맨 앞으로 만든다. PID는 1보다 커야 한다 — `kill -1`은 그 계정의 모든 프로세스다. 서버 쪽 감시(하트비트로 스스로 끝나게)나 sshd 설정은 고르지 않았다: 앞의 것은 실행 줄과 LSP 스트림을 건드리고, 뒤의 것은 사용자의 서버를 고치는 일이다.
- **기본 LSP 명령:** python은 `pyright-langserver --stdio`, 없으면 `pylsp`. basedpyright는 후보에 넣지 않는다(2026-10-06 사용자 결정) — 쓰려면 `[lsp.python] command`로 정한다. js/ts는 `typescript-language-server --stdio`, markdown은 `marksman server`. `command -v`로 없으면 끈다. **찾고 띄울 때 PATH는 `<프로젝트>/.venv/bin`, 로그인 셸의 PATH, `~/.local/bin` 순이다**(2026-10-06 사용자 결정, VS Code처럼 프로젝트의 것이 먼저). 원격 Ubuntu 서버에서 pyright가 uv tool로 `~/.local/bin`에, 프로젝트 `.venv/bin`에도 있었는데 `~/.local/bin`을 PATH에 넣는 줄이 `.zshrc`에만 있어 로그인 셸(`zsh -lc`)이 못 찾았다. 대화형 셸은 `.zshrc`를 읽지만 그 서버는 시작할 때 fastfetch를 stdout에 찍어 LSP 메시지와 섞인다. 파일을 열 때는 조용하고, **그 파일에서 두 번 탭이나 F12로 무언가를 물을 때 배너에 잠깐 "이 프로젝트의 서버에서 Python 언어 서버를 찾지 못했습니다."를 띄운다**(2026-10-06 사용자 결정 — 폴드8에서 서버가 없는 프로젝트를 길게 눌러도 아무 일이 없어 고장으로 보였다). `unsupported`나 실패로 멈춘 서버도 물을 때 그 까닭을 다시 띄운다. 문서 URI는 `file://<원격 절대경로>`다.
- **Web 쪽 붙이는 방법:** `new LSPClient({rootUri, extensions: languageServerExtensions()})`를
  만들고 `client.plugin(uri, languageID)`를 에디터 확장에 넣는다. `languageServerSupport`는
  deprecated이고 진단이 빠져 있다. 진단은 `languageServerExtensions()`에 든 `serverDiagnostics`가
  `@codemirror/lint`로 넣으므로 lint 확장을 따로 넣을 필요는 없다.
- **페이지 쪽(`web/src/lsp.ts`, M6):**
  - 어느 파일이 어느 서버로 가는지는 Kotlin이 문서에 실어 준다(`lsp`: 프로젝트 id, 서버 종류, language id, `rootUri`, `uri`). 프로젝트 안의 파일이고 확장자를 `LanguageServer.forFile`이 알 때만이다. URI는 `vscode-uri`처럼 비예약 문자와 `/` 말고는 UTF-8 퍼센트 인코딩한다(`LspUri`) — 클라이언트가 서버가 말하는 문서를 URI 문자열 비교로 찾기 때문이다.
  - 브리지: 페이지→Kotlin `lspSend {project, server, message}`, Kotlin→페이지 `lspMessage {project, server, message}`, `lspEnded {project, server, reason, message?}`. Kotlin은 `lspSend`를 채널 하나로 받은 순서대로 처리한다 — 세션을 찾는 동안 `initialize`와 바로 뒤의 `didOpen`이 서로 앞지르지 않게.
  - **(프로젝트, 서버 종류)마다 연결 하나, 서버를 띄울 때마다 새 `LSPClient`다.** `disconnect()`는 transport를 놓지 않고 `initializing`만 새로 만들어서, 끊긴 사이 보낸 알림이 다음 서버의 `didOpen`보다 먼저 나간다. 그래서 다시 연결하지 않고 새 클라이언트를 만들고, 화면의 뷰는 LSP compartment의 플러그인을 갈아 끼운다. 새 플러그인이 지금 버퍼로 `didOpen`을 보낸다.
  - **서버에 열려 있는 것은 화면의 파일 하나다**(2026-10-05 사용자 결정). 뷰가 화면의 파일에만 있고 라이브러리의 기본 workspace가 뷰를 따르므로, 파일을 바꾸면 `didClose` 뒤 `didOpen`이다. 저장하지 않은 다른 파일의 편집은 서버가 모른다.
  - **다시 띄우기(`lspPolicy.ts`):** `idle`·`stopped`·`closed`·`disconnected`는 다음에 필요할 때 — 파일이 화면에 올 때, 타이핑, hover나 정의를 물을 때, **앱으로 돌아올 때**(`onStart`의 `lspWake`) — 띄운다. 바로 띄우면 내린 까닭이 없어진다. `disconnected`는 서버가 아니라 **링크가** 끊긴 것이다(sshj 전송·소켓 예외, 다시 붙지 못한 연결): 폴드8을 닫았다 여니 끊긴 링크에서 바로 다시 띄우기가 1초 안에 세 번 실패해 "멈췄습니다 Connection lost"로 포기했다(2026-10-06). 그래서 실패로 세지 않고 조용히 내려 둔다. `notInstalled`·`unsupported`는 페이지가 떠 있는 동안 다시 띄우지 않는다. `failed`는 화면에 그 서버의 파일이 있으면 바로 다시 띄운다(끊긴 뒤 진단이 돌아오게). **30초 안에 끝난 실패가 세 번 이어지면 멈추고** 배너로 알린다. `initialize`가 거절되거나 시간을 넘겨도 `failed`로 친다.
  - **터치(2026-10-05 사용자 결정, 2026-10-06 바꿈):** 읽기 레이어(viewer, diff)에서 단어를 **두 번 탭하면** hover 툴팁을 띄운다. 그 자리의 진단과 `정의로 이동` 버튼이 같이 있다. **툴팁이 떠 있는 동안 그 단어(`wordAt`)의 배경을 선택색(`--editor-selection`)으로 칠한다**(2026-10-06 사용자 결정) — 팔레트의 `Show Hover`도 같다. 두 탭은 손을 뗀 뒤 300ms 안, 24px 안이어야 하고, 그 사이 24px 넘게 움직이면 스크롤로 본다. 한 번 탭은 기다리지 않는다 — 읽기 레이어에서 한 번 탭에 따로 붙은 일이 없다. 세 번째 탭은 처음부터 다시 세서 툴팁을 닫는다. editor에서는 두 번 탭이 단어 선택이라, 팔레트의 `Go to Definition`·`Show Hover`가 커서 자리로 묻는다(LSP가 있는 파일의 코드 뷰에서만 뜬다). 하드웨어 키보드의 F12도 같은 정의 이동이다. 툴팁은 편집, 키, 툴팁 밖 탭에서 닫힌다 — 선택이 바뀌는 것으로는 닫지 않는다. 브라우저가 탭 뒤에 같은 자리로 마우스 누름을 만들어 선택을 옮기는데, CodeMirror가 그것을 DOM에서 읽어 포인터가 한 것으로 표시하지 않기 때문이다(탭에서 봤다).
    - 처음에는 길게 누르기(0.5초)였다. Android가 같은 몸짓에 단어 선택과 메뉴를 띄워서 `contextmenu`를 막아 가며 다퉈야 했고, 읽기 레이어에서 길게 눌러 복사하는 길이 막혔다. **길게 누르기는 브라우저에 돌려주었다**(2026-10-06 사용자 결정). 페이지가 확대를 꺼 두어(`user-scalable=no`) 두 번 탭은 브라우저가 쓰지 않는다.
  - 라이브러리의 마우스 hover(`hoverTooltips`)는 마우스를 위해 남긴다. 터치 뒤에 브라우저가 만드는 `mousemove`(`sourceCapabilities.firesTouchEvents`)는 뷰에 닿기 전에 막는다 — 안 막으면 탭할 때마다 그 hover가 떴다.
  - **툴팁의 차례와 스크롤(2026-10-12 사용자 결정, `my.next.spec.md` "fix: 01"):** 시그니처 → `정의로 이동` → 진단 → docstring. 긴 docstring이 버튼과 진단을 아래로 밀어내지 않게 하려는 것이다. 시그니처는 hover 내용의 맨 앞 코드 블록(배열이면 앞쪽의 코드 원소들)이고, 그 뒤의 `---`는 docstring 앞의 구분선으로 남긴다. 시그니처가 없으면 버튼이 맨 위다. 툴팁은 높이에 상한을 두고 그 안에서 스크롤하며, `overscroll-behavior: contain`으로 끌기가 뒤의 문서로 넘어가지 않게 한다. 전에는 높이 규칙의 선택자(`.cm-tooltip .skiff-hover`)가 걸리지 않아 — CodeMirror는 `create`가 돌려준 요소 자체에 `cm-tooltip`을 붙인다 — 공간이 모자라면 CodeMirror가 줄인 상자 밖으로 내용이 넘쳐 화면 끝에서 잘렸다.
  - **정의로 이동(2026-10-05 사용자 결정):** 커서를 그 줄·열에 두고 줄을 화면 맨 위로 올린다. 같은 파일이면 페이지가 바로 하고, 다른 파일이면 `openDefinition {project, uri, line, col}`으로 Kotlin이 그 줄·열로 연다. 링크의 `line`/`col`도 같은 자리에 커서를 둔다(`col`이 없으면 줄 첫머리, 1부터 세고 UTF-16 단위). **프로젝트 밖(표준 라이브러리 등)의 정의도 경로를 묻지 않고 연다** — 사용자가 앱 안에서 고른 것이라 팔레트의 파일과 같다.

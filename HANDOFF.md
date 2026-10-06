# 인수인계

다음 세션이 이어받기 위한 문서다. **세션을 끝낼 때마다 이 파일을 현재 상태로 덮어쓴다.**
계속 쌓는 기록이 아니다. 지난 기록은 git 히스토리에 남는다.

- 할 일, 아직 확인하지 않은 것, 알려진 문제: [`docs/skiffcode.plan.md`](docs/skiffcode.plan.md)
- 설계와 사용자와 정한 것: [`docs/skiffcode.spec.md`](docs/skiffcode.spec.md)
- 기기와 환경: [`docs/devices.md`](docs/devices.md)
- 규칙, 툴체인, 커밋 전 점검, 보고와 알림 규칙: [`AGENTS.md`](AGENTS.md)
- 이 문서에 담는 것: 위 문서들에 없는 **직전 세션의 맥락**(무엇을 했고, 무엇이 남았는지)뿐이다.
  세션이 지나도 남는 것은 위 문서들의 제자리에 적는다.

---

## 마지막 세션 (2026-10-05~06)

### 지금 상태
- 브랜치 `plan/skiffcode`(git worktree). M0~M5 완료, **M6 ①②③④ 완료**(계획의 "Web `@codemirror/lsp-client` 연결" 항목이 ③④를 함께 담는다).
- 이번에 사용자와 정한 것 여섯은 계획 그 항목의 첫 줄과 spec "git과 LSP"의 페이지 쪽에 있다.
- 바뀐 것: Kotlin `LanguageServer.forFile`, `LspUri`, `Settings.Lsp`, `LspManager`(탐지 캐시, `Closed`), `ProjectSessions`, `MainActivity`(브리지와 백그라운드 10분). 페이지 `lsp.ts`(M0 스파이크를 대체), `lspPolicy.ts`, `sanitize.ts`, pane, main, commands.
- JVM code 265·app 32·core 38, vitest 129, 린트 경고 11(기준과 같다). 탭에 이번 빌드가 설치돼 있다.
- **이 맥에 `brew install pyright`를 했다**(사용자 동의). 기기 확인용 임시 레포 `~/skiff-lsp-check`(app.py, util.py, 커밋 하나)를
  남겨 두었다 — 다음 항목(⑤ 심볼, ⑥ 확인)에서 다시 쓴다. 탭에 그 프로젝트도 생겼다. 다 쓰면 둘 다 지운다.
- 탭의 화면 꺼짐 시간은 600000으로 되돌렸다.
- 2026-10-06: viewer·diff의 hover를 길게 누르기에서 **두 번 탭**으로 바꿨다(사용자 결정, spec "git과 LSP"의 터치). 탭에서 CDP로 확인했고 이 빌드가 탭에 있다.
  폴드8에는 바꾸기 전 빌드가 있다.
- 폴드8에서 길게 눌러도 툴팁이 안 뜬 것: 그 프로젝트가 Ubuntu 서버에 있고, 로그로 보아 그 서버의 로그인 셸에 Python 언어 서버가 없다
  (`initialize`가 바로 끝나고, 버려진 요청이 60초 뒤 `Request timed out`을 찍는다 — 라이브러리의 `disconnect`가 대기 중 요청을 거절하지 않는다).
  사용자에게 서버에서 `bash -lc 'command -v pyright-langserver pylsp'`를 보라고 했다.
- CDP로 기기를 다루는 스크립트(두 번 탭, 툴팁 버튼, 팔레트 실행)는 세션 scratchpad에 두어 남지 않는다.
  다시 만들 때: Node 24의 전역 `WebSocket`으로 `http://127.0.0.1:9333/json`의 페이지에 붙고, 뷰는
  `document.querySelector('.cm-content').cmTile.view`로 얻는다. 두 번 탭은 `Input.dispatchTouchEvent`
  touchStart, 60ms, touchEnd를 120ms 간격으로 두 번.

### 다음 세션이 할 일
1. `AGENTS.md`를 읽는다.
2. 커밋이 아직이면 사용자에게 "커밋할까요?"를 묻는다.
3. 계획의 첫 `- [ ]`는 M6 ⑤ `@` 심볼 모드를 LSP `documentSymbol`/`workspace/symbol`로 확장이다.
   - 공급원만 바꾼다(spec "심볼 검색": LSP가 붙어 있으면 LSP, 아니면 lezer). `workspace/symbol`을 팔레트에 어떻게
     섞을지(파일 안 심볼과 프로젝트 심볼을 한 목록에? 접두어로 가를지)는 사용자에게 묻는다.
4. 사용자에게 손가락으로 viewer 두 번 탭(툴팁)과 길게 누르기(브라우저 선택이 돌아왔는지)를 해 봐 달라고 할 것.
   언어 서버가 없을 때 물으면 배너를 잠깐 띄우는 것도 넣었다(2026-10-06 사용자 결정, 탭에서 확인).
   폴드8의 원격 Ubuntu 서버의 프로젝트에서 그 배너가 떴다 — 원인은 `~/.local/bin`이 `.zshrc`에만 있던 것. 언어 서버의 PATH에
   `.venv/bin`(앞)과 `~/.local/bin`(뒤)을 붙였다(사용자 결정). 폴드8에서 툴팁이 뜨는지 사용자 확인이 남았다.
   폴드8을 닫았다 열면 "멈췄습니다 Connection lost"가 떴다 — 링크 끊김을 서버 실패로 세어 바로 세 번 다시 띄우다 포기한 것.
   `LspEnd.Disconnected`와 `lspWake`로 고쳤다. hover가 떠 있는 동안 그 단어를 선택색으로 칠한다(사용자 요청).
   폴드8에서 사용자가 둘 다 잘 된다고 했다. basedpyright는 후보에 넣지 않기로 했다(사용자 결정).
5. 보지 않은 것: 2MB 파일에서 diff 레이어에 들어가는 시간, 큰 프로젝트의 walk 시간, `project_unchecked` 배너.
6. 블로그를 Hugo로 다시 배포하는 일이 생기면, 그 뒤 `https://bynaki.github.io/.well-known/assetlinks.json`이 200인지 본다.

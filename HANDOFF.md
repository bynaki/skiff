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
- 이번 세션의 결정과 확인은 모두 계획 그 항목의 하위 줄과 spec "git과 LSP"에 있다: viewer hover는 **두 번 탭**,
  언어 서버가 없을 때 물으면 배너, PATH에 `.venv/bin`(앞)과 `~/.local/bin`(뒤), 링크 끊김은 `LspEnd.Disconnected`와
  `lspWake`, hover 중인 단어를 선택색으로. basedpyright는 넣지 않는다.
- 정의로 이동할 때 키워드를 잠깐 강조하는 기능은 계획했다가 **사용자가 취소했다**. 다시 꺼내지 않는다.
- JVM code 269, vitest 129. 탭과 폴드8 모두 이번 빌드가 깔려 있고, 폴드8에서 사용자가 확인했다.
- `LspUriTest`의 예시 경로(다른 테스트처럼 `alice`의 홈 아래)는 레포 관례라 그대로 둔다. 커밋 점검의 "home path"에 걸리지만 오탐이다
  (2026-10-06 사용자 결정 — 바꿨다가 되돌렸다).
- 이 맥에 `brew install pyright`가 있고, 기기 확인용 임시 레포 `~/skiff-lsp-check`(app.py, util.py)와 탭의 그 프로젝트가
  남아 있다. ⑤ 심볼과 ⑥ 확인에서 다시 쓴 뒤 둘 다 지운다.

### 다음 세션이 할 일
1. `AGENTS.md`를 읽는다.
2. 계획의 첫 `- [ ]`는 M6 ⑤ `@` 심볼 모드를 LSP `documentSymbol`/`workspace/symbol`로 확장이다.
   - 공급원만 바꾼다(spec "심볼 검색": LSP가 붙어 있으면 LSP, 아니면 lezer). `workspace/symbol`을 팔레트에 어떻게
     섞을지(파일 안 심볼과 프로젝트 심볼을 한 목록에? 접두어로 가를지)는 **시작 전에 사용자에게 묻는다.**
3. 그다음은 M6 ⑥ 확인(백그라운드에 오래 두면 원격 LSP 프로세스가 끝나는지 `ps`로)과 데몬 판정 측정.
4. 사용자에게 부탁할 것: 손가락으로 길게 누르기가 브라우저 선택·복사로 돌아왔는지.
5. 보지 않은 것: 2MB 파일에서 diff 레이어에 들어가는 시간, 큰 프로젝트의 walk 시간, `project_unchecked` 배너.
6. 블로그를 Hugo로 다시 배포하는 일이 생기면, 그 뒤 `https://bynaki.github.io/.well-known/assetlinks.json`이 200인지 본다.

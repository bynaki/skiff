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

## 마지막 세션 (2026-10-05)

### 지금 상태
- 브랜치 `plan/skiffcode`(git worktree). M0~M5 완료, **M6 첫 항목 완료**.
- M6 진행 순서를 사용자와 정했다(2026-10-05): ① `LspProcess`+왕복 테스트 ② `LspManager` ③ 브리지와 editor
  ④ viewer ⑤ `@` 심볼 ⑥ 확인과 데몬 판정 측정. 계획 문서의 M6 체크리스트가 이 순서다.
- 이번에 한 것: `code/.../lsp/LspFraming.kt`(Content-Length 프레이밍, `LspReader`, `LspProtocolError`),
  `lsp/LspProcess.kt`(보내기 큐, 받기 루프, stderr 비우기와 마지막 4KB, `onEnd`), `ExecChannel.stderr`.
  - 테스트: `LspFramingTest` 9개(분할, 결합, 멀티바이트, 끊김, 헤더 아닌 줄), `LspProcessTest` 6개(MINA 위
    인프로세스 스텁과 initialize·didOpen·shutdown·exit 왕복, stderr 8MB, `cat`으로 실제 명령, 배너, 메시지 중간에
    죽음, close).
  - stderr를 비우지 않으면 8MB 테스트가 시간 초과로 실패하는 것을 직접 봤다(되돌려 놓았다).
- 린트 에러 0(경고 11, 기준과 같다), JVM code 247, vitest 121. core·app은 건드리지 않았다.
- 기기에는 설치하지 않았다. 앱 동작은 바뀌지 않았다(`LspProcess`를 아직 아무도 부르지 않는다).
- 탭의 확인용 찌꺼기는 그대로다(프로젝트 `skiff-layer-check`, 열린 파일 `a.py`·`b.md`, 컨테이너 계정 프로필 셋).
- 기기 확인용 도구: 탭의 맥 프로필은 `dev-mac`(LAN 주소)이고 Tailscale 주소가 아니다. 프로젝트로 열리려면
  저장소가 홈 아래에 있어야 한다(`GitScopeFinder`). 이 셸의 `grep`은 함수로 덮여 있어 일부 파일에서 결과가
  비므로 `command grep`을 쓴다.

### 다음 세션이 할 일
1. `AGENTS.md`를 읽는다.
2. 계획의 첫 `- [ ]`는 M6의 `LspManager`다. spec "git과 LSP"의 `LspManager`와 `$SHELL -lc` 실행 방법을 먼저 읽는다.
   명령 줄(`cd <root> && exec <command>`)을 만드는 것은 `LspManager` 쪽이다 — `LspProcess`는 열린 채널만 받는다.
   테스트는 `LspProcessTest`의 `StubServer`를 넓혀 쓰면 된다.
3. ③(editor 연결)을 시작할 때 이 맥에 pyright를 설치해도 되는지 사용자에게 묻는다.
4. 보지 않은 것: 2MB 파일에서 diff 레이어에 들어가는 시간, 큰 프로젝트의 walk 시간, `project_unchecked` 배너.
5. 블로그를 Hugo로 다시 배포하는 일이 생기면, 그 뒤 `https://bynaki.github.io/.well-known/assetlinks.json`이 200인지 본다.

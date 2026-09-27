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

## 마지막 세션 (2026-09-27)

### 지금 상태
- 브랜치 `plan/skiffcode`(git worktree). **main 병합은 하지 않기로 했다**(사용자, 2026-09-26) — 계획에
  적힌 대로 커맨드 레지스트리 ①②③과 그다음 확인 항목이 끝난 뒤에 다시 묻는다.
- 커맨드 레지스트리의 **③ `@` 심볼 모드를 끝냈다**(lezer, LSP가 없을 때의 대체 수단). 이로써 ①②③이 다 끝나
  체크박스를 켰다. 한 일과 기기 확인은 `docs/skiffcode.plan.md`의 그 항목 아래에 있다.
- 탭에 확인용 파일이 남아 있다: 기기의 `/storage/emulated/0/Download/pal/`(a.md, b.py, c.txt, d.txt, sub/,
  sym.py, sym.md, long.py, long.md). 앱에도 그중 몇 개가 열려 있다.
- **탭의 화면 꺼짐 시간을 30분으로 바꿨다**(`settings put system screen_off_timeout 1800000`). 확인 중에 화면이
  잠들어 DevTools가 멈춰서였다. 원래 값은 적어 두지 않았다 — 사용자에게 알렸다.

### 다음 세션이 할 일
1. `AGENTS.md`를 읽는다. 툴체인 제약, Before committing(**점검이 깨끗하면 커밋하고 보고, 아니면 묻는다. push는 늘 먼저 묻는다**),
   보고와 알림 규칙, 그리고 이 파일을 작게 두는 규칙.
2. 그다음 할 일은 계획의 **확인(M3에서 옮겨 왔다)** 항목이다 — 원격(SFTP) 파일을 앱에서 저장하고 서버의
   `git diff`에 의도한 변경만 보이는지. 그게 끝나면 **main 병합을 사용자에게 묻는다**(2026-09-21 결정).

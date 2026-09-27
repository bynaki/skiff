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
- 브랜치 `plan/skiffcode`(git worktree). **2026-09-27 사용자 승인으로 `origin/main`을 이 브랜치로 fast-forward
  했다**(`4a152e2`). 원래 체크아웃(`~/projects/skiff`)의 로컬 `main`은 아직 옛 위치라 `git pull`이 필요하다.
  옛 hand-off 커밋 둘(`19fdc68`, `02842c9`)에 이 맥의 호스트 이름이 남아 있는 것을 알고 병합했다(사용자 결정).
- 커맨드 레지스트리 ①②③과 **그다음 확인 항목(원격 저장)까지 끝났다.** 저장 확인에서 EUC-KR 거부와
  `content://` 읽기 전용 답도 함께 봤다. 결과는 `docs/skiffcode.plan.md`의 그 항목 아래에 있다.
- 탭에 확인용 파일이 남아 있다: 기기의 `/storage/emulated/0/Download/pal/`(a.md, b.py, c.txt, d.txt, sub/,
  sym.py, sym.md, long.py, long.md). 앱에도 그중 몇 개와 이 맥의 임시 레포 파일이 열려 있다.
- 탭의 화면 꺼짐 시간은 300000으로 되돌려 두었다.

### 다음 세션이 할 일
1. `AGENTS.md`를 읽는다. 툴체인 제약, Before committing(**작업이 끝나면 린트와 테스트를 돌리고 커밋할지 묻는다. 커밋하라고 하면 점검하고, 깨끗하면 커밋한다. push는 늘 먼저 묻는다**),
   보고와 알림 규칙, 그리고 이 파일을 작게 두는 규칙.
2. 계획의 첫 `- [ ]`인 `settings.toml` 로드와 적용이다.

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

## 마지막 세션 (2026-09-27, 두 번째)

### 지금 상태
- 브랜치 `plan/skiffcode`(git worktree). `origin/main`은 이 세션 전 커밋(`51308c6`의 부모 `4a152e2`)까지 fast-forward돼 있다.
  원래 체크아웃(`~/projects/skiff`)의 로컬 `main`은 아직 옛 위치라 `git pull`이 필요하다.
- **`settings.toml` 로드와 적용을 끝냈다.** 사용자가 정한 넷(앱 내부 + `Open Settings`, 지금 읽는 키만, 줄바꿈 켬,
  글꼴 배율 따름)과 탭 확인 결과는 `docs/skiffcode.plan.md`의 그 항목 아래에 있다.
- 커밋했다(`settings.toml` 작업 + `docs/devices.md`의 무선 디버깅 포트 찾기 + 이 파일). **push는 하지 않았다.**
- 이 빌드를 폴드8에도 설치했다. 사용자가 손으로 확인하는 중일 수 있다 — 커버 화면 줄바꿈과 `Open Settings`.
- 폴드8 설치는 사용자 개인 스킬 `fold8-install`이 맡는다(레포 밖, `~/.claude/skills/`). Wi-Fi(mDNS) → 테일스케일 포트
  스캔 순으로 찾고, 다 안 되면 APK를 `SendUserFile`로 보낸다. 주소와 시리얼은 그 스킬과 로컬 메모에만 있다.
- 탭에서 시험한 `settings.toml`과 `pal/big.txt`는 지웠다. `pal/`의 나머지 확인용 파일은 그대로다.
- 탭의 화면 꺼짐 시간이 600000이었다(지난 세션은 300000으로 되돌렸다고 적었다). 이번 세션은 건드리지 않았다.

### 다음 세션이 할 일
1. `AGENTS.md`를 읽는다. 툴체인 제약, Before committing(**작업이 끝나면 린트와 테스트를 돌리고 커밋할지 묻는다. 커밋하라고 하면 점검하고, 깨끗하면 커밋한다. push는 늘 먼저 묻는다**),
   보고와 알림 규칙, 그리고 이 파일을 작게 두는 규칙.
2. 계획의 첫 `- [ ]`인 `themes/*.toml` → CSS 변수 매핑이다. `theme` 키는 그 항목에서 `settings.toml`에 더한다.
3. 기기에서 아직 못 본 것: `poll_seconds`·`kept_buffers`가 실제로 먹는지, 폴드8 커버 화면의 줄바꿈.

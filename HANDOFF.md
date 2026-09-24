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

## 마지막 세션 (2026-09-25): 문서 정리만 — 코드 동작은 그대로

### 지금 상태
- 브랜치 `plan/skiffcode`(git worktree). 이번 세션 커밋은 하나이고 push했다. 코드는 주석의 문서 경로만 바뀌었다.
- **직전 커밋의 히스토리를 다시 썼다**(사용자 요청). 옛 `HANDOFF.md`에 테일스케일 주소와 페어링 이름이
  있었다. 그 값이 든 커밋은 `2f3ab81` 하나뿐이어서 그것만 값을 가린 `0b8aa8b`로 바꾸고 force push했다.
  main은 그대로다. **다른 곳에 이 브랜치를 받아 둔 사본이 있으면** `git fetch` 뒤
  `git reset --hard origin/plan/skiffcode`로 맞춘다. GitHub에는 옛 커밋이 SHA로 한동안 남을 수 있다 —
  완전히 지우려면 GitHub Support에 요청해야 하고, 사용자에게 알렸다.
- **`docs/skiffcode.plan.md`의 첫 `- [ ]`는 여전히 커맨드 레지스트리 ②/③이다.** 그 뒤에 상단바 파일 이름과
  저장 상태 점 항목이 설계까지 준비된 채 있다.
- 테스트와 빌드는 돌리지 않았다(문서와 주석만 바뀌었다). 지난 세션 기준으로 vitest 60개와 유닛 테스트가 통과했고,
  **lint는 지난 세션에도 돌리지 않았다.**
- 기기: 지난 세션 그대로다 — 탭에는 최신 빌드가 깔려 있고, **폴드8에는 Skiff Code의 로딩 표시가 깔려 있지 않다.**

### 이번 세션에서 한 것
- **`plan.md`를 `docs/`로 나눴다.** Skiff Code는 `docs/skiffcode.spec.md`(정한 것, 설계, 범위 밖)와
  `docs/skiffcode.plan.md`(이어받는 방법, 체크리스트, 검증 명령), Skiff는 `docs/skiff.spec.md`와
  `docs/skiff.plan.md`로 나눴다. 원래 요구사항 `skiff.code.plan.md`는 `docs/my.skiffcode.spec.md`로
  이름을 바꾸고 `menu.layout.jpg`와 함께 `docs/`로 옮겼다. 코드 주석의 `plan.md "…"`도 새 경로로 고쳤다.
- **이 파일을 줄였다**(291줄 → 50줄 안팎). 세션이 지나도 남는 것은 제자리로 옮겼다 — 확인 못 한 것과 알려진
  문제는 `docs/skiffcode.plan.md`의 새 두 절로, 기기와 환경 노트는 새 파일 `docs/devices.md`로 옮겼다. 결정 표는 spec에 없던
  이유만 spec으로 옮기고 나머지는 지웠다(이미 spec·plan에 있었다). 규칙은 `AGENTS.md`에 적었다.
- **레포에 넣으면 안 되는 값은 레포 밖 로컬 메모에 있다**(사용자 결정). 문서에는 자리표시자만 둔다.

### 다음 세션이 할 일
1. `AGENTS.md`를 읽는다. 툴체인 제약, Before committing(**커밋마다, push마다 사용자에게 먼저 묻는다**),
   보고와 알림 규칙, 그리고 이 파일을 작게 두는 규칙.
2. **로딩 표시를 SFTP 링크로 눈으로 확인한다.** 탭에 이 맥을 가리키는 프로필이 비밀번호까지 저장돼 있어
   링크 하나면 된다. 볼 것: 라인이 200ms 뒤에 뜨는지, 문서가 없을 때 스켈레톤이 함께 뜨는지, 문서가
   그려지는 순간 스켈레톤이 걷히는지.
3. 그다음은 사용자에게 묻는다 — **상단바 파일 이름과 점**(설계가 준비돼 있다)이냐,
   **커맨드 레지스트리 ②/③**(`docs/skiffcode.plan.md`의 첫 `- [ ]`)이냐.
4. **폴드8에 Skiff Code를 깐다.** 기기가 Wi-Fi에 붙으면 무선 디버깅으로 된다 — **페어링은 이미 돼 있다.**
   자세한 것은 [`docs/devices.md`](docs/devices.md)의 "환경".

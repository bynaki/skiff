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

## 마지막 세션 (2026-10-04)

### 지금 상태
- 브랜치 `plan/skiffcode`(git worktree). M0~M5 완료, M6부터 남았다.
- **알려진 문제 8, 9를 풀었다**(사용자 결정, 2026-10-04). 계획의 알려진 문제에 취소선으로 남겼다.
  - 8: 링크의 `layer`를 페이지로 넘긴다. `OpenFlow.Opened`가 `line` 대신 `OpenAt`을 들고, 새 파일은 `document`의
    `layer`로, 이미 열린 파일은 `documentsChanged`의 `layer`로 받는다. `diff`는 pane의 `wantDiff`가 비교 사본을
    기다린다(`code/web/src/layers/pane.ts`). 동작은 spec "URI"에 적었다.
  - 9: `reconcile` 끝에서 `paletteView.refresh()`(`code/web/src/main.ts`).
  - 탭에서 링크로 확인했다: 새 파일 diff+line, 열린 파일 editor+line, 백그라운드 파일 diff, 마크다운 diff,
    프로젝트 아닌 파일 diff→viewer, 팔레트를 연 채 다른 프로젝트 링크.
  - 새로 적은 것: 알려진 문제 10(링크의 `col`이 쓰이지 않는다). 손대지 않았다.
- 린트 에러 0(경고 11, 기준과 같다), JVM core 38·app 32·code 232, vitest 121. 페이지 쪽은 DOM 테스트 환경이 없어
  유닛 테스트를 더하지 않았다.
- 탭과 폴드8에 이 상태의 빌드가 설치돼 있다.
- 탭에 확인용 찌꺼기가 남아 있다: 프로젝트 `skiff-layer-check`(맥에서는 지웠다), 열린 파일 `a.py`·`b.md`·
  스크래치패드의 `a.py`. 그 전부터 컨테이너 계정 프로필 셋과 그 프로젝트 셋도 있다. 지우는 길은 사이드바의
  프로젝트 길게 누르기, 또는 M7의 store 초기화다.
- 기기 확인용 도구: 탭의 맥 프로필은 `dev-mac`(LAN 주소)이고 Tailscale 주소가 아니다. 프로젝트로 열리려면
  저장소가 홈 아래에 있어야 한다(`GitScopeFinder`). 이 셸의 `grep`은 함수로 덮여 있어 일부 파일에서 결과가
  비므로 `command grep`을 쓴다.

### 다음 세션이 할 일
1. `AGENTS.md`를 읽는다.
2. 계획의 첫 `- [ ]`는 M6의 `LspProcess` Content-Length 프레이밍 + `LspFramingTest`다. M6 머리의 재현 메모를 먼저 읽는다.
3. 보지 않은 것: 2MB 파일에서 diff 레이어에 들어가는 시간, 큰 프로젝트의 walk 시간, `project_unchecked` 배너.
4. 블로그를 Hugo로 다시 배포하는 일이 생기면, 그 뒤 `https://bynaki.github.io/.well-known/assetlinks.json`이 200인지 본다.

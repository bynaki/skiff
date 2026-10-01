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

## 마지막 세션 (2026-10-01)

### 지금 상태
- 브랜치 `plan/skiffcode`(git worktree). 이 세션의 커밋 하나(M4 확인과 고친 셋, 이 문서)가 있고
  **push하지 않았다** — origin보다 커밋 1개 앞선다. push는 사용자가 하라고 할 때만 한다.
- **M4의 마지막 항목(확인) — 끝, 체크함.** 결과와 고친 것은 `docs/skiffcode.plan.md` M4 맨 끝 항목에 있다.
  고친 셋: 라이트 테마의 내비게이션 줄(`MainActivity`의 `isNavigationBarContrastEnforced = false`), 팔레트의
  Enter가 편집기에 줄바꿈을 넣던 것(`chrome/palette.ts`), 팔레트 입력칸 포커스 테두리를 `ui.accent`로(`index.html`,
  사용자 결정). 린트 에러 0(경고 11 기존), JVM 169, vitest 94 통과.
- 탭과 폴드8 모두에 이 세션의 마지막 빌드가 설치돼 있다. 두 기기의 설정·테마·다크 모드·화면 꺼짐 시간은 시험 전으로
  되돌렸다. 폴드8은 사용자가 쓰는 설정(가져온 테마 둘, `wrap = false`)이라 백업했다가 md5까지 같게 되돌렸다.
- 탭의 외장 키보드는 세션 끝 무렵 떨어져 있었다(화면 키보드가 올라온다). Enter 수정은 키보드가 붙어 있을 때
  adb `input keyevent`로만 확인했고, 진짜 외장 키보드로는 누르지 않았다.

### 다음 세션이 할 일
1. `AGENTS.md`를 읽는다.
2. 계획의 첫 `- [ ]`는 M5의 `GitScopeFinder` + `GitScopeFinderTest`다(MINA: 홈 경계에서 멈춤, worktree의 `.git` 파일,
   홈 밖 경로는 찾지 않음). M5부터 `exec`가 들어오니 `AGENTS.md`의 exec 원칙(첫 exec는 `RemoteExec`에만)을 먼저 본다.
3. 블로그를 Hugo로 다시 배포하는 일이 생기면, 그 뒤 `https://bynaki.github.io/.well-known/assetlinks.json`이 200인지 본다.

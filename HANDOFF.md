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

## 마지막 세션 (2026-10-01, 세 번째)

### 지금 상태
- 브랜치 `plan/skiffcode`(git worktree). 이 세션의 커밋은 하나다(이 문서 포함). origin보다 커밋 4개 앞서고
  **push하지 않았다** — push는 사용자가 하라고 할 때만 한다.
- M5의 `ProjectStore`와 파일 여는 흐름 2~4단계를 끝냈다: `project/ProjectStore`(프로필 id + canonical 루트, `SkiffCodeData.projects`),
  `project/ProjectSessions`(프로젝트마다 `RemoteExec` 하나, `checkGit` → `GitState`), `OpenFlow.activateProject`, 묻는 창
  `askCreateProject`, git이 안 될 때의 배너(`Opened.notice`).
- 사용자 결정: "단일 파일로 열기"는 **기억하지 않는다**(spec에 적었다). 저장하지 않은 프로필에는 프로젝트를 만들지 않는다(spec).
- 린트 에러 0(code 경고 11, 기준과 같다), JVM code 196(새로 `ProjectStoreTest` 4, `CheckGitTest` 3), vitest 94 통과.
- 탭에 설치해 이 맥의 원격 로그인으로 확인했다(계획 항목 아래에 적었다). **탭의 store에 이 worktree를 루트로 한 프로젝트가 하나 남아 있다** —
  다음 항목(사이드바)에서 쓸 수 있다. 지우는 기능은 아직 없다.
- 폴드8은 무선 디버깅이 닿지 않아(Wi-Fi·테일스케일 둘 다) 설치하지 못하고 debug APK를 사용자에게 보냈다. 폴드8에서 확인했는지는 듣지 못했다.
- 사용자 요청으로 계획 맨 끝에 **M7. 마무리 — 팔레트의 store 초기화 명령**을 넣었다. store에 들어갈 것이 다 정해진 뒤에 하려고
  맨 끝에 뒀다. 범위와 이름은 그 항목에서 사용자에게 묻는다.

### 다음 세션이 할 일
1. `AGENTS.md`를 읽는다.
2. 계획의 첫 `- [ ]`는 사이드바의 프로젝트 목록과 SFTP 지연 로딩 파일 트리다.
   - 활성 프로젝트는 아직 어디에도 들고 있지 않다: `OpenFlow`가 `ProjectSessions`에 세션을 만들고 git을 확인할 뿐, 열린 문서
     (`OpenDocuments.Entry`)나 페이지는 어느 프로젝트인지 모른다. 사이드바가 그것을 처음 필요로 한다.
   - 프로젝트를 지우는 길(`ProjectStore`에 remove가 없다)도 이 항목에서 정한다.
3. 사용자에게 아직 묻지 않은 것: git 없는 프로젝트의 파일 검색 대체(계획의 `ls-files` 항목 "정할 것"), M5 확인에 쓸
   internal-sftp 계정을 어디에 만들지.
4. 블로그를 Hugo로 다시 배포하는 일이 생기면, 그 뒤 `https://bynaki.github.io/.well-known/assetlinks.json`이 200인지 본다.

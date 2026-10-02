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

## 마지막 세션 (2026-10-02)

### 지금 상태
- 브랜치 `plan/skiffcode`(git worktree). 시작할 때 origin과 같았다(push 완료). 이 세션의 작업은 **아직 커밋하지 않았다** —
  커밋은 사용자가 하라고 할 때만 한다.
- M5의 "사이드바에 프로젝트 목록과 SFTP 지연 로딩 파일 트리"를 끝냈다(계획에 체크, 결정은 spec의 ① 사이드바).
  열린 파일이 이제 자기 프로젝트를 든다(`OpenDocuments.Entry.project` = 프로젝트 + 루트 기준 경로).
- 사용자 결정(2026-10-02): 프로젝트가 트리의 맨 위 줄, 화면의 파일을 따라 펼침, 길게 눌러 지우기, `.git`만 숨김.
- 페이지가 보낼 수 있는 것이 넓어졌다: 트리의 파일은 프로젝트 id + 상대 경로로 열고 경로를 묻지 않는다.
  `ProjectTree.resolve`가 루트 밖으로 못 나가게 한다(`AGENTS.md` "Links that arrive from outside"에 적었다).
- 린트 에러 0(code 경고 11, 기준과 같다), JVM code 201(새로 `ProjectTreeTest` 4, `ProjectStoreTest` +1), vitest 102(새로 `tree.test.ts` 8).
- 탭에 설치해 확인했다(계획 항목 아래). 탭의 store에는 이 worktree를 루트로 한 프로젝트가 하나 있다(지우기를 확인한 뒤 다시 만들었다).
- 폴드8에는 이번에 설치하지 않았다. 지난 세션에 보낸 APK를 폴드8에서 확인했는지는 아직 듣지 못했다.

### 다음 세션이 할 일
1. `AGENTS.md`를 읽는다.
2. 계획의 첫 `- [ ]`는 `GitService` + `GitServiceTest`다.
3. 사용자에게 아직 묻지 않은 것: git 없는 프로젝트의 파일 검색 대체(계획의 `ls-files` 항목 "정할 것"), M5 확인에 쓸
   internal-sftp 계정을 어디에 만들지.
4. 지운 프로젝트의 열린 파일은 `Entry.project`를 그대로 든다. 사이드바에는 영향이 없지만(목록에 없는 프로젝트는 무시한다),
   git 거터가 `Entry.project`를 쓰기 시작하면 지운 프로젝트를 어떻게 볼지 정해야 한다.
5. 블로그를 Hugo로 다시 배포하는 일이 생기면, 그 뒤 `https://bynaki.github.io/.well-known/assetlinks.json`이 200인지 본다.

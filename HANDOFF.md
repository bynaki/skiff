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
- 브랜치 `plan/skiffcode`(git worktree).
- M5 항목 둘을 끝냈다(계획에 체크, 기기 확인과 테스트 내역은 각 항목 아래).
  - **사이드바의 프로젝트 목록과 SFTP 지연 로딩 파일 트리.** 결정은 spec의 ① 사이드바(2026-10-02 사용자 결정 넷).
    열린 파일이 자기 프로젝트를 든다(`OpenDocuments.Entry.project`). 트리의 파일은 프로젝트 id + 상대 경로로 열고 경로를
    묻지 않는다 — `ProjectTree.resolve`가 루트 밖을 막는다(`AGENTS.md` "Links that arrive from outside"). 탭과 폴드8에서 확인했다.
  - **`GitService` + `GitServiceTest`.** 설계는 spec의 "git과 LSP". `RemoteExec.start`로 연 `cat-file --batch` 하나로 내용을 읽는다.
    **아직 앱 어디에도 붙어 있지 않다.**
- `AGENTS.md`에 "HANDOFF에는 커밋·푸시 상태를 적지 않는다"를 넣었다(사용자 결정).
- 린트 에러 0(code 경고 11, 기준과 같다), JVM code 214, vitest 102.
- 탭의 store에는 이 worktree를 루트로 한 프로젝트가 하나 있다.

### 다음 세션이 할 일
1. `AGENTS.md`를 읽는다.
2. 계획의 첫 `- [ ]`는 git 거터다.
   - `GitService`를 `ProjectSession`에 붙이고, 세션을 닫거나 바꿀 때 `GitService.close()`도 부른다(소켓에 쓰므로 IO에서).
   - `show`는 바이트를 돌려준다. HEAD 내용은 그 파일과 같은 인코딩으로 읽어야 한다(`TextLoader` 참고).
   - 시작할 때 사용자에게 물을 것: **지운 프로젝트의 열린 파일**(`Entry.project`를 그대로 든다)에 거터를 계속 보일지,
     **HEAD가 바뀐 것을 언제 다시 볼지**(파일 감시 폴링에 붙일지, 전환·앱 복귀 때만 볼지).
3. 사용자에게 아직 묻지 않은 것: git 없는 프로젝트의 파일 검색 대체(계획의 `ls-files` 항목 "정할 것"), M5 확인에 쓸
   internal-sftp 계정을 어디에 만들지.
4. 블로그를 Hugo로 다시 배포하는 일이 생기면, 그 뒤 `https://bynaki.github.io/.well-known/assetlinks.json`이 200인지 본다.

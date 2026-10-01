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

## 마지막 세션 (2026-10-01, 두 번째)

### 지금 상태
- 브랜치 `plan/skiffcode`(git worktree). 이 세션의 커밋은 셋이다 — `GitScopeFinder`, `ShellQuote`, `RemoteExec`(이 문서 포함).
  **push하지 않았다** — origin보다 커밋 3개 앞선다. push는 사용자가 하라고 할 때만 한다.
- M5의 앞 세 항목이 끝났다. 사용자 동의로 M5 순서를 바꿨다: `ShellQuote`와 `RemoteExec`를 `ProjectStore` 앞으로 옮기고
  (활성화가 exec로 git을 확인하므로), `ProjectStore`와 사이드바 트리를 두 항목으로 나눴다.
- 세 항목 모두 JVM 테스트로만 확인했다. **아직 앱 어디에서도 부르지 않으므로 기기에는 아무것도 설치하지 않았다.**
  린트 에러 0(경고 core 15, app 4, code 11 — 기준과 같다), JVM core 38, app 32, code 189 통과.
- `:core`도 조금 바뀌었다: `SshConnection`의 오류 번역을 공개 함수(`toConnectionError`, `isFatalAuth`)로 꺼냈고,
  `SftpTestServer`가 `CommandFactory`를 받는다(안 주면 지금처럼 exec를 모두 거절).

### 다음 세션이 할 일
1. `AGENTS.md`를 읽는다.
2. 계획의 첫 `- [ ]`는 `ProjectStore`와 파일 여는 흐름 2~4단계(프로젝트 활성화와 `command -v git`, 묻는 창)다.
   - `GitScopeFinder.find`, `RemoteExec.supported()`/`run`을 여기서 처음 `OpenFlow`에 붙인다. **exec 판별은 `supported()`로
     하고, 그 뒤에 `command -v git`을 본다** — `command -v` 하나로는 "git 없음"과 "exec 안 됨"을 가를 수 없다(spec).
   - `GitScopeFinder`는 홈 자신의 `.git`도 본다. 홈을 dotfiles 레포로 쓰면 홈 아래 거의 모든 파일에서 묻는 창이 뜬다 —
     "단일 파일로 열기"를 고른 답을 기억할지 이 항목에서 정한다(사용자에게 묻는다).
   - `GitScopeFinder`는 파일 폴더를 `canonicalize`한 경로로 루트를 돌려준다. 2단계 "저장된 프로젝트의 루트 아래인지"도
     같은 기준(canonical)으로 비교해야 링크 경로와 어긋나지 않는다.
   - 경로를 git에 넘길 때는 `--` 뒤에 둔다. `-`로 시작하는 경로는 셸 따옴표로 막을 수 없다(`GitService` 항목에서도 같다).
3. 사용자에게 아직 묻지 않은 것: git 없는 프로젝트의 파일 검색 대체(계획의 `ls-files` 항목 "정할 것"), M5 확인에 쓸
   internal-sftp 계정을 어디에 만들지.
4. 블로그를 Hugo로 다시 배포하는 일이 생기면, 그 뒤 `https://bynaki.github.io/.well-known/assetlinks.json`이 200인지 본다.

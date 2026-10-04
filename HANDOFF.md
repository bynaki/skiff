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
- 브랜치 `plan/skiffcode`(git worktree).
- **M5를 끝냈다.** 마지막 **확인** 항목을 탭에서 통과했다(결과는 계획의 그 항목 아래).
  - internal-sftp·nologin·git 없는 계정은 이 맥의 Docker 컨테이너로 만들었다(사용자 결정, 2026-10-04).
    git 없는 Debian 13 + OpenSSH 10.0. 확인 뒤 컨테이너는 지웠고 Dockerfile은 레포에 넣지 않았다.
    다시 필요하면 같은 구성으로 새로 만든다: `Subsystem sftp internal-sftp`, `Match User … ForceCommand internal-sftp`,
    셸이 `/usr/sbin/nologin`인 계정, 보통 bash 계정, 홈마다 `.git`이 있는 작은 프로젝트, 포트 2222.
    맥의 Docker는 `/usr/local/bin/docker`에 있다(Docker Desktop).
  - 확인 중에 찾은 버그를 고쳤다: `NoExec`/`Missing` 배너가 뜨자마자 `show()`의 `banner.hide()`에 지워졌다.
    페이지의 `notice`가 `refresh`와 같은 `queue`에서 차례를 기다린다(`code/web/src/main.ts`).
  - 실제 OpenSSH 10.0의 강제 `internal-sftp`는 exec에 문구와 1을 돌려줬다. spec "git과 LSP"에 적었다.
- 린트 에러 0(경고 11, 기준과 같다), JVM code 232, vitest 121.
- 탭에 이 상태의 빌드가 설치돼 있다. 폴드8에는 배너 수정 전 빌드다.
- 탭에 컨테이너 계정 프로필 셋(이 맥의 주소, 포트 2222의 sftponly·nologin·nogit)과 그 프로젝트 셋이 남아 있다.
  컨테이너가 없어서 열면 연결이 실패한다. 지우는 길은 M7의 store 초기화다.

### 다음 세션이 할 일
1. `AGENTS.md`를 읽는다.
2. 계획의 첫 `- [ ]`는 M6의 `LspProcess` Content-Length 프레이밍 + `LspFramingTest`다. M6 머리의 재현 메모를 먼저 읽는다.
3. 알려진 문제 9(팔레트가 열린 채로 링크가 다른 파일을 열면 앞 파일의 목록이 남는다)는 사용자에게 알렸고
   고칠지 답을 받지 않았다. **먼저 물어본다.** 알려진 문제 8(링크의 `layer=`)도 손대지 않았다.
4. 보지 않은 것: 2MB 파일에서 diff 레이어에 들어가는 시간, 마크다운 파일의 diff 레이어, 큰 프로젝트의 walk 시간,
   `project_unchecked` 배너.
5. 블로그를 Hugo로 다시 배포하는 일이 생기면, 그 뒤 `https://bynaki.github.io/.well-known/assetlinks.json`이 200인지 본다.

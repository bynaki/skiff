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

## 마지막 세션 (2026-10-03, 오후)

### 지금 상태
- 브랜치 `plan/skiffcode`(git worktree).
- M5 **🔍 파일 모드를 프로젝트로 확장**을 끝냈다(계획에 체크, 결정·테스트·탭 확인은 그 항목 아래).
  - 열린 파일의 프로젝트가 살아 있으면 팔레트의 파일 모드가 프로젝트 전체를 루트 기준 상대 경로로 보여 준다.
    git이 있으면 `ls-files`, 없거나 실패하면 SFTP walk(`ProjectTree.walk`).
  - 사용자 결정(2026-10-03): git이 없으면 walk, 뺄 폴더와 상한은 `settings.toml`의 `[search] skip_dirs`·`max_files`.
    spec "커맨드 버튼과 팔레트"에 적었다. 브리지는 새 메서드 없이 `folder`/`openFromFolder`가 프로젝트를 안다.
  - `AGENTS.md` "Links that arrive from outside"에 팔레트도 프로젝트 상대 경로를 보낸다는 것을 더했다.
- 린트 에러 0(경고 11, 기준과 같다), JVM code 232, vitest 121.
- 탭과 폴드8에 이 상태의 빌드가 설치돼 있다. 폴드8에서 사용자가 프로젝트 파일 검색을 써 보고 확인했다(2026-10-03).
  폴드8의 `settings.toml`에 `[search]` 키가 채워졌는지는 사용자가 `Open Settings`를 열어야 보인다.

### 다음 세션이 할 일
1. `AGENTS.md`를 읽는다.
2. 계획의 첫 `- [ ]`는 M5 **확인** 항목이다. internal-sftp 계정이 필요한데 **어디에 만들지 아직 묻지 않았다 — 먼저 묻는다.**
   그 계정에서 이번 walk(exec가 거부된 경로)도 같이 본다.
3. 알려진 문제 8(링크의 `layer=`가 페이지로 넘어가지 않는다)은 사용자에게 알렸고 손대지 않았다.
4. 보지 않은 것: 2MB 파일에서 diff 레이어에 들어가는 시간, 마크다운 파일의 diff 레이어, 큰 프로젝트의 walk 시간.
5. 블로그를 Hugo로 다시 배포하는 일이 생기면, 그 뒤 `https://bynaki.github.io/.well-known/assetlinks.json`이 200인지 본다.

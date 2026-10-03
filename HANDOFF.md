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

## 마지막 세션 (2026-10-03)

### 지금 상태
- 브랜치 `plan/skiffcode`(git worktree).
- M5 항목 둘을 끝냈다(계획에 체크, 결정·테스트·기기 확인은 각 항목 아래).
  - **diff 레이어.** ③이 viewer → editor → diff를 돈다(비교할 것이 없으면 건너뜀). ④ 더보기에서 HEAD / 직전 커밋을 고른다.
    CM6 버그는 `code/web/patches/`의 `patch-package`. 하다가 `lineDiff`가 파일 끝이 바뀐 경우 문서 밖 위치를 내던 버그를
    고쳤다(그런 파일은 거터도 안 나왔다). 탭에서 `git diff -U0`과 청크를 대조했다.
  - **`settings.toml` 키 채우기**(사용자 요청). 모든 키를 주석 처리된 기본값으로 보여 주고, `Open Settings`로 열 때
    빠진 키를 채운다. 키 목록은 `SettingsToml.KEYS` 하나다. ktoml이 빈 표를 거부해서 `read`가 그런 표 머리를 비운다.
- 폴드8에서 사용자가 본 뒤 바꾼 것(2026-10-03, spec "레이어"와 "설정과 테마"에 결정으로 적었다):
  merge 기본 테마의 2px 밑줄이 이기던 것을 고쳤고, **글자 배경을 없애고 줄 배경만 남겼다**, `diff_alpha`는 0~100·기본 30.
- 린트 에러 0, JVM code 227, vitest 121.
- 폴드8에는 이 상태의 빌드가 설치돼 있다. 폴드8의 `settings.toml`은 아직 `Open Settings`를 열지 않아 `diff_alpha` 줄이 없다
  (열면 채워진다 — 사용자가 직접 확인할 것). 탭의 파일은 확인하면서 채워졌다.
- 폴드8 연결은 개인 스킬 `~/.claude/skills/fold8-install`(`connect.sh`)로 한다. 이번 세션 스킬 목록에 안 보여서 한 번
  손으로 찾았다 — 메모리에 적어 두었다.

### 다음 세션이 할 일
1. `AGENTS.md`를 읽는다.
2. 계획의 첫 `- [ ]`는 **🔍 파일 모드를 `git ls-files` 캐시로 확장**이다. 그 항목의 "정할 것"(git이 없는 프로젝트에서
   SFTP로 하위 폴더를 걷는 대체 길을 둘지, 상한과 제외 규칙)을 **먼저 사용자에게 묻는다.** 계획을 세워 보이고 시작한다.
3. 그다음 M5 **확인** 항목에는 internal-sftp 계정이 필요하다 — 어디에 만들지 아직 묻지 않았다.
4. 알려진 문제 8(링크의 `layer=`가 페이지로 넘어가지 않는다)은 사용자에게 알렸고 손대지 않았다.
5. 보지 않은 것: 2MB 파일에서 diff 레이어에 들어가는 시간, 마크다운 파일의 diff 레이어.
6. 블로그를 Hugo로 다시 배포하는 일이 생기면, 그 뒤 `https://bynaki.github.io/.well-known/assetlinks.json`이 200인지 본다.

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

## 마지막 세션 (2026-10-06)

### 지금 상태
- 브랜치 `plan/skiffcode`(git worktree). M0~M5 완료, **M6 ①~⑤ 완료**. ⑤는 `@` 심볼 모드를 LSP로 넓힌 것이다.
- ⑤의 결정과 이유는 spec "심볼 검색"의 LSP 쪽과 계획 그 항목의 하위 줄에 있다:
  - 한 목록이다(사용자 결정): 이 파일 심볼이 위, 프로젝트 심볼이 경로를 달고 아래.
  - 변수는 파일 맨 위(모듈 수준)에 있을 때만 넣는다.
  - `initialize`에 `workspaceFolders`를 더한다. 이것이 없으면 pyright가 프로젝트 심볼에 늘 빈 목록을 준다.
- **변수 규칙은 내가 정했고 사용자에게 알렸다.** 이 규칙 때문에 클래스 속성(`Box.mine` 등)도 빠진다. 사용자가 넣자고 하면
  `symbols.ts`의 `LSP_VARIABLE` 두 곳을 본다.
- JVM 269, vitest 146, 린트 경고 11(기준과 같다).
- 탭에서 CDP로 확인했다. 폴드8은 adb가 붙지 않아(Wi-Fi·무선 디버깅 꺼짐으로 보임) APK를 보냈고,
  사용자가 손으로 설치해 "잘 돌아간다"고 확인했다.
- 이 맥의 `brew install pyright`, 임시 레포 `~/skiff-lsp-check`(app.py, util.py, 원래대로 되돌려 둠), 탭의 그 프로젝트가
  남아 있다. ⑥ 확인에서 다시 쓴 뒤 둘 다 지운다.
- `LspUriTest`의 예시 경로(`alice` 홈 아래)는 레포 관례라 그대로 둔다. 커밋 점검의 "home path"에 걸리지만 오탐이다
  (2026-10-06 사용자 결정). 이번 `symbols.test.ts`도 같은 `alice` 경로를 쓴다.

### 다음 세션이 할 일
1. `AGENTS.md`를 읽는다.
2. 계획의 첫 `- [ ]`는 M6 ⑥ **확인**이다: 실제 서버의 파이썬 프로젝트에서 정의 이동, 진단, 심볼 검색이 동작하는지,
   앱을 백그라운드에 오래 두면(`onStop` 10분 뒤 `stopLanguageServers`) 원격 LSP 프로세스가 끝나는지 `ps`로 본다.
3. 그다음은 M6 데몬 판정 측정(Wi-Fi↔LTE 전환 뒤 진단이 다시 뜰 때까지, 그중 서버 재인덱싱 몫).
4. 사용자에게 부탁할 것: 손가락으로 길게 누르기가 브라우저 선택·복사로 돌아왔는지. 손가락 스와이프와 좁은 화면에서
   팔레트 경로 표시가 어떤지(폴드8에서 "잘 돈다"까지만 들었다).
5. 보지 않은 것: 큰 프로젝트에서 `workspace/symbol`이 입력을 따라오는 속도, TypeScript 서버의 심볼, 2MB 파일에서 diff
   레이어에 들어가는 시간, 큰 프로젝트의 walk 시간, `project_unchecked` 배너.
6. 블로그를 Hugo로 다시 배포하는 일이 생기면, 그 뒤 `https://bynaki.github.io/.well-known/assetlinks.json`이 200인지 본다.

# 인수인계

다음 세션이 이어받기 위한 문서다. **세션을 끝낼 때마다 이 파일을 현재 상태로 덮어쓴다.**
계속 쌓는 기록이 아니다. 지난 기록은 git 히스토리에 남는다.

- 할 일, 아직 확인하지 않은 것, 알려진 문제: [`docs/skiffcode.plan.md`](docs/skiffcode.plan.md)
  (끝난 마일스톤은 [`docs/skiffcode.plan.done.md`](docs/skiffcode.plan.done.md), 시작할 때 읽지 않는다)
- 설계와 사용자와 정한 것: [`docs/skiffcode.spec.md`](docs/skiffcode.spec.md)(목차). 주제별 파일은 `docs/skiffcode.spec/`에
  있고, 할 일 항목의 `읽을 spec:` 줄에 적힌 것만 읽는다.
- 기기와 환경: [`docs/devices.md`](docs/devices.md)
- 규칙, 툴체인, 커밋 전 점검, 보고와 알림 규칙: [`AGENTS.md`](AGENTS.md)
- 이 문서에 담는 것: 위 문서들에 없는 **직전 세션의 맥락**(무엇을 했고, 무엇이 남았는지)뿐이다.
  세션이 지나도 남는 것은 위 문서들의 제자리에 적는다.

---

## 마지막 세션 (2026-10-26)

### 지금 상태
- **M15 "사이드바 트리에서 바뀐 파일 강조"를 끝내고 `plan.done.md`로 옮겼다.** 성공 조건 여섯 개가 모두 ✅다. 탭(라이트·다크·
  `tokyonight-night`)과 폴드8 커버 화면은 사용자가 스크린샷을 보고 괜찮다고 했다. plan의 "할 일"은 다시 비어 있다.
  - 앱: `GitService.status()`(`--no-optional-locks`), `ProjectSession.changes`, 브리지 `projectChanges`, `tree.ts`의 색 표시를
    만들었다. 테마 키 `ui.git_modified`·`ui.git_untracked`를 더하고 `ThemeCatalog.FORMAT`을 2로 올렸다.
  - 테마 저장소(`~/projects/skiffcode-themes`): 원본 17개를 기록된 커밋에서 다시 읽어 두 키를 넣었다(Neovim 원본은 사용자가 플러그인
    코드 실행을 허락). `FORMAT`은 2다. Nord와 Nightfox 7개를 목록에 더해 53개가 됐다(사용자 요청). `user-themes/`의 두 파일은
    사용자 요청으로 지웠다. `/theme-convert` 스킬과 그 저장소의 `AGENTS.md`도 고쳤다.
  - `AGENTS.md`("Themes live outside the app too")에 테마 키를 바꿀 때 테마 저장소와 스킬에서 볼 것을 더했다.
- **테마 저장소의 `FORMAT` 2(53개)는 push했다**(2026-10-26 사용자 결정). raw.githubusercontent.com에서 `format 2`,
  53개가 나오는 것까지 봤다. 앱에는 아직 릴리스 절차가 없다(GitHub 릴리스·태그 없음, `main`에는 `Download Theme...`가 없다).
  그래서 목록을 읽는 앱은 사용자 기기의 `spec/next` 빌드뿐이고, 탭과 폴드8은 push 전에 FORMAT 2 빌드로 다시 깔았다. 규칙은
  `AGENTS.md`의 "Themes live outside the app too" 끝에 고쳐 적었다.
- 이 맥의 셸에는 `java`가 PATH에 없다. Gradle은 `JAVA_HOME=$(brew --prefix openjdk@17)/libexec/openjdk.jdk/Contents/Home`으로
  돌렸다. npm은 `~/.local/share/fnm/aliases/default/bin`을 PATH에 더해 썼다.
- 탭과 폴드8이 둘 다 adb에 붙어 있으면 `-s <serial>` 없이 부른 adb는 "more than one device"로 실패한다. 탭의 화면은 밀어서
  열리지만 폴드8은 PIN이 있어 사용자가 풀어야 한다.

### 정리가 덜 된 것
- **폴드8에 확인용 프로젝트 `skiff-m15-sample`이 남아 있다.** 폴드8이 PIN으로 잠겨 지우지 못했다. 잠금이 풀리면 사이드바에서
  프로젝트 줄을 길게 눌러 지운다. 그다음 이 맥의 홈 아래 확인용 저장소 `skiff-m15-sample`을 지운다. 탭은 이미 되돌렸다(프로젝트
  지움, `settings.toml` md5 같음, 넣은 테마 파일 지움).

### 다음 세션이 할 일
1. 위의 폴드8 정리.
2. 다음 마일스톤은 사용자에게 묻는다(원래 요구사항은 `docs/my.next.spec.md`, 남은 확인은 plan의 "아직 확인하지 않은 것",
   M15에서 생긴 대비 문제는 plan의 "알려진 문제" 마지막 항목). 커밋도 푸시도 사용자가 말할 때만 한다.

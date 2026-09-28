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

## 마지막 세션 (2026-09-28)

### 지금 상태
- 브랜치 `plan/skiffcode`(git worktree). 이 세션 시작 때 `270457a`까지 커밋돼 있었고 **push는 하지 않았다**(지난 세션 것도 포함).
- **테마(`themes/*.toml` → CSS 변수)를 끝냈다.** 사용자가 정한 셋(`system` 기본, `settings.toml` + 팔레트 `Theme:` 명령, 번들 둘만)과
  탭 확인 결과는 `docs/skiffcode.plan.md`의 그 항목 아래에 있다. 린트와 테스트(JVM 148, vitest 87) 통과.
- 테마 작업은 이 파일과 함께 한 커밋으로 들어갔다. **push는 하지 않았다.**
- 폴드8은 무선 디버깅으로 찾지 못해(Wi-Fi·테일스케일 모두) 이 빌드의 APK를 `SendUserFile`로 보냈다. 폰에 설치됐는지는 모른다.
- 탭에 이 빌드가 설치돼 있고, 탭의 `settings.toml`에는 `theme = "system"`이 들어 있다. 탭은 다크 모드(`cmd uimode night yes`), 원래 상태 그대로다.
- 이 세션 중 탭의 USB adb가 여러 번 끊겼다가 돌아왔다. 기기 명령은 `adb wait-for-device`와 재시도로 감쌌다.

### 다음 세션이 할 일
1. `AGENTS.md`를 읽는다(툴체인 제약, Before committing, 보고와 알림, 이 파일을 작게 두는 규칙).
2. push할지 묻는다(`plan/skiffcode`가 `origin`보다 앞서 있다).
3. 계획의 첫 `- [ ]`인 설정과 테마 import/export(SAF). 사용자 테마를 들이면 `Themes`가 번들 외의 이름도 알아야 하고,
   `ThemeToml.read`의 문제(`SettingsProblem`)를 알릴 말(테마 파일용 `Unreadable`/`UnknownKey` 문구)이 그때 필요하다.
4. 기기에서 아직 못 본 것: 다크의 사이드바·열린 파일 메뉴·배너, 탭 아래 내비게이션 줄 색, `poll_seconds`·`kept_buffers`, 폴드8 커버 화면 줄바꿈.

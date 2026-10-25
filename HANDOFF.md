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

## 마지막 세션 (2026-10-25)

### 지금 상태
- M14 `/theme-convert`는 탭 확인 하나만 남았다. 테마 저장소에서 연 세션이 Nord(VS Code 원본)와 Dawnfox(Neovim 원본, 이름
  `Light (Dawnfox)`)를 끝까지 돌렸고, 이 저장소의 `SKIFF_THEMES_DIR` 테스트로 두 파일이 읽히는 것까지 봤다(plan의 M14).
  그 두 파일은 테마 저장소의 `user-themes/`에 있다(커밋되지 않음).
- `code/build.gradle.kts`: 테스트 입력에 `user-themes/*.toml`을 더했다. 전에는 빠져 있어 새 파일이 생겨도 테스트가 up to date로 남았다.
- 이 맥의 셸에는 `java`가 PATH에 없다. Gradle은 `JAVA_HOME=$(brew --prefix openjdk@17)/libexec/openjdk.jdk/Contents/Home`으로 돌렸다.
- 테마 저장소(`~/projects/skiffcode-themes`): `scripts/generate.ts`의 한 파일 모드(`writeOne`, `single`, `nameOf`,
  CLI `npm run themes -- --one <데이터> --out <이름>.toml`)와 `.claude/skills/theme-convert/SKILL.md`, `AGENTS.md`의 명령 한 줄과
  "One theme for one person" 절. vitest 41.
- 앱: `ThemeTomlTest`가 테마 저장소의 `user-themes/`도 읽는다(있을 때). JVM 353(`SKIFF_THEMES_DIR`를 주면 건너뜀 0), lint 경고 11(그대로).
- 라이선스 전문은 머리 주석이 아니라 **파일 끝에 주석으로** 붙인다(Apache-2.0 전문이 색 위에 200줄 오지 않게). spec과 성공 조건을
  그렇게 고쳤다 — 사용자가 아직 이 선택에 답하지 않았다. 머리 쪽을 원하면 `single`만 바꾸면 된다.

### 다음 세션이 할 일
1. 테마 저장소의 `user-themes/Nord.toml`과 `user-themes/Light (Dawnfox).toml`을 adb로 탭의 `Download/`에 넣고
   `Import Theme`으로 들인다(경고 배너 없음). 견본을 찍어 사용자가 원본과 견주게 한다. Nord는 강조색(`#3b4252`)이 거의
   안 보일 수 있는데, 원본이 그렇게 정한 색이다(plan의 M14 "한 것").
2. 그 성공 조건이 ✅이면 확인 항목에 체크하고 M14를 `plan.done.md`로 옮긴다. 커밋도 푸시도 사용자가 말할 때만 한다.

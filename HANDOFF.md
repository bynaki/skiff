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
- M14 `/theme-convert`의 앞 두 항목을 끝냈다. 남은 것은 "확인: 두 테마로 끝까지, 탭에 들이기" 하나다.
- 테마 저장소(`~/projects/skiffcode-themes`): `scripts/generate.ts`의 한 파일 모드(`writeOne`, `single`, `nameOf`,
  CLI `npm run themes -- --one <데이터> --out <이름>.toml`)와 `.claude/skills/theme-convert/SKILL.md`, `AGENTS.md`의 명령 한 줄과
  "One theme for one person" 절. vitest 41.
- 앱: `ThemeTomlTest`가 테마 저장소의 `user-themes/`도 읽는다(있을 때). JVM 353(`SKIFF_THEMES_DIR`를 주면 건너뜀 0), lint 경고 11(그대로).
- 라이선스 전문은 머리 주석이 아니라 **파일 끝에 주석으로** 붙인다(Apache-2.0 전문이 색 위에 200줄 오지 않게). spec과 성공 조건을
  그렇게 고쳤다 — 사용자가 아직 이 선택에 답하지 않았다. 머리 쪽을 원하면 `single`만 바꾸면 된다.
- 테마 저장소의 `user-themes/`는 지금 비어 있다(없다). 견본 둘로 앱 파서를 확인한 뒤 지웠다.

### 다음 세션이 할 일
1. **스킬은 `~/projects/skiffcode-themes`에서 연 세션에서만 보인다**(프로젝트 스킬은 세션 디렉토리의 `.claude/skills/`에서 읽힌다).
   그 세션에서: 스킬 목록에 `/theme-convert`가 명령으로 보이는지 보고, VS Code 원본 하나·Neovim 원본 하나로 끝까지 돌린다
   (어느 것으로 할지 사용자에게 묻는다, 후보는 Nord와 Nightfox). 돌릴 때마다 `git status --short`가 그대로인지, 예약어 이름을 주면
   다른 이름을 제안하는지 본다. 그 세션은 이 파일과 plan을 읽지 않으므로, 할 일은 사용자가 건네는 프롬프트에 있다.
2. 이 저장소로 돌아와 `SKIFF_THEMES_DIR=~/projects/skiffcode-themes ./gradlew :code:testDebugUnitTest`로 두 파일이 읽히는지 보고,
   adb로 탭의 `Download/`에 넣어 `Import Theme`으로 들인다(경고 배너 없음). 견본을 찍어 사용자가 원본과 견주게 한다.
3. plan의 M14 성공 조건을 채우고, 다 ✅면 M14를 `plan.done.md`로 옮긴다. 커밋도 푸시도 사용자가 말할 때만 한다.

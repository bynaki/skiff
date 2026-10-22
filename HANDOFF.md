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

## 마지막 세션 (2026-10-22)

### 지금 상태
- M13 "앱에서 번들 빼기"를 끝냈다. `code/src/main/assets/themes/`에는 `light.toml`·`dark.toml`과 두 줄짜리 `index.json`만 남았고,
  `code/web/scripts/`, `test/themes.test.ts`, `@types/node`, `npm run themes`, `tsconfig.json`의 `scripts`, `buildWeb`의
  `web/scripts` 입력, `AGENTS.md`의 해당 줄 둘(`@types/node`, 번들 테마 손으로 고치지 않기)과 명령 한 줄을 지웠다.
- `@types/node` 없이도 `tsc`가 통과한다(Vite 타입의 참조는 `skipLibCheck`로 넘어감). `window.setTimeout` 두 곳은 그대로 뒀다.
- JVM 337(건너뜀 1 — `SKIFF_THEMES_DIR` 없을 때), vitest 208(테마 테스트 26개가 빠짐), lint 경고 11(그대로).
  `SKIFF_THEMES_DIR=~/projects/skiffcode-themes`로 `ThemeTomlTest` 12개가 건너뜀 없이 통과.
- 테마 데이터를 고치는 곳은 이제 테마 저장소뿐이다(`~/projects/skiffcode-themes`, `AGENTS.md`의 "Themes live outside the app too").
- 앱 레포의 `code/web`도 vitest 5.0.5로 올렸다(사용자 결정, 5.0.1의 moderate 권고 둘). `npm audit`에는 high 셋이 남았다:
  `markdown-it` 15.0.2(→15.0.3), `source-map-js`(`npm audit fix`), `patch-package`가 끌어오는 `braces` — 손대지 않았다.

### 다음 세션이 할 일
1. `AGENTS.md`를 읽는다.
2. `skiffcode.plan.md`의 첫 `- [ ]`, M13의 "받기 (Kotlin)"부터 한다. 읽을 spec은 M13 머리의 줄이다.
3. 푸시는 사용자에게 묻는다(커밋도 푸시도 사용자가 말할 때만).

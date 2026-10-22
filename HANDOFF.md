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
- M13 "테마 저장소 만들기"를 끝냈다. 공개 저장소 `bynaki/skiffcode-themes`(로컬 `~/projects/skiffcode-themes`)에 테마 45개,
  `index.json`(`format: 1`, `swatch`·`size`·`sha256`), README·LICENSE·NOTICE.md·AGENTS.md, 스크립트와 데이터, 테스트를 올렸고,
  `raw.githubusercontent.com`에서 로그인 없이 45개가 sha256대로 받아지는 것을 확인했다. 자세한 것은 plan의 그 항목.
- 앱 레포에는 `ThemeTomlTest`의 `SKIFF_THEMES_DIR` 테스트(다음 항목 것을 당겨 옴)와 그것을 위한 `code/build.gradle.kts`의 입력만
  더했다. **앱의 번들 45개·스크립트·데이터는 아직 그대로다** — 다음 항목에서 지운다.
- 테마 저장소는 vitest 5.0.5다(5.0.1의 moderate 권고 둘 때문). 앱 레포의 `code/web`은 아직 5.0.1이다 — 올릴지 사용자에게 물어볼 것.
- 이제 테마 데이터를 고치는 곳은 테마 저장소다. 앱 레포의 `code/web/scripts/themes/`를 고치지 않는다(곧 지운다).

### 다음 세션이 할 일
1. `AGENTS.md`를 읽는다.
2. `skiffcode.plan.md`의 첫 `- [ ]`, M13의 "앱에서 번들 빼기"부터 한다. 읽을 spec은 M13 머리의 줄이다.
3. 푸시는 사용자에게 묻는다(커밋도 푸시도 사용자가 말할 때만).

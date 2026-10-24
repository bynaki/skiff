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

## 마지막 세션 (2026-10-22 ~ 10-24)

### 지금 상태
- M13 "받기 (Kotlin)"를 끝냈다. `settings/ThemeCatalog.kt`에 `ThemeCatalog`(`list`, `install`), 주입하는 `Fetch`, 진짜 HTTP인
  `HttpFetch`, 실패 종류 `CatalogError`가 있다. 컨테이너에 `catalog`가 있고, `MainActivity`에 브리지 `themeCatalog`·`downloadTheme`와
  배너 문구(영어·한국어 10개)가 있다. 모양은 plan의 그 항목 "한 것"과 spec의 "받는 것의 검사"에 적었다.
- **페이지는 아직 이 브리지를 부르지 않는다.** `Theme...`·`Download Theme...`와 `PaletteMode`의 두 모드는 다음 항목이다.
- JVM 353(건너뜀 1 — `SKIFF_THEMES_DIR` 없을 때), vitest 208, lint 경고 11(그대로).
- 커밋 점검 스크립트(`.claude/skills/commit/check.sh`)는 이제 민감 값을 `~/private/skiff.values`에서 직접 읽고, 끝까지 돈 실행만
  `--- checked to the end`로 끝난다. 규칙을 바꾸면 `check.test.sh`(64개)를 돌린다.
- 옛 HANDOFF에 이 맥의 호스트 이름이 들어간 커밋 둘이 이미 공개돼 있다. 사용자는 역사를 다시 쓰지 않고 그대로 두기로 했다.
- `code/web`의 남은 high 권고는 `patch-package`가 끌어오는 `braces` 하나다(고치려면 `patch-package`를 6.0.7로 내려야 해서 두었다).

### 다음 세션이 할 일
1. `AGENTS.md`를 읽는다.
2. `skiffcode.plan.md`의 첫 `- [ ]`, M13의 "페이지: `Theme...`와 `Download Theme...`"부터 한다. 읽을 spec은 M13 머리의 줄이다.
   브리지의 답 모양은 "받기 (Kotlin)" 항목의 "브리지" 줄에 있다.
3. 커밋도 푸시도 사용자가 말할 때만 한다.

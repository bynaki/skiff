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
- M13 "받기 (Kotlin)"와 "페이지: `Theme...`와 `Download Theme...`"를 끝냈다. 남은 것은 "확인: 탭, 견본 모음, 폴드8" 하나다.
- Kotlin: `settings/ThemeCatalog.kt`(`ThemeCatalog`, `Fetch`, `HttpFetch`, `CatalogError`), 브리지 `themeCatalog`·`downloadTheme`,
  `settings` 답의 `chosenTheme`.
- 페이지: `palette.ts`의 `theme`·`download` 모드(`isList`, `enter`, `busy`), `chrome/palette.ts`의 색 띠·묶음·표시 그리기,
  `commands.ts`의 `themeItems`·`catalogItems`, `main.ts`의 `downloadTheme`. 모양은 plan의 두 항목 "한 것"에 있다.
- **아직 기기에서 본 적이 없다.** 줄 모양(색 띠 크기, 오른쪽 표시, 묶음 글씨)과 버튼 그림 둘은 탭에서 처음 보게 된다 — 어색하면
  다음 항목에서 고친다.
- JVM 353(건너뜀 1 — `SKIFF_THEMES_DIR` 없을 때), vitest 226, lint 경고 11(그대로).
- 커밋 점검 스크립트(`.claude/skills/commit/check.sh`)는 민감 값을 `~/private/skiff.values`에서 직접 읽고, 끝까지 돈 실행만
  `--- checked to the end`로 끝난다. 규칙을 바꾸면 `check.test.sh`(64개)를 돌린다.
- 옛 HANDOFF에 이 맥의 호스트 이름이 들어간 커밋 둘이 이미 공개돼 있다. 사용자는 역사를 다시 쓰지 않고 그대로 두기로 했다.
- `code/web`의 남은 high 권고는 `patch-package`가 끌어오는 `braces` 하나다(고치려면 `patch-package`를 6.0.7로 내려야 해서 두었다).

### 다음 세션이 할 일
1. `AGENTS.md`를 읽는다.
2. `skiffcode.plan.md`의 첫 `- [ ]`, M13의 "확인: 탭, 견본 모음, 폴드8"을 한다. 성공 조건의 탭 세 항목과 폴드8 항목이다.
   폴드8은 사용자 기기라 받기·바꾸기 전에 묻고, 끝나면 되돌린다. 견본 모음은 스크립트로 만들어 사용자에게 보낸다.
3. 커밋도 푸시도 사용자가 말할 때만 한다.

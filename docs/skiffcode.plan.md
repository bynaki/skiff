# Skiff Code 할 일

설계와 정한 것은 [`skiffcode.spec.md`](skiffcode.spec.md)(목차)와 그 아래 주제별 파일에 있다. 이 문서는 그것을 한 세션 크기의 할 일로 나눈 체크리스트다.
끝난 마일스톤(0, M0~M12, F1, F2)은 [`skiffcode.plan.done.md`](skiffcode.plan.done.md)로 옮겼다.

## 이어받는 방법

- 시작 전에 `AGENTS.md`를 읽는다. 툴체인 제약과 "Before committing" 규칙은 여기서도 그대로 적용된다.
- **아래 체크리스트에서 위에서부터 처음 나오는 `- [ ]`가 다음 할 일이다.** 한 항목은 한 세션에서 끝내고 커밋할 수 있는 크기로 잡았다.
- 항목을 끝내면 `[x]`로 바꾸고 그 변경을 **같은 커밋에** 넣는다.
- 막히면 `[ ]`는 그대로 두고 항목 아래에 `  - 막힘: <이유>`를 한 줄 적는다.
- 세션을 시작할 때 [`HANDOFF.md`](../HANDOFF.md)에서 직전 세션의 맥락을 읽고, 끝낼 때 그 파일을 현재 상태로 덮어쓴다.
  **HANDOFF에는 직전 세션만 둔다.** 세션이 지나도 남는 것은 제자리에 적는다 — 결정과 이유는 spec의 주제 파일, 확인 못 한 것과 알려진 문제는 이 문서의 아래 절, 기기와 환경은 [`devices.md`](devices.md).
- **spec은 전부 읽지 않는다.** 목차와, 지금 마일스톤의 `읽을 spec:` 줄에 적힌 파일만 읽는다. 마일스톤을 새로 적을 때 그 줄을 꼭 둔다.
- **마일스톤마다 성공 조건을 둔다**(2026-10-12 사용자 결정, 규칙은 `AGENTS.md`). 머리 아래, 항목들 앞에 `- ⬜`/`- ✅`로 적는다(`- [ ]`로 쓰지 않는다 — 다음 할 일로 잡힌다). spec의 그 주제 파일 끝 `## 성공 조건`과 같은 것을 말해야 한다. 애매하면 적기 전에 사용자에게 묻는다. **모두 ✅가 될 때까지 마일스톤을 닫지 않는다.** 테스트는 늘, 화면·제스처는 기기로, 페이지·링크는 브라우저로 확인하고, 그 밖에 필요한 것은 사용자에게 요청한다.
- 각 단계의 마지막 "확인" 항목이 통과해야 다음 단계로 넘어간다.
- 마일스톤의 항목이 모두 `[x]`가 되면 그 마일스톤을 [`skiffcode.plan.done.md`](skiffcode.plan.done.md)의 끝으로 옮긴다(같은 커밋에서).
- 설계와 다르게 구현하게 되면 그 주제의 spec 파일도 같은 커밋에서 고친다. 두 문서가 늘 현재 상태를 말해야 한다.

## 할 일

### M13. 커뮤니티 테마 저장소, `Download Theme...`과 `Theme...` 목록

읽을 spec: [`community-themes.md`](skiffcode.spec/community-themes.md). 테마 파일의 규칙은 [`settings-theme.md`](skiffcode.spec/settings-theme.md)의 `themes/*.toml`과 import·복사 항목만, 팔레트 모드는 [`palette.md`](skiffcode.spec/palette.md)의 A·B·C와 최근 목록만.

원래 요구사항은 [`my.next.spec.md`](my.next.spec.md)의 "커뮤니티 테마들 .toml 제공". 사용자 결정(2026-10-22): 15개 저장소의 **모든 변형**(대비 단계 포함), 목록은 **팔레트 안의 목록**, `system`이 따를 테마 고르기는 **이번엔 하지 않음**.

**방향 전환(2026-10-22 사용자 결정):** 번들로 넣던 것을 바꿔, 테마 45개를 **공개 저장소 `bynaki/skiffcode-themes`**에 모으고(README에 원본 링크와 소개) 앱에서 **`Download Theme...`**(`Theme...`와 따로)으로 골라 받는다. 받은 테마는 **사용자 테마로** 저장한다. 앱은 `light`·`dark`만 갖는다. **Ocean은 저장소에 올리지 않고**, **Gruvbox는 지금처럼** README·`package.json`의 MIT로 넣는다. 아래 다섯 [x] 항목은 번들로 넣던 때 한 것이고, 그 스크립트·데이터·테마 파일을 테마 저장소로 옮긴다.

지금(코드로 확인, 2026-10-22): 테마 45개와 생성 스크립트·데이터는 테마 저장소에 있고, 앱의 `code/src/main/assets/themes/`에는 `light.toml`·`dark.toml`과 그 둘을 적은 `index.json`만 있다. `Themes`는 `index.json`의 번들 id(`light`·`dark`)를 예약하고 `settings` 답에 띠를 싣는다. 페이지는 아직 `Theme: …` 명령이다. 앱에는 인터넷 권한과 https로 받는 `doc/WebLoader.kt`가 있다.

**성공 조건**

테마 저장소:

- ✅ 공개 저장소 `bynaki/skiffcode-themes`에 45개 테마, `index.json`, README(묶음마다 원본 링크·소개·라이선스), `LICENSE`, `NOTICE.md`(묶음마다 라이선스 전문)가 있고, 로그인 없이 `index.json`과 테마 파일이 받아진다. (2026-10-22, 로그인 없이 curl로 45개와 sha256)
- ✅ 저장소의 vitest: 생성 결과가 커밋된 `themes/*.toml`·`index.json`·`NOTICE.md`와 같다. 모든 파일에 원본·커밋·라이선스 머리 주석이 있다. `editor.foreground` 대비가 4.5:1 이상이다(`lowContrast` 예외는 실제로 미달일 때만). `base`가 배경의 밝기와 맞는다. `index.json`의 `size`·`sha256`·`swatch`가 파일과 맞는다. 대응표의 키가 `reference/light.toml`과 같다 — 확인: `npm test`. (2026-10-22, 32개 통과)
- ✅ 앱의 파서: `SKIFF_THEMES_DIR`를 준 JVM 테스트에서 45개가 모두 `ThemeToml`로 문제없이 읽힌다. (2026-10-22)

앱:

- ✅ JVM: 번들은 `light`·`dark`뿐이고 예약어는 `system`/`light`/`dark`다. 목록 받기: `index.json`을 읽고(`format`, 줄마다의 검사), 높은 `format`은 거부한다. 테마 받기: 크기 상한, 길이·sha256 불일치, `ThemeToml` 문제가 있으면 저장하지 않는다. 받은 것은 사용자 테마로 저장되고 같은 이름은 확인 뒤 덮어쓴다 — 가짜 HTTP로. `:code` 린트에 새 경고가 없다. (2026-10-24, `ThemeCatalogTest` 12 + `HttpFetchTest` 4, 검사를 하나씩 빼면 테스트가 실패하는 것을 일곱 곳에서 확인)
- ✅ vitest: 커맨드: `Theme: …`가 없고 `Theme...`·`Download Theme...`가 있다. 테마 목록: 설치된 것만, 차례·표시·띠·퍼지 검색, 고르면 `setTheme`과 `>`로 닫힘. 받을 목록: 받는 중 줄, 실패 배너와 `>`, installed 표시, 퍼지 검색, 고르면 받기 요청과 `>` — 확인: `commands.test.ts`, `palette.test.ts`. (2026-10-24, `commands.test.ts` +6, `palette.test.ts` +12)
- ⬜ 탭: `Download Theme...`에 45줄이 색 띠와 함께 보이고 `mocha`를 치면 좁혀진다. 고르면 받아서 메뉴·사이드바·팔레트·viewer·editor·diff·렌더된 마크다운·상태바 아이콘이 한 번에 바뀌고, `settings.toml`에 `theme = "<id>"`가 적히며, 앱을 다시 켜도 남는다. `Theme...`에 그 테마가 표시와 함께 있다. 비행기 모드에서 `Download Theme...`은 배너를 내고 `>`로 돌아온다.
- ⬜ 탭, 사용자 확인: 45개 테마로 같은 견본(Python·TypeScript 파일의 viewer, git 변경이 있는 diff, 렌더된 마크다운)을 찍은 모음을 사용자가 원본 테마와 견주어 보고 괜찮다고 한다. 모음은 스크립트가 테마를 차례로 받아 바꾸며 `screencap`으로 만들고, 끝나면 받은 테마를 지우고 `settings.toml`을 md5로 되돌린다.
- ⬜ 폴드8 커버 화면(글꼴 배율 1.5): 두 목록의 이름과 띠가 잘리지 않고 목록이 스크롤된다. 사용자 기기라 받기·바꾸기는 사용자에게 묻고, 끝나면 되돌린다.

- [x] 번들 목록과 이름 (Kotlin)
  - `assets/themes/index.json`(id, 보이는 이름, 묶음, `base`)을 두고 지금은 `light`·`dark` 두 줄만 넣는다. `Themes.bundled`를 이 파일에서 만든다.
  - `nameOf`가 번들 id를 대소문자 없이 거부한다. 같은 이름의 사용자 파일이 이미 있으면 사용자 것이 이긴다.
  - `settings` 답의 `themes`를 이름 목록에서 `{ id, title, dark, swatch }` 목록으로 바꾼다(띠 색 여섯, `system`은 따르는 번들의 것). 페이지의 `THEME_TITLES`를 지운다.
  - 테스트: `ThemesTest`(예약어, 사용자 것이 이김, 차례, 띠), `ThemeTomlTest`(index의 모든 번들이 키를 다 가짐, index ↔ 파일).
  - 한 것: `nameOf`는 번들 id를 알아야 해서 `Themes`의 메서드가 됐다. 이미 있는 사용자 파일을 읽는 쪽(`listImported`, `ownAt`)은 번들 id를 거부하지 않는 이름 규칙을 따로 쓴다 — 그래야 사용자 것이 이긴다. 가려지는 것은 커뮤니티 번들뿐이다(`light`·`dark`라는 사용자 파일은 원래 없다). 차례는 `index.json`의 순서가 아니라 `Themes.names`가 정한다(`light`·`dark`, 나머지는 보이는 이름 순). 테마 목록은 `pageSettings`가 IO에서 읽는다 — 테마마다 한 번 읽고 캐시하므로 첫 답만 파일을 읽는다. 페이지는 Kotlin이 답하기 전에는 `Theme: …` 명령이 없다(예전에는 세 개를 박아 두었다).
  - 테스트: `ThemesTest` +4(번들 id 거부, 차례와 보이는 이름, 같은 이름이면 사용자 것이 이기고 지우면 번들이 돌아옴, 띠와 `system`의 띠), `ThemeTomlTest` +1(index ↔ 파일, id 모양, `base`)과 기존 1개가 index의 모든 번들을 본다. `commands.test.ts`는 목록 모양만 바꿨다. JVM 336, vitest 208, lint 경고 12(모두 이번에 손대지 않은 줄).
- [x] 생성 스크립트와 첫 묶음 (Catppuccin 4)
  - `code/web/scripts/themes/generate.ts`와 `npm run themes`. 묶음 데이터의 모양(원본 URL·커밋 SHA·키마다 값과 원본 이름), 대응표의 "없을 때" 규칙, `diff.*`를 불투명하게 섞기, 머리 주석, `index.json`과 `NOTICE.md` 쓰기, 대비 표.
  - Catppuccin은 `catppuccin/vscode`의 JSON을 읽어 데이터로 옮긴다. `light`·`dark`의 원문은 바꾸지 않는다(스크립트가 쓰지 않는 파일로 둔다).
  - vitest `themes.test.ts`: 생성 결과 = 커밋된 파일, 머리 주석, `NOTICE.md`, `editor.foreground` 대비 4.5:1.
  - 한 것: 데이터는 `scripts/themes/data/catppuccin.json`(JSON, 색 하나가 한 줄). VS Code 테마를 대응표대로 읽는 `scripts/themes/vscode.ts`를 따로 두었다 — 남은 VS Code 묶음 5개와 M14가 같은 것을 쓴다. Catppuccin은 저장소에 만들어진 JSON이 없어 `catppuccin-vsc-v3.19.0` 릴리스의 `.vsix`에서 읽었다(커밋 `befc9e6`). toml의 색마다 원본 이름을 줄 끝 주석으로 단다(ktoml이 읽는 것은 `ThemeTomlTest`가 본다). `@types/node`(24.19.1, 개발용)를 더했고, 그 때문에 `tsc`가 페이지의 `setTimeout`을 Node 것으로 봐 두 곳을 `window.setTimeout`으로 바꿨다(`AGENTS.md`). `buildWeb`의 입력에 `web/scripts`를 더했다(`tsc`가 본다).
  - 대비(`editor.foreground`·주석·키워드·문자열·정의·타입·숫자): Latte 7.1·3.5·4.8·3.0·4.3·2.3·2.6, Frappé 8.1·4.5·5.6·7.1·5.3·7.6·5.8, Macchiato 9.9·5.3·6.8·9.2·6.6·10.2·7.6, Mocha 11.3·5.8·8.1·11.0·7.8·12.9·9.3. Latte의 문법 색이 낮은 것은 원본 그대로다.
  - 테스트: 새 `themes.test.ts` 17개(커밋된 파일 = 생성 결과, toml이 그것뿐, 머리 주석, `NOTICE.md`의 전문, 대비 4.5:1(`light`·`dark` 포함), id·이름, 대응표 키 = `light.toml`의 키, 없을 때 채우기·scrim·`diff` 섞기·거부 세 가지·서로 가리키는 두 키, WCAG 대비, toml 모양, `vscode.ts`의 스코프 고르기 셋). `ThemesTest`의 번들 목록을 박아 둔 6곳을 실제 번들로 바꿨다. 손으로 고친 번들 파일을 테스트가 잡는 것을 한 번 확인했다. JVM 336, vitest 225, lint 경고 11.
- [x] Neovim에서 읽는 묶음 (15개)
  - Tokyo Night 4, Kanagawa 3, Vague 1, Cyberdream 2(→3), Kanagawa Paper 2, Oxocarbon 2. 각 저장소의 팔레트와 highlight 정의에서 대응표의 Neovim 칸을 읽는다. Tokyo Night은 Apache-2.0이라 `NOTICE.md`에 전문과 바꾼 것임을 적는다.
  - 한 것: `scripts/themes/neovim.ts` — 색 구성표를 `nvim --headless --clean`에서 실제로 불러 대응표의 그룹을 Neovim이 그리는 대로 읽는다(링크의 끝, `reverse`는 뒤집은 색). Lua를 정적으로 읽으면 묶음마다 팔레트·설정·링크를 다시 짜야 해서다. 데이터 여섯 개(`tokyonight`, `kanagawa`, `vague`, `cyberdream`, `kanagawa-paper`, `oxocarbon`)는 각 저장소의 기본 브랜치 머리를 Neovim 0.12.4에서 읽었다. Cyberdream에 세 번째 변형 `cyberdream-muted`가 있어 넣었다("모든 변형", 합계 46). Vague는 `PmenuSel`을 정하지 않아 Neovim 기본(`reverse`만)이 쓰여, 뒤집힌 그룹의 빈 색을 그 아래 글의 색으로 채우게 했다(안 그러면 글자와 배경이 같은 색). 대응표의 Neovim 칸을 실제로 읽은 그룹에 맞췄다(클래스는 `@lsp.type.class`→`@type`, `strong_foreground`는 `Pmenu` fg도, diff는 `Added`·`Removed`·`Changed`도).
  - Kanagawa Paper Canvas는 원본의 `Normal`(`#616569` 위 `#e1e1de`)이 4.48:1이라 4.5:1 규칙에 걸린다. 원본 그대로 넣기로 했다(2026-10-22 사용자 결정): 데이터의 `lowContrast`에 이유를 적은 테마는 실패 대신 경고이고, 4.5:1을 넘게 되면 테스트가 예외를 지우라고 실패한다. Canvas의 대비 4.48·2.4·3.0·3.4·3.2·3.2·3.0.
  - 대비(`editor.foreground`·주석·키워드·문자열·정의·타입·숫자): Tokyo Night Night 10.6·2.8·5.1·9.4·6.8·8.1·8.4, Storm 9.0·2.4·4.4·8.0·5.8·6.9·7.2, Moon 10.3·3.1·8.6·11.1·6.7·7.4·7.2, Day 4.5·2.5·4.7·4.0·3.1·3.6·3.7, Kanagawa Wave 11.3·3.3·4.7·7.5·5.9·6.2·5.6, Dragon 10.8·4.2·5.8·6.0·6.9·6.9·6.2, Lotus 6.2·2.9·6.1·3.3·4.6·3.9·3.7, Vague 11.6·3.0·5.7·10.0·6.0·8.5·8.4, Cyberdream 17.8·4.7·10.8·13.6·6.8·5.3·10.8, Light 17.8·3.8·3.2·4.5·6.4·5.2·3.2, Muted 17.8·4.7·8.6·10.9·7.3·5.9·8.6, Kanagawa Paper Ink 11.3·3.3·5.6·5.4·5.9·6.2·5.6, Oxocarbon Dark 11.7·2.3·7.7·7.7·7.7·7.7·10.6, Light 9.6·18.1·3.3·2.4·7.3·3.3·1.8(주석이 본문보다 진한 `#161616`인 것은 원본 그대로).
  - `base`는 `vim.o.background`가 아니라 `Normal` 배경의 밝기로 정한다: `cyberdream-light`와 `kanagawa-lotus`는 밝은 색을 불러 놓고 `background`를 `dark`로 둔다(커밋 전 데이터를 열어 보다 찾았다).
  - 테스트: `themes.test.ts` +5(`neovim.ts`: 첫 그룹·링크·뒤집힘 주석, 밝기로 정하는 `base`, 없는 키는 비움, 모든 키와 그룹 한 번씩; 모든 번들의 `base`가 배경의 밝기와 맞음). 나머지는 기존 테스트가 새 번들을 그대로 본다. JVM 336, vitest 230, lint 경고 11(새 경고 없음).
- [x] VS Code에서 읽는 묶음 (14개)
  - Dracula 2(Alucard는 `dracula-theme`의 스펙에서), Rosé Pine 3(`rose-pine/vscode`), Winter is Coming 3, Flexoki 2(`flexoki/vscode`), Vesper 1, Ayu 3(`ayu-colors`의 `themes/*.yaml`과 `src`). 이탤릭 없는 변형은 넣지 않는다.
  - 한 것: 데이터 여섯 개(`dracula`, `rose-pine`, `winter-is-coming`, `flexoki`, `vesper`, `ayu`). Dracula는 저장소가 YAML에서 빌드해 마지막 릴리스 v2.24.3(`9726e1f`)의 `.vsix`를 읽었다. Alucard는 그 JSON의 색을 Dracula 스펙의 Alucard 색으로 바꿔 라이트 테마로 읽었다. Ayu는 같은 org의 `vscode-ayu`가 커밋한 JSON을 읽었다(계획의 `ayu-colors` YAML보다 우선순위 ①). Dracula Soft(VS Code 포트만의 변형)와 Ayu `unbordered`는 넣지 않았다.
  - `vscode.ts`에 더한 것: JSONC 읽기(Vesper), 테마가 적지 않은 workbench 색은 VS Code의 기본값(Winter is Coming의 diff·경고, Vesper의 경계선, 여럿의 커서), `ui.accent`는 불투명한 것만(Rosé Pine의 20% 회색 `focusBorder` 대신 `button.background`), `ui.border`의 셋째 원본 `editorWidget.border`(패널 경계를 숨긴 Rosé Pine).
  - 대비(`editor.foreground`·주석·키워드·문자열·정의·타입·숫자): Dracula 13.4·3.0·6.0·12.7·10.4·10.3·5.9, Alucard 15.9·5.6·7.3·4.8·6.0·5.8·6.0, Rosé Pine 13.4·3.4·3.4·10.8·10.5·10.4·10.5, Moon 11.9·3.0·4.3·9.5·7.1·9.2·7.1, Dawn 6.7·2.7·5.6·2.1·2.6·3.1·2.6, Winter is Coming Dark Blue 12.3·6.4·8.6·14.3·8.3·8.8·12.7, Dark 10.0·5.2·6.9·11.5·6.7·7.1·10.3, Light 5.2·5.2·3.7·5.7·6.3·8.7·9.3, Flexoki Dark 12.0·5.2·6.1·6.7·5.8·8.1·5.4, Light 18.6·5.0·4.4·4.4·4.7·3.4·7.6, Vesper 19.0·2.6·7.3·16.1·12.6·12.6·12.6, Ayu Light 6.1·2.2·2.4·2.4·2.1·2.7·3.3, Mirage 8.9·3.4·7.5·12.8·9.8·8.4·9.0, Dark 9.8·3.1·8.1·11.2·10.5·9.3·9.3. Ayu Light와 Rosé Pine Dawn의 문법 색이 낮은 것은 원본 그대로다.
  - 테스트: `themes.test.ts` +4(JSONC, VS Code 기본값과 따라가는 기본값, 테마의 원본이 먼저이고 투명하게 숨긴 색엔 기본값 없음, 불투명한 강조색). JVM 336, vitest 234, lint 경고 11(새 경고 없음).
- [x] Vim에서 읽는 묶음과 Ocean (13개) — Ocean은 뒤의 결정으로 저장소에 올리지 않는다
  - Gruvbox 6, Everforest 6 — 대비 단계는 원본의 옵션(`g:gruvbox_contrast_*`, `g:everforest_background`)이 바꾸는 배경 값을 그대로 쓴다.
  - Ocean: 폴드8의 `ocean copy.toml`을 사용자 허락을 받고 `run-as`로 꺼내 데이터로 옮긴다. 성공 조건의 "키마다 같다"를 이때 본다.
  - 한 것(Gruvbox·Everforest): Vim 색 구성표도 `neovim.ts`로 Neovim에서 읽는다. 색 구성표 앞에 옵션을 정하는 ex 명령을 받게 했다. Gruvbox를 읽다 찾은 두 가지를 고쳤다: ① Neovim보다 오래된 색 구성표가 정하지 않은 그룹(`NormalFloat`, `@variable.parameter`)에 Neovim 자신의 색이 들어온다 → 손대지 않았고 색 구성표 없을 때와 같은 색이면 읽지 않는다(기본 링크가 테마의 그룹에 닿으면 읽는다). 그 대신 `VertSplit`, `Boolean`, ALE 부호를 표 끝에 더했다. ② 선택이 색 뒤집기 → 원본 옵션 `g:gruvbox_invert_selection = 0`. 비어 있는 tree-sitter 그룹이 부모로 가는 것도 주석에 적게 했다(기존 Neovim 묶음은 주석만 바뀌고 값은 같다). Gruvbox는 라이선스 파일이 없어 README·`package.json`의 MIT와 작성자로 쓴 전문을 `NOTICE.md`에 적었다.
  - 대비(`editor.foreground`·주석·키워드·문자열·정의·타입·숫자): Gruvbox Dark Hard 12.0·4.5·4.8·7.9·7.9·9.7·6.0, Medium 10.7·4.0·4.3·7.1·7.1·8.7·5.4, Soft 9.6·3.6·3.8·6.4·6.4·7.7·4.8, Light Hard 10.5·3.3·7.8·4.4·4.4·3.4·6.1, Medium 10.2·3.2·7.6·4.3·4.3·3.3·5.9, Soft 9.2·2.9·6.9·3.9·3.9·3.0·5.4, Everforest Dark Hard 8.2·4.2·5.0·6.5·6.9·7.5·6.0, Medium 7.4·3.8·4.5·5.9·6.2·6.8·5.4, Soft 6.7·3.5·4.1·5.3·5.6·6.2·4.9, Light Hard 5.4·2.7·3.2·2.9·2.8·2.2·3.0, Medium 5.2·2.6·3.0·2.8·2.7·2.1·2.8, Soft 4.7·2.3·2.7·2.5·2.4·1.9·2.6(Everforest 라이트의 문법 색이 낮은 것은 원본 그대로).
  - 한 것(Ocean): 사용자 허락을 받고 폴드8에서 `run-as`로 `ocean copy.toml`을 읽어(기기는 바꾸지 않음) 번들로 옮겼으나, 같은 날 "저장소에 올리지 않는다"는 결정으로 뺐다. 앱 레포도 공개라 Ocean 데이터와, 그것만을 위해 생성 스크립트에 더했던 "커밋·라이선스 없는 묶음" 갈래는 커밋하지 않았다.
  - JVM 336, vitest 234.
- [x] 테마 저장소 만들기
  - **GitHub에 공개 저장소를 만들고 올리기 직전에 사용자에게 묻는다.** 로컬에서 먼저 다 만든다(`~/projects/skiffcode-themes`).
  - 옮기는 것: `scripts/`(`generate.ts`, `vscode.ts`, `neovim.ts`, `data/`), `test/themes.test.ts`, 생성된 `themes/*.toml` 45개.
  - 바꾸는 것: 출력 위치(`themes/`, 저장소 뿌리의 `index.json`·`NOTICE.md`), `index.json`을 카탈로그 모양으로(`format: 1`, 줄마다 `swatch`·`size`·`sha256`, `light`·`dark` 줄 없음), 머리 주석의 "Written by" 경로, `reference/light.toml`(앱의 사본)과 대조하는 테스트.
  - 더하는 것: README(영어, 묶음마다 원본 링크·소개·라이선스·변형, Alucard는 파생물이라고), `LICENSE`(MIT, 스크립트), `package.json`(Node 24, `npm run themes`, `npm test`; vitest·TypeScript·`@types/node` 고정 버전), `tsconfig.json`, `.gitignore`(`node_modules`, `user-themes/`), `AGENTS.md`(짧게: 명령, 손으로 고치지 않기, `format`·`reference` 규칙).
  - 확인: 저장소에서 `npm run themes`가 커밋된 것과 같은 것을 쓰고 `npm test`가 통과. 앱 레포에서 `SKIFF_THEMES_DIR=~/projects/skiffcode-themes ./gradlew :code:testDebugUnitTest`가 45개를 읽는다(아래 항목의 테스트).
  - 한 것(로컬, 2026-10-22): `~/projects/skiffcode-themes`에 위 구조를 다 만들었다. 45개의 색은 앱 번들과 한 줄도 다르지 않고 머리 주석 4–6줄만 바뀌었다 — 파일이 혼자 다니므로 `NOTICE.md`를 "beside this file" 대신 저장소 주소로 가리킨다. `index.json`은 한 줄에 테마 하나(`swatch`는 `Themes.SWATCH`와 같은 여섯, `resolve`한 값). 테스트는 옮긴 것에 더해 `index.json`(형식, 파일마다 한 줄, `size`·`sha256`·`swatch`·`base`가 파일과 맞음, id 모양), 예약어를 쓰지 않음, README가 모든 id와 원본 링크를 적음, 머리 주석의 저장소 주소. 손으로 고친 테마를 세 테스트가 잡는 것을 확인했다. vitest는 5.0.1에 moderate 권고 둘(GHSA-qxg6-89mg-rqv8, GHSA-xp26-3454-mjgp)이 있어 테마 저장소는 5.0.5로 고정했다(앱 레포는 아직 5.0.1). `npm test`는 `tsc && vitest run`이다.
  - `SKIFF_THEMES_DIR` 테스트는 아래 항목 것이었지만 이 확인에 필요해 여기서 넣었다: `ThemeTomlTest`의 한 테스트가 `themes/*.toml`을 모두 `ThemeToml`로 읽어 문제가 없고 모든 키가 차례대로 파일에 적혔는지 보고, 변수가 없으면 `Assume`으로 건너뛴다. `:code`의 `build.gradle.kts`가 변수와 그 파일들을 테스트의 입력으로 삼아(그래야 up-to-date로 건너뛰지 않는다) 테스트 JVM에 넘긴다. 45개를 읽고, 변수 없이 건너뛰고, 모르는 표를 더한 파일에 실패하는 것을 확인했다. JVM 337, vitest 234, lint 경고 11(새 경고 없음), 테마 저장소 vitest 32.
  - 올림(2026-10-22, 사용자 확인): `gh repo create`로 공개 저장소 `bynaki/skiffcode-themes`를 만들고 첫 커밋을 `main`에 올렸다. `raw.githubusercontent.com/bynaki/skiffcode-themes/main/`에서 로그인 없이 `index.json`(로컬과 같음), 테마 45개(모두 `index.json`의 sha256과 같음), README·LICENSE·NOTICE.md가 받아진다.
- [x] 앱에서 번들 빼기
  - `assets/themes/`에 `light.toml`·`dark.toml`·`index.json`(두 줄)만 남긴다. `code/web/scripts/`, `test/themes.test.ts`, `@types/node`, `tsconfig.json`의 `scripts`, `buildWeb`의 `web/scripts` 입력, `AGENTS.md`의 `npm run themes`·"번들 테마는 손으로 고치지 않는다"·`@types/node` 줄을 지운다. `window.setTimeout` 두 곳은 그대로 둔다(맞는 코드다).
  - (`ThemeTomlTest`의 `SKIFF_THEMES_DIR` 테스트는 위 항목에서 넣었다.)
  - (`AGENTS.md`의 테마 저장소 설명은 "Themes live outside the app too" 절로 이미 들어갔다, 2026-10-22. 지우는 것은 위의 번들·`npm run themes` 줄만.)
- [x] 받기 (Kotlin)
  - `settings/ThemeCatalog.kt`: 박힌 기준 주소, `index.json` 받기와 검사(`format`, 줄마다 id·`nameOf`·`base`·`swatch`·`size`·`sha256`), 테마 받기와 검사(크기 상한, 길이·sha256, `ThemeToml` 문제 없음), 저장은 `Themes.import`. HTTP는 주입할 수 있게(테스트는 가짜). 시간 제한과 크기 상한은 `WebLoader`의 것을 본다.
  - 브리지: 페이지가 목록을 청하면 `{ themes: [...], installed: [...] }` 또는 까닭, 받기를 청하면 같은 이름이 있을 때 `Import Theme`처럼 묻고 저장한 뒤 `setTheme`과 같은 길로 바꾼다.
  - 테스트: `ThemeCatalogTest`(성공 조건의 JVM 항목).
  - 한 것: `ThemeCatalog`(`list`, `install`)와 주입하는 `Fetch`, 진짜 HTTP는 `HttpFetch`(`WebLoader`와 같은 시간 제한 15초·30초, 캐시 없음, 2xx만, 길이를 먼저 알리든 아니든 상한+1바이트에서 멈춤). 실패는 `CatalogError`(`Unreachable`, `Status`, `TooLarge`, `TooNew`, `Malformed`, `Mismatch`, `Invalid`). 목록은 보이는 이름 순으로 돌려주고, 받기는 마지막 목록의 id만 된다(크기·해시를 페이지가 주지 않는다). spec에 더한 검사: `size`는 1~64KB, `sha256`은 소문자 64자, 같은 id는 앞의 것만, 받은 파일이 UTF-8이고 `base`가 목록과 같음. 같은 이름은 대소문자 없이 보고(`Themes.ownNamed`), 다른 대소문자의 것은 지우고 id로 저장한다. 내려받은 뒤에 묻는다(받기가 실패하면 묻지 않는다).
  - 브리지: `themeCatalog` → `{ themes: [{ id, title, family, dark, swatch }], installed: [id] }` 또는 `{ error: "<배너 문구>" }`. `downloadTheme { id }` → `{ result: "installed" | "declined" | "failed" }`. 실패는 Kotlin이 배너로 알리고, 저장하면 `setTheme`과 같은 길(`settingsChanged`)과 "받았습니다" 배너. 문구는 `strings.xml`(영어·한국어) 10개.
  - 테스트: `ThemeCatalogTest` 12(주소와 상한, http 거부, 규칙을 어긴 줄 20가지, 높은 `format`, 읽히지 않는 목록 10가지, 저장, 마지막 목록 밖의 id, 길이·해시·`base` 불일치, 파서 문제 넷, 같은 이름 묻기와 아니요, 다른 대소문자, 처음 받기), `HttpFetchTest` 4(그대로 읽기, 상한 둘, 404, 닿지 않음과 시간 초과). JVM 353(건너뜀 1), lint 경고 11(새 경고 없음).
- [x] 페이지: `Theme...`와 `Download Theme...`
  - `Theme: …` 명령을 지우고 `Theme...`·`Download Theme...`를 더한다. `PaletteMode`에 `theme`·`download`를 더하되 스와이프 순환에는 넣지 않는다.
  - 테마 목록: 설치된 것만, 차례, 지금 테마 표시, 색 띠, 퍼지 검색, 고르면 `setTheme` 후 `>`로 A.
  - 받을 목록: 받는 중 줄, 실패 배너와 `>`, 줄마다 이름·묶음·띠·installed, 퍼지 검색(이름과 묶음), 고르면 받기 요청 후 `>`로 A.
  - vitest: `commands.test.ts`, `palette.test.ts`. `Settings.kt`의 `theme` 키 설명 주석(받은 테마도 이름으로 적는다)을 고친다.
  - 한 것: `palette.ts`에 `theme`·`download` 모드와 `isList`, `enter(mode)`, `busy`를 더했다. 목록 모드는 아무것도 치기 전에 전부 보이고(최근 목록 아님), 실행한 것을 기억하지 않으며, 실행·취소로 닫히면 `>`다. 스와이프는 목록 모드에서도 `>` 자리에서 돈다. 줄의 `run()`이 promise를 돌려주면(받기) 팔레트가 그 줄에 `Downloading…`을 달고 열려 있다가, 끝나면(실패해도) A와 `>`로 닫힌다 — 그 사이 취소했거나 다른 모드로 열었으면 건드리지 않는다. `inert` 줄(`Loading themes…`)은 무엇을 쳐도 남고 실행되지 않는다. 퍼지 검색은 이름과 `detail`(묶음)을 이어 본다.
  - 줄 모양: 색 띠(배경 위에 다섯 칸), 이름, 묶음(받을 목록만), 오른쪽 끝에 `current`/`installed`. 버튼 그림은 반쪽 원(테마)과 아래 화살표(받기). 목록 항목은 `commands.ts`의 `themeItems`·`catalogItems`가 만든다. `CommandSource`의 `themes()`·`setTheme`을 `chooseTheme`·`downloadTheme`로 바꿨다.
  - `main.ts`: `Download Theme...`은 열 때마다 `themeCatalog`를 부르고, 늦게 온 옛 답은 버린다. `error`면 배너와 `>`. 받기는 `downloadTheme`의 답을 기다린다(배너는 Kotlin이 낸다). `settings` 답에 `chosenTheme`(`settings.toml`의 `theme` 그대로)을 더했다 — 지금 테마 표시에 쓴다.
  - 테스트: `commands.test.ts` +6(`Theme: …` 없음과 두 명령, 테마 목록의 차례·띠·표시·id, 받을 목록의 받는 중 줄·이름·묶음·installed·id로 청함), `palette.test.ts` +12(스와이프로 못 감, 전부 보임, 명령에서 들어감, 고르면 `>`이고 기억 안 함, 취소도 `>`, 이름·묶음 검색, 받는 중 줄, 받는 중 열림과 끝나면 `>`, 실패도 `>`, 취소한 뒤 온 답, 목록에서 스와이프). JVM 353(건너뜀 1), vitest 226, lint 경고 11(새 경고 없음).
- [ ] 확인: 탭, 견본 모음, 폴드8
  - 성공 조건의 탭 세 항목과 폴드8 항목. 견본 모음은 테마를 차례로 받아 바꾸며 찍는 스크립트로 만들어 사용자에게 보낸다. 끝나면 받은 테마를 지우고 두 기기의 `settings.toml`을 md5로 되돌린다.

### M14. 테마 변환 스킬 `/theme-convert`

읽을 spec: [`community-themes.md`](skiffcode.spec/community-themes.md)의 "바꾸는 방법"과 "변환 스킬". 이름 규칙은 [`settings-theme.md`](skiffcode.spec/settings-theme.md)의 가져오기 항목만.

M13이 끝난 뒤에 한다(2026-10-22 사용자 요청). 사용자 결정(2026-10-22): 결과는 **`Import Theme`으로 들일 toml 하나**(저장소 목록에 넣지 않음), **명령으로만** 부른다, 파일을 **`user-themes/<이름>.toml`에 만드는 것까지**이고 건네기와 커밋은 하지 않는다. 자리는 처음엔 "생성 스크립트와 같은 커밋에 있게 앱 레포에"였는데, 스크립트가 테마 저장소로 옮겨 가므로 같은 까닭으로 **테마 저장소의 `.claude/skills/theme-convert/`**와 `user-themes/`(`.gitignore`)로 간다(M13 방향 전환에 따른 것, 2026-10-22 사용자 확인).

지금(M13이 끝났을 때를 가정): 테마 저장소의 `scripts/generate.ts`가 묶음 데이터에서 `themes/*.toml`, `index.json`, `NOTICE.md`를 쓴다. 테마 저장소에는 스킬이 없다.

**성공 조건**

- ⬜ vitest(테마 저장소): 한 파일 모드가 같은 데이터에 목록 모드와 똑같은 toml을 쓰고, `index.json`·`NOTICE.md`·`themes/`를 건드리지 않는다. 잘못된 데이터(빠진 필수 키, 색이 아닌 값)는 파일을 쓰지 않고 실패한다 — 확인: `npm test`.
- ⬜ 앱의 파서: 그 견본 toml이 `SKIFF_THEMES_DIR` 테스트처럼 `ThemeToml`로 문제없이 읽힌다.
- ⬜ 스킬: 목록에 없는 테마 둘로 `/theme-convert`를 끝까지 돌린다 — VS Code 원본 하나, Neovim 원본 하나(어느 것으로 할지 그때 사용자에게 묻는다). 한 번 돌릴 때마다 저장소에서 바뀐 것은 `user-themes/`의 새 파일 하나뿐이고(커밋되지 않음), 머리 주석에 원본·커밋·라이선스가 있다. `system`/`light`/`dark`와 겹치는 이름을 주면 다른 이름을 제안한다.
- ⬜ 탭, 사용자 확인: 그 두 파일을 `Import Theme`으로 들이면 경고 배너 없이 들어오고, 견본을 찍은 화면을 사용자가 원본과 견주어 보고 괜찮다고 한다. 확인용으로는 adb로 탭의 `Download/`에 넣어 들인다.
- ⬜ 스킬의 프런트매터에 `disable-model-invocation: true`가 있고, 스킬 목록에 명령으로 보인다.

- [ ] 생성 스크립트의 한 파일 모드
  - 테마 저장소에서 `npm run themes -- --one <데이터> --out <경로>`. 목록 모드와 같은 함수로 toml을 쓰고, 목록·NOTICE는 쓰지 않는다. 필수 키가 빠졌거나 색이 아니면 쓰지 않고 실패한다. 대비 표는 같이 낸다.
  - 테스트: 테마 저장소의 `themes.test.ts`에 한 파일 모드. 앱 레포의 `SKIFF_THEMES_DIR` 테스트가 `user-themes/`도 읽게 한다.
- [ ] 스킬 쓰기
  - 테마 저장소의 `.claude/skills/theme-convert/SKILL.md`: 프런트매터(`disable-model-invocation: true`, 인자 `<저장소 URL> [변형]`). 단계는 원본 고르기 → 원본 파일과 커밋 SHA → scratchpad에 묶음 데이터 → 한 파일 모드 → 대비 표 보기 → 이름 정하기(`nameOf` 규칙, `system`/`light`/`dark` 피하기, 목록 id와 겹치면 알림) → `user-themes/<이름>.toml`에 쓰기(커밋하지 않음). 대응표는 `community-themes.md`를 가리키고 옮겨 적지 않는다. 라이선스가 허용 라이선스가 아니면 알린다.
  - 묶음 데이터의 모양은 `scripts/data/`의 파일 하나를 예로 가리킨다.
  - `user-themes/`는 `.gitignore`에 들고, 무엇을 두는 곳인지는 테마 저장소의 `AGENTS.md`에 적는다.
  - 테마 저장소의 `AGENTS.md`에 스킬 한 줄(무엇을 하고, 목록에 더하는 것은 아니라는 것).
- [ ] 확인: 두 테마로 끝까지, 탭에 들이기
  - 성공 조건의 스킬·탭 항목. 고를 테마는 사용자에게 묻는다(후보: VS Code 원본 Nord, Neovim 원본 Nightfox).

## 아직 확인하지 않은 것

세션이 지나도 이어지는 목록이다. 확인하면 지우고, 안 되는 게 나오면 그 주제의 spec 파일(목차는 [`skiffcode.spec.md`](skiffcode.spec.md))의 설계를 먼저 고친다.
- **hover 툴팁이 위아래 어느 쪽에도 40vh가 안 남는 자리**(CodeMirror가 인라인 `height`로 줄이는 경우)는 만들어 보지 못했다(F1). 그때도 `overflowY: auto`로 스크롤되어야 한다.
- **로딩 라인과 스켈레톤의 `prefers-reduced-motion` 쪽을 보지 못했다.** 보통 쪽은 2026-09-27 사용자가 SFTP
  링크로 봤다. 로컬 파일은 설계상 아무것도 띄우지 않으므로 **SFTP 링크로만 볼 수 있다.**
- **기준선이 한 칸 앞설 수 있는 창**(한 왕복 안에 변경이 두 번, 그 사이에 타이핑)은 만들어 보지 못했다.
- **파일 모드를 파일이 수천 개인 디렉토리에서 보지 못했다.** 결과 개수에 상한이 없어서(2026-09-21 결정)
  `.` 한 글자에 전부가 그려진다. 본 것은 파일 너덧 개짜리 디렉토리다.
- **심볼 모드를 큰 파일에서 재 보지 못했다.** 팔레트를 열 때마다 버퍼 전체를 한 번 파싱한다. 본 것은 400줄
  남짓이다. TypeScript/JavaScript의 개요는 vitest로만 봤고 기기에서는 Python과 Markdown만 봤다.
- **다른 키보드에서 `inputmode="email"`이 먹는지 모른다.** 본 것은 탭의 삼성 키보드 하나다.
- **`localStorage`가 꽉 찼을 때**는 코드가 삼키지만 본 적은 없다.
- **열린 파일이 30개를 넘는 것을 실제로 보지 못했다.** 상한은 1로 낮춰서 확인했다.
- **`ACCESS_LOCAL_NETWORK`를 거부했을 때**가 남았다(`pm revoke` 후 다시 열면 된다).
- **Skiff → Skiff Code 프로필 공유를 아직 못 봤다.** 보려면 **Skiff에 서버를 하나 넣고** Skiff에서 파일을
  탭해야 한다. 이 맥의 원격 로그인은 켜져 있다 — **비밀번호 입력은 사용자가 직접 해야 한다.**
- **`content://` 감시를 기기에서 본 적이 없다.** 컬럼을 주는 provider와 안 주는 provider 양쪽 다.
- **Skiff(`:app`)의 호스트키 창은 기기에서 보지 않았다.** 로직은 `:core` 공용이고 남은 것은 다이얼로그 UI뿐이다.
- **손가락으로 하는 핀치.** 확인은 전부 CDP `Input.dispatchTouchEvent`로 만든 터치였다. 사이드바를 손가락으로
  밀어 여는 것도 없다 — 지금은 ①과 바깥 탭뿐이다.
- **editor의 손 사용감** 전반: 선택 핸들, 길게 누르기, 커서 옮기기.
- **서명이 다른 앱이 끼어드는 경로**(가짜 Skiff Code, 가짜 provider)는 그런 앱을 만들지 않아 보지 못했다.
- **Android 15 미만에서 링크가 전부 확인창을 받는 것.** 두 기기 다 15 이상이다.
- release APK의 배포용 서명. 지난번에는 debug 키로 서명해서 확인만 했다.
- **`GitScopeFinder`가 홈 밖을 가리키는 심볼릭 링크를 "홈 밖"으로 보는 것은 실제 sshd에서만 볼 수 있다.**
  MINA의 REALPATH는 경로를 정규화만 하고 링크를 따라가지 않아서 테스트로 만들 수 없었다. OpenSSH는 `realpath(3)`로
  풀어 준다. 이 맥의 원격 로그인으로 `~/link → /tmp/…` 아래 파일을 열어 보면 된다.
- **빈 출력에 0으로 끝나는 강제 `internal-sftp`는 실제로 보지 못했다.** 실제 OpenSSH 10.0은 문구와 1이었다(M5 확인 항목).
  **신호로 죽은 명령의 `exitStatus`가 null인 것**도 MINA가 128+n을 종료 코드로 보내 테스트하지 못했다.
- **확인이 실패했을 때의 배너(`project_unchecked`)를 기기에서 보지 못했다.** `NoExec`·`Missing` 배너는 M5 확인 항목에서 봤다.
- **로그인 셸이 POSIX가 아니면(fish, csh) 따옴표가 맞는지 모른다.** `ShellQuote`는 sh 기준이고, sshd는 명령 줄을 사용자의 로그인 셸에 넘긴다.
  csh는 작은따옴표 안의 줄바꿈을 받지 않는다.
- 진단이 수백~수천 개일 때의 비용. 큰 파일에서 편집 중 동기화 비용. **2MB 파일에서 병합 diff가 얼마나
  드는지도 아직 안 쟀다.**
- 테마 CSS 변수와 SAF import/export는 라이브러리 버전도 고르지 않았다. lezer 심볼 추출은 패키지가 이미
  있으니 남은 것은 **무엇을 심볼로 볼지**다.

## 알려진 문제

사용자에게 알렸고, 범위 밖이라 손대지 않고 둔 것이다.


1. **재생성 때 열린 파일 목록과 활성 파일은 넘어가지만, 파일들의 버퍼·레이어·스크롤은 넘어가지 않는다.**
   `uiMode`(다크 모드), 언어, 글꼴 크기는 `configChanges`에 없어서, 바꾸면 보던 레이어가 뷰어로 돌아가고
   **타이핑하던 것이 사라진다.** 일부러 재생성시키려면 `settings put system font_scale 1.3` 후 되돌린다.
   **확대 배율은 이번에 `localStorage`로 넘어갔다** — 같은 길이 나머지에도 답이 될 수 있지만 **버퍼 본문까지
   적을지부터 정해야 한다.**
2. **확인 창이 떠 있는데 새 링크가 오면 앞의 것이 소리 없이 대체된다.** adb로 파일 여럿을 열 때는
   **한 번에 하나씩 열고 "열기"를 누른 뒤 다음을 보내야 한다.**
3. ~~줄바꿈이 없어 좁은 화면에서 문장이 잘린다.~~ `settings.toml`의 `wrap`, 기본값 켬으로 풀었다(2026-09-27).
   폴드8 커버 화면에서는 아직 보지 않았다.
4. **경로 빵부스러기가 좁은 화면에서 오른쪽으로 잘린다**(Skiff, 커버 화면). 열린 파일 메뉴의 경로 줄은 왼쪽이
   잘리게 해 두었으니 Skiff도 같은 방향이 맞을 듯하다.
5. **시스템 글꼴 배율이 그대로 곱해진다.** 폴더블의 1.5 때문에 14px이 21px이 된다. **따르기로 했다**(2026-09-27
   사용자 결정) — 문제가 아니라 동작이다. `font_size`가 기기마다 다른 크기가 되는 것이 그 값이다.
6. **키 교환 대기가 5분이다.** 줄이고 싶으면 `SshClientFactory.KEX_TIMEOUT_MS`다.
7. 한 번 봤지만 재현하지 못한 것: Skiff 화면 아래 절반이 **빈 키보드 창**에 먹혀 있었다(폴더블,
   접기/펴기 직후). 세 번 더 해봤지만 재현되지 않아 확정된 버그로 적지 않는다.
8. ~~링크의 `layer`가 쓰이지 않는다.~~ 페이지로 넘기고, `diff`는 비교할 사본이 온 뒤에 들어가게 풀었다(2026-10-04).
9. ~~팔레트가 열린 채로 링크가 다른 파일을 열면 앞 파일의 목록이 남는다.~~ 열린 파일이 바뀔 때마다 팔레트가 다시 찾게 풀었다(2026-10-04).
10. ~~링크의 `col`이 쓰이지 않는다.~~ 커서를 줄·열에 두고 줄을 맨 위로 올리게 풀었다(2026-10-05, M6 정의로 이동과 같은 결정).
11. **editor에서 소프트 키보드가 떠 있을 때 가장자리에서 밀면 키보드가 닫히면서 커서가 민 자리의 줄 끝으로 옮겨진다**(탭, 2026-10-12,
    46줄 → 51줄). 페이지는 `pointerdown`과 `pointercancel`만 받는다. 점프 목록이 비어 콜백이 없을 때도 같아서 M8 때문이 아니라
    WebView가 시스템의 뒤로 가기에 그렇게 반응하는 것이다. 키보드가 없을 때는 커서가 움직이지 않는다. adb 스와이프에서 본 것이고
    손가락으로는 보지 않았다.

## 검증 명령

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17
./gradlew :core:testDebugUnitTest :app:testDebugUnitTest :code:testDebugUnitTest
(cd code/web && npm test)
./gradlew :code:lintDebug :app:installDebug :code:installDebug
adb -s <serial> shell am start -a android.intent.action.VIEW -d 'skiffcode://<user>@<host>:22/<path>'
adb -s <serial> logcat -s SkiffCode:V Skiff:V
```
WebView는 debug 빌드에서 `chrome://inspect`로 디버깅한다.

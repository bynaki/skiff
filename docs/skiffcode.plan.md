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

### M13. 커뮤니티 테마와 `Theme...` 목록

읽을 spec: [`community-themes.md`](skiffcode.spec/community-themes.md). 테마 파일의 규칙은 [`settings-theme.md`](skiffcode.spec/settings-theme.md)의 `themes/*.toml`과 import·복사 항목만, 팔레트 모드는 [`palette.md`](skiffcode.spec/palette.md)의 A·B·C와 최근 목록만.

원래 요구사항은 [`my.next.spec.md`](my.next.spec.md)의 "커뮤니티 테마들 .toml 제공". 사용자 결정(2026-10-22): 15개 저장소의 **모든 변형**(대비 단계 포함, 45개 = 44 + `ocean`), "ocean theme"은 **폴드8의 `ocean copy`**, 목록은 **팔레트 안의 목록**, `system`이 따를 테마 고르기는 **이번엔 하지 않음**. 같은 이름의 사용자 테마가 있으면 **사용자 것이 이긴다**(같은 날 사용자 결정).

지금(코드로 확인): `settings/Theme.kt`의 `Themes.bundled`가 `light`·`dark`를 박아 두고, `names`가 `system` + 번들 + 가져온 것을 낸다. 이름 규칙은 `Themes.nameOf`(`system`/`light`/`dark` 거부). 페이지는 `commands.ts`의 `THEME_TITLES`와 `Theme: …` 명령(`source.themes()`마다 하나), `palette.ts`의 `PaletteMode`가 `command`·`file`·`symbol` 셋이다. 번들 파일은 `code/src/main/assets/themes/{light,dark}.toml`이고 `ThemeTomlTest`가 키를 다 갖는지 본다.

**성공 조건**

- ⬜ JVM: 번들 47개(커뮤니티 45 + `light`·`dark`)가 모든 키를 갖고 읽을 때 문제가 없다. `index.json`과 `assets/themes/*.toml`이 하나씩 맞는다. 번들 id가 가져오기와 복사에서 대소문자 없이 거부된다. 같은 이름의 사용자 테마가 있으면 그것이 이긴다. `names`의 차례 — 확인: `ThemeTomlTest`, `ThemesTest`. `:code` 린트에 새 경고가 없다.
- ⬜ vitest: 생성 스크립트의 결과가 커밋된 파일과 같다. 모든 파일에 원본·커밋·라이선스 머리 주석이 있고, `NOTICE.md`에 묶음마다 라이선스가 있다. 모든 테마의 `editor.foreground` 대비가 4.5:1 이상이다. 팔레트: `Theme: …`가 없고 `Theme...`가 있다, 테마 목록의 차례·표시·퍼지 검색, 고르면 `setTheme`과 `>` 모드로 닫힘 — 확인: 새 테스트 `themes.test.ts`, `commands.test.ts`, `palette.test.ts`.
- ⬜ 탭: `Theme...`에 48줄이 색 띠와 함께 보이고 지금 테마에 표시가 있다. 고르면 메뉴·사이드바·팔레트·viewer·editor·diff·렌더된 마크다운·상태바 아이콘이 한 번에 바뀌고, `settings.toml`에 `theme = "<id>"`가 적히며, 앱을 다시 켜도 남는다. `mocha`를 치면 좁혀진다.
- ⬜ 탭, 사용자 확인: 45개 테마로 같은 견본(Python·TypeScript 파일의 viewer, git 변경이 있는 diff, 렌더된 마크다운)을 찍은 모음을 사용자가 원본 테마와 견주어 보고 괜찮다고 한다. 모음은 스크립트가 테마를 차례로 바꾸며 `screencap`으로 만든다.
- ⬜ 폴드8 커버 화면(글꼴 배율 1.5): 테마 목록의 이름과 띠가 잘리지 않고 목록이 스크롤된다. 사용자 기기라 테마를 바꾸는 것은 사용자에게 묻고, 끝나면 되돌린다.
- ⬜ 폴드8: 번들 `ocean`의 색이 사용자의 `ocean copy`와 키마다 같다.

- [x] 번들 목록과 이름 (Kotlin)
  - `assets/themes/index.json`(id, 보이는 이름, 묶음, `base`)을 두고 지금은 `light`·`dark` 두 줄만 넣는다. `Themes.bundled`를 이 파일에서 만든다.
  - `nameOf`가 번들 id를 대소문자 없이 거부한다. 같은 이름의 사용자 파일이 이미 있으면 사용자 것이 이긴다.
  - `settings` 답의 `themes`를 이름 목록에서 `{ id, title, dark, swatch }` 목록으로 바꾼다(띠 색 여섯, `system`은 따르는 번들의 것). 페이지의 `THEME_TITLES`를 지운다.
  - 테스트: `ThemesTest`(예약어, 사용자 것이 이김, 차례, 띠), `ThemeTomlTest`(index의 모든 번들이 키를 다 가짐, index ↔ 파일).
  - 한 것: `nameOf`는 번들 id를 알아야 해서 `Themes`의 메서드가 됐다. 이미 있는 사용자 파일을 읽는 쪽(`listImported`, `ownAt`)은 번들 id를 거부하지 않는 이름 규칙을 따로 쓴다 — 그래야 사용자 것이 이긴다. 가려지는 것은 커뮤니티 번들뿐이다(`light`·`dark`라는 사용자 파일은 원래 없다). 차례는 `index.json`의 순서가 아니라 `Themes.names`가 정한다(`light`·`dark`, 나머지는 보이는 이름 순). 테마 목록은 `pageSettings`가 IO에서 읽는다 — 테마마다 한 번 읽고 캐시하므로 첫 답만 파일을 읽는다. 페이지는 Kotlin이 답하기 전에는 `Theme: …` 명령이 없다(예전에는 세 개를 박아 두었다).
  - 테스트: `ThemesTest` +4(번들 id 거부, 차례와 보이는 이름, 같은 이름이면 사용자 것이 이기고 지우면 번들이 돌아옴, 띠와 `system`의 띠), `ThemeTomlTest` +1(index ↔ 파일, id 모양, `base`)과 기존 1개가 index의 모든 번들을 본다. `commands.test.ts`는 목록 모양만 바꿨다. JVM 336, vitest 208, lint 경고 12(모두 이번에 손대지 않은 줄).
- [ ] 생성 스크립트와 첫 묶음 (Catppuccin 4)
  - `code/web/scripts/themes/generate.ts`와 `npm run themes`. 묶음 데이터의 모양(원본 URL·커밋 SHA·키마다 값과 원본 이름), 대응표의 "없을 때" 규칙, `diff.*`를 불투명하게 섞기, 머리 주석, `index.json`과 `NOTICE.md` 쓰기, 대비 표.
  - Catppuccin은 `catppuccin/vscode`의 JSON을 읽어 데이터로 옮긴다. `light`·`dark`의 원문은 바꾸지 않는다(스크립트가 쓰지 않는 파일로 둔다).
  - vitest `themes.test.ts`: 생성 결과 = 커밋된 파일, 머리 주석, `NOTICE.md`, `editor.foreground` 대비 4.5:1.
- [ ] Neovim에서 읽는 묶음 (14개)
  - Tokyo Night 4, Kanagawa 3, Vague 1, Cyberdream 2, Kanagawa Paper 2, Oxocarbon 2. 각 저장소의 팔레트와 highlight 정의에서 대응표의 Neovim 칸을 읽는다. Tokyo Night은 Apache-2.0이라 `NOTICE.md`에 전문과 바꾼 것임을 적는다.
- [ ] VS Code에서 읽는 묶음 (14개)
  - Dracula 2(Alucard는 `dracula-theme`의 스펙에서), Rosé Pine 3(`rose-pine/vscode`), Winter is Coming 3, Flexoki 2(`flexoki/vscode`), Vesper 1, Ayu 3(`ayu-colors`의 `themes/*.yaml`과 `src`). 이탤릭 없는 변형은 넣지 않는다.
- [ ] Vim에서 읽는 묶음과 Ocean (13개)
  - Gruvbox 6, Everforest 6 — 대비 단계는 원본의 옵션(`g:gruvbox_contrast_*`, `g:everforest_background`)이 바꾸는 배경 값을 그대로 쓴다.
  - Ocean: 폴드8의 `ocean copy.toml`을 사용자 허락을 받고 `run-as`로 꺼내 데이터로 옮긴다. 성공 조건의 "키마다 같다"를 이때 본다.
- [ ] 페이지: `Theme...`와 테마 목록 모드
  - `Theme: …` 명령을 지우고 `Theme...`를 더한다. `PaletteMode`에 `theme`을 더하되 스와이프 순환에는 넣지 않는다. 빈 입력에 전부, 차례, 지금 테마 표시, 색 띠, 퍼지 검색, 고르면 `setTheme` 후 `>` 모드로 A.
  - vitest: `commands.test.ts`, `palette.test.ts`.
- [ ] 확인: 탭, 견본 모음, 폴드8
  - 성공 조건의 탭 두 항목과 폴드8 두 항목. 견본 모음은 테마를 차례로 바꾸며 찍는 스크립트로 만들어 사용자에게 보낸다. 끝나면 두 기기의 `settings.toml`을 md5로 되돌린다.

### M14. 테마 변환 스킬 `/theme-convert`

읽을 spec: [`community-themes.md`](skiffcode.spec/community-themes.md)의 "바꾸는 방법"과 "변환 스킬". 이름 규칙은 [`settings-theme.md`](skiffcode.spec/settings-theme.md)의 가져오기 항목만.

M13이 끝난 뒤에 한다(2026-10-22 사용자 요청). 사용자 결정(2026-10-22): 결과는 **`Import Theme`으로 들일 toml 하나**(번들에 넣지 않음), 자리는 **레포의 `.claude/skills/theme-convert/`**, **명령으로만** 부른다. 파일은 **레포의 `user-themes/<이름>.toml`에 만드는 것까지**이고 건네기와 커밋은 하지 않는다(같은 날 사용자 결정).

지금(M13이 끝났을 때를 가정): `code/web/scripts/themes/generate.ts`가 묶음 데이터에서 `assets/themes/*.toml`, `index.json`, `NOTICE.md`를 쓴다. 레포 스킬은 `.claude/skills/commit` 하나다.

**성공 조건**

- ⬜ vitest: 한 파일 모드가 같은 데이터에 번들 모드와 똑같은 toml을 쓰고, `index.json`·`NOTICE.md`·`assets/themes`를 건드리지 않는다. 잘못된 데이터(빠진 필수 키, 색이 아닌 값)는 파일을 쓰지 않고 실패한다 — 확인: `themes.test.ts`.
- ⬜ JVM: 한 파일 모드가 쓴 견본 toml이 `ThemeToml.read`에서 문제없이 읽힌다 — 확인: `ThemeTomlTest`(테스트 리소스의 견본).
- ⬜ 스킬: 번들에 없는 테마 둘로 `/theme-convert`를 끝까지 돌린다 — VS Code 원본 하나, Neovim 원본 하나(어느 것으로 할지 그때 사용자에게 묻는다). 한 번 돌릴 때마다 레포에서 바뀐 것은 `user-themes/`의 새 파일 하나뿐이고, 머리 주석에 원본·커밋·라이선스가 있다. 번들 id와 겹치는 이름을 주면 다른 이름을 제안한다.
- ⬜ 탭, 사용자 확인: 그 두 파일을 `Import Theme`으로 들이면 경고 배너 없이 들어오고, 견본을 찍은 화면을 사용자가 원본과 견주어 보고 괜찮다고 한다. 확인용으로는 adb로 탭의 `Download/`에 넣어 들인다.
- ⬜ 스킬의 프런트매터에 `disable-model-invocation: true`가 있고, 스킬 목록에 명령으로 보인다.

- [ ] 생성 스크립트의 한 파일 모드
  - `npm run themes -- --one <데이터> --out <경로>`. 번들 모드와 같은 함수로 toml을 쓰고, 목록·NOTICE는 쓰지 않는다. 필수 키가 빠졌거나 색이 아니면 쓰지 않고 실패한다. 대비 표는 같이 낸다.
  - 테스트: `themes.test.ts`에 한 파일 모드, `ThemeTomlTest`에 견본 읽기.
- [ ] 스킬 쓰기
  - `.claude/skills/theme-convert/SKILL.md`: 프런트매터(`disable-model-invocation: true`, 인자 `<저장소 URL> [변형]`). 단계는 원본 고르기 → 원본 파일과 커밋 SHA → scratchpad에 묶음 데이터 → 한 파일 모드 → 대비 표 보기 → 이름 정하기(`nameOf` 규칙, 번들 id 피하기) → `user-themes/<이름>.toml`에 쓰기(커밋하지 않음). 대응표는 `community-themes.md`를 가리키고 옮겨 적지 않는다. 라이선스가 허용 라이선스가 아니면 알린다.
  - 묶음 데이터의 모양은 M13의 데이터 파일 하나를 예로 가리킨다.
  - `user-themes/`를 만든다(무엇을 두는 곳인지 적은 `README.md` 하나와 함께).
  - `AGENTS.md`에 스킬 한 줄(무엇을 하고, 번들에 더하는 것은 아니라는 것).
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

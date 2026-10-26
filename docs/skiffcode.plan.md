# Skiff Code 할 일

설계와 정한 것은 [`skiffcode.spec.md`](skiffcode.spec.md)(목차)와 그 아래 주제별 파일에 있다. 이 문서는 그것을 한 세션 크기의 할 일로 나눈 체크리스트다.
끝난 마일스톤(0, M0~M14, F1, F2)은 [`skiffcode.plan.done.md`](skiffcode.plan.done.md)로 옮겼다.

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

### M15. 사이드바 트리에서 바뀐 파일 강조

읽을 spec: [`top-menu.md`](skiffcode.spec/top-menu.md)의 사이드바 항목, [`git.md`](skiffcode.spec/git.md)의 `GitService`. 테마 키를 고치는 항목은 [`settings-theme.md`](skiffcode.spec/settings-theme.md)의 테마 키 목록과 [`community-themes.md`](skiffcode.spec/community-themes.md)의 "바꾸는 방법"(대응표)도 읽는다.

원래 요구사항은 `my.next.spec.md`의 "사이드바 프로젝트 파일 목록에 변경된 파일 강조"다. 사용자 결정(2026-10-26): 기준은 **작업 트리의 변경**(`git status`)이다. 색은 **수정과 새 파일 둘**이다. **접힌 폴더도 칠한다.** 테마에는 **키를 새로 둔다**.

지금: 트리(`tree.ts`, `chrome/sidebar.ts`)는 디렉토리를 펼칠 때 `projectDir`로 SFTP 목록만 읽는다. git은 `GitService`(HEAD 내용, 이력, `ls-files`)로만 묻고, `status`를 묻는 곳은 없다. 테마 키는 `ThemeToml.KEYS`의 36개, 테마 저장소의 목록 `FORMAT`은 1이다.

**테마 키 제안**(AGENTS.md "Themes live outside the app too"에 따른 것이다. 사용자가 "새 키 추가"를 골랐다. 아래 값과 폴백은 이 제안대로 간다.)

| 키 | 그리는 곳 | light | dark | 뜻 |
|---|---|---|---|---|
| `ui.git_modified` | 사이드바 트리의 이름 글자 | `#9a6700` | `#d29922` | HEAD에 있던 경로가 바뀜 |
| `ui.git_untracked` | 같음 | `#1a7f37` | `#3fb950` | HEAD에 없던 경로(추적 안 함, 추가, 이름 바꾼 새 경로) |

- 값은 번들 테마가 따르는 GitHub Primer의 attention/success 글자색이다. 흰 배경(`#fff`)과 다크의 `ui.background`(`#151b23`)에서 4.5:1을 넘는지 구현할 때 잰다. VS Code처럼 수정은 노랑, 새 파일은 초록이다. 거터의 `diff.changed`(파랑)와는 일부러 다르게 했다. 거터는 줄의 막대이고, 이것은 파일 이름의 글자다.
- `[diff]`가 아니라 `[ui]`에 둔다. 그리는 곳이 사이드바(`ui.background` 위)이기 때문이다.
- **이미 퍼진 파일:** 두 키가 없는 파일은 지금 규칙대로 같은 `base`인 번들 테마의 값을 받는다. 경고는 나오지 않는다. 사용자가 만든 다크 테마에 Primer 색이 섞이는 셈이지만, 같은 base라 읽히기는 한다. 따로 폴백(그 파일의 `diff.changed`/`diff.added`)을 두지 않는다. 그 색은 막대용이라 글자로 쓰면 대비가 약하다.
- **테마 저장소:** `reference/light.toml`에 두 키를 넣는다. `generate.ts`의 `FALLBACK`은 `ui.git_modified → diff.changed`, `ui.git_untracked → diff.added`로 둔다. 원본에 없을 때만 쓰고, 그때는 diff 색이 이미 배경에 합성된 불투명 색이다. `vscode.ts`의 `SOURCES`는 `ui:gitDecoration.modifiedResourceForeground`, `ui:gitDecoration.untrackedResourceForeground`이고, `DEFAULTS`에 VS Code `registerColor`의 기본값을 같은 커밋에서 옮긴다. `neovim.ts`의 `SOURCES`는 `NvimTreeGitDirty fg`, `NeoTreeGitModified fg` / `NvimTreeGitNew fg`, `NeoTreeGitUntracked fg`이다. 이 그룹이 원본들의 덤프에 실제로 있는지는 그때 보고, 없는 것은 뺀다. 45개를 모두 다시 만든다. `user-themes/`의 두 파일(Nord, Light (Dawnfox))은 `/theme-convert`를 다시 돌려 새로 만든다.
- **대비 표:** `generate.ts`의 대비 표(`MEASURED`)는 지금 모든 키를 `editor.background`에 대고 잰다. 두 키는 사이드바 글자라서 `ui.background`에 대고 재게 한다. 목록 모드와 한 파일 모드에 똑같이 적용한다.
- **`/theme-convert` 스킬**(`.claude/skills/theme-convert/SKILL.md`): 대응표, 읽기 스크립트, 폴백은 스킬 밖에 있어서 두 키가 저절로 따라온다. 고칠 것은 셋이다.
  - 6단계 보고: 두 키가 `ui.background`에 대고 4.5:1 아래면 짚는다. 지금은 3:1 아래의 문법 색만 짚는다.
  - 6단계 보고: 이 파일을 들이려면 두 키를 아는 앱 버전 이상이 필요하다고 알린다. `Import Theme`은 파일 하나를 받을 때 `FORMAT`을 거치지 않는다. 그래서 예전 앱에 들이면 모르는 키로 보고 테마 전체가 번들 색으로 그려진다.
  - 대응표를 읽는 곳: "옆의 Skiff Code 체크아웃" 가운데 **대응표가 `reference/light.toml`의 키를 모두 덮는 것**을 읽는다. 앱이 릴리스되기 전에는 `main` 체크아웃에 두 키의 줄이 없다.
- **`FORMAT` 1 → 2.** 두 키를 모르는 앱은 새 파일을 모르는 키(`UnknownKey`)로 보고, **테마 전체를 같은 base의 번들 색으로** 그린다(`ThemeToml.read`). 그래서 올려야 한다. 앱의 `ThemeCatalog.FORMAT`도 2로 올린다. **테마 저장소의 push는 이 앱이 릴리스된 뒤에, 사용자에게 물어서 한다.** 그 전에 push하면 깔려 있는 앱이 목록을 아예 못 읽는다.
- **spec:** `settings-theme.md`의 키 목록과 `community-themes.md`의 대응표에 두 줄씩 더한다. `community-themes.md`의 "변환 스킬" 절에는 보고에 더한 두 가지(두 키의 대비, 앱 버전 경고)를 적는다.

**성공 조건**

- ✅ JVM: `GitService.status()`가 실제 git(MINA)에서 수정(작업 트리, 스테이징), 새 파일(추적 안 함, 스테이징한 추가, 이름 바꾼 새 경로), 추적 안 하는 폴더(`dir/`), 충돌을 가른다. 공백·한글·줄바꿈이 든 이름을 그대로 돌려준다. 돌린 뒤 `.git/index`가 바이트까지 같다. exec가 안 되거나 git이 없는 프로젝트는 예외 없이 "없음"이다 — 확인: `GitServiceTest`, `GitChangeTest`. 브리지가 부르는 `ProjectSession.changes`는 `ProjectChangesTest`로 확인했다(2026-10-26, JVM 369, 건너뜀 1 — `SKIFF_THEMES_DIR` 테스트).
- ✅ vitest `tree.test.ts`: 파일 줄은 자기 상태를 받는다. 폴더 줄은 아래에 수정이 하나라도 있으면 수정, 새 파일만 있으면 새 파일이다. 추적 안 하는 폴더 아래는 전부 새 파일이다. 루트가 접힌 프로젝트는 묻지 않는다. 다시 묻는 동안은 전 색을 둔다. 답이 "없음"이거나 실패면 색이 없다 — 확인: vitest `tree.test.ts` +6(2026-10-26, web 236).
- ✅ 테마: 두 키가 `ThemeToml.KEYS`, 번들 `light.toml`·`dark.toml`, 페이지의 CSS 변수, `settings-theme.md`에 있다. 두 키가 없는 예전 파일은 문제 없이 번들 값을 받는다 — 확인: `ThemeTomlTest`, vitest `theme.test.ts`(2026-10-26, JVM 370).
- ✅ 테마 저장소: 45개와 `user-themes/` 둘에 두 키가 있다. `FORMAT`이 2다. 저장소 `npm test`가 통과한다. 이 저장소에서 `SKIFF_THEMES_DIR=~/projects/skiffcode-themes ./gradlew :code:testDebugUnitTest --rerun`이 45+2개를 문제없이 읽는다. 대비 표가 두 키를 `ui.background`에 대고 재고, 4.5:1 아래인 테마는 보고한다. `/theme-convert`가 고쳐져 있고(위 세 가지), `user-themes/`의 두 파일을 그 스킬로 다시 만들었을 때 보고에 두 키의 대비와 앱 버전 경고가 나온다.
  - 사용자가 바꾼 것(2026-10-26): `user-themes/`의 두 파일을 다시 만들지 않는다. 대신 Nord와 Nightfox 전 변형을 목록에 넣고, 두 파일은 지운다. 그래서 확인한 것은 이렇다(2026-10-26).
    - 목록 53개에 두 키가 있고 `FORMAT`이 2다. 저장소 vitest 43이 통과한다.
    - `SKIFF_THEMES_DIR` 테스트가 "read 53 themes and 0 in user-themes"이고, JVM 370은 건너뜀 0이다.
    - 대비 표가 두 키를 `ui.background`에 대고 잰다. 4.5:1 아래인 테마는 아래 항목에 적었다.
    - 스킬은 고쳤지만 이번에 돌리지는 않았다. 보고에 두 키의 대비와 앱 버전 경고가 나오는지는 다음에 스킬을 쓸 때 보게 된다.
- ⬜ 탭, 사용자 확인: 이 맥의 원격 로그인으로 연 프로젝트에서 수정한 파일, 새 파일, 둘을 품은 접힌 폴더가 두 색으로 보인다. 파일을 저장하고 사이드바를 다시 열면 색이 따라 바뀐다. 라이트, 다크, 커뮤니티 테마 하나에서 사용자가 보고 읽힌다고 한다.
- ⬜ 폴드8 커버 화면에서 같은 트리가 색과 함께 읽힌다(사용자 확인).

- [x] `GitService.status()`와 브리지
  - `git --no-optional-locks status --porcelain=v1 -z`를 `git()`로 돌린다. 캐시하지 않는다. 파서는 순수 함수로 떼어 낸다(`GitStatus.parse`). 상태 XY를 가르는 규칙은 `git.md`에 있다.
  - 브리지 `projectChanges(project)` → `{ changes: [{ path, kind: 'modified' | 'untracked', directory }] }`이다. git을 물을 수 없으면 `{ changes: null }`이다(`baseline`이 `GitService`를 얻는 길을 그대로 쓴다). 실패는 `logFailure`하고 `null`로 답한다. 트리에 이유를 쓰지 않는다. `CancellationException`은 다시 던진다.
  - 테스트: `GitServiceTest`에 위 성공 조건의 경우들(MINA + 실제 git)과 `.git/index` 바이트 비교를 넣는다. 파서 단위 테스트에는 NUL 구분, `R`의 두 경로, `dir/`, 충돌을 넣는다.
  - 한 것: `GitService.status()`는 `git --no-optional-locks status --porcelain=v1 -z -unormal`이다. `-unormal`은 사용자의 `status.showUntrackedFiles` 설정에 맡기지 않으려고 넣었다. 파서는 `GitChange.parse`로 뗐다.
    - 충돌 가운데 한쪽이 지운 것(`UD`, `DU`)도 파일이 트리에 남으므로 수정으로 친다. 버리는 충돌은 `DD`뿐이다. 이 순서를 `git.md`에 고쳐 적었다.
    - "없음"으로 답하는 판단은 `ProjectSession.changes`로 뗐다. git이 없거나 exec가 안 되면 `null`이고, 실패하면 `failed`로 알린 뒤 `null`이다. 브리지 `projectChanges`가 이것을 부르고, 실패는 `Log.w`로 남긴다. 프로필이 지워진 프로젝트도 `null`이다.
    - 이 맥에서 md5로 확인했다. 플래그 없는 `status`는 stat이 바뀐 파일이 있으면 `.git/index`를 다시 쓴다. 플래그를 붙이면 그대로다. 그래서 인덱스 바이트 비교 테스트가 실제로 잡는 것이 있다.
  - 테스트:
    - `GitServiceTest` +4: 수정, 새 파일, 이름 바꾸기, 추적 안 하는 폴더, 줄바꿈이 든 이름, 무시한 파일, 지운 파일을 한 저장소에서. 충돌. 인덱스 바이트. 첫 커밋 전. 저장소가 아닌 폴더에서 `status`도 `GitFailed`인지 더했다.
    - `GitChangeTest` 8.
    - `ProjectChangesTest` 4: 가짜 `git` 스크립트, 실패하는 git, git 없음, exec 안 됨.
  - 결과: JVM 369(건너뜀 1), web 230, lint 경고 11(그대로).
- [x] 페이지의 색과 앱의 테마 키
  - `tree.ts`: `createTree`가 `changes(project)` 출처를 하나 더 받는다. `set`/`refresh`가 루트가 펼쳐진 프로젝트마다 묻는다. 답은 프로젝트마다 들고, 다시 묻는 동안은 전 것을 둔다. `file`·`directory` 줄에 `change?: 'modified' | 'untracked'`가 붙는다. 폴더의 상태는 경로 앞부분으로 얻는다(수정이 이긴다). `dir/` 아래는 모두 새 파일이다.
  - `chrome/sidebar.ts`: `li`에 `modified`/`untracked` 클래스를 단다. `index.html`에서 `.name`의 색을 `--ui-git-modified`/`--ui-git-untracked`로 칠한다. `current`의 배경은 그대로 겹친다.
  - 테마: `ThemeToml.KEYS`, `light.toml`·`dark.toml`(주석 한 줄씩), 페이지의 기본 변수, `ThemeCatalog.FORMAT = 2`. `settings-theme.md`의 키 목록과 `community-themes.md`의 대응표를 고친다(대응표의 출처 칸은 다음 항목에서 확정한다).
  - 테스트: `tree.test.ts`(성공 조건의 경우들), `theme.test.ts`, `ThemeTomlTest`(두 키 없는 예전 파일). lint를 먼저 돌린다.
  - 한 것:
    - `tree.ts`: `createTree`가 세 번째 인자 `changesOf`를 받는다. 루트를 펼칠 때(`toggle`)와 `refresh` 때, 루트가 펼쳐진 프로젝트마다 한 번 묻는다. 묻는 중이면 다시 묻지 않는다. 답은 경로와 그 위 폴더마다 표시(`marksOf`)로 들고, 수정이 이긴다. 추적 안 하는 폴더는 따로 들어 그 아래 경로를 새 파일로 친다. 답이 `null`이거나 실패하면 표시를 지운다.
    - `chrome/sidebar.ts`: 줄의 `li`에 `modified`/`untracked` 클래스를 단다. `index.html`이 `.name`만 칠하므로 지금 파일의 배경은 그대로다.
    - `main.ts`: 출처는 `projectChanges`다.
    - 테마: `ThemeToml.KEYS`에 두 키를 `[ui]`의 끝에 더했다. 번들 `light.toml`·`dark.toml`(설명 주석과 함께), `index.html`의 기본 변수에도 넣었다. `ThemeCatalog.FORMAT`은 2다. `ThemeCatalogTest`의 "새 앱용 목록" 경우는 `FORMAT + 1`로 고쳐 다음에 올릴 때 깨지지 않게 했다.
    - spec: `settings-theme.md`에 두 키를 적었다. `community-themes.md` 대응표에 두 줄을 더했다. Neovim 그룹은 다음 항목에서 덤프를 보고 확정한다.
  - 대비(재 봤다): 사이드바 배경에서 라이트 4.87·5.08, 다크 6.86·6.82다. **지금 파일 줄의 배경(`ui.subtle`) 위에서는 라이트가 4.30·4.49로 4.5에 조금 못 미친다**(다크 5.56·5.53). 정한 값을 그대로 두고 사용자에게 알렸다.
  - 테스트: vitest `tree.test.ts` +6(web 236), `ThemeTomlTest` +1(JVM 370, 건너뜀 1), `npm run build`(tsc) 통과, lint 경고 11(그대로).
  - **`SKIFF_THEMES_DIR`를 주면 `ThemeTomlTest`가 테마 저장소의 파일에서 깨진다.** 그 파일들에는 아직 두 키가 없다. 다음 항목에서 다시 만들면 풀린다.
- [x] 테마 저장소: 두 키로 다시 만들기
  - 위 "테마 키 제안"의 테마 저장소, 대비 표, `/theme-convert` 줄대로 한다. Neovim 그룹이 덤프에 있는지 보고 `SOURCES`를 확정한다. 대비 표를 사용자에게 보인다.
  - 스킬을 고친 뒤 그 스킬로 `user-themes/`의 두 파일을 다시 만든다. 스킬은 `disable-model-invocation`이라 사용자가 `/theme-convert`로 부른다. 테마 저장소에서 연 세션에서 부를 수도 있다.
  - 테마 저장소의 `AGENTS.md`에 두 키와 `FORMAT` 2를 적는다. 스킬 한 줄이 가리키는 것이 바뀌었으면 그것도 고친다.
  - 두 저장소가 함께 바뀐다. 앱 → 테마 저장소 → 이 저장소의 `SKIFF_THEMES_DIR` 테스트 순서다. 테마 저장소의 커밋과 push는 각각 사용자에게 묻는다. push는 앱 릴리스 뒤에 한다.
  - 한 것(2026-10-26, 테마 저장소에서 커밋하지 않음):
    - 스크립트: `reference/light.toml`을 다시 옮기고, `FALLBACK`(`diff.changed`/`diff.added`), 두 읽기 스크립트의 `SOURCES`, `FORMAT = 2`를 고쳤다.
    - `vscode.ts`의 `DEFAULTS`: 두 색의 기본값은 VS Code 코어의 `registerColor`가 아니라 내장 git 확장의 `package.json`에 있다. 같은 커밋(`3a12a6ee`)에서 읽었다. 수정은 `#895503`/`#e2c08d`, 새 파일은 `#007100`/`#73c991`이다.
    - `generate.ts`: 두 키는 불투명하지 않으면 `ui.background` 위에 섞는다. 대비 표는 두 키를 `ui.background`에 대고 잰다.
    - 원본 다시 읽기: 데이터 파일이 적은 커밋에서 15개 원본을 scratchpad로 받았다. **VS Code 원본 18개 테마**(Ayu, Catppuccin, Dracula/Alucard, Flexoki, Rosé Pine, Vesper, Winter is Coming)만 다시 읽어 두 키를 데이터에 넣었다. 다시 읽은 다른 색은 모두 기록과 같았다(같은 읽기라는 확인). Alucard는 `dracula.json`에 이미 적힌 짝대로 바꿨다(`#8be9fd → #036a96`, `#50fa7b → #14710a`). Flexoki와 Vesper는 원본에 없어 VS Code 기본값을 받았다.
    - 테마 저장소 `AGENTS.md`의 키 규칙에 다시 읽기, 대비 표, 스킬 점검, `FORMAT` 2를 더했다. 스킬의 보고에 두 키의 대비와 앱 버전 경고를 넣고, 대응표를 읽는 체크아웃 조건을 넣었다. `community-themes.md`의 스킬 절에도 적었다.
    - 테스트: 테마 저장소 vitest 43(+2: 폴백·섞기, `ui.background` 대비). `SKIFF_THEMES_DIR` 테스트는 목록의 45개를 통과했다. `user-themes/`의 두 예전 파일에는 키가 없어서 그 둘에서 실패한다.
    - **Neovim 원본 27개 테마**는 사용자가 플러그인 코드 실행을 허락해(2026-10-26) 같은 커밋에서 Neovim v0.12.4로 다시 읽었다. 다른 색은 모두 기록과 같았다. 읽기 스크립트가 XDG 폴더를 임시 폴더로 돌리므로, kanagawa-paper가 쓴 캐시도 거기에 갔다가 지워졌다. 찾은 그룹은 다음과 같다.
      - nvim-tree 그룹: Kanagawa, Kanagawa Paper, Tokyo Night.
      - neo-tree 그룹: Everforest. **새 파일이 `Fg`로 이어져 보통 글자와 같은 색이다** — 원본이 새 파일을 칠하지 않는다.
      - Vague: 새 파일만 있다(`#d8647e`). 수정은 폴백이다.
      - Cyberdream, Gruvbox, Oxocarbon: 그룹이 없어 둘 다 폴백(diff 색)이다.
    - 대비 표에서 두 키 가운데 하나라도 4.5:1 아래인 것(원본 색이라 보고만 한다):
      - VS Code 원본: ayu-light(3.4/2.2), catppuccin-latte(2.2/2.7), alucard(3.7/3.8), rose-pine-dawn(2.6/2.1), winter-is-coming-light(새 파일 3.3).
      - Neovim 원본: cyberdream-light(3.2/4.5), everforest-dark-medium·soft, everforest-light 셋, gruvbox-light 셋, kanagawa-paper-ink·canvas, kanagawa-lotus(1.5/2.2), oxocarbon-light(3.2/2.2), tokyonight-night·storm·day.
    - 테마 저장소 vitest 43. `SKIFF_THEMES_DIR` 테스트는 목록의 45개를 통과하고, `user-themes/`의 두 예전 파일에서만 실패한다.
  - Nord와 Nightfox(2026-10-26 사용자 요청): `/theme-convert`로 다시 만드는 대신 목록에 넣었다.
    - M14와 같은 커밋(Nord `8ead098`, Nightfox `4dacd3f`)에서 읽어 `scripts/data/nord.json`(1개)과 `nightfox.json`(7개)을 만들었다.
    - Nightfox 7개는 모두 nvim-tree의 git 그룹이 있다. Nord는 원본의 `gitDecoration` 색을 받았다.
    - Nord와 Dawnfox는 M14의 `user-themes/` 파일과 두 키 말고는 색이 같다.
    - README에 두 절을 더하고 "53 themes from 17 projects"로 고쳤다. `community-themes.md`의 표와 개수, 목차도 고쳤다.
    - `user-themes/`의 두 파일은 지웠다. 폴더는 스킬의 자리라 남겼다.
    - 대비 4.5:1 아래: dayfox(수정 3.8), dawnfox(1.8/3.2).
  - 테마 저장소는 아직 커밋하지 않았다. 커밋과, 앱 릴리스 뒤의 push는 사용자에게 묻는다.
- [ ] 확인: 탭과 폴드8
  - 성공 조건의 탭·폴드8 항목. 프로젝트는 이 맥의 원격 로그인으로 연다. 비밀번호 입력은 사용자가 한다. 서버 쪽 변경(파일 수정, 새 파일, 추적 안 하는 폴더)은 확인용 저장소에서 만들고 끝나면 되돌린다. 커뮤니티 테마 하나를 받아 확인했으면 지우고, `settings.toml`은 md5로 되돌린다.

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

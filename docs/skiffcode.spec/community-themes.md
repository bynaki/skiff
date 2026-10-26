# 커뮤니티 테마와 테마 목록

[목차](../skiffcode.spec.md)

원래 요구사항은 [`my.next.spec.md`](../my.next.spec.md)의 "커뮤니티 테마들 .toml 제공"이다. 많이 쓰는 커뮤니티 테마를 Skiff Code
테마 toml로 바꿔 **공개 저장소 `bynaki/skiffcode-themes` 한곳에 모으고, 앱에서는 사용자가 골라 받는다**(2026-10-22 사용자 결정).
앱은 번들 `light`·`dark`만 갖는다. 테마 파일의 모양과 검사·폴백, import/export, 복사·편집·삭제는 [설정과 테마](settings-theme.md)
그대로다. 이 문서는 그 위에 더하는 것만 적는다.

처음에는 앱에 번들로 넣기로 했고(M13의 앞 다섯 항목), 변환과 생성 스크립트는 그때 앱 레포에서 만들었다. 같은 날 위 결정으로
바뀌어, 그 스크립트·데이터·테마 파일을 테마 저장소로 옮긴다.

## 무엇을 모으나

- **요구사항의 15개 저장소의 모든 변형이다**(2026-10-22 사용자 결정). 대비 단계(hard·medium·soft)도 각각 하나의 테마다. 이탤릭을
  끈 변형(winteriscoming의 `no-italics`)은 넣지 않는다 — Skiff 테마에 글꼴 모양 키가 없어 색이 같은 두 벌이 된다. 합쳐 **45개**였다.
  **M15에서 Nord와 Nightfox의 모든 변형을 더해 17개 저장소, 53개가 됐다**(2026-10-26 사용자 요청). 둘은 M14에서 `/theme-convert`로
  `user-themes/`에 만들어 확인했던 것이고, 같은 커밋에서 읽어 목록에 넣었다. 그때의 두 파일은 지웠다. Nord와 Dawnfox는 그 파일들과
  색이 같다. Dawnfox는 그때 `background=light`를 주고 읽었는데, 주지 않고 읽어도 같았다.

  | 묶음 | 저장소 | 라이선스 | 변형(id) | 개수 |
  |---|---|---|---|---|
  | Catppuccin | `catppuccin/catppuccin`(읽은 곳은 `catppuccin/vscode`) | MIT | `catppuccin-latte`, `-frappe`, `-macchiato`, `-mocha` | 4 |
  | Dracula | `dracula/dracula-theme`(읽은 곳은 `dracula/visual-studio-code`) | MIT | `dracula`, `alucard`(라이트) | 2 |
  | Rosé Pine | `rose-pine/rose-pine-theme`(읽은 곳은 `rose-pine/vscode`) | MIT | `rose-pine`, `rose-pine-moon`, `rose-pine-dawn` | 3 |
  | Tokyo Night | `folke/tokyonight.nvim` | **Apache-2.0** | `tokyonight-night`, `-storm`, `-moon`, `-day` | 4 |
  | Winter is Coming | `johnpapa/vscode-winteriscoming` | MIT | `winter-is-coming-dark-blue`, `-dark`, `-light` | 3 |
  | Gruvbox | `morhetz/gruvbox` | MIT/X11(README와 `package.json`) | `gruvbox-dark-hard`, `-dark-medium`, `-dark-soft`, `-light-hard`, `-light-medium`, `-light-soft` | 6 |
  | Kanagawa | `rebelot/kanagawa.nvim` | MIT | `kanagawa-wave`, `-dragon`, `-lotus` | 3 |
  | Everforest | `sainnhe/everforest` | MIT | `everforest-dark-hard`, `-dark-medium`, `-dark-soft`, `-light-hard`, `-light-medium`, `-light-soft` | 6 |
  | Vague | `vague-theme/vague.nvim` | MIT | `vague` | 1 |
  | Cyberdream | `scottmckendry/cyberdream.nvim` | MIT | `cyberdream`, `cyberdream-light`, `cyberdream-muted` | 3 |
  | Kanagawa Paper | `thesimonho/kanagawa-paper.nvim` | MIT | `kanagawa-paper-ink`, `-canvas` | 2 |
  | Oxocarbon | `nyoom-engineering/oxocarbon.nvim` | MIT | `oxocarbon-dark`, `-light` | 2 |
  | Flexoki | `kepano/flexoki` | MIT | `flexoki-dark`, `-light` | 2 |
  | Vesper | `raunofreiberg/vesper` | MIT | `vesper` | 1 |
  | Ayu | `ayu-theme/ayu-colors`(읽은 곳은 `ayu-theme/vscode-ayu`) | MIT | `ayu-light`, `-mirage`, `-dark` | 3 |
  | Nord | `nordtheme/nord`(읽은 곳은 `nordtheme/visual-studio-code`) | MIT | `nord` | 1 |
  | Nightfox | `EdenEast/nightfox.nvim` | MIT | `nightfox`, `dayfox`, `dawnfox`, `duskfox`, `nordfox`, `terafox`, `carbonfox` | 7 |

  라이선스는 2026-10-22에 GitHub API로 봤다. 변형 이름은 저장소가 부르는 이름을 따랐다.
  `cyberdream-muted`는 구현 때 저장소에서 찾은 세 번째 변형이다(`colors/cyberdream-muted.lua`, README의 `variant = "muted"`).
  넣지 않은 것: VS Code 포트만의 변형인 Dracula Soft(`dracula/visual-studio-code`가 Dracula 색의 채도를 20% 낮춘 것 — Dracula
  스펙의 변형은 Dracula와 Alucard뿐이다), Ayu의 `unbordered`(경계선만 다른 모양 변형), Rosé Pine의 `no-italics`.
- **라이선스(2026-10-22 확인, 사용자에게 보고):** 41개는 MIT 전문과 저작권 줄이 저장소에 있다. 따로 적는 것:
  - **Gruvbox**: 라이선스 파일이 없다 — README가 "MIT/X11", `package.json`이 `"license": "MIT"`와 작성자 "Pavel Pertsev"를 적는다.
    그 사실과 그 작성자로 쓴 MIT 전문을 `NOTICE.md`에 적고 **그대로 넣는다**(사용자 결정).
  - **Tokyo Night**: Apache-2.0. 전문을 함께 두고 바꾼 파일임을 알리면 된다(머리 주석과 `NOTICE.md`). 저장소에 저작권 줄과
    NOTICE 파일이 없어 저작권자는 "by Folke Lemaitre"로 적는다.
  - **Alucard**: 원본에 VS Code 테마가 없어 우리가 만든 파생물이다(아래 "바꾸는 방법"). 두 원본 모두 MIT(Dracula Theme). README에 공식
    테마가 아니라고 밝힌다.
- **Ocean(폴드8의 사용자 테마 `ocean copy`)은 저장소에 올리지 않는다**(2026-10-22 사용자 결정). 사용자 기기에만 사용자 테마로 있다.
  번들로 넣던 때 폴드8에서 읽어 데이터로 옮겼지만, 앱 레포도 공개라 그 데이터는 커밋하지 않고 지웠다.
- **id**는 소문자와 `-`만 쓴다. 저장소의 `themes/<id>.toml`, 받은 뒤의 사용자 테마 이름, `settings.toml`의 `theme = "…"`가 모두
  이것이다. 화면에 보이는 이름(`Catppuccin Mocha`, `Rosé Pine Dawn`)은 따로 둔다.

## 테마 저장소 `bynaki/skiffcode-themes`

- **공개 저장소다** — 앱이 로그인 없이 받는다. 만드는 것은 GitHub에 공개로 올리는 일이라 만들기 직전에 사용자에게 묻는다.
- 구조:

  ```
  README.md            묶음마다 원본 링크, 짧은 소개, 라이선스, 변형(id와 보이는 이름)
  LICENSE              저장소의 스크립트(MIT). 테마 파일은 각 원본의 라이선스를 따른다고 README와 NOTICE.md에 적는다
  NOTICE.md            묶음마다 원본·커밋·라이선스 전문
  index.json           앱이 받는 목록
  themes/<id>.toml     53개(M15 전에는 45개)
  scripts/             generate.ts, vscode.ts, neovim.ts, data/<묶음>.json
  test/themes.test.ts
  reference/light.toml 앱의 번들 light.toml 사본: 키 목록과 차례의 기준
  package.json         npm run themes, npm test
  ```
- README는 영어로 쓴다(공개 저장소). 묶음마다 원본 저장소 링크와 한두 문장 소개, 라이선스, 읽은 곳이 원본 저장소와 다르면 그곳
  (`catppuccin/vscode` 등)을 적는다.
- **`index.json`**은 생성 스크립트가 쓴다:

  ```json
  { "format": 1,
    "themes": [
      { "id": "catppuccin-mocha", "title": "Catppuccin Mocha", "family": "Catppuccin", "base": "dark",
        "swatch": ["#1e1e2e", "#cdd6f4", "#cba6f7", "#a6e3a1", "#89b4fa", "#cba6f7"],
        "size": 2843, "sha256": "…" } ] }
  ```

  `swatch`는 앱의 띠와 같은 여섯 색(`editor.background`, `editor.foreground`, `syntax.keyword`, `syntax.string`, `syntax.definition`,
  `ui.accent`)이라, 받기 전에도 목록에 색을 그린다. `size`와 `sha256`은 `themes/<id>.toml`의 것이다.
- **`format`은 테마 파일의 형식 버전이다.** 앱의 테마 키가 바뀌면(앱은 모르는 키를 문제로 본다) 올린다. 앱은 자기가 아는 것보다
  높은 `format`의 목록을 받지 않고 "더 새 Skiff Code가 필요하다"고 알린다. 지금은 1이다.
- **키 목록의 기준은 앱이다.** `reference/light.toml`은 앱의 `code/src/main/assets/themes/light.toml` 사본이고, 생성 스크립트의
  대응표가 그 키와 차례를 따르는지 테스트가 본다. 앱이 키를 바꾸면 사본을 바꾸고 `format`을 올린다.
- **앱의 파서로 읽히는지는 앱 레포가 본다.** 앱의 JVM 테스트는 `SKIFF_THEMES_DIR`가 테마 저장소 체크아웃을 가리키면 그
  `themes/*.toml`을 모두 `ThemeToml`로 읽어 문제가 없는지 보고, 없으면 건너뛴다. 테마 저장소에 올리기 전에 돌린다.
- 원본이 바뀌면 데이터를 다시 쓰고 생성 스크립트를 돌려 커밋한다. 앱을 업데이트하지 않아도 목록이 바뀐다.

## 바꾸는 방법

- **색은 각 테마가 스스로 정한 배치를 따른다.** 팔레트만 가져와 우리가 문법 색을 다시 고르지 않는다. 원본 배치를 읽는 곳은
  묶음마다 하나이고, 우선순위는 ① 같은 저장소(또는 같은 org)의 VS Code 테마 JSON, ② Neovim/Vim 색 구성표의 highlight
  정의, ③ 그 저장소가 낸 helix·zed 포트다. 묶음마다 어느 파일의 어느 커밋을 읽었는지 생성 스크립트의 데이터에 적는다.
- **Skiff 키와 원본의 대응표**다. VS Code는 workbench 색 이름과 TextMate 스코프, Neovim은 highlight 그룹이다. 원본에 없는
  키는 오른쪽 끝 칸의 규칙으로 채운다.

  | Skiff 키 | VS Code | Neovim | 없을 때 |
  |---|---|---|---|
  | `ui.background` | `sideBar.background` | `NormalFloat` bg | `editor.background` |
  | `ui.foreground` | `sideBar.foreground`, `foreground` | `NormalFloat` fg | `editor.foreground` |
  | `ui.muted` | `descriptionForeground` | `Comment` fg | `syntax.comment` |
  | `ui.border` | `panel.border`, `sideBar.border`, `editorWidget.border` | `FloatBorder` fg, `WinSeparator` fg, `VertSplit` fg | `editor.gutter_border` |
  | `ui.faint` | `disabledForeground` | `NonText` fg | `ui.muted` |
  | `ui.subtle` | `list.hoverBackground` | `CursorLine` bg | `editor.selection` |
  | `ui.accent` | `focusBorder`, `button.background`(불투명한 것만) | `Function` fg | `syntax.definition` |
  | `ui.scrim` | — | — | `editor.background` + 알파 `99` |
  | `ui.strong_background` / `strong_foreground` | `button.background` / `button.foreground` | `PmenuSel` bg / fg(없으면 `Pmenu` fg) | `ui.foreground` / `ui.background` |
  | `ui.git_modified` | `gitDecoration.modifiedResourceForeground` | `NvimTreeGitDirty`, `NeoTreeGitModified` | `diff.changed` |
  | `ui.git_untracked` | `gitDecoration.untrackedResourceForeground` | `NvimTreeGitNew`, `NeoTreeGitUntracked` | `diff.added` |
  | `editor.background` / `foreground` | `editor.background` / `editor.foreground` | `Normal` bg / fg | — (반드시 있다) |
  | `editor.gutter_background` | `editorGutter.background` | `SignColumn` bg | `editor.background` |
  | `editor.gutter_foreground` | `editorLineNumber.foreground` | `LineNr` fg | `ui.muted` |
  | `editor.gutter_border` | `editorGroup.border` | `WinSeparator` fg, `VertSplit` fg | `ui.border` |
  | `editor.cursor` | `editorCursor.foreground` | `Cursor` bg | `editor.foreground` |
  | `editor.selection` | `editor.selectionBackground` | `Visual` bg | `ui.subtle` |
  | `editor.code_background` | `textCodeBlock.background` | `ColorColumn`·`CursorLine` bg | `ui.background` |
  | `syntax.keyword` | `keyword`, `storage` | `@keyword`, `Keyword` | — |
  | `syntax.atom` | `constant.language` | `@boolean`, `@constant.builtin`, `Boolean` | `syntax.literal` |
  | `syntax.literal` | `constant.numeric` | `@number`, `Number` | — |
  | `syntax.string` | `string` | `@string`, `String` | — |
  | `syntax.regexp` | `string.regexp` | `@string.regexp` | `syntax.string` |
  | `syntax.definition` | `entity.name.function` | `@function`, `Function` | — |
  | `syntax.local` | `variable.parameter` | `@variable.parameter` | `editor.foreground` |
  | `syntax.type` | `entity.name.type`, `support.type` | `@type`, `Type` | — |
  | `syntax.class` | `entity.name.class` | `@lsp.type.class`, `@type` | `syntax.type` |
  | `syntax.macro` | `entity.name.function.preprocessor` | `@function.macro`, `Macro` | `syntax.keyword` |
  | `syntax.property` | `variable.other.property` | `@property`, `@variable.member` | `editor.foreground` |
  | `syntax.comment` | `comment` | `@comment`, `Comment` | — |
  | `syntax.meta` | `meta.decorator`, `storage.type.annotation` | `@attribute`, `PreProc` | `syntax.comment` |
  | `syntax.invalid` | `invalid`, `editorError.foreground` | `DiagnosticError`, `ALEErrorSign` | — |
  | `syntax.warning` | `editorWarning.foreground` | `DiagnosticWarn`, `ALEWarningSign` | — |
  | `diff.added` | `editorGutter.addedBackground` | `GitSignsAdd`, `diffAdded`, `Added` | — |
  | `diff.removed` | `editorGutter.deletedBackground` | `GitSignsDelete`, `diffRemoved`, `Removed` | — |
  | `diff.changed` | `editorGutter.modifiedBackground` | `GitSignsChange`, `diffChanged`, `Changed` | — |

  - 알파가 든 원본 색(VS Code의 `#rrggbbaa` 선택색 등)은 **그대로 둔다.** Skiff 테마가 `#rrggbbaa`를 받는다. 다만 `diff.*`는
    `diff_alpha`가 다시 곱해지므로 불투명하게 바꾼다(그 배경 위에 섞은 색).
  - `base`는 원본의 라이트/다크 구분이다. 상태바 아이콘과 빠진 키의 폴백이 그것을 따른다.
- **생성 스크립트로 만들고, 만든 파일을 커밋한다**(테마 저장소에서). `scripts/data/<묶음>.json`에 묶음마다 데이터 파일(원본 위치·
  커밋 SHA·라이선스 전문·위 표의 키마다 고른 값과 그 값이 원본의 어느 이름에서 왔는지, 색 하나가 한 줄) 하나와, 그것을 toml로
  쓰는 `scripts/generate.ts` 하나를 둔다. Node 24가 `.ts`를 바로 돌린다(`npm run themes`). 개발 의존성은 vitest·TypeScript·
  `@types/node`다. 생성할 때 네트워크에서 받지 않는다 — 원본에서 값을 옮기는 것은 데이터 파일을 쓸 때 한 번이다.
  - 데이터 파일에 없는 키는 `generate.ts`가 위 표의 "없을 때"로 채운다. "—"인 키가 없거나, 서로를 가리키는 두 키(`ui.border`와
    `editor.gutter_border`, `ui.subtle`과 `editor.selection`)가 둘 다 없거나, 색이 아닌 값이 있으면 아무것도 쓰지 않고 실패한다.
    `ui.scrim`은 VS Code에 맞는 것이 없어 늘 `editor.background`에 알파 `99`다(앱의 번들 `light`·`dark`의 scrim과 같은 알파).
  - VS Code 원본은 `scripts/vscode.ts <theme.json>`이 위 표대로 읽어 데이터의 `colors`를 낸다. TextMate 스코프는 VS Code처럼
    고른다: 문법이 실제로 붙이는 스코프(`keyword.control`, `string.quoted`, `comment.line`)에 대해, 그것이거나 그 부모인 선택자 중
    마디가 가장 많은 것, 같으면 뒤의 규칙. 부모 스코프가 있어야 맞는 선택자(`meta.function entity.name`)와 언어별 규칙
    (`string.regexp.ts`)은 보지 않는다. 알파가 `00`인 색(`sideBar.border = #00000000`)은 없는 것으로 친다.
    - 파일은 JSONC여도 된다(주석과 끝 쉼표, VS Code가 받는 그대로 — Vesper).
    - 테마가 workbench 색을 아예 적지 않으면 **VS Code가 그리는 기본값**을 쓴다(`DEFAULTS`, microsoft/vscode의 `registerColor`에서
      옮김: `panel.border`, `disabledForeground`, `editorLineNumber.foreground`, `editorCursor.foreground`, `editorError`·`editorWarning.foreground`,
      `editorGutter.*Background`). 그 키의 모든 원본을 테마에서 찾은 뒤에만 보고, 주석은 `# editorGutter.addedBackground: VS Code's
      default for a dark theme`다. 테마가 투명하게 적은 색(Rosé Pine의 `panel.border = #0000`)은 일부러 숨긴 것이라 기본값을 쓰지 않는다.
    - `ui.accent`는 불투명한 색만 받는다. Rosé Pine의 `focusBorder`는 20% 회색이라 강조가 되지 않아 `button.background`로 간다.
    - `ui.border`의 셋째 원본 `editorWidget.border`는 패널 경계를 숨긴 테마(Rosé Pine)가 위젯에는 긋는 선이다.
  - Neovim 원본은 `scripts/neovim.ts <플러그인 디렉터리> <colorscheme> [light|dark] [<ex 명령>...]`가 읽는다. ex 명령은
    색 구성표를 부르기 전에 돌아 그 옵션을 정한다(`let g:gruvbox_contrast_dark = 'hard'`). 정적으로 Lua를 읽지 않고,
    `nvim --headless --clean`에서 그 색 구성표를 실제로 불러(`background`가 주어지면 먼저 맞추고) 위 표의 그룹을 Neovim이 그리는
    대로 읽는다: 링크를 따라가고(색 구성표가 정하지 않은 tree-sitter 그룹은 Neovim 기본 링크의 끝), `reverse`인 그룹은 뒤집은
    색이며 빈 쪽은 그 아래 글(`PmenuSel`은 `Pmenu`, 나머지는 `Normal`)의 색이다 — Neovim 자신의 `PmenuSel`이 `reverse`뿐이다.
    클래스 이름은 언어 서버가 칠하면 `@lsp.type.class`, 아니면 `@type`이라 그 차례로 본다. 색 구성표의 코드를 Neovim처럼 돌리고,
    캐시는 임시 디렉터리에 쓴다. 비어 있는 tree-sitter 그룹은 Neovim처럼 부모로 간다(`@function.macro` → `@function`,
    주석은 `(via Function)`).
    - **색 구성표가 손대지 않았고 색 구성표 없이 그릴 때와 같은 색인 그룹은 읽지 않는다**(`highlight clear` 뒤와 견준다). 그
      색은 Neovim 자신의 색 구성표 것이다 — `NormalFloat`도 tree-sitter도 모르는 Vim 색 구성표(Gruvbox)에 Neovim의 회색 창과
      `@variable.parameter`가 들어오던 것을 막는다. 손대지 않았어도 기본 링크가 그 테마의 그룹에 닿으면(Vague의 `FloatBorder` →
      `NormalFloat`, Gruvbox의 `@keyword` → `Keyword`) 테마의 색이라 읽는다. 그래서 Vim 색 구성표를 위해 `VertSplit`, `Boolean`과,
      Neovim 진단보다 오래된 색 구성표가 오류·경고를 칠하는 ALE의 부호(`ALEErrorSign`, `ALEWarningSign`)를 표 끝에 더했다.
    - `base`는 `Normal` 배경이 흰색과 검은색 중 어디에 가까운지로 정한다 — `cyberdream-light`와
    `kanagawa-lotus`는 밝은 색을 불러 놓고 `background`를 `dark`로 둔다. 모든 테마의 `base`가 배경과 맞는지 테스트가 본다. 주석은 `# @number fg (via Constant)`, `# PmenuSel bg (reversed)`처럼 링크의 끝과 뒤집힘을 적는다.
  - toml의 색마다 줄 끝 주석에 그 값이 원본의 어느 이름에서 왔는지 적는다(`# sideBar.background`, `# token keyword (for
    keyword.control)`, `# none in the source: editor.foreground`). 복사해서 고치는 사람이 원본과 견줄 수 있게.
  - Catppuccin은 `catppuccin/vscode`를 읽었다. 저장소에는 테마를 만드는 TS만 있고 만들어진 JSON이 없어, 그 커밋의 릴리스
    (`catppuccin-vsc-v3.19.0`)에 붙은 `.vsix` 안의 `themes/*.json`을 읽고 데이터에 그렇게 적었다. npm의 `@catppuccin/vscode`는
    3.18.1에 멈춰 있어 쓰지 않았다.
  - VS Code로 읽은 여섯 묶음: Dracula는 `dracula/visual-studio-code`가 YAML에서 빌드하는 저장소라 마지막 릴리스 v2.24.3
    (커밋 `9726e1f`)의 `.vsix` 안 `theme/dracula.json`을 읽었다. **Alucard**는 VS Code 테마가 없어, 그 JSON의 모든 색을 Dracula
    스펙(draculatheme.com/spec)의 같은 역할의 Alucard 색으로 바꾸고 라이트 테마로 읽었다(Background Light와 떠 있는 배경이 Dracula에선
    같은 `#343746`이지만 읽는 색에는 나오지 않는다). 주석은 `# focusBorder, Dracula's #6272a4 as Alucard's`다. Ayu는 같은 org의
    `ayu-theme/vscode-ayu`가 커밋해 둔 `ayu-{light,mirage,dark}.json`을 읽었다(`ayu-colors`의 YAML보다 우선순위 ①). Rosé Pine,
    Winter is Coming, Flexoki(`kepano/flexoki`의 `vscode/`), Vesper는 저장소의 JSON을 그대로 읽었다.
  - Neovim으로 읽은 여섯 묶음(Tokyo Night, Kanagawa, Vague, Cyberdream, Kanagawa Paper, Oxocarbon)은 각 저장소의 기본 브랜치
    머리를 Neovim 0.12.4에서 읽었다. 설정은 모두 기본값이다(투명 배경 끔 등). Oxocarbon은 색 구성표가 하나라 `background`를
    `dark`·`light`로 바꿔 두 번 읽었다. Tokyo Night은 저장소에 저작권 줄이 없어 저작권자를 "by Folke Lemaitre"로 적고, Apache-2.0
    4(b)의 "바꾼 파일임을 알림"은 머리 주석의 "converted to a Skiff Code theme"과 `NOTICE.md` 머리말이 한다.
  - Vim 색 구성표인 Gruvbox와 Everforest도 같은 `neovim.ts`로 Neovim에서 읽었다. 대비 단계는 원본의 옵션
    (`g:gruvbox_contrast_dark`/`_light`, `g:everforest_background`)을 `hard`·`medium`·`soft`로 바꿔 가며 읽었다. Gruvbox는 기본으로 선택을
    색 뒤집기로 그리는데 Skiff Code의 선택은 글 아래 배경이라, 원본의 옵션 `g:gruvbox_invert_selection = 0`으로 읽었다(선택 = `bg3`).
    Gruvbox 저장소에는 라이선스 파일이 없다 — README가 "MIT/X11", `package.json`이 "MIT"와 작성자 "Pavel Pertsev"를 적는다. 그래서
    `NOTICE.md`에는 그 사실과 그 작성자로 쓴 MIT 전문을 적었다.
  - vitest가 생성 결과와 커밋된 `themes/*.toml`·`index.json`·`NOTICE.md`가 같은지 본다. 손으로 고친 테마 파일은 테스트가 잡는다.
  - 스크립트는 테마마다 WCAG 대비(`editor.foreground`·문법 색 대 `editor.background`)를 표로 낸다. **`editor.foreground`만
    4.5:1 미만이면 실패**로 하고, 나머지는 보고만 한다 — 주석을 일부러 흐리게 둔 테마(Rosé Pine, Vesper)가 있고, 원본을 따르는
    것이 우선이다.
    - 예외는 테마 데이터의 `lowContrast`에 이유를 적은 것뿐이다. 그 테마는 실패 대신 경고를 내고, 원본이 바뀌어 4.5:1을 넘으면
      테스트가 예외를 지우라고 실패한다. 지금은 Kanagawa Paper Canvas 하나다(원본 `#616569` 위 `#e1e1de`, 4.48:1 — 원본 그대로
      넣기로 2026-10-22 사용자 결정).
- **파일마다 머리 주석**에 테마 이름, 원본 저장소와 커밋, 라이선스, 저작권자, "Skiff Code 테마로 바꾼 것"을 적는다.
  받은 테마는 원문 그대로 저장되고 `Export Theme`도 원문을 내보내므로 이 주석이 함께 다닌다. 라이선스 전문은 저장소의
  `NOTICE.md` 하나에 묶음마다 모은다. 파일은 저장소 없이 혼자 다니므로, 머리 주석은 "NOTICE.md beside this file"이 아니라
  전문이 있는 곳을 저장소 주소로 적는다(`The full text is in NOTICE.md of https://github.com/bynaki/skiffcode-themes`)
  (Apache-2.0인 Tokyo Night은 전문과 함께 바꾼 것임을 적는다). 앱 안에서 그것을 보여 주는 화면은 이번에 만들지 않는다.

## 앱에서 받기

- **앱의 번들은 `light`·`dark`뿐이다.** `assets/themes/`에는 `light.toml`, `dark.toml`과 그 둘을 적은 `index.json`만 남는다.
  예약어는 `system`/`light`/`dark`다(M13 앞 항목의 "번들 id는 예약어" 코드가 그대로 이것이 된다).
- **팔레트 커맨드 `Download Theme...`**(2026-10-22 사용자 결정 — `Theme...`와 따로). 실행하면 같은 팔레트가 **받을 테마 목록**으로
  바뀐다. [팔레트](palette.md)의 다섯 번째 모드이고, 테마 목록처럼 **스와이프로는 가지 않으며 닫히면 `>`로 돌아온다**.
  - 열 때마다 `index.json`을 받는다. 받는 동안은 "Loading themes…" 한 줄이다. 실패하면(오프라인, 시간 초과, 높은 `format`) 배너로
    까닭을 알리고 `>`로 닫힌다.
  - 줄마다 보이는 이름, 묶음, 색 띠(`swatch`)를 그린다. 같은 이름(대소문자 무시)의 사용자 테마가 이미 있으면 "installed" 표시가
    붙는다. 차례는 보이는 이름 순이고, 치면 보이는 이름과 묶음으로 퍼지 검색한다(`mocha`, `gruv soft`).
  - 탭하거나 Enter면 그 테마를 받아 **사용자 테마로 저장하고 그 테마로 바꾼다** — 파일에서 `Import Theme`한 것과 같다(2026-10-22
    사용자 결정). 같은 이름이 있으면 `Import Theme`처럼 덮어쓸지 묻는다. 받는 동안 그 줄에 표시가 붙고, 끝나면 A로 닫힌다.
    실패해도 A로 닫힌다(까닭은 배너). 받는 동안 빈 곳을 탭해 닫아도 받기는 Kotlin에서 끝까지 가고, 저장되면 그 테마로 바뀐다.
- **받는 것의 검사.** 주소는 앱에 박힌 하나(`https://raw.githubusercontent.com/bynaki/skiffcode-themes/main/`)이고 https만 쓴다.
  - 크기 상한: `index.json` 256KB, 테마 64KB. 연결·읽기 시간 제한이 있다.
  - `index.json`: `format`이 정수이고 앱이 아는 값 이하, 테마마다 id가 `^[a-z][a-z0-9-]{0,63}$`이고 `Themes.nameOf`를 통과하며,
    `base`가 `light`/`dark`, `swatch`가 색 여섯, `size`(1~64KB의 정수)·`sha256`(소문자 16진수 64자)이 있다. 하나라도 어기는 줄은
    목록에서 뺀다. 같은 id가 두 번 나오면 앞의 것만 둔다.
  - 테마 파일: 길이와 sha256이 `index.json`과 같고, UTF-8이며, `ThemeToml`이 문제를 하나도 내지 않고, `base`가 목록의 것과 같아야
    저장한다. 어기면 저장하지 않고 배너로 알린다(형식이 맞지 않는 파일을 사용자 테마로 남기지 않는다).
  - 받기는 마지막으로 받은 목록에 있는 id만 된다. 크기·해시는 페이지가 아니라 그 목록에서 온다.
  - 같은 이름은 대소문자를 가리지 않는다. `Aurora`가 있는데 `aurora`를 받으면 `Aurora`를 바꿀지 묻고, 예면 `Aurora`를 지우고
    `aurora`로 저장한다(사용자 테마가 대소문자만 다른 둘로 남지 않게).
  - 받는 일은 Kotlin이 한다. 페이지는 목록과 받기를 브리지로 청하고 결과만 받는다.
- 받은 파일은 사용자 테마라 `Theme...` 목록에 나오고 편집·복사·내보내기·삭제가 지금과 같다. 저장소의 테마가 고쳐져도 저절로 바뀌지
  않는다 — 다시 받으면 된다.

## 팔레트의 `Theme...`

- **`Theme: …` 명령들을 `Theme...` 하나로 바꾼다**(요구사항). 사용자 테마가 늘면 커맨드 목록이 테마로 덮인다. `Import Theme`,
  `Export Theme`, `Copy Theme`, `Edit Theme`, `Delete Theme`, `Download Theme...`은 커맨드다.
- **`Theme...`를 실행하면 같은 팔레트가 테마 목록으로 바뀐다**(2026-10-22 사용자 결정, VS Code의 Color Theme 고르기와 같다). 따로
  띄우는 창은 만들지 않는다. [팔레트](palette.md)의 네 번째 모드지만 **입력 버튼의 스와이프로는 가지 않고** `Theme...`로만 들어간다.
  - **설치된 테마만 보인다**: `System`, `Light`, `Dark`, 그 사람의 테마(가져온 것과 받은 것, 보이는 이름 순). 아무것도 치지 않아도
    전부 보인다(최근 다섯 개가 아니다). 네트워크를 쓰지 않는다.
  - 줄마다 보이는 이름과 **색 띠**(그 테마의 `editor.background` 위에 `editor.foreground`·`keyword`·`string`·`definition`·`ui.accent`
    다섯 칸)를 그린다. `System`은 지금 따르는 번들의 띠다. 지금 `settings.toml`의 `theme`인 줄에 표시가 붙는다.
  - 치면 보이는 이름으로 퍼지 검색한다.
  - 탭하거나 Enter면 `setTheme(id)`로 바꾸고 A로 닫힌다. **닫힌 뒤의 모드는 `>`다** — 테마 목록은 스와이프로 오는 모드가 아니라
    마지막 모드로 남지 않는다. 취소는 다른 모드와 같다(빈 곳 탭).
  - 띠의 색은 Kotlin이 `settings` 답의 `themes`에 이름과 함께 싣는다(id, 보이는 이름, `base`, 띠 색 여섯). 사용자 테마도 읽어서
    싣는다 — 파일이 작고, 목록을 열 때가 아니라 테마가 바뀔 때만 다시 읽는다. 받은 테마의 보이는 이름은 파일 이름(id)이다.
- 명령 최근 목록에 남아 있던 `Theme: ocean` 같은 이름은 그 명령이 없어져 보이지 않게 된다(지금의 규칙대로). 따로 옮기지 않는다.

## 변환 스킬 `/theme-convert` (M14)

M13의 변환 방법을 다른 테마에도 쓰게 스킬로 만든다(2026-10-22 사용자 요청). M13이 끝난 뒤에 한다.

- **결과는 `Import Theme`으로 들일 toml 파일 하나다**(2026-10-22 사용자 결정). 저장소의 목록에 넣지 않는다 — 목록에 더하는 것은
  묶음 데이터를 테마 저장소에 커밋하는 일이고 스킬의 일이 아니다.
- **테마 저장소의 `.claude/skills/theme-convert/`에 두고 커밋한다.** 처음 결정(2026-10-22)은 "생성 스크립트와 대응표가 같은 커밋에
  있게 앱 레포에"였는데, 스크립트가 테마 저장소로 옮겨 가므로 같은 까닭으로 스킬도 그리로 간다(같은 날 사용자 확인).
- **명령으로만 부른다**(2026-10-22 사용자 결정): `/theme-convert <저장소 URL> [변형]`. 프런트매터에 `disable-model-invocation: true`를
  두어 "테마 바꿔 줘" 같은 말에는 뜨지 않는다.
- **같은 방법을 쓴다.** 스킬은 대응표를 다시 적지 않고 이 문서의 "바꾸는 방법"을 가리킨다. 하는 일은 묶음 하나를 더할 때와
  같다: 원본 고르기(VS Code JSON → Neovim/Vim → helix·zed 순), 원본 파일과 커밋 SHA 적기, 대응표대로 묶음 데이터 쓰기, 생성 스크립트로
  toml 쓰기, 대비 표 보기. 다른 점은 둘이다:
  - 묶음 데이터는 저장소가 아니라 **세션의 scratchpad**에 쓴다.
  - 생성 스크립트의 **한 파일 모드**(`npm run themes -- --one <데이터> --out <경로>`)로 쓴다. `index.json`과 `NOTICE.md`는 건드리지 않고,
    라이선스는 그 파일 안에 적는다: 머리 주석은 "The full text is at the end of this file"이고 전문은 파일 끝에 주석으로 붙는다(머리에
    두면 Apache-2.0 전문이 색 위에 200줄 온다, 2026-10-25 사용자 결정). 목록의 테마와 같은 코드가 써서 그 두 줄과 끝의 전문 말고는 같다.
    `Import Theme`이 거부할 이름, 이미 있는 파일, 목록 안의 자리에는 쓰지 않는다.
- **이름:** 파일 이름이 가져온 테마의 이름이다([설정과 테마](settings-theme.md)). `Themes.nameOf`의 규칙(글자·숫자·공백·`_`·`-`·괄호,
  64자)에 맞추고, `system`/`light`/`dark`와 겹치면 다른 이름을 제안한다(가져오기에서 거부되므로). 저장소 목록의 id와 겹치면 알린다
  (나중에 그것을 받으면 덮어쓸지 묻게 된다).
- **라이선스:** 개인이 들여 쓰는 것이라 막지 않지만, 라이선스가 없거나 MIT·Apache 같은 허용 라이선스가 아니면 결과와 함께 알린다.
- **보고에 더한 두 가지**(M15, 2026-10-26):
  - 대비 표에서 사이드바의 `git_modified`/`git_untracked`가 `ui.background`에 대고 4.5:1 아래면 짚는다. 원본 색이라 고치지는 않는다.
  - 그 파일을 들일 수 있는 앱을 말한다. `Import Theme`은 파일 하나를 받을 때 `format`을 거치지 않는다. 그래서 키를 모르는 예전 앱에
    들이면 모르는 키로 보고 테마 전체를 번들 색으로 그린다.
- **대응표는 키를 모두 덮는 체크아웃에서 읽는다.** 키가 바뀐 뒤 앱이 릴리스되기 전까지는 `main`의 대응표에 새 키의 줄이 없다.
- **테마 저장소의 `user-themes/`(`.gitignore`에 든다)에 `<이름>.toml`을 만드는 것까지다**(2026-10-22 사용자 결정의 "레포의
  `user-themes/`"를 테마 저장소로). 공개 저장소라 커밋되지 않게 한다. 기기로 건네는 것은 스킬의 일이 아니다.

## 하지 않는 것

- `system`이 따를 라이트·다크 테마를 고르는 것(`light_theme`/`dark_theme`)은 이번에 하지 않는다(2026-10-22 사용자 결정). `system`은
  지금처럼 번들 `light`/`dark`다.
- 받은 테마의 자동 갱신과 "새 버전 있음" 표시(사용자 테마로 저장하기로 해서).
- 받기 전에 테마를 적용해 보는 미리보기. 목록의 색 띠가 그 일을 한다. 목록에서 움직이는 동안 미리 적용하는 것도 없다.
- 다른 목록 주소를 더하는 것, 비공개 저장소.
- 이탤릭·굵게 같은 글꼴 모양. 테마 키가 색뿐이다.
- 라이선스를 앱 안에서 보여 주는 화면.

## 성공 조건

이 주제가 됐다고 말할 수 있는 조건과 확인 방법이다(`AGENTS.md`). ✅는 충족, ⬜는 아직이다. 확인한 때와 자세한 내역은
[`skiffcode.plan.done.md`](../skiffcode.plan.done.md)의 그 마일스톤에 있다.

테마 저장소:

- ✅ 공개 저장소 `bynaki/skiffcode-themes`에 45개 테마, `index.json`, README(묶음마다 원본 링크·소개·라이선스), `LICENSE`,
  `NOTICE.md`(묶음마다 라이선스 전문)가 있고, 로그인 없이 `index.json`과 테마 파일이 받아진다.
- ✅ 저장소의 vitest: 생성 결과가 커밋된 `themes/*.toml`·`index.json`·`NOTICE.md`와 같다. 모든 파일에 원본·커밋·라이선스 머리
  주석이 있다. `editor.foreground` 대비가 4.5:1 이상이다(`lowContrast` 예외는 실제로 미달일 때만). `base`가 배경의 밝기와 맞는다.
  `index.json`의 `size`·`sha256`·`swatch`가 파일과 맞는다. 대응표의 키가 `reference/light.toml`과 같다 — 확인: `npm test`.
- ✅ 앱의 파서: `SKIFF_THEMES_DIR`를 준 JVM 테스트에서 45개가 모두 `ThemeToml`로 문제없이 읽힌다.

앱:

- ✅ JVM: 번들은 `light`·`dark`뿐이고 예약어는 `system`/`light`/`dark`다. 목록 받기: `index.json`을 읽고(`format`, 줄마다의 검사),
  높은 `format`은 거부한다. 테마 받기: 크기 상한, 길이·sha256 불일치, `ThemeToml` 문제가 있으면 저장하지 않는다. 받은 것은 사용자
  테마로 저장되고 같은 이름은 확인 뒤 덮어쓴다 — 가짜 HTTP로. `:code` 린트에 새 경고가 없다.
- ✅ vitest: 커맨드: `Theme: …`가 없고 `Theme...`·`Download Theme...`가 있다. 테마 목록: 설치된 것만, 차례·표시·띠·퍼지 검색,
  고르면 `setTheme`과 `>`로 닫힘. 받을 목록: 받는 중 줄, 실패 배너와 `>`, installed 표시, 퍼지 검색, 고르면 받기 요청과 `>` —
  확인: `commands.test.ts`, `palette.test.ts`.
- ✅ 탭: `Download Theme...`에 45줄이 색 띠와 함께 보이고 `mocha`를 치면 좁혀진다. 고르면 받아서 메뉴·사이드바·팔레트·viewer·
  editor·diff·렌더된 마크다운·상태바 아이콘이 한 번에 바뀌고, `settings.toml`에 `theme = "<id>"`가 적히며, 앱을 다시 켜도 남는다.
  `Theme...`에 그 테마가 표시와 함께 있다. 비행기 모드에서 `Download Theme...`은 배너를 내고 `>`로 돌아온다.
- ✅ 탭, 사용자 확인: 45개 테마로 같은 견본(Python·TypeScript 파일의 viewer, git 변경이 있는 diff, 렌더된 마크다운)을 찍은 모음을
  사용자가 원본 테마와 견주어 보고 괜찮다고 한다. 모음은 스크립트가 테마를 차례로 받아 바꾸며 `screencap`으로 만들고, 끝나면 받은
  테마를 지우고 `settings.toml`을 md5로 되돌린다. (2026-10-25 사용자 결정: 모음은 Python diff와 마크다운으로 마치고
  TypeScript viewer는 다시 찍지 않는다.)
- ✅ 폴드8 커버 화면(글꼴 배율 1.5): 두 목록의 이름과 띠가 잘리지 않고 목록이 스크롤된다. 사용자 기기라 받기·바꾸기는 사용자에게
  묻고, 끝나면 되돌린다.

변환 스킬(M14):

- ✅ vitest(테마 저장소): 한 파일 모드가 같은 데이터에 목록 모드와 똑같은 toml을 쓰고(라이선스가 있는 곳만 다르다), `index.json`·
  `NOTICE.md`·`themes/`를 건드리지 않는다. 잘못된 데이터(빠진 필수 키, 색이 아닌 값)는 파일을 쓰지 않고 실패한다.
- ✅ 스킬: 목록에 없는 테마 둘로 `/theme-convert`를 끝까지 돌린다 — VS Code 원본 하나, Neovim 원본 하나(어느 것으로 할지 그때
  사용자에게 묻는다). 한 번 돌릴 때마다 저장소에서 바뀐 것은 `user-themes/`의 새 파일 하나뿐이고(커밋되지 않음), 머리 주석에
  원본·커밋·라이선스가 있다. `system`/`light`/`dark`와 겹치는 이름을 주면 다른 이름을 제안한다.
- ✅ 앱의 파서: 그 두 파일이 `SKIFF_THEMES_DIR` 테스트처럼 `ThemeToml`로 문제없이 읽힌다.
- ✅ 탭, 사용자 확인: 그 두 파일을 `Import Theme`으로 들이면 경고 배너 없이 들어오고, 견본을 찍은 화면을 사용자가 원본과 견주어 보고
  괜찮다고 한다. 확인용으로는 adb로 탭의 `Download/`에 넣어 들인다.
- ✅ 스킬의 프런트매터에 `disable-model-invocation: true`가 있고, 스킬 목록에 명령으로 보인다.

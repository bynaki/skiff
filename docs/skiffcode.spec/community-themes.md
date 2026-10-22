# 커뮤니티 테마와 테마 목록

[목차](../skiffcode.spec.md)

원래 요구사항은 [`my.next.spec.md`](../my.next.spec.md)의 "커뮤니티 테마들 .toml 제공"이다. 많이 쓰는 커뮤니티 테마를 Skiff Code
테마 toml로 바꿔 **번들로 기본 제공**하고, 팔레트의 `Theme...` 하나로 목록을 띄워 고른다. 테마 파일의 모양과 검사·폴백,
import/export, 복사·편집·삭제는 [설정과 테마](settings-theme.md) 그대로다. 이 문서는 그 위에 더하는 것만 적는다.

## 무엇을 넣나

- **요구사항의 15개 저장소의 모든 변형과, 폴드8의 사용자 테마 `ocean copy`다**(2026-10-22 사용자 결정). 대비 단계(hard·medium·soft)도
  각각 하나의 테마다. 이탤릭을 끈 변형(winteriscoming의 `no-italics`)은 넣지 않는다 — Skiff 테마에 글꼴 모양 키가 없어 색이
  같은 두 벌이 된다. 합쳐 **46개**이고, 지금의 `light`·`dark`와 `system`을 더해 목록은 49줄이다.

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
  | Ocean | 폴드8의 `ocean copy`(사용자 것) | — | `ocean` | 1 |

  라이선스는 2026-10-22에 GitHub API로 봤다. 변형 이름은 저장소가 부르는 이름을 따르고, 구현 때 원본에서 다시 맞춘다.
  `cyberdream-muted`는 구현 때 저장소에서 찾은 세 번째 변형이다(`colors/cyberdream-muted.lua`, README의 `variant = "muted"`).
  넣지 않은 것: VS Code 포트만의 변형인 Dracula Soft(`dracula/visual-studio-code`가 Dracula 색의 채도를 20% 낮춘 것 — Dracula
  스펙의 변형은 Dracula와 Alucard뿐이다), Ayu의 `unbordered`(경계선만 다른 모양 변형), Rosé Pine의 `no-italics`.
- **id**는 소문자와 `-`만 쓴다. `settings.toml`의 `theme = "catppuccin-mocha"`와 `assets/themes/<id>.toml`의 파일 이름이다.
  화면에 보이는 이름(`Catppuccin Mocha`, `Rosé Pine Dawn`)은 따로 둔다(아래 "목록 파일").
- **Ocean**은 폴드8 앱 안의 `filesDir/themes/ocean copy.toml`을 그대로 가져와 머리 주석만 붙인다. 사용자 기기라 꺼내는 것은
  묻고 한다(debug 빌드의 `run-as`).

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
  | `ui.border` | `panel.border`, `sideBar.border`, `editorWidget.border` | `FloatBorder` fg, `WinSeparator` fg | `editor.gutter_border` |
  | `ui.faint` | `disabledForeground` | `NonText` fg | `ui.muted` |
  | `ui.subtle` | `list.hoverBackground` | `CursorLine` bg | `editor.selection` |
  | `ui.accent` | `focusBorder`, `button.background`(불투명한 것만) | `Function` fg | `syntax.definition` |
  | `ui.scrim` | — | — | `editor.background` + 알파 `99` |
  | `ui.strong_background` / `strong_foreground` | `button.background` / `button.foreground` | `PmenuSel` bg / fg(없으면 `Pmenu` fg) | `ui.foreground` / `ui.background` |
  | `editor.background` / `foreground` | `editor.background` / `editor.foreground` | `Normal` bg / fg | — (반드시 있다) |
  | `editor.gutter_background` | `editorGutter.background` | `SignColumn` bg | `editor.background` |
  | `editor.gutter_foreground` | `editorLineNumber.foreground` | `LineNr` fg | `ui.muted` |
  | `editor.gutter_border` | `editorGroup.border` | `WinSeparator` fg | `ui.border` |
  | `editor.cursor` | `editorCursor.foreground` | `Cursor` bg | `editor.foreground` |
  | `editor.selection` | `editor.selectionBackground` | `Visual` bg | `ui.subtle` |
  | `editor.code_background` | `textCodeBlock.background` | `ColorColumn`·`CursorLine` bg | `ui.background` |
  | `syntax.keyword` | `keyword`, `storage` | `@keyword`, `Keyword` | — |
  | `syntax.atom` | `constant.language` | `@boolean`, `@constant.builtin` | `syntax.literal` |
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
  | `syntax.invalid` | `invalid`, `editorError.foreground` | `DiagnosticError` | — |
  | `syntax.warning` | `editorWarning.foreground` | `DiagnosticWarn` | — |
  | `diff.added` | `editorGutter.addedBackground` | `GitSignsAdd`, `diffAdded`, `Added` | — |
  | `diff.removed` | `editorGutter.deletedBackground` | `GitSignsDelete`, `diffRemoved`, `Removed` | — |
  | `diff.changed` | `editorGutter.modifiedBackground` | `GitSignsChange`, `diffChanged`, `Changed` | — |

  - 알파가 든 원본 색(VS Code의 `#rrggbbaa` 선택색 등)은 **그대로 둔다.** Skiff 테마가 `#rrggbbaa`를 받는다. 다만 `diff.*`는
    `diff_alpha`가 다시 곱해지므로 불투명하게 바꾼다(그 배경 위에 섞은 색).
  - `base`는 원본의 라이트/다크 구분이다. 상태바 아이콘과 빠진 키의 폴백이 그것을 따른다.
- **생성 스크립트로 만들고, 만든 파일을 커밋한다.** `code/web/scripts/themes/data/<묶음>.json`에 묶음마다 데이터 파일(원본 위치·
  커밋 SHA·라이선스 전문·위 표의 키마다 고른 값과 그 값이 원본의 어느 이름에서 왔는지, 색 하나가 한 줄) 하나와, 그것을 toml로
  쓰는 `generate.ts` 하나를 둔다. Node 24가 `.ts`를 바로 돌린다(`npm run themes`). 늘어난 의존성은 스크립트의 타입을 위한
  `@types/node`(개발용) 하나다. 빌드 때 네트워크에서 받지 않는다 — 원본에서 값을 옮기는 것은 데이터 파일을 쓸 때 한 번이다.
  - 데이터 파일에 없는 키는 `generate.ts`가 위 표의 "없을 때"로 채운다. "—"인 키가 없거나, 서로를 가리키는 두 키(`ui.border`와
    `editor.gutter_border`, `ui.subtle`과 `editor.selection`)가 둘 다 없거나, 색이 아닌 값이 있으면 아무것도 쓰지 않고 실패한다.
    `ui.scrim`은 VS Code에 맞는 것이 없어 늘 `editor.background`에 알파 `99`다(번들 `light`·`dark`의 scrim과 같은 알파).
  - VS Code 원본은 `scripts/themes/vscode.ts <theme.json>`이 위 표대로 읽어 데이터의 `colors`를 낸다. TextMate 스코프는 VS Code처럼
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
  - Neovim 원본은 `scripts/themes/neovim.ts <플러그인 디렉터리> <colorscheme> [light|dark]`가 읽는다. 정적으로 Lua를 읽지 않고,
    `nvim --headless --clean`에서 그 색 구성표를 실제로 불러(`background`가 주어지면 먼저 맞추고) 위 표의 그룹을 Neovim이 그리는
    대로 읽는다: 링크를 따라가고(색 구성표가 정하지 않은 tree-sitter 그룹은 Neovim 기본 링크의 끝), `reverse`인 그룹은 뒤집은
    색이며 빈 쪽은 그 아래 글(`PmenuSel`은 `Pmenu`, 나머지는 `Normal`)의 색이다 — Neovim 자신의 `PmenuSel`이 `reverse`뿐이다.
    클래스 이름은 언어 서버가 칠하면 `@lsp.type.class`, 아니면 `@type`이라 그 차례로 본다. 색 구성표의 코드를 Neovim처럼 돌리고,
    캐시는 임시 디렉터리에 쓴다. `base`는 `Normal` 배경이 흰색과 검은색 중 어디에 가까운지로 정한다 — `cyberdream-light`와
    `kanagawa-lotus`는 밝은 색을 불러 놓고 `background`를 `dark`로 둔다. 모든 번들의 `base`가 배경과 맞는지 테스트가 본다. 주석은 `# @number fg (via Constant)`, `# PmenuSel bg (reversed)`처럼 링크의 끝과 뒤집힘을 적는다.
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
  - vitest가 생성 결과와 커밋된 `assets/themes/*.toml`이 같은지 본다. 손으로 고친 번들 파일은 테스트가 잡는다.
  - 스크립트는 테마마다 WCAG 대비(`editor.foreground`·문법 색 대 `editor.background`)를 표로 낸다. **`editor.foreground`만
    4.5:1 미만이면 실패**로 하고, 나머지는 보고만 한다 — 주석을 일부러 흐리게 둔 테마(Rosé Pine, Vesper)가 있고, 원본을 따르는
    것이 우선이다.
    - 예외는 테마 데이터의 `lowContrast`에 이유를 적은 것뿐이다. 그 테마는 실패 대신 경고를 내고, 원본이 바뀌어 4.5:1을 넘으면
      테스트가 예외를 지우라고 실패한다. 지금은 Kanagawa Paper Canvas 하나다(원본 `#616569` 위 `#e1e1de`, 4.48:1 — 원본 그대로
      넣기로 2026-10-22 사용자 결정).
- **파일마다 머리 주석**에 테마 이름, 원본 저장소와 커밋, 라이선스, 저작권자, "Skiff Code 테마로 바꾼 것"을 적는다.
  `Export Theme`은 원문을 내보내므로 이 주석이 함께 나간다. 라이선스 전문은 `assets/themes/NOTICE.md` 하나에 묶음마다 모은다
  (Apache-2.0인 Tokyo Night은 전문과 함께 바꾼 것임을 적는다). 앱 안에서 그것을 보여 주는 화면은 이번에 만들지 않는다.

## 목록 파일과 이름

- `assets/themes/index.json`이 번들의 목록이다: id, 보이는 이름, 묶음, `base`. 생성 스크립트가 함께 쓰고, `light`·`dark`도 여기
  든다(보이는 이름 `Light`·`Dark`). Kotlin의 `Themes`는 지금 `light`·`dark`를 박아 둔 것(`Theme.kt`의 `bundled`) 대신 이 파일을
  읽는다. 페이지의 `THEME_TITLES`도 없어지고 이름은 Kotlin이 싣는다.
- **번들 id는 예약어다.** `Themes.nameOf`가 `system`/`light`/`dark`처럼 대소문자 없이 거부한다 — 가져오기와 `Copy Theme` 둘 다.
  `catppuccin-mocha.toml`을 들이면 이름 규칙 배너로 거부된다. 고친 것을 들이려면 다른 이름으로(`catppuccin-mocha mine.toml`).
- **이미 같은 이름의 사용자 테마가 있으면 사용자 것이 이긴다**(2026-10-22 사용자 결정). 앱이 업데이트되어 새 번들
  id가 생겨도 그 사람의 파일은 건드리지 않고, 목록에는 그 사람 것 하나만 나온다. 탭에 예전에 들인 `ocean`이 이 경우다.

## 팔레트의 `Theme...`

- **`Theme: …` 명령들을 `Theme...` 하나로 바꾼다**(요구사항). 49개가 커맨드 목록에 섞이면 팔레트가 테마로 덮인다. `Import Theme`,
  `Export Theme`, `Copy Theme`, `Edit Theme`, `Delete Theme`은 그대로 커맨드다.
- **`Theme...`를 실행하면 같은 팔레트가 테마 목록으로 바뀐다**(2026-10-22 사용자 결정, VS Code의 Color Theme 고르기와 같다). 따로
  띄우는 창은 만들지 않는다. [팔레트](palette.md)의 네 번째 모드지만 **입력 버튼의 스와이프로는 가지 않고** `Theme...`로만 들어간다.
  - 아무것도 치지 않아도 **전부 보인다**(최근 다섯 개가 아니다). 차례는 `System`, `Light`, `Dark`, 번들의 커뮤니티 테마(보이는 이름
    순), 그 사람의 테마다.
  - 줄마다 보이는 이름과 **색 띠**(그 테마의 `editor.background` 위에 `editor.foreground`·`keyword`·`string`·`definition`·`ui.accent`
    다섯 칸)를 그린다. `System`은 지금 따르는 번들의 띠다. 지금 `settings.toml`의 `theme`인 줄에 표시가 붙는다.
  - 치면 보이는 이름으로 퍼지 검색한다(`mocha`, `gruv soft`).
  - 탭하거나 Enter면 `setTheme(id)`로 바꾸고 A로 닫힌다. **닫힌 뒤의 모드는 `>`다** — 테마 목록은 스와이프로 오는 모드가 아니라
    마지막 모드로 남지 않는다. 취소는 다른 모드와 같다(빈 곳 탭).
  - 띠의 색은 Kotlin이 `settings` 답의 `themes`에 이름과 함께 싣는다(id, 보이는 이름, `base`, 띠 색 여섯). 사용자 테마도 읽어서
    싣는다 — 파일이 작고, 목록을 열 때가 아니라 테마가 바뀔 때만 다시 읽는다.
- 명령 최근 목록에 남아 있던 `Theme: ocean` 같은 이름은 그 명령이 없어져 보이지 않게 된다(지금의 규칙대로). 따로 옮기지 않는다.

## 변환 스킬 `/theme-convert` (M14)

M13의 변환 방법을 다른 테마에도 쓰게 스킬로 만든다(2026-10-22 사용자 요청). M13이 끝난 뒤에 한다.

- **결과는 `Import Theme`으로 들일 toml 파일 하나다**(2026-10-22 사용자 결정). 번들에 넣지 않는다 — 번들에 더하는 것은 지금처럼 묶음
  데이터를 커밋하는 일이고 스킬의 일이 아니다.
- **레포의 `.claude/skills/theme-convert/`에 두고 커밋한다**(2026-10-22 사용자 결정). `commit` 스킬과 같은 자리다. 생성 스크립트와
  대응표가 같은 커밋에 있어 버전이 어긋나지 않는다.
- **명령으로만 부른다**(2026-10-22 사용자 결정): `/theme-convert <저장소 URL> [변형]`. 프런트매터에 `disable-model-invocation: true`를
  두어 "테마 바꿔 줘" 같은 말에는 뜨지 않는다.
- **같은 방법을 쓴다.** 스킬은 대응표를 다시 적지 않고 이 문서의 "바꾸는 방법"을 가리킨다. 하는 일은 M13에서 묶음 하나를 더할 때와
  같다: 원본 고르기(VS Code JSON → Neovim/Vim → helix·zed 순), 원본 파일과 커밋 SHA 적기, 대응표대로 묶음 데이터 쓰기, 생성 스크립트로
  toml 쓰기, 대비 표 보기. 다른 점은 둘이다:
  - 묶음 데이터는 레포가 아니라 **세션의 scratchpad**에 쓴다.
  - 생성 스크립트의 **한 파일 모드**(`npm run themes -- --one <데이터> --out <경로>`)로 쓴다. `index.json`과 `NOTICE.md`는 건드리지 않고,
    라이선스는 머리 주석에만 적는다. 번들과 같은 코드가 써서 결과 모양이 같다.
- **이름:** 파일 이름이 가져온 테마의 이름이다([설정과 테마](settings-theme.md)). `Themes.nameOf`의 규칙(글자·숫자·공백·`_`·`-`·괄호,
  64자)에 맞추고, 번들 id나 `system`/`light`/`dark`와 겹치면 다른 이름을 제안한다(가져오기에서 거부되므로).
- **라이선스:** 개인이 들여 쓰는 것이라 막지 않지만, 라이선스가 없거나 MIT·Apache 같은 허용 라이선스가 아니면 결과와 함께 알린다.
- **레포의 사용자 테마 폴더 `user-themes/`에 `<이름>.toml`을 만드는 것까지다**(2026-10-22 사용자 결정). 기기로 건네는 것은 스킬의
  일이 아니다. 폴더는 M14에서 만든다. 스킬은 그 파일을 커밋하지 않는다 — 커밋은 늘 사용자가 말할 때다. 레포에서 바뀌는 것은 그
  파일 하나뿐이고, 묶음 데이터는 scratchpad에 남는다.

## 하지 않는 것

- `system`이 따를 라이트·다크 테마를 고르는 것(`light_theme`/`dark_theme`)은 이번에 하지 않는다(2026-10-22 사용자 결정). `system`은
  지금처럼 번들 `light`/`dark`다.
- 목록에서 움직이는 동안 미리 적용해 보는 것(VS Code의 미리보기). 터치에는 "고르는 중"이 없다.
- 이탤릭·굵게 같은 글꼴 모양. 테마 키가 색뿐이다.
- 라이선스를 앱 안에서 보여 주는 화면.

## 성공 조건

이 주제가 됐다고 말할 수 있는 조건과 확인 방법이다(`AGENTS.md`). ✅는 충족, ⬜는 아직이다. 확인한 때와 자세한 내역은
[`skiffcode.plan.done.md`](../skiffcode.plan.done.md)의 그 마일스톤에 있다.

- ⬜ JVM: 번들 48개(커뮤니티 46 + `light`·`dark`)가 모든 키를 갖고 읽을 때 문제가 없다. `index.json`과 `assets/themes/*.toml`이 하나씩
  맞는다. 번들 id가 가져오기와 복사에서 대소문자 없이 거부된다. 같은 이름의 사용자 테마가 있으면 그것이 이긴다. `names`의 차례 —
  확인: `ThemeTomlTest`, `ThemesTest`. `:code` 린트에 새 경고가 없다.
- ⬜ vitest: 생성 스크립트의 결과가 커밋된 파일과 같다. 모든 파일에 원본·커밋·라이선스 머리 주석이 있고, `NOTICE.md`에 묶음마다
  라이선스가 있다. 모든 테마의 `editor.foreground` 대비가 4.5:1 이상이다. 팔레트: `Theme: …`가 없고 `Theme...`가 있다, 테마 목록의
  차례·표시·퍼지 검색, 고르면 `setTheme`과 `>` 모드로 닫힘 — 확인: 새 테스트 `themes.test.ts`, `commands.test.ts`, `palette.test.ts`.
- ⬜ 탭: `Theme...`에 49줄이 색 띠와 함께 보이고 지금 테마에 표시가 있다. 고르면 메뉴·사이드바·팔레트·viewer·editor·diff·렌더된
  마크다운·상태바 아이콘이 한 번에 바뀌고, `settings.toml`에 `theme = "<id>"`가 적히며, 앱을 다시 켜도 남는다. `mocha`를 치면 좁혀진다.
- ⬜ 탭, 사용자 확인: 46개 테마로 같은 견본(Python·TypeScript 파일의 viewer, git 변경이 있는 diff, 렌더된 마크다운)을 찍은 모음을
  사용자가 원본 테마와 견주어 보고 괜찮다고 한다. 모음은 스크립트가 테마를 차례로 바꾸며 `screencap`으로 만든다.
- ⬜ 폴드8 커버 화면(글꼴 배율 1.5): 테마 목록의 이름과 띠가 잘리지 않고 목록이 스크롤된다. 사용자 기기라 테마를 바꾸는 것은
  사용자에게 묻고, 끝나면 되돌린다.
- ⬜ 폴드8: 번들 `ocean`의 색이 사용자의 `ocean copy`와 키마다 같다.

변환 스킬(M14):

- ⬜ vitest: 한 파일 모드가 같은 데이터에 번들 모드와 똑같은 toml을 쓰고, `index.json`·`NOTICE.md`·`assets/themes`를 건드리지 않는다.
  잘못된 데이터(빠진 필수 키, 색이 아닌 값)는 파일을 쓰지 않고 실패한다 — 확인: `themes.test.ts`.
- ⬜ JVM: 한 파일 모드가 쓴 견본 toml이 `ThemeToml.read`에서 문제없이 읽힌다 — 확인: `ThemeTomlTest`(테스트 리소스의 견본).
- ⬜ 스킬: 번들에 없는 테마 둘로 `/theme-convert`를 끝까지 돌린다 — VS Code 원본 하나, Neovim 원본 하나(어느 것으로 할지 그때 사용자에게
  묻는다). 한 번 돌릴 때마다 레포에서 바뀐 것은 `user-themes/`의 새 파일 하나뿐이고, 머리 주석에 원본·커밋·라이선스가 있다. 번들 id와 겹치는 이름을 주면 다른 이름을 제안한다.
- ⬜ 탭, 사용자 확인: 그 두 파일을 `Import Theme`으로 들이면 경고 배너 없이 들어오고, 견본을 찍은 화면을 사용자가 원본과 견주어 보고
  괜찮다고 한다. 확인용으로는 adb로 탭의 `Download/`에 넣어 들인다.
- ⬜ 스킬의 프런트매터에 `disable-model-invocation: true`가 있고, 스킬 목록에 명령으로 보인다.

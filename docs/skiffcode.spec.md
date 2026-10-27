# Skiff Code 설계

코드 뷰어이자 에디터인 **독립 앱**이다. Skiff에서 "나중에"로 미뤄둔 미리보기 뷰어가 이 앱이 됐다.
원래 요구사항은 [`my.skiffcode.spec.md`](my.skiffcode.spec.md)에, 화면 설계 손그림은
[`menu.layout.jpg`](menu.layout.jpg)에 있다. 설계는 주제마다 [`skiffcode.spec/`](skiffcode.spec/)의 파일 하나에
있고, 이 문서는 그 목차다. 할 일 목록은 [`skiffcode.plan.md`](skiffcode.plan.md)에 있다.

## 읽는 법, 고치는 법

- **전부 읽지 않는다.** 이 목차와, 할 일 항목의 `읽을 spec:` 줄이 가리키는 파일만 읽는다. 다른 주제가 걸리면
  그때 아래 표에서 찾는다.
- 정한 것과 그 이유는 **주제에 맞는 파일**에 적는다. 맞는 파일이 없으면 새로 만들고 아래 표에 한 줄을 더한다.
- **파일마다 끝에 `## 성공 조건`이 있다**(2026-10-12 사용자 결정, 규칙은 `AGENTS.md`). 기능을 새로 적으면 성공 조건도 함께 적고, 애매하면
  적기 전에 사용자에게 묻는다. 같은 조건이 할 일 목록의 그 마일스톤에도 있다.
- 파일 사이의 참조는 `[레이어](layers.md)`처럼 링크로 쓴다. 코드 주석은 `docs/skiffcode.spec/<파일>.md`를 가리킨다.
- `docs/skiffcode.spec.md "git과 LSP"`처럼 예전 절 이름으로 된 참조(git 히스토리, 끝난 계획)는 "예전 절" 칸으로 찾는다.

## 목차

| 파일 | 예전 절 | 무엇을 정했나 |
|---|---|---|
| [`decisions.md`](skiffcode.spec/decisions.md) | 정한 것, 가정 | 같은 레포 멀티모듈, 데몬 없이 SSH exec(근거 셋), WebView 하나, 프로필 공유, 로컬 파일의 두 경로 |
| [`modules.md`](skiffcode.spec/modules.md) | 모듈 | `:core`·`:app`·`:code`가 나눠 맡는 것, Kotlin과 Web의 역할, 양방향 알림 브리지 |
| [`uri.md`](skiffcode.spec/uri.md) | URI | `skiffcode://`와 https 링크의 모양, 비밀번호 든 링크 거부, Skiff가 아닌 앱이 보낸 링크는 묻기 |
| [`open-flow.md`](skiffcode.spec/open-flow.md) | 파일을 여는 흐름 | 로컬·프로젝트·단일 파일을 가르는 순서, SFTP만으로 `.git` 찾기, 프로젝트 만들기와 exec·git 확인, 같은 서버의 다른 주소(호스트 키) |
| [`document-io.md`](skiffcode.spec/document-io.md) | 문서 읽기, 저장, 감시 | `TextLoader`, `DocumentSaver`, `FileWatcher`(감시), 열린 파일과 최근 파일 |
| [`layers.md`](skiffcode.spec/layers.md) | 레이어 | viewer·editor·diff가 한 뷰의 확장 묶음인 것, 각 레이어의 동작, diff 비교 대상 |
| [`top-menu.md`](skiffcode.spec/top-menu.md) | 화면 메뉴(상단 메뉴, 기다리는 동안) | ①~④ 메뉴, 사이드바와 프로젝트 트리, 파일 이름과 저장 점, 열린 파일 메뉴, 로딩 라인 |
| [`palette.md`](skiffcode.spec/palette.md) | 화면 메뉴(커맨드 버튼과 팔레트), 심볼 검색 | 우하단 버튼, `>`·🔍·`@` 모드, 최근 목록, 파일 검색 범위, 심볼 검색, 영문 키보드 힌트 |
| [`state.md`](skiffcode.spec/state.md) | 상태 저장 | 쓰면서 쌓이는 것은 `localStorage`, 그 세 규칙 |
| [`reset-data.md`](skiffcode.spec/reset-data.md) | 데이터 초기화 | 팔레트의 `Reset Data…` 체크리스트 |
| [`jumps.md`](skiffcode.spec/jumps.md) | 전 화면, 앞 화면 (점프 목록) | 점프 목록, 시스템 뒤로 가기 제스처의 가장자리, 적는 때 |
| [`markdown-links.md`](skiffcode.spec/markdown-links.md) | — | 렌더된 마크다운의 링크: 상대 경로는 묻지 않고 열기, `#제목`, https `.md`는 읽기 전용 웹 문서, GitHub blob → raw |
| [`outline.md`](skiffcode.spec/outline.md) | — | 사이드바 개요 탭: 머리의 두 번째 탭, 마지막에 보던 탭 기억, 공급원과 목록을 나눈 모양, 마크다운 목차(viewer·editor, lezer 제목, 수준별 들여쓰기, 화면 맨 위 줄로 고르는 지금 위치, 누르면 그 줄로 가고 점프 목록에 적음), 소스 코드 심볼(떠 있는 LSP 아니면 lezer, 심볼 트리 깊이, 범위로 고르는 지금 위치, 종류 아이콘) |
| [`margins.md`](skiffcode.spec/margins.md) | — | 문서 여백과 scrolloff: 세 코드 레이어에 아래 N줄(scrolloff)과, 줄바꿈이 꺼졌을 때 오른쪽 N글자(sidescrolloff), 마크다운 viewer는 예외 |
| [`nav-bar.md`](skiffcode.spec/nav-bar.md) | — | 오른쪽 가는 막대: 문서 전체의 git 변경과 LSP 진단, 보이는 구간 상자, 터치 없음, 마크다운 viewer는 예외, `nav_bar` 기본 끔 |
| [`settings-theme.md`](skiffcode.spec/settings-theme.md) | 설정과 테마 | `settings.toml`의 키와 잘못된 값, 테마 고르기·가져오기·복사·편집·지우기 |
| [`community-themes.md`](skiffcode.spec/community-themes.md) | — | 커뮤니티 테마 53개(17개 저장소의 모든 변형, M15에서 Nord·Nightfox를 더함)를 모은 공개 저장소 `bynaki/skiffcode-themes`와 앱의 `Download Theme...` 받기, 원본 배치를 따르는 대응표와 생성 스크립트, 라이선스, 팔레트의 `Theme...` 목록, 다른 테마를 가져올 toml로 바꾸는 `/theme-convert` 스킬 |
| [`git.md`](skiffcode.spec/git.md) | git과 LSP(앞쪽) | `RemoteExec`, `GitService`, git 거터와 기준을 다시 묻는 때 |
| [`lsp.md`](skiffcode.spec/lsp.md) | git과 LSP(뒤쪽) | `LspProcess`, `LspManager`, 다시 띄우기, 페이지 쪽 LSP, 두 번 탭 hover, 정의로 이동 |
| [`security.md`](skiffcode.spec/security.md) | 보안 규칙 | 브리지, 원격 내용 렌더링과 sanitize, exec, ProfileProvider — 커밋 전 두 번째 읽기 대상 |
| [`out-of-scope.md`](skiffcode.spec/out-of-scope.md) | 범위 밖 | 하지 않는 것, 데몬을 다시 꺼내는 조건과 M6 실측 |

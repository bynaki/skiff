# my next spec

## skiff code

### [done] 전 화면으로 이동, 앞 화면으로 이동
- vim의 ctrl+o(전화면으로), ctrl+i(앞화면으로) 기능과 같다.
- editor, viewer, diff 모두 작동해야한다.
- 파일간의 이동도 가능해야한다.
- 제스처로 동작한다.
- 화면 오른쪽 가장자리에서 왼쪽으로 스와프: 전화면으로
- 화면 왼쪽 가장자리에서 오른쪽으로 스와프: 앞화면으로

### 참조로 이동, Explorer

### 변경이력 Explorer

### 현재 선택된 파일:줄번호-줄번호 claude앱에 전달

### [done] 오른쪽에 가는 막대 네비게이션 바
- 오른쪽 가는 막대로 변경이력 표시하는 네비게이션바
- 그 문서 전체의 변경이력을 한 화면에 볼수있다.
- 터치는 지원하지 않음
- setting.toml에 끄고 키는 세팅.

### [done] fix: 01
- viewer 에서 더블탭 튤팁이 길어지면 화면이 잘리는 문제. 스크롤이 필요하다.
- 정의로이동 버튼을 심볼 시그니처와 docstring 사이에 넣자.

### [done] feat: 01
- editor 에서 화면 맨 아래에서 편집할때 너무 아래라 불편하다.
- vim에 socrolloff 처럼 문서 밑에 몇줄 공백을 두자.
- 화면여백이 아니라 문서 자체의 여백을 오른쪽과 아래에 두자.
- editor, viewer, diff 모두 전환할때 자연스럽게 맞추기위해 똑같이 여백 + scrolloff 적용. 단 마크다운 viewer는 예외.
- setting.toml에서 세팅할수 있게 하자

### [done] fix: 02
- 외부 `<서버 도메인>`와 tailscale에 등록된 `<프로필 별칭>`와 `<Tailscale 주소>`는 모두 같은 호스트이다.
- 프로젝트로 열때 같은 프로젝트인데도 불구하고 중복해서 열린다.
- 하나의 프로젝트로 인식하게 고쳐야한다.

### [done] markdown 문서 링크 열기
- markdown viewer에서 문서 링크를 탭하면 여는 기능
- .md 링크가 상대 경로이면 바로 연다.
- https 일때 viewer에서 링크로 표시히고 탭하면 연결앱 창 띠운다.
- https가 .md 파일이면 앱 내에서 연다.
- .md 파일이면 이 앱내에서 연다.

### [done] 커뮤니티 테마들 .toml 제공
- 아래 테마들을 skiffcode 테마에 맞게 .toml로 변환해서 기본 제공
- 커맨드 팔레트 메뉴 `Theme...` 로 하고 선택하면 테마 목록 창 띠우기
- 정한 것(2026-10-22): 변형은 대비 단계까지 전부, ocean은 폴드8의 `ocean copy`, 목록은 팔레트 안의 목록. 설계는 `skiffcode.spec/community-themes.md`, 할 일은 `skiffcode.plan.md`의 M13.
- https://github.com/catppuccin/catppuccin
- https://github.com/dracula/dracula-theme
- https://github.com/rose-pine/rose-pine-theme
- https://github.com/folke/tokyonight.nvim
- https://github.com/johnpapa/vscode-winteriscoming
- https://github.com/morhetz/gruvbox
- https://github.com/rebelot/kanagawa.nvim
- https://github.com/sainnhe/everforest
- https://github.com/vague-theme/vague.nvim
- https://github.com/scottmckendry/cyberdream.nvim
- https://github.com/thesimonho/kanagawa-paper
- https://github.com/nyoom-engineering/oxocarbon.nvim
- https://github.com/kepano/flexoki
- https://github.com/raunofreiberg/vesper
- https://github.com/ayu-theme/ayu-colors
- ocean theme

### [done] markdown 문서내 제목 목록(toc) 보이기
- 왼쪽 사이드바에 만든다.
- 프로젝트 트리 레이어, toc 레이어 등으로..
- 사이드바를 열었을때 왼쪽상단 사이드바 아이콘 옆에 `프로젝트 트리` 아이콘, 그 옆에 `toc` 아이콘 배치.
- 탭하면 사이드 바에 toc 보이기
- 정한 것(2026-10-26): 사이드바를 열면 마지막에 보던 탭, viewer와 editor(diff 제외), 수준별 들여쓰기, 지금 위치 강조, 누르면 이동하고 사이드바 닫기. 소스 코드 심볼은 나중에 같은 탭에 들어올 자리만 남김(탭 이름은 "개요"). 설계는 `skiffcode.spec/outline.md`, 할 일은 `skiffcode.plan.md`의 M16.

### [done] 사이드바 프로젝트 파일 목록에 변경된 파일 강조
- 사이드바 프로젝트 파일목록에 변경이력있는 파일 색상으로 강조
- 테마 키워드 필요하면 추가하고 모든 테마 (@skiffcode-themes 테마 포함) 적용

### [done] 사이드바 아웃라인에 소스코드 아웃라인 기능 추가
- 열린 파일이 소스코드일때 심볼 아웃라인 표시
- 정한 것(2026-10-27): 떠서 준비된 LSP가 있으면 그 답, 아니면 기다리지 않고 lezer. 지금 위치는 화면 맨 위 줄을 품은 가장 깊은 심볼. 종류는 이름 앞의 작은 선 아이콘. 대상은 TS/JS, Python. 설계는 `skiffcode.spec/outline.md`의 "소스 코드 심볼", 할 일은 `skiffcode.plan.md`의 M17.

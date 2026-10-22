# my next spec

## skiff code

### 전 화면으로 이동, 앞 화면으로 이동
- vim의 ctrl+o(전화면으로), ctrl+i(앞화면으로) 기능과 같다.
- editor, viewer, diff 모두 작동해야한다.
- 파일간의 이동도 가능해야한다.
- 제스처로 동작한다.
- 화면 오른쪽 가장자리에서 왼쪽으로 스와프: 전화면으로
- 화면 왼쪽 가장자리에서 오른쪽으로 스와프: 앞화면으로

### 참조로 이동, Explorer

### 변경이력 Explorer

### 현재 선택된 파일:줄번호-줄번호 claude앱에 전달

### 오른쪽에 가는 막대 네비게이션 바
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

### markdown 문서 링크 열기
- markdown viewer에서 문서 링크를 탭하면 여는 기능
- .md 링크가 상대 경로이면 바로 연다.
- https 일때 viewer에서 링크로 표시히고 탭하면 연결앱 창 띠운다.
- https가 .md 파일이면 앱 내에서 연다.
- .md 파일이면 이 앱내에서 연다.

### 커뮤니티 테마들 .toml 제공
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
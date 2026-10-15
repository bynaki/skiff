# 레이어

[목차](../skiffcode.spec.md)

- `pane`은 파일과 1:1이고 전체화면이다. viewer, editor, diff가 레이어로 겹쳐 있다.
- **레이어는 뷰가 아니라 확장 묶음이다.** `pane` 하나에 `EditorView` 하나와 `EditorState` 하나를 두고, 레이어를 바꾸는 것은 `Compartment`에 든 확장 묶음을 바꾸는 것이다. 그래서 문서, 파싱 트리, 높이맵, undo 기록이 세 레이어에 하나씩만 있고, 나간 레이어의 state field는 CM6가 알아서 버린다(2026-09-20에 정했다. 근거는 `AGENTS.md`의 CM6 관찰). 마크다운 viewer만 CM6가 아닌 별도 DOM이라 예외다.
- **색 네모:** `.toml`·`.css`·`.json`에서 `#0b3d5c` 같은 16진 색 앞에 그 색의 작은 네모를 붙인다(2026-09-29 사용자 요청과 결정). **보기만 하고** 누르는 것은 없다. 파일 종류를 좁힌 것은 다른 파일에서 `#add`, `#cafe` 같은 주석 글자가 색으로 잡히기 때문이다. 레이어 compartment 밖에 두어 세 레이어 모두에 보인다(`web/src/swatches.ts`).
- **viewer:**
  - 읽기 전용 CM6에 하이라이팅, 줄 번호, git 거터를 보여준다.
  - **문법은 이름으로 고른다** — `LanguageDescription.matchFilename`이 `Makefile` 같은 이름과 확장자를 함께 안다. 내용은 보지 않는다.
    - **`.jsonl`은 language-data가 모른다**(JSON 항목이 `json`과 `map`만 claim한다). 한 줄에 하나씩 든 JSON이라 같은 문법이 맞고, 이름의 끝 `l`을 떼어 JSON 항목이 답하게 한다(2026-09-23 사용자 요청). 문서 전체로는 값이 여러 개라 파서가 오류를 내지만 **줄바꿈마다 복구해서 모든 줄이 그대로 강조된다**(파서를 직접 돌려 확인: 3줄 중 3줄이 Object, 최상위 오류 노드 2개). 뷰어에 JSON 린터가 없어 그 오류가 화면으로 새지도 않는다.
  - 마크다운은 `markdown-it`(`html: false`)으로 렌더링하고 테마를 적용한다. 링크를 탭하면 여는 것은 [마크다운 문서 링크](markdown-links.md).
  - 스크롤과 확대/축소를 가장 먼저 잘 만든다. 핀치 줌은 CSS 변수 `--code-font-size`를 바꾸고, 손가락 사이의 줄이 제자리에 있게 한다.
  - 줌 중심 줄 고정은 CM6의 `EditorView.scrollIntoView(pos, {y: 'start', yMargin})` 효과로 한다. CM6가 줄 높이를 다시 잰 뒤 적용하기 때문이다. `requestMeasure`에서 `scrollTop`을 직접 쓰면 CM6의 스크롤 앵커와 싸워서 수만 px씩 어긋난다(M0에서 확인).
- **editor:**
  - viewer와 같은 `EditorState`를 쓰고, `Compartment`로 readOnly와 확장만 바꾼다.
  - 글자 크기는 viewer와 같이 핀치로 바꾸고, 상단 ②로 원래 크기에 돌아온다. 하단 ± 메뉴는 두지 않는다([화면 메뉴](top-menu.md)).
  - LSP 진단과 자동완성을 붙인다.
- **diff:**
  - 읽기 전용 unified 뷰다. 현재 버퍼를 문서로 두고 `@codemirror/merge`의 `unifiedMergeView({original, mergeControls: false})`로 비교 대상과 비교한다. 지운 줄은 블록 위젯으로 끼워진다.
  - **diff 알고리즘은 줄 단위로 먼저 하고, 바뀐 줄 범위 안에서만 글자 단위로 한다(`diffConfig.override`).** 패키지 기본값(`scanLimit` 500)은 큰 파일에서 파일 전체를 청크 하나로 포기하고, 제한을 풀면 2MB에 6.7초가 걸리며 그래도 부정확하다. 줄 단위는 2MB에 약 240ms다(M0에서 확인). git 거터의 `Chunk.build`도 같은 설정을 쓴다.
  - +/- 기호는 별도 거터로 그린다. 추가된 줄은 `lineMarker`, 지운 줄 블록은 `widgetMarker`에 지운 줄 수만큼 `-`를 쌓는다. 초록/빨강 배경의 투명도는 CSS 변수 `--diff-alpha`로 조절한다. 문법 하이라이팅도 한다(지운 줄까지).
  - **배경은 줄에만 칠하고 줄 안에서 바뀐 글자는 따로 칠하지 않는다**(2026-10-03 사용자 결정, `highlightChanges: false`). 글자 배경이 줄 배경 위에 겹쳐 새로 쓴 줄 전체가 두 배로 진해져 읽기 어려웠다. 줄 안의 작은 변경(`[ ]`→`[x]`)은 바로 위의 지운 줄과 견주어 찾는다.
  - **`@codemirror/view` 6.43.12에 버그가 있다.** 블록 위젯이 줄 경계에 있으면 `HeightMapBranch.forEachLine`이 범위를 clamp하지 않아서 `viewportLineBlocks`가 화면 밖 수천 줄로 늘어난다. 모든 거터가 그만큼 요소를 만들어서 줌 한 단계가 5배 이상 느려진다. 두 곳에 `Math.max(from, …)`/`Math.min(to, …)`를 넣으면 고쳐진다. **정한 것:** upstream에는 올리지 않고(CodeMirror는 AI가 쓴 코드를 받지 않는다), M5 diff 레이어에서 `patch-package`로 이 두 줄을 패치한다. CM6 버전을 올릴 때마다 이 함수에 clamp가 들어갔는지 확인하고, 들어갔으면 패치를 뺀다.
  - 스크롤과 줌은 viewer 코드를 그대로 쓴다. 줄로 스크롤할 때(레이어 전환, 링크의 줄) 그 줄 바로 앞의 지운 줄 블록은 그 줄과 한 덩어리라 같이 화면에 둔다(화면 절반까지).
  - **비교할 것이 없으면 ③이 diff를 건너뛴다**(2026-10-03 사용자 결정). 단일 파일, git 없는 프로젝트, HEAD에 없는 새 파일은 viewer ↔ editor만 돈다. diff에 있는 동안 비교할 것이 없어지면(프로젝트를 지웠다) viewer로 간다.
  - 비교 대상은 ④에서 고르고 **파일마다, 열려 있는 동안만** 기억한다(2026-10-03 사용자 결정). 닫았다 다시 열면 HEAD다. `localStorage`에는 두지 않는다.
  - **"직전 커밋"은 현재 버퍼와 비교한다**(2026-10-03 사용자 결정). 직전 커밋은 `git log -n 2 -- <rel>`의 두 번째, 즉 이 파일을 마지막으로 바꾼 커밋의 앞이다. 저장된 상태면 그 마지막 커밋의 변경이 그대로 보이고, 수정 중이면 지금 친 것까지 함께 보인다. 엄밀히 "직전 커밋 ↔ HEAD"로 하려면 문서가 버퍼가 아니어야 해서 뷰가 하나 더 필요하다 — 레이어가 뷰 하나라는 설계를 지키려고 이쪽을 골랐다.
  - 투명도는 `settings.toml`의 `[editor] diff_alpha`다([설정과 테마](settings-theme.md)).

## 성공 조건

이 주제가 됐다고 말할 수 있는 조건과 확인 방법이다(`AGENTS.md`). ✅는 충족, ⬜는 아직이다. 확인한 때와 자세한 내역은
[`skiffcode.plan.done.md`](../skiffcode.plan.done.md)의 그 마일스톤에 있다.

- ✅ viewer·editor·diff가 한 `EditorView`의 확장 묶음으로 바뀌고, 바꿔도 보던 줄과 undo가 이어진다 — 확인: 2MB 파일로 탭에서 레이어를 바꿔 JS 힙이 그대로(23.1MB), 탭에서 보던 줄 유지(M3).
- ✅ 2MB 파일에서 스크롤 86–91fps, 핀치 한 단계 3ms+8ms, 줄 단위 diff 약 240ms — 확인: 탭 측정(M0).
- ✅ 이름으로 문법을 고르고 `.jsonl`도 JSON으로 강조된다 — 확인: vitest `language.test.ts`.
- ✅ diff 레이어와 거터가 서버의 `git diff`와 같다. 비교할 것이 없으면 ③이 diff를 건너뛴다 — 확인: vitest `diff.test.ts`, 탭에서 `git diff -U0`와 비교(M5).
- ✅ 색 문자열 앞에 그 색의 네모가 `.toml`·`.css`·`.json`에서만 붙는다 — 확인: vitest `swatches.test.ts`(M4).

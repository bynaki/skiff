# 인수인계

다음 세션이 이어받기 위한 문서다. **세션을 끝낼 때마다 이 파일을 현재 상태로 덮어쓴다.**
계속 쌓는 기록이 아니다. 지난 기록은 git 히스토리에 남는다.

- 할 일 목록과 설계: [`plan.md`](plan.md)의 `# Skiff Code` 섹션
- 규칙, 툴체인, 커밋 전 점검, 보고와 알림 규칙: [`AGENTS.md`](AGENTS.md)
- 이 문서에 담는 것: 위 두 문서에 없는 **직전 세션의 맥락**(무엇을 했고, 왜 그렇게 정했고, 무엇이 아직 확인되지 않았는지)

---

## 마지막 세션 (2026-09-21, 두 번째): 커맨드 팔레트가 생겼다

### 지금 상태
- 브랜치 `plan/skiffcode`(git worktree). **이번 세션 커밋은 둘이다** — `0c6bd16`(팔레트 A/B/C 상태 기계)과
  그다음 하나(스와이프·퍼지 점수·최근 목록·가로 상한·아이콘). **push는 아직 하지 않았다.** origin은 `2aad401`이다.
- **M4의 첫 두 항목이 끝났고, `plan.md`의 첫 `- [ ]`는 M4의 커맨드 레지스트리**(레이어 전환·확대/축소·저장·
  사이드바·설정 열기 + 파일 모드 + lezer 심볼 모드)다.
- **`main`에는 아직 합치지 않았다.** 사용자가 "내가 언급할 때 하자"고 했으므로 **먼저 꺼내지 않는다.**
- 테스트: `:core` 37개, `:app` 24개, `:code` 118개, **페이지(vitest) 44개**(지난 세션 8개에서 늘었다).
  실패 없음. lint 경고 `:app` 4개, `:code` 11개로 지난 세션과 같다.
- **탭 한 대만 붙어 있었다.** 갤럭시탭 S10 FE (SM-X526N, Android 16/API 36). 폴더블(SM-F971N)은 이번에도
  연결되지 않아 아무것도 보지 못했다 — **지원 대상이므로 문서는 그대로 둔다.**
- 기기 상태는 되돌려 두었다: `show_ime_with_hard_keyboard`는 0, `/sdcard/Download/palette-test.txt`는 삭제,
  `adb forward`도 정리했다.

### 이번 세션에서 한 것
설계와 이유는 `plan.md`의 체크된 항목과 `### 상태 저장` 절에 적었다. 여기에는 그 문서에 없는 것만 남긴다.

**M4-1 커맨드 버튼 A/B/C + M4-2 스와이프와 퍼지 점수, 그리고 사용자가 그 위에 얹은 넷**

- 상태 기계는 `web/src/palette.ts`에 **DOM 없이** 있고 그리는 것은 `web/src/chrome/palette.ts`다. vitest가
  보는 것은 앞쪽이다. 점수는 `web/src/fuzzy.ts`, 저장은 `web/src/recents.ts`.
- **커맨드는 영문이고, 그 글자는 Kotlin을 거치지 않는다.** 페이지의 다른 글자와 다른 유일한 자리다.
- **팔레트가 열리면 키보드가 영문으로 온다.** `inputmode="email"` 하나다. 왜 그것뿐인지는 결정 표에 있고,
  **재는 방법**(삼성 키보드는 자판이 `screencap`에 안 나온다 → `dumpsys activity service …honeyboard`의
  `currentLang`)은 `AGENTS.md`에 적었다.
- **실행은 `click`에서 한다.** `pointerdown`에서 하면 실행이 화면을 바꾼 뒤 뒤따르는 click이 **새 화면에**
  떨어진다 — `Toggle Sidebar`가 사이드바를 열고, 그 click을 사이드바 스크림이 받아 곧바로 닫았다.
  `pointerdown`은 `preventDefault`로 포커스만 붙잡는다(입력칸이 포커스를 잃으면 키보드가 내려가고,
  키보드만큼 짧던 페이지가 손가락 밑에서 자란다).
- **스와이프에는 `touch-action: none`이 필요하다.** 없으면 몇 픽셀 만에 브라우저가 스크롤로 가져가며
  `pointercancel`을 보낸다. 한 제스처에 한 칸만 돈다 — 24px마다 계속 돌게 했더니 60px에 두 칸이 넘어가
  겨냥해야 하는 제스처가 됐다.
- **점수는 한 번의 스캔이다.** 질의의 글자마다 "여기서 끝나면 얼마"를 이름의 자리마다 들고 다음 글자로
  넘긴다. 왼쪽부터 걸으며 처음 찾은 자리를 쓰면 `Toggle Layer`에서 `tl`의 `l`이 `Toggle`의 것이 된다.
  **이어지는 것(8)이 단어 첫 글자(8)에서 건너뛰기(-2)를 뺀 값보다 커야** `togg`가 `The Old Grey Goose`보다
  `Toggle Layer`를 고른다 — 처음에 6으로 두었더니 정확히 동점이었다.
- **`localStorage`를 이번에 처음 켰다.** `domStorageEnabled`는 기본이 꺼짐이고, 그때 `window.localStorage`는
  **null**이라 `.setItem`이 `TypeError`로 떨어진다(기기에서 확인). `MainActivity`에서 한 줄로 켰고,
  `recents.ts`는 저장소가 없어도·깨져 있어도 빈 목록으로 간다.
- 상단 메뉴의 셋(`Toggle Layer`, `Reset Zoom`, `Toggle Sidebar`)을 팔레트에 등록해 두었다. 레지스트리가
  다음 항목이라 지금은 이 셋뿐이고, `main.ts`에서 상단 메뉴와 **같은 함수**를 나눠 쓴다.
- 아이콘은 `topbar.ts`의 `svg()`를 `export`해서 함께 쓴다. 아이콘 그리는 방식이 페이지에 한 군데만 있게 했다.

### 실기기에서 본 것 (탭)
페이지 상태는 DevTools로 읽었고, **아이콘과 배치만 `screencap`으로 봤다.**

- A→B→C→실행→A가 전부 진짜 터치로 돌았다. 키보드는 팔레트에서 **English**, 문서를 탭하면 **한국어**다.
- `tl`에 `*Toggle Layer`가 `Toggle Sidebar`보다 먼저 왔고, 결과 줄을 눌러도 입력 버튼을 눌러도 실행됐다.
  빈 곳을 누르면 취소되고 **그 탭이 문서에 닿지 않는다.** 외장 키보드의 ↓와 Enter도 된다.
- 스와이프로 `>`→🔍→`@`→`>`가 한 칸씩 돌고 아래로도 돈다. 질의가 든 채 모드를 바꾸면 결과가 그 자리에서
  다시 난다.
- 최근 목록: 실행하면 `palette.recent`에 `{"command":["Toggle Sidebar"]}`가 적히고, **force-stop 후 다시
  켜도** 팔레트가 그것을 선택된 채로 열었다.
- 가로: 페이지 1152px에서 팔레트가 **600px**, 오른쪽 16px·아래 16px. 배너가 떠 있을 때 버튼은 배너 위였다.

### 지난 인수인계에서 틀렸던 것
- **"push가 밀려 있다"는 이 세션을 시작할 때 이미 사실이 아니었다.** `2aad401`까지 origin에 올라가 있었다.

### 다음 세션이 할 일
1. `AGENTS.md`를 읽는다. 특히 툴체인 제약, Before committing
   (**커밋마다, push마다 사용자에게 먼저 묻는다**), 보고와 알림 규칙.
2. **push가 밀려 있다.** 커밋 둘이 올라가지 않았으니 먼저 물어본다.
3. **`plan.md`의 첫 `- [ ]`인 커맨드 레지스트리**를 한다. 레이어 전환·확대/축소·**저장**·사이드바·설정 열기와
   파일 모드(열린 파일 + 같은 디렉토리), 단일 파일 심볼 모드(lezer).
   - 팔레트 쪽은 `items(mode)` 하나만 채우면 된다. 지금 `main.ts`의 `commands()`가 그 자리다.
   - **저장 커맨드가 붙으면 그다음 항목(M3에서 옮겨 온 확인)에서 `DocumentSaver`가 앱에서 처음 돈다.**
4. 사용자가 물어 둔 것 하나가 열려 있다: **확대 배율을 `localStorage`에 넣을지** — 규칙상 넣는 게 맞고
   `recents.ts`와 같은 모양으로 열 줄쯤이다. `settings.toml` 항목 때 같이 할지 먼저 할지만 정하면 된다.

### 아직 확인하지 않은 것
안 되는 게 나오면 `plan.md`의 설계를 먼저 고친다.
- **팔레트를 좁은 화면에서 보지 못했다.** 가로 600px 상한은 탭에서만 걸렸고, 폴더블 커버(475dp)에서는
  상한 아래라 전처럼 화면 너비를 쓴다 — 계산이 그렇다는 것이지 본 것이 아니다. **스와이프도 손가락이 아니라
  CDP 터치로만 봤다.**
- **다른 키보드에서 `inputmode="email"`이 먹는지 모른다.** 본 것은 탭의 삼성 키보드 하나다. Gboard는
  대체로 따르지만 그 언어가 추가돼 있을 때만이다. 안 먹으면 `EditorInfo.hintLocales`가 남은 길이다.
- **`localStorage`가 꽉 찼을 때**는 코드가 삼키지만 본 적은 없다.
- **열린 파일이 30개를 넘는 것을 실제로 보지 못했다.** 상한은 1로 낮춰서 확인했다.
- **`ACCESS_LOCAL_NETWORK`를 거부했을 때**가 남았다(`pm revoke` 후 다시 열면 된다).
- **Skiff → Skiff Code 프로필 공유를 아직 못 봤다.** 보려면 **Skiff에 서버를 하나 넣고** Skiff에서 파일을
  탭해야 한다. 이 맥의 원격 로그인은 켜져 있다 — **비밀번호 입력은 사용자가 직접 해야 한다.**
- **`content://` 감시를 기기에서 본 적이 없다.** 컬럼을 주는 provider와 안 주는 provider 양쪽 다.
- **Skiff(`:app`)의 호스트키 창은 기기에서 보지 않았다.** 로직은 `:core` 공용이고 남은 것은 다이얼로그 UI뿐이다.
- **`DocumentSaver`를 실제 앱에서 돌려본 적이 없다.** 저장을 거는 UI가 다음 항목이라 유닛 테스트뿐이다.
- **손가락으로 하는 핀치.** 확인은 전부 CDP `Input.dispatchTouchEvent`로 만든 터치였다. 사이드바를 손가락으로
  밀어 여는 것도 없다 — 지금은 ①과 바깥 탭뿐이다.
- **editor의 손 사용감** 전반: 선택 핸들, 길게 누르기, 커서 옮기기.
- **undo에 닿을 UI가 아직 없다.** 팔레트는 생겼지만 undo 커맨드는 레지스트리 항목이다.
- **서명이 다른 앱이 끼어드는 경로**(가짜 Skiff Code, 가짜 provider)는 그런 앱을 만들지 않아 보지 못했다.
- **Android 15 미만에서 링크가 전부 확인창을 받는 것.** 두 기기 다 15 이상이다.
- release APK의 배포용 서명. 지난번에는 debug 키로 서명해서 확인만 했다.
- **실제 SSH 서버로 LSP를 띄워 본 적은 없다.** M0의 확인은 이 맥 안의 MINA 루프백이다.
- 진단이 수백~수천 개일 때의 비용. 큰 파일에서 편집 중 동기화 비용. **2MB 파일에서 병합 diff가 얼마나
  드는지도 아직 안 쟀다.**
- lezer 심볼 추출, 테마 CSS 변수, SAF import/export는 라이브러리 버전도 고르지 않았다.

### 고치지 않고 둔 것
사용자에게 알렸고, 범위 밖이라 손대지 않았다.

1. **확대 배율이 재생성을 넘기지 못한다.** `zoom.ts`는 메모리뿐이라 앱을 껐다 켜거나 다크 모드·글꼴 배율로
   액티비티가 재생성되면 14px로 돌아간다. **이제 규칙(`localStorage`)이 생겼으니 넣으면 되고**, 사용자에게
   지금 할지 `settings.toml` 때 할지 물어 둔 상태다.
2. **재생성 때 열린 파일 목록과 활성 파일은 넘어가지만, 파일들의 버퍼·레이어·스크롤은 넘어가지 않는다.**
   `uiMode`(다크 모드), 언어, 글꼴 크기는 `configChanges`에 없어서, 바꾸면 보던 레이어가 뷰어로 돌아가고
   **타이핑하던 것이 사라진다.** 일부러 재생성시키려면 `settings put system font_scale 1.3` 후 되돌린다.
   이것도 `localStorage`가 답이 될 수 있지만 **버퍼 본문까지 적을지부터 정해야 한다.**
3. **확인 창이 떠 있는데 새 링크가 오면 앞의 것이 소리 없이 대체된다.** adb로 파일 여럿을 열 때는
   **한 번에 하나씩 열고 "열기"를 누른 뒤 다음을 보내야 한다.**
4. **줄바꿈이 없어 좁은 화면에서 문장이 잘린다.** 커버 화면(475dp)에서 마크다운 본문 한 줄이 오른쪽으로
   사라진다. `settings.toml`에 항목으로 잡혀 있지만 **기본값을 무엇으로 둘지**가 폴더블 때문에 실제 문제가 됐다.
5. **경로 빵부스러기가 좁은 화면에서 오른쪽으로 잘린다**(Skiff, 커버 화면). 사이드바의 경로 줄은 왼쪽이
   잘리게 해 두었으니 Skiff도 같은 방향이 맞을 듯하다.
6. **시스템 글꼴 배율이 그대로 곱해진다.** 폴더블의 1.5 때문에 14px이 21px이 된다. 따를지 지울지 정해야 한다.
7. **키 교환 대기가 5분이다.** 줄이고 싶으면 `SshClientFactory.KEX_TIMEOUT_MS`다.
8. 한 번 봤지만 재현하지 못한 것: Skiff 화면 아래 절반이 **빈 키보드 창**에 먹혀 있었다(폴더블,
   접기/펴기 직후). 세 번 더 해봤지만 재현되지 않아 확정된 버그로 적지 않는다.

### 사용자와 정한 것, 그리고 이유
다시 논의하지 말고 이대로 진행한다.

| 결정 | 고른 것 | 이유 |
|---|---|---|
| 레포 구조 | 같은 레포, `:core` / `:app` / `:code` 멀티모듈 | 별도 레포는 SFTP 계층을 복사하게 되고, 두 사본이 따로 바뀌면서 달라진다 |
| 원격 실행 | 데몬 없이 SSH exec (프로젝트 모드에서만) | 데몬은 exec 없이 뜨지도 못하고(SFTP는 실행을 못 한다), 남에게 배포하는 앱이 남의 서버에 상주 프로세스를 심는 것은 "서버에 아무것도 설치하지 않는다"는 제약과 충돌한다 |
| UI | 전부 WebView 하나 (TS + CodeMirror 6) | Compose와 WebView를 섞으면 테마를 두 곳에 적용해야 하고, 스크롤에 따른 메뉴 숨김도 브리지를 거친다 |
| 레이어 구조 | 뷰 하나 + 파일당 state 하나. 레이어는 `Compartment`의 확장 묶음 | 뷰를 나누면 파싱 트리·높이맵이 레이어 수만큼 생긴다. 근거는 `AGENTS.md`의 CM6 관찰 |
| 레이어 전환 시 스크롤 | 보던 줄을 잇는다. 레이어별로 따로 기억하지 않는다 | 사용자 결정 |
| 링크가 준 경로 (2026-09-20) | 보낸 앱이 Skiff가 아니면 서버와 경로를 보여주고 묻는다. 판별은 `ComponentCaller` | 사용자 결정. alias는 어느 서버인지만 정하고 경로는 링크의 것이라, 이름만 맞히면 아무 파일이나 열렸다 |
| 에디터 진입 시 포커스 (2026-09-21) | 하드웨어 키보드가 붙어 있을 때만 포커스한다 | 사용자 결정. 없으면 화면 키보드가 문서의 절반을 가린다 |
| 저장 시 끝 줄바꿈 (2026-09-21) | 버퍼가 정한다. 불러올 때의 상태로 되돌리지 않는다 | 사용자 결정. CM6는 끝 줄바꿈을 저절로 넣지도 빼지도 않으므로 화면에 보이는 것이 그대로 파일이 된다 |
| 저장 시 인코딩 (2026-09-21) | 엄격하게 한다. 못 쓰는 글자가 있으면 저장을 거부한다 | 사용자 결정. `?`로 바꾸면 조용한 데이터 손실이다 |
| `content://` 저장 (2026-09-21) | 하지 않는다. 읽기 전용이다 | 사용자 결정. `stat`이 없어 충돌 비교가 성립하지 않는다 |
| 감시용 원격 연결 (2026-09-21) | 파일을 연 그 `SftpFileSystem`의 browse 연결을 쓴다 | 사용자 결정. `stat`은 browse, 읽기·저장은 transfer라 부딪히지 않는다. M5 사이드바 파일 트리가 browse를 쓰면 다시 본다 |
| `content://` 감시 (2026-09-21) | 크기·수정 시각 컬럼이 있으면 폴링, 둘 다 없으면 앱 복귀 때만 다시 읽어 본문 비교 | 사용자 결정. 값이 없는 provider에서 2MB 스트림을 2초마다 다시 읽는 것을 피한다 |
| "내 것 유지" 뒤의 저장 기준선 (2026-09-21) | 그대로 둔다. 저장할 때 `Conflict`로 한 번 더 묻는다 | 사용자 결정. "내 것 유지"는 "지금은 안 불러온다"는 뜻이지 "덮어써도 좋다"는 뜻이 아니다 |
| 사이드바의 범위 (2026-09-21) | 사이드바 + 여러 파일 열기 + 전환 + 닫기까지 한 항목에 | 사용자 결정. 전환이 버퍼를 버리면 수정 중인 파일에서 나갔다 오는 순간 타이핑한 것이 사라진다 |
| 파일 닫기 (2026-09-21) | 사이드바 항목의 ×. 수정 중이면 그 줄에서 한 번 묻는다 | 사용자 결정. 저장이 M4라 "사라집니다"가 정확한 문장이다 |
| **LRU가 버리는 것 (2026-09-21)** | **`PaneMemory.state`뿐. `source`·`layer`·`line`은 남는다** | 사용자 결정. 돌아온 파일이 같은 본문·레이어·보던 줄로 열리고 잃는 것은 undo 기록과 선택뿐이다. 남기는 셋은 값이 싸다 |
| **더티 파일과 상한 (2026-09-21)** | **버리지는 않지만 30자리를 차지한다** | 사용자 결정. 상한이 "살아 있는 `EditorState` 수"를 그대로 뜻하게 한다 |
| **M3의 확인 항목 (2026-09-21)** | **M4의 저장 커맨드 뒤로 옮긴다** | 사용자 결정. 앞 절반(실시간 반영·배너)은 이미 봤고 뒤 절반(저장)은 저장을 거는 UI가 M4라 지금 새로 얻을 것이 없다 |
| **팔레트의 말 (2026-09-21)** | **팔레트 안쪽은 전부 영문.** 커맨드 이름도 placeholder도 "No matches"도 | 사용자 결정. 커맨드 이름만 영문이면 한 줄 안에서 언어가 섞인다. 그래서 팔레트의 글자만은 Kotlin string resource를 거치지 않고 페이지에 있다 — 지역화하지 않기로 한 표면이라 `values-ko`가 들 것이 없다 |
| **팔레트의 키보드 (2026-09-21)** | **`inputmode="email"`로 영문 키보드를 부른다** | 사용자 요청. 강제하는 API는 없다. 탭에서 재 보니 이것만 먹었다 — `lang="en"`은 IME에 닿지도 않고(`hintLocales=null`) URL 칸은 한국어로 온다 |
| **B에 보여줄 것 (2026-09-21)** | **그 모드에서 마지막으로 실행한 다섯 개** | 사용자 요청. 대개 다음에 칠 것이 그 안에 있다 |
| **팔레트 가로 (2026-09-21)** | **600px에서 멈추고 오른쪽 구석에 남는다** | 사용자 요청. 탭에서 1120px까지 늘어나 엄지가 있는 버튼에서 멀었다 |
| **결과 개수 (2026-09-21)** | **상한을 두지 않는다** | 사용자 결정. 여덟 줄로 끊는 것을 넣었다가 뺐다. 길면 목록이 스크롤된다(`max-height: 40vh`) |
| **모드 글리프 (2026-09-21)** | **셋 다 글자가 아니라 선 아이콘** | 사용자 요청. 🔍는 시스템이 제 색으로 칠하는 이모지고, `>`·`@`는 폰트 베이스라인에 앉아 버튼 가운데가 아니었다 |
| **상태 저장 (2026-09-21)** | **앱이 쌓는 것은 페이지의 `localStorage`에, 사람이 고치는 것은 `settings.toml`에** | 사용자 결정. 최근 목록·배율·레이어·줄은 앱이 알아차린 것이지 사람이 고를 것이 아니다. 자세한 조건은 `plan.md`의 `### 상태 저장` |
| **`main` 병합 (2026-09-20)** | **사용자가 언급할 때까지 꺼내지 않는다** | 사용자 지시 |
| 서버 정보 공유 | Skiff가 서명 보호 Provider로 프로필만 공유, 비밀번호는 각자 저장 | Keystore 키는 앱마다 따로라 암호문을 공유할 수 없고, 비밀번호를 넘기면 평문이 IPC를 지나간다 |
| 프로필 합치기 (2026-09-19) | Skiff id → 이름 순으로 찾아 Skiff의 주소·시작 경로를 받고 자기 id와 비밀번호는 유지. host·port·user가 바뀌면 비밀번호를 지운다. **지우지는 않는다** | 사용자 결정 |
| 호스트키 합치기 (2026-09-19) | 그 host:port에 **없을 때만** 받는다 | 사용자 결정. 어느 쪽이 맞는지는 `HostKeyGate`의 경고 창이 판단할 일이다 |
| 받는 쪽 서명 확인 (2026-09-19) | 서로 같은 키로 서명됐는지 본다 | 사용자 결정 |
| 실기기 | **갤럭시탭 S10 FE와 폴더블(SM-F971N) 두 대** | 폴더블도 지원 대상이라 관련 문서와 코드는 지우지 않는다 |
| CM6 버그 처리 | **upstream에 올리지 않는다.** M5 diff 레이어에서 `patch-package`로 패치한다 | CodeMirror는 AI가 쓴 코드를 받지 않는다 |
| TOML | ktoml을 쓴다 | M0에서 확인했다 |
| exec | `:app`은 금지, `:code`는 허용. 근거는 **"셸 없는 `internal-sftp` 계정을 지원하기 위해"** 다 | 금지의 출처가 AI의 잘못된 유도였던 것이 드러났다. 규칙은 남기고 이유를 고쳤다 |
| 상단 메뉴 | ① 사이드바, ② 원래 크기, ③ 레이어 토글, ④ 더보기. 불투명 막대 | 확대/축소는 핀치로 한다 |
| 보고 방식 | 작업마다 한국어로 보고하고 푸시 알림을 보낸다 | 사용자가 자리를 비울 때가 많다 |

### 사용자 확인을 아직 받지 않은 가정
해당 단계에 들어가기 전에 한 번 물어본다.
1. diff와 git 거터의 기본 비교 대상은 **HEAD와 현재 버퍼**다. (M5 전)
2. 원격 LSP 서버는 자동 설치하지 않고 PATH에서 찾는다. (M6 전)

### 실제 LSP 서버 확인 (M0에서 한 것, M6에서 다시 필요하다)
스파이크 하네스는 사용자 지시로 지웠다. 다시 만들 때 필요한 것은 이것뿐이다.
- 서버 쪽: MINA `SshServer`에 `commandFactory = ProcessShellCommandFactory.INSTANCE`.
- 클라이언트 쪽: sshj `session.exec("/bin/sh -lc 'cd <root> && exec <command>'")`.
- 프레이밍: 헤더는 `\r\n\r\n`까지 한 바이트씩, 본문은 `Content-Length`만큼 채워 읽는다.
- 언어 서버: `npm i pyright` → `node_modules/.bin/pyright-langserver --stdio`. 레포에 넣지 않았다.
- **`org.json`은 유닛 테스트 JVM에서 스텁이라 전부 던진다.** `org.json:json`을 테스트 의존성으로 넣고
  Gradle에서는 `systemProperty`로 넘긴다(`-D`는 테스트 JVM에 전달되지 않는다).
- 결과: initialize 98ms, 진단까지 346ms(로컬 루프백). `positionEncoding`은 응답에 없고(= `utf-16`).

### 주의할 점
- **세션을 끝낼 때 `plan.md` 체크리스트를 갱신하고 이 파일을 덮어쓴다.**
- **레포에 기기나 네트워크를 알아볼 수 있는 것을 남기지 않는다.** 호스트 이름, IP, 계정, 지문이 문서와
  테스트에 들어가기 쉽다. 픽스처와 문서는 `192.0.2.0/24`(RFC 5737)와 `.example`을 쓴다. **프로필
  이름도 마찬가지다.** 주변기기 이름도 일반화한다.
- **exec 규칙:** `:app`은 금지, `:code`는 허용.
- `npm install` 때 fsevents install 스크립트는 npm 11 정책으로 실행되지 않는다. macOS용 선택 의존성이라 영향 없다.
- 기기 공통:
  - 시리얼은 `adb devices`로 얻고 레포에 적지 않는다. 두 대가 붙어 있을 수 있으므로 `ANDROID_SERIAL`
    또는 `adb -s`를 **항상** 쓴다. Gradle도 `ANDROID_SERIAL`을 본다.
  - **네이티브 다이얼로그는 `uiautomator dump`로 읽고 두드린다.** `adb shell uiautomator dump /sdcard/ui.xml`
    → `text`와 `bounds`를 뽑아 중앙을 `input tap`. **앞선 창이 막 닫힌 직후의 탭은 먹히지 않을 때가
    있어** 1초쯤 두고 누른다. **끝나면 `/sdcard/ui.xml`을 지운다.**
  - **페이지의 버튼은 `uiautomator dump`에 나오지 않는다.** CDP `Input.dispatchTouchEvent`는 **CSS px
    그대로** 받으므로 `getBoundingClientRect()` 값을 그대로 넘기면 된다(`devicePixelRatio`도 WebView
    오프셋도 필요 없다). **탭이 한 번 먹히지 않는 일이 있으니** 누른 뒤 결과를 읽어 확인한다.
    상태만 확인하면 될 때는 **DOM의 `.click()`이 더 빠르다** — 사이드바 행은 `#sidebar li .entry`다.
  - `Runtime.evaluate`를 여러 번 할 때 **`const`는 전역에 남아** 다음 호출이 "already been declared"로
    죽는다. 식을 `(async () => { … })()`로 감싸면 되고, 그러면 `await`도 쓸 수 있다.
  - **`screencap`이 다이얼로그가 떠 있는 화면을 비게 찍은 적이 있다.** 화면 대신
    `uiautomator dump`(네이티브)와 DevTools(페이지)를 믿는다.
  - **`adb shell run-as … sh -c '…'`는 명령 전체를 한 번 더 따옴표로 감싸야 한다.** 안 그러면 adb가
    인자를 공백으로 이어 붙여 `sh -c cd`만 실행되고 나머지가 기기 셸에서 돈다. 저장 파일을 고칠 때는
    먼저 `am force-stop`하고, `adb push`로 `/data/local/tmp`에 둔 뒤 `chmod 644`하고 넘긴다.
  - **adb로는 멀티터치도 조합키도 만들 수 없다.** 둘 다 DevTools로 한다:
    `adb forward tcp:9333 localabstract:webview_devtools_remote_$(adb shell pidof com.naki.skiff.code)`
    → `curl -s localhost:9333/json`에서 `webSocketDebuggerUrl` → `Input.dispatchTouchEvent`(터치 점 두
    개, CSS px 기준)와 `Input.dispatchKeyEvent`(`modifiers: 2`가 Ctrl). 글자를 넣기만 할 때는
    **`Input.insertText`가 더 간단하다**(포커스가 `.cm-content`에 있어야 한다). **`ws` 패키지는 설치돼
    있지 않다** — Node 24의 내장 `WebSocket`으로 충분하다. `performance.memory`도 여기서 읽는다.
  - **DevTools 타깃이 여러 개일 수 있다.** 액티비티가 재생성되면 죽은 WebView의 타깃이 남아서, `[0]`을
    집으면 엉뚱한 페이지를 읽는다. `document.visibilityState === 'visible'`인 타깃을 골라야 한다.
  - **CM6 뷰에 DevTools에서 닿는 법:** `document.querySelector('.cm-content').cmTile.root.view`.
  - provider 확인: `adb shell content query --uri content://com.naki.skiff.profiles/profiles`는
    **거부되는 것이 정상이다**(shell은 권한이 없다). 권한 부여는
    `dumpsys package com.naki.skiff.code | grep READ_PROFILES`로 본다.
- 탭 (SM-X526N):
  - 가로 방향(2304x1440), 화면 하나라 `screencap`에 display id가 필요 없다.
  - 상단 메뉴 좌표: ② `tap 2088 117`, ③ `tap 2160 117`. 확인 대화상자: 취소 `tap 975 1310`,
    열기/신뢰 `tap 1328 1310`. 본문에 포커스를 주려면 `tap 600 300`쯤을 누른다.
  - **화면은 5분 뒤 꺼진다(300000).** 긴 확인 전에 `settings put system screen_off_timeout 1800000`으로
    올렸다가 **끝나고 300000으로 되돌린다.** 깨우기는 `input keyevent KEYCODE_WAKEUP`.
  - **외장 키보드가 연결돼 있으면 화면 키보드가 뜨지 않는다.** `dumpsys input`은 떼어낸 뒤에도 항목이
    남아 믿을 수 없다 — `am get-config`의 `nokeys`/`qwerty`로 본다(이 값도 블루투스 키보드를 못 볼 때가
    있다. 앱은 `InputDevice`에 묻는다). **이번 세션에는 붙어 있었다.** 떼지 않고 화면 키보드를 보려면
    `settings put secure show_ime_with_hard_keyboard 1`로 띄우고 **끝나면 0으로 되돌린다**(되돌려 두었다).
    키보드가 어느 언어인지는 화면으로 못 읽는다 — `AGENTS.md`의 `currentLang` 항목을 본다.
  - 확인용 파일이 `/sdcard/Download/skiffcode-test/`에 있다(`hello.py` 32KB, `readme.md` 17KB,
    `Makefile` 20B, `old.txt` 30B, 2MB 바로 아래인 `big.ts`(56677줄), 상한 초과용 3MB `huge.txt`,
    인코딩용 `enc`, `bad.txt`, `image.txt`). **여기에 임시 파일을 만들었으면 끝나고 지운다.**
- 폴더블 (SM-F971N) — **이번 세션에는 연결돼 있지 않았다. 지원 대상이므로 남긴다:**
  - 화면 둘. **내부** `2448x1848`(933x704dp), **커버** `1248x1972`(475x751dp). display id는
    `dumpsys SurfaceFlinger --display-id`로 얻고, 먼저 나오는 것이 내부다. 가로/세로가 아니라
    **접힘 상태**가 어느 쪽을 켤지 정한다(`cmd device_state state 0`(커버) / `3`(내부) /
    `state reset`(기기에 돌려줌) — **`reset` 단독은 "Unknown command"다**).
  - **접기/펴기는 액티비티를 재생성하지 않는다**(`configChanges`의 `screenLayout|smallestScreenSize|
    screenSize|density`). 사이드바를 꺼낸 채 접어도 열린 채 넘어간다.
  - **커버 화면의 WebView는 `screencap`으로 못 찍는다.** 좌표를 얻는 법은 `AGENTS.md`의 On-device.
  - 내부 화면 상단 메뉴: ② `tap 2162 180`, ③ `tap 2266 180`. 확인 대화상자 열기 `tap 1453 1680`.
  - 시스템 글꼴 배율이 1.5라 에디터 기본 14px이 21px로 나온다.
  - **화면은 10분 뒤 꺼진다**(기본값 600000). 올렸으면 그 값으로 되돌린다.
  - **접었다 펴면 USB가 재열거되어 adb가 1~2초 끊긴다.** 명령 하나가 "device not found"로 실패한 것을
    결과로 읽지 말고 재시도한다.
  - 프로필 하나(이 맥)와 호스트키 하나가 저장돼 있고 `ACCESS_LOCAL_NETWORK`도 허용돼 있다.
  - 확인용 파일이 `/sdcard/Download/skiffcode-test/`에 있다(`hello.md`, 3MB `big.ts`,
    1.9MB/24,167줄 `near2mb.ts`). 탭의 같은 디렉토리와 내용이 다르다.
- 환경:
  - `node`(v24)와 `npm`은 PATH에 있다. Gradle 데몬에도 `npm`이 있어야 한다(`:code:preBuild` → `buildWeb`,
    그리고 `:code:check` → `testWeb`).
  - `java`는 PATH에 없어서 `JAVA_HOME=/opt/homebrew/opt/openjdk@17`이 필요하다.
  - `adb`도 PATH에 없고 `/opt/homebrew/share/android-commandlinetools/platform-tools/adb`에 있다.
    `aapt2`, `zipalign`, `apksigner`는 `…/build-tools/37.0.0/`에 있다.
  - macOS에는 `timeout`이 없다. 오래 걸릴 수 있는 node 스크립트는 끝에 `process.exit(0)`를 둔다.
  - **USB 허브를 지나는 연결이 불안정하다.** `unauthorized`로 떨어지거나 명령 도중 끊긴 적이 있다
    (몇 초 기다렸다 `adb forward`부터 다시 걸면 된다).
  - 이 맥의 원격 로그인(SSH)은 켜져 있다. 기기에서 실제 서버로 확인할 때 쓴다. **탭에는 이 맥을 가리키는
    프로필이 비밀번호까지 저장돼 있어** 링크 하나로 원격 파일을 열 수 있다(프로필은
    `run-as com.naki.skiff.code cat files/datastore/skiffcode.json`으로 확인하고, **거기서 읽은 것은
    레포에 옮기지 않는다**).
  - AOSP 소스를 봐야 할 때는
    `curl -s "https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android15-release/<경로>?format=TEXT" | base64 -d`.
    developer.android.com의 레퍼런스는 WebFetch로 본문이 안 나온다.
  - 라이브러리 바이트코드를 확인해야 할 때는 `~/.gradle/caches/modules-2/`의 jar를 풀어 `javap -p -c`.
  - release APK를 기기에 넣을 때는 debug 키로 서명한다(`~/.android/debug.keystore`, 비밀번호 `android`,
    별칭 `androiddebugkey`). 확인이 끝나면 `installDebug`로 되돌린다.
  - worktree에는 gitignore된 `local.properties`가 따로 있어야 한다(메인 체크아웃에서 복사했다).

# Skiff Code 할 일

설계와 정한 것은 [`skiffcode.spec.md`](skiffcode.spec.md)(목차)와 그 아래 주제별 파일에 있다. 이 문서는 그것을 한 세션 크기의 할 일로 나눈 체크리스트다.
끝난 마일스톤(0, M0~M17, F1, F2)은 [`skiffcode.plan.done.md`](skiffcode.plan.done.md)로 옮겼다.

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

### M18. 마크다운 이미지

읽을 spec: [`markdown-images.md`](skiffcode.spec/markdown-images.md) 전부, [`markdown-links.md`](skiffcode.spec/markdown-links.md)의 "링크 종류와 하는 일"과 "https 마크다운", [`security.md`](skiffcode.spec/security.md), [`jumps.md`](skiffcode.spec/jumps.md)의 겹쳐 나온 것.

원래 요구사항은 `my.next.spec.md`의 "마크다운 뷰에 이미지 보이기"다(2026-11-01). 사용자 결정(2026-11-01)은 다섯 가지다.
- 못 보이면 **alt 글을 담은 자리 상자**(이유는 적지 않음).
- raw HTML `<img>`는 **이번에 안 함**.
- 탭하면 **전체 화면**, 탭·뒤로 가기로 닫기.
- 웹 이미지는 **늘 받고 끄는 설정 없음**.
- 받는 길은 페이지가 `src`를 같은 출처 주소로 바꾸고 Kotlin이 문서 기준으로 풀어 받는 것이다(CSP 그대로).

지금 상태는 이렇다.
- 페이지 CSP는 `img-src 'self' data:`이고, `MainActivity`의 `shouldInterceptRequest`는 `/assets/web/` 밖을 모두 `refused()`(빈 403)로 막는다. 그래서 문서 속 이미지는 하나도 안 보인다.
- `index.html`에 `.markdown img { max-width: 100%; }`만 있다.
- 링크는 `markdown.ts`가 `href` 원문을 `openLink`로 보내고, `MarkdownLink.resolve`가 `Base.Path`/`NoFolder`/`Web` 기준으로 푼다. `.md`가 아닌 https는 `External`이다.
- 원격 읽기는 문서의 `Folder`(그 문서를 읽은 `FileSystem`)가 들고 있고, 웹 받기는 `WebLoader`(`HttpURLConnection`)다.
- 겹쳐 나온 것은 `web/src/back.ts`가 `MutationObserver`로 보고 `navigationState.overlay`를 보낸다.

**성공 조건**

- ✅ JVM: 이미지 `src` 풀기가 spec의 표대로다(상대·`../`·`/`의 프로젝트와 단일 파일, 퍼센트 인코딩, 쿼리·조각 버림, 루트 위 `..`·NUL 거부, `content://` 기준, https는 확장자와 상관없이, GitHub blob → raw, 웹 문서 기준의 상대, http·그 밖의 스킴은 받지 않음). 이미지인 것만(PNG, JPEG, GIF, WebP, BMP, ICO, SVG) 10MB 안에서 내주고 나머지는 404다. 웹 받기가 https만·`text/html` 거부·상한·쿠키 없음을 지킨다. — 확인: JVM `MarkdownLinkTest`(이미지 6개 더함), `ImageLoaderTest`(8개, 로컬 파일과 로컬 HTTP 서버)(2026-11-01).
- ✅ vitest: 페이지가 `src`를 `/doc-image/<id>?src=…`로 바꾸고, `data:`는 두고, 받지 않는 `src`는 요청 없이 자리 상자다. 실패하면 자리 상자(alt, 없으면 파일 이름)로 바꾼다. 링크 안의 이미지는 링크를 연다. — 확인: vitest `images.test.ts`(13개, 2026-11-01). `error`에서 자리 상자로 바꾸는 것과 링크 밖 이미지의 탭은 DOM이 있어야 해서 탭에서 본다(항목 3).
- ✅ 탭에서 원격 프로젝트, 기기 로컬 파일, https 웹 문서의 상대·`/`·https 이미지(PNG, JPEG, SVG 배지)가 보인다. 없는 파일, 이미지가 아닌 파일, `http://` 이미지는 자리 상자다. 이미지를 탭하면 전체 화면이고, 탭과 가장자리 밀기로 닫힌다. 이미지가 늦게 들어와도 `?line=`·`#제목`으로 간 자리가 밀리지 않는다. — 확인: 탭, 견본 git 저장소·기기 로컬 `.md`·fd README(웹 문서)를 열어 DOM을 읽고 스크린샷을 사용자에게 보냄(2026-11-01).
- ✅ 폴드8 커버 화면에서 이미지가 폭에 맞고, 전체 화면이 열리고 닫힌다. — 확인: 폴드8 커버 화면, 견본 `README.md`를 단일 파일로 열어 DOM을 읽고 스크린샷으로 봄(2026-11-01).
- ✅ 전체 화면에서 핀치로 확대·축소하고 끌어 옮기며, 그동안 뒤의 문서 글자 크기는 그대로다. X 버튼은 늘 닫고, 확대 중 탭은 화면 맞춤으로 돌아간다. — 확인: vitest `imageViewer.test.ts`(10개), 탭(CDP 터치와 adb 탭), 폴드8은 사용자가 손가락으로(2026-11-01).
- ✅ 사용자가 화면(탭, 폴드8)을 보고 괜찮다고 한다. — 확인: 사용자 "확인했어"(2026-11-01).

- [x] Kotlin이 이미지를 푸는 쪽과 내주는 쪽(`intent/MarkdownLink.kt`, 새 `doc/ImageLoader.kt`, `ui/MainActivity.kt`, `ui/Folder.kt`)
  - 풀기: `MarkdownLink`에 이미지 풀기(`resolveImage`)를 더하고, 파일·조각·`withinRoot`·GitHub 변환은 링크와 함께 쓴다. https는 확장자와 상관없이 `Web`, http·그 밖은 받지 않는다.
  - 읽기: `ImageLoader`는 파일(문서의 `FileSystem`, 크기를 먼저 봄)과 https(`WebLoader`와 같은 시간 제한, 쿠키·`Referer` 없음, 받으며 셈)를 읽는다. 첫 바이트로 종류를 판정해 MIME을 정하고, 아니면 null이다. 상한은 10MB다.
  - 내주기: `shouldInterceptRequest`에 `/doc-image/<id>?src=` 길을 더한다. 문서의 기준과 파일시스템만 메인 스레드에서 꺼내고, 읽기는 그 스레드에서 한다. 실패는 모두 빈 404이고, 성공은 판정한 MIME으로 내준다.
  - 문서: `security.md`의 로더·CSP 줄과 `markdown-links.md`의 "CSP가 외부 이미지(추적 픽셀)를 막는다"를 지금 모습으로 고친다.
  - 확인: JVM 테스트(`MarkdownLinkTest`에 이미지 풀기, 새 `ImageLoaderTest`는 종류 판정·상한·로컬 파일·로컬 HTTP 서버로 웹), lint.
  - 2026-11-01
    - `MarkdownLink.resolveImage`와 `ImageSource`(File/Web/None)를 더했다. 링크의 경로 풀기는 `filePath`로 떼어 둘이 함께 쓴다. https는 호스트가 있어야 하고, GitHub blob은 raw로 바꾼다.
    - `doc/ImageLoader.kt`: 앞 1KB로 종류를 판정하고, 아니면 더 읽지 않는다. BMP는 `BM` 두 글자라 약하지만 그려지지 않을 뿐이다. 파일은 `stat`으로 크기와 폴더를 먼저 보고, 웹은 `Content-Length`와 받는 양을 함께 센다. 실패는 모두 null이다.
    - `Folder.image`가 문서의 `FileSystem`으로 읽는다.
    - `MainActivity.documentImage`가 `https://appassets.androidplatform.net/doc-image/<id>?src=`만 받는다. 문서의 기준(`linkBase`, `openLink`와 함께 씀)과 `Folder`는 `Dispatchers.Main`에서 꺼내고, 읽기는 WebView 스레드에서 `runBlocking`으로 한다. 실패와 예외는 빈 404이고, 로그에는 예외 이름만 남긴다(경로와 주소는 남기지 않음).
    - `security.md`, `markdown-links.md`, `index.html`의 CSP 주석을 고쳤다. 페이지는 아직 `src`를 바꾸지 않으므로 화면은 그대로다(다음 항목).
    - JVM 384(건너뜀 1, `SKIFF_THEMES_DIR`), vitest 265, `lintDebug` 경고 11개는 모두 전부터 있던 것이다.
- [x] 페이지 쪽(`web/src/markdown.ts`, `index.html` CSS, `back.ts`, 새 전체 화면 모듈)
  - markdown-it의 `image` 규칙에서 받을 `src`는 `/doc-image/<id>?src=<encodeURIComponent>`로 바꾸고 `loading="lazy"`·`decoding="async"`를 단다. `data:`는 둔다. 받지 않는 것(`http:`, 그 밖의 스킴)은 처음부터 자리 상자다. 렌더에 열린 문서 id가 필요하므로 `renderMarkdown`/`showMarkdown`이 id를 받는다.
  - 자리 상자: 끊긴 이미지 선 아이콘(`topbar.ts`의 `svg()`와 같은 모양)과 alt 글(없으면 `src`의 파일 이름)을 담고, `ui.border` 테두리, `ui.muted` 글자다. `error` 이벤트에서 바꾼다.
  - 제자리 지키기: 화면 맨 위 블록보다 위에서 이미지가 `load`/`error`로 높이가 바뀌면, 그 블록이 같은 자리에 남게 `scrollTop`을 옮긴다(`zoom.ts`의 `holdBlock`과 같은 방법).
  - 전체 화면: 링크 밖의 보이는 이미지를 탭하면 연다. 화면에 비율대로 맞추고 배경은 `ui.scrim`이며(탭 확인 뒤 불투명한 `editor.background`로 바꿈, 항목 3), 상단 메뉴와 커맨드 버튼을 덮는다. 탭하면 닫는다. `back.ts`가 이것을 겹쳐 나온 것으로 보고, 뒤로 가기는 이것부터 닫는다.
  - 확인: vitest(`src` 바꾸기, `data:` 유지, 받지 않는 것·실패의 자리 상자와 alt/파일 이름, 링크 안 이미지, 전체 화면 열고 닫기, 제자리 지키기 계산), `tsc`.
  - 2026-11-01
    - `web/src/images.ts`(DOM 없음): `imageUrl`(`/doc-image/<id>?src=`, 앞에 `/`를 둔다 — 페이지는 `/assets/web/` 아래라 상대 주소면 그 밑으로 간다), `imageLabel`, `missingImageHtml`, markdown-it의 `image` 렌더 규칙, `topBlockIndex`.
    - 렌더 규칙은 markdown-it가 정규화한 `src`(한글은 `%XX`)를 보낸다. 링크의 `href`와 같은 것이고, Kotlin이 한 번 푼다. 이미지가 안 되는 `src`는 markdown-it가 이미지로 읽지 않아 글자로 남는다.
    - `markdown.ts`:
      - `renderMarkdown`/`showMarkdown`이 문서 id를 받는다(`TextDocument.id`, `main.ts`가 넣는다).
      - `error`를 잡는 쪽(capture)에서 자리 상자로 바꾼다.
      - 링크 밖의 불러온 이미지를 탭하면 `hooks.image`. `LinkHooks`는 `MarkdownHooks`로 이름을 바꿨다.
      - 제자리 지키기: 스크롤할 때와 `scrollToLine` 직후에 맨 위 블록과 그 거리를 적어 두고, 이미지의 `load`/`error` 다음 프레임에 그 블록을 같은 자리로 돌린다. `ResizeObserver`는 핀치의 `hold`와 다툴 수 있어 쓰지 않았다.
    - `chrome/imageViewer.ts`: `#image-viewer`(z-index 40, `object-fit: contain`)를 두고 탭하면 닫는다. `main.ts`의 겹쳐 나온 것 목록과 `MutationObserver`에 더했고, 다른 파일을 띄우면 닫는다.
    - `jumps.md`와 `BackGesture.kt` 주석의 겹쳐 나온 것 목록에 전체 화면 이미지를 더했다.
    - DOM이 없는 vitest라 전체 화면 열고 닫기와 `error` 바꾸기는 탭에서 본다.
    - vitest 278(13개 더함), `tsc`, `vite build` 통과.
- [ ] 확인: 탭과 폴드8
  - 견본 준비
    - 이 맥의 홈 아래 견본 git 저장소를 링크로 열어 프로젝트로 만든다.
    - 넣는 것: `README.md`, `img/` 아래 PNG·JPEG·SVG, 하위 폴더 문서의 `../img/…`, `/img/…`, 없는 파일, 이미지가 아닌 파일(`.txt`를 `.png`로), https PNG, shields.io SVG 배지, `http://` 이미지, 링크로 감싼 배지, alt 없는 이미지, 이미지 위로 긴 본문 뒤의 제목.
    - 기기 로컬 `.md`와 그 옆 이미지도 하나 둔다.
    - https 웹 문서는 상대 이미지가 있는 공개 GitHub README의 blob 링크로 본다.
  - 탭에서 보는 것
    - 위 각각이 보이거나 자리 상자인지.
    - 전체 화면을 열고 탭·가장자리 밀기로 닫히는지.
    - 이미지 아래 줄로 `?line=`와 `#제목`으로 갔을 때 자리가 밀리지 않는지.
    - editor로 갔다 와도 그대로인지.
    - 스크린샷을 사용자에게 보낸다(서버 주소가 보이는 화면은 잘라서).
  - 폴드8 커버 화면: 폭 맞춤과 전체 화면을 본다. adb가 붙지 않으면 APK를 보내 사용자가 본다.
  - 끝나면 확인용 파일과 앱의 확인용 프로젝트를 지우고, 사용자 확인을 받은 뒤 마일스톤을 닫는다.
  - 2026-11-01 탭(사용자가 "탭에만 일단")
    - 준비: 탭의 Tailscale이 꺼져 있어 처음 열기가 "연결이 끊어졌습니다"였다. 앱을 열자 다시 붙었다. 링크의 호스트는 탭 프로필의 이름이어야 한다(테일스케일 주소로 보내면 "등록되지 않은 서버" 창).
    - 홈 아래 견본 git 저장소의 `README.md`를 프로젝트로 열었다. DOM으로 읽은 결과:
      - 보임: PNG(800px), JPEG, SVG, `/img/…`, 웹 PNG, shields.io SVG 배지 둘(하나는 링크 안).
      - 자리 상자: 없는 파일, 이미지가 아닌 파일(`.png` 이름의 글), alt 없는 것(파일 이름 `nothing.png`), `http://` 이미지.
      - 하위 문서 `docs/sub.md`(링크로 이동)의 `../img/red.png`와 `/img/logo.svg`도 보였다.
    - 전체 화면: 이미지를 탭하면 열리고, 탭·오른쪽 가장자리 밀기·왼쪽 가장자리 밀기 모두 닫으며 문서 자리는 그대로다.
      - 처음 모습은 작은 이미지가 원래 크기 그대로였고, `ui.scrim`이 반투명이라 본문이 비쳤다.
      - 화면에 맞춰 키우고(가장자리 16px) 불투명한 `editor.background`로 바꿨다. spec도 고쳤다.
    - 제자리 지키기:
      - 처음에는 앱을 새로 띄워 `?line=105`(문서 끝 가까운 `## 아래 제목`)로 열면 제목이 화면 아래쪽(위에서 495px)에 있었다. 아래쪽 이미지가 들어오기 전이라 문서가 짧아 스크롤이 끝에서 멈췄고, 그 멈춘 자리를 붙잡았기 때문이다.
      - `scrollToLine`이 보낸 블록을 붙잡고, 이 화면이 스스로 옮긴 스크롤은 붙잡을 곳을 바꾸지 않게 고쳤다.
      - 다시 재니 제목이 상단 메뉴 바로 밑(56px)에 있었다. `#아래-제목` 링크도 56px이다.
    - 오른쪽 가장자리 밀기로 하위 문서에서 README로 돌아온다. editor→diff→viewer를 돌아와도 이미지 8장과 자리 상자 4개가 그대로다. 돌아온 자리가 조금 위인 것은 editor가 문서 끝에서 더 내려가지 못한 줄을 viewer가 따른 것으로, 이번 변경과 상관없다.
    - 기기 로컬 `Download/skiff-m18.md`: 옆 그림은 보이고, 없는 그림은 자리 상자다.
    - 웹 문서: 로컬 `.md`의 링크로 `github.com/sharkdp/fd/blob/master/README.md`를 열었다. raw 주소 기준으로 상대 `doc/screencast.svg`(620px)와 GitHub Actions·shields.io 배지가 보였다.
    - 스크린샷 네 장(자리 상자, 배지와 http, SVG 전체 화면, 웹 문서)을 사용자에게 보냈다.
  - 2026-11-01 폴드8 커버 화면
    - 준비: 무선 디버깅의 adb 키가 풀려 있어(포트는 열렸지만 `offline`) 사용자가 띄운 페어링 코드로 다시 페어링했다. 그 뒤로는 Wi-Fi로 붙었다. 이 빌드(항목 2와 탭에서 고친 것, 커밋 전)를 설치했다.
    - 탭과 같은 링크로 견본 `README.md`를 열고, 확인용 프로젝트를 남기지 않으려고 "파일만 열기"로 열었다. 그래서 `/img/…`는 서버의 절대 경로로 풀려 자리 상자다(spec대로).
    - 폭 맞춤: 화면 폭 475px(CSS)에서 본문 폭 안의 427px로 PNG·JPEG가 줄었다. SVG(240px), 웹 PNG(288px), 배지는 원래 크기다. 자리 상자 다섯 개도 줄을 넘지 않았다.
    - 전체 화면: 큰 PNG와 작은 배지 모두 화면 폭에 맞춰 열렸다. 탭·오른쪽 가장자리 밀기·왼쪽 가장자리 밀기 모두 닫았고, 문서 자리는 그대로이며 다른 파일로 가지 않았다.

- [x] 전체 화면의 확대·축소와 X 버튼(`chrome/imageViewer.ts`, `zoom.ts`, `main.ts`, `index.html`) — 폴드8에서 사용자가 "확대 축소가 안 된다, 뒤의 문서로 넘어가는 것 같다, X 버튼은 왜 없나"(2026-11-01)
  - 사용자 결정(2026-11-01): X 버튼을 둔다. 확대 중 탭은 화면 맞춤으로 돌아가고, 화면 맞춤에서 탭하면 닫는다.
  - 원인: 문서의 핀치(`installPinchZoom`)가 `document` 전체의 터치를 들어서, 전체 화면 위의 핀치가 가려진 문서의 글자 크기를 바꿨다. 전체 화면 자체에는 확대가 없었다(처음 계획에서 뺐다).
  - 2026-11-01
    - `zoom.ts`에 `pausePinchZoom`을 두고, `main.ts`가 전체 화면이 떠 있는 동안 문서 핀치를 멈춘다. 대상 요소를 보지 않고 상태로 막는 것은, 에디터가 커서를 들고 있으면 두 번째 손가락의 `touchstart`가 `<html>`로 오는 Chrome의 버릇 때문이다.
    - `imageViewer.ts`: `containSize`, `pinchZoom`(손가락 사이의 자리를 붙잡음, 1~8배), `clampZoom`(가장자리가 화면 안으로 못 들어옴, 좁은 쪽은 가운데)을 DOM 없이 두고, 터치로 `transform`을 그린다. 탭은 10px·500ms 안의 한 손가락이다. 탭을 처리하면 `touchend`에서 `preventDefault`로 뒤따르는 click을 막는다. click은 마우스일 때만 본다. 화면 크기가 바뀌면 화면 맞춤으로 돌아간다.
    - X 버튼: 오른쪽 위 40px, `ui.background` 동그라미·`ui.border`·`ui.foreground`. 이름은 Kotlin이 보내는 `close` 글을 함께 쓴다. 새 테마 키는 없다.
    - spec의 "누르기: 전체 화면"을 고쳤다(처음의 "핀치 확대는 이번에 하지 않는다"를 지움).
    - vitest 288(10개 더함), `tsc`, `vite build` 통과.
    - 탭(가로, CDP 터치): 벌리기 3배, 끌기 124·50px 옮김, 확대 중 탭은 화면 맞춤으로 돌아감, 1배 아래로 오므리면 1배에서 멈춤, 화면 맞춤에서 탭은 닫힘. 그동안 문서 글자 크기는 14px 그대로였다. 확대한 채 X(adb 탭)와 오른쪽 가장자리 밀기 모두 닫혔고, 파일은 그대로였다.
    - 폴드8은 화면이 잠겨 있어 사용자가 손가락으로 보고 "확인했어"라고 했다.

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
12. **사이드바의 git 색이 테마에 따라 잘 안 읽힐 수 있다**(M15, 사용자에게 알림). 번들 라이트는 지금 파일 줄의 배경(`ui.subtle`) 위에서 4.30·4.49:1로 4.5에 조금 못 미친다(Primer에서 한 단계 진한 `#7d4e00`/`#116329`가 후보). 커뮤니티 테마 중 원본 색이 4.5:1 아래인 것이 여럿이다(목록은 `plan.done.md`의 M15). Everforest는 원본이 새 파일을 보통 글자색으로 칠해 새 파일이 구별되지 않는다.

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

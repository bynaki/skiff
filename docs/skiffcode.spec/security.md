# 보안 규칙 (커밋 전 두 번째 읽기 대상)

[목차](../skiffcode.spec.md)

- **브리지:**
  - `WebViewCompat.addWebMessageListener`의 허용 origin은 `https://appassets.androidplatform.net` 하나뿐이다.
  - `addJavascriptInterface`는 쓰지 않는다.
  - 번들은 `WebViewAssetLoader`로만 로드하고 file:// 접근은 끈다. 로더는 `/assets/web/` 아래만 내주고, 그 밖의 요청은 모두 빈 403으로 막는다(외부 네트워크에 닿지 않는다).
  - CSP 메타 태그를 넣는다: `default-src 'none'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:`. 마크다운 속 외부 이미지(추적 픽셀)도 여기서 막힌다.
  - 메시지는 메인 프레임에서 온 것만 받는다.
  - 페이지는 다른 곳으로 이동하지 않는다. 렌더된 마크다운의 링크는 페이지가 이동을 막고 `href` 원문을 `openLink`로 보내, Kotlin이 그 문서를 기준으로 푼다([마크다운 문서 링크](markdown-links.md)). 그래도 이동하려는 것은 `shouldOverrideUrlLoading`에서 가로채 http, https, mailto만 다른 앱으로 넘기고, `intent:` 같은 나머지는 버린다.
  - `setWebContentsDebuggingEnabled`는 debug 빌드에서만 켠다.
- **원격 파일 내용은 권한 있는 브리지와 같은 페이지에 렌더링된다.** 그래서 마크다운의 raw HTML은 끈다. 링크는 사람이 탭해야만 따라가고, 페이지는 문서 id와 `href`만 보낸다. 같은 서버의 파일은 경로를 묻지 않고 열고(2026-10-15 사용자 결정), 서버나 계정을 고를 길은 없다.
- **언어 서버가 보내는 마크다운(hover, 자동완성 문서)도 원격 파일에서 온다.** `@codemirror/lsp-client`는 `marked`로 렌더링해 raw HTML을 통과시키므로 `sanitizeHTML`(`web/src/sanitize.ts`)로 거른다: 문서 모양의 요소만 남기고, 속성은 `class`와 http/https/mailto 링크의 `href`뿐이다. `<template>` 안에서 파싱해 걸러지는 동안 아무것도 로드되거나 실행되지 않는다.
- **exec:**
  - `:core`, `:app`, Skiff Code의 단일 파일 모드에는 지금처럼 **exec가 없다.**
  - exec는 `:code`의 `session/RemoteExec` 한 곳에만 있고, 사용자가 만든 프로젝트에서만 열린다.
- **ProfileProvider:** 서명 권한으로 보호하고 비밀번호는 절대 내보내지 않는다.

## 성공 조건

이 주제가 됐다고 말할 수 있는 조건과 확인 방법이다(`AGENTS.md`). ✅는 충족, ⬜는 아직이다. 확인한 때와 자세한 내역은
[`skiffcode.plan.done.md`](../skiffcode.plan.done.md)의 그 마일스톤에 있다.

- ✅ 브리지의 origin이 하나뿐이고, `addJavascriptInterface`가 없고, 로더가 `/assets/web/` 밖을 403으로 막는다 — 확인: `WebBridgeTest`, 코드 검색, 커밋 전 점검(`.claude/skills/commit`).
- ✅ 마크다운의 raw HTML과 언어 서버가 보낸 HTML이 걸러진다 — 확인: vitest `sanitize.test.ts`.
- ✅ 경로가 바깥 입력인 명령은 인자마다 `ShellQuote`로 감싼다 — 확인: `ShellQuoteTest`(`'; rm -rf ~'`, 줄바꿈, `$()`, 백틱, 작은따옴표).
- ✅ `ProfileProvider`는 서명 권한이고 비밀번호를 내보내지 않는다 — 확인: 매니페스트와 코드 읽기, 커밋 전 점검.

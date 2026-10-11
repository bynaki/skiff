# 보안 규칙 (커밋 전 두 번째 읽기 대상)

[목차](../skiffcode.spec.md)

- **브리지:**
  - `WebViewCompat.addWebMessageListener`의 허용 origin은 `https://appassets.androidplatform.net` 하나뿐이다.
  - `addJavascriptInterface`는 쓰지 않는다.
  - 번들은 `WebViewAssetLoader`로만 로드하고 file:// 접근은 끈다. 로더는 `/assets/web/` 아래만 내주고, 그 밖의 요청은 모두 빈 403으로 막는다(외부 네트워크에 닿지 않는다).
  - CSP 메타 태그를 넣는다: `default-src 'none'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:`. 마크다운 속 외부 이미지(추적 픽셀)도 여기서 막힌다.
  - 메시지는 메인 프레임에서 온 것만 받는다.
  - 페이지는 다른 곳으로 이동하지 않는다. 링크는 `shouldOverrideUrlLoading`에서 가로채 http, https, mailto만 다른 앱으로 넘기고, `intent:` 같은 나머지는 버린다.
  - `setWebContentsDebuggingEnabled`는 debug 빌드에서만 켠다.
- **원격 파일 내용은 권한 있는 브리지와 같은 페이지에 렌더링된다.** 그래서 마크다운의 raw HTML은 끄고, 링크는 `shouldOverrideUrlLoading`에서 외부 브라우저로 넘긴다.
- **언어 서버가 보내는 마크다운(hover, 자동완성 문서)도 원격 파일에서 온다.** `@codemirror/lsp-client`는 `marked`로 렌더링해 raw HTML을 통과시키므로 `sanitizeHTML`(`web/src/sanitize.ts`)로 거른다: 문서 모양의 요소만 남기고, 속성은 `class`와 http/https/mailto 링크의 `href`뿐이다. `<template>` 안에서 파싱해 걸러지는 동안 아무것도 로드되거나 실행되지 않는다.
- **exec:**
  - `:core`, `:app`, Skiff Code의 단일 파일 모드에는 지금처럼 **exec가 없다.**
  - exec는 `:code`의 `session/RemoteExec` 한 곳에만 있고, 사용자가 만든 프로젝트에서만 열린다.
- **ProfileProvider:** 서명 권한으로 보호하고 비밀번호는 절대 내보내지 않는다.

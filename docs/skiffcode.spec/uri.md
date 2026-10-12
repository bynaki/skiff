# URI

[목차](../skiffcode.spec.md)

```
원격  skiffcode://alice@192.0.2.10:22/home/alice/demo/a.md?alias=home-server&line=42&layer=viewer
로컬  skiffcode:///storage/emulated/0/Documents/a.md
```
- 경로는 퍼센트 인코딩한다. 선택 쿼리는 `alias`, `line`, `col`, `layer`(viewer|editor|diff)다.
  - `layer`는 새로 여는 파일이면 그 레이어로 열고, 이미 열린 파일이면 그 레이어로 바꾼다. `diff`는 비교할 사본(HEAD)이
    늦게 오므로 그것이 올 때 들어가고, 사본이 없으면(프로젝트가 아님, git 없음, HEAD에 없는 파일) 조용히 viewer에 남는다.
    그 사이 사용자가 레이어를 바꾸면 기다리던 `diff`는 버린다.
- **`user:password@`는 거부한다.**
- 프로필은 `alias` 일치 → `(user, host, port)` 일치 순으로 찾는다. 없으면 "알 수 없는 서버" 확인창에서 열기 여부, 필요한 정보(비밀번호), 프로필로 저장할지를 묻는다.
- **보낸 앱이 Skiff가 아니면 서버와 경로를 보여주고 묻는다**(2026-09-20). alias가 맞아도 정하는 것은 *어느 서버*뿐이고 경로는 링크의 것이라, 이름만 맞히면 저장된 자격증명으로 그 서버의 아무 파일이나 열렸다. 보낸 앱은 `ComponentCaller`로 알아낸다(`getReferrer()`는 호출자가 위조한다). 자세한 것은 `AGENTS.md`의 "Links that arrive from outside".
- 호스트키는 `HostKeyGate`(TOFU)를 그대로 쓴다. **PromiscuousVerifier는 쓰지 않는다.**
- 다른 앱에서 띄우기:
  - 노트나 터미널 앱의 링크를 누르면 커스텀 스킴이 열린다.
  - Chrome은 사용자가 직접 누른 링크만 열리고, `intent://` 형식이 더 안정적이다.
  - **https 링크도 받는다**(2026-09-29 사용자 결정). Claude 앱처럼 https만 넘기는 앱을 위해서다. 모양은 `https://bynaki.github.io/skiffcode/open#<skiffcode:// 뒤의 나머지>`이고, `SkiffCodeUri.fromWebLink`가 `#` 뒤를 `skiffcode://`에 붙여 같은 파서와 같은 경로 확인 창으로 보낸다. 최근 파일에는 `skiffcode://` 형태로 남는다.
    - 경로는 `#` 뒤에 둔다. 링크가 브라우저로 새도 `#` 뒤는 웹 서버로 가지 않는다.
    - 매니페스트는 `/skiffcode/open` 한 경로만 받는다(`pathPrefix`가 아니라 `path` — `/skiffcode/openx`도 걸리지 않게). 블로그의 다른 주소는 브라우저로 간다.
    - 호스트는 블로그(`bynaki.github.io`)다. `paran.blue`는 2026-05에 남이 다시 등록한 도메인이라 쓸 수 없다. 안드로이드의 확인은 넘김을 따라가지 않으므로 넘김 없는 주소여야 한다.
    - 소유 증명은 `/.well-known/assetlinks.json`에 서명 지문을 싣는다. 지금은 디버그 키 하나이고, 릴리스 키가 생기면 함께 싣는다. Pages가 Jekyll로 빌드하므로 `.nojekyll`이 있어야 점으로 시작하는 폴더가 올라간다. 블로그를 다시 빌드해도 지워지지 않게 원본(`bynaki/paran.blue`)의 `static/`에도 같은 파일을 둔다.
    - 앱이 없거나 확인되지 않은 기기에서 브라우저로 열리면 `/skiffcode/open`(`open.html`) 한 장이 `#` 뒤를 보여주고 `intent://` 링크로 한 번 더 누르게 한다. 그 페이지는 `#` 뒤를 어디로도 보내지 않는다.

## 성공 조건

이 주제가 됐다고 말할 수 있는 조건과 확인 방법이다(`AGENTS.md`). ✅는 충족, ⬜는 아직이다. 확인한 때와 자세한 내역은
[`skiffcode.plan.done.md`](../skiffcode.plan.done.md)의 그 마일스톤에 있다.

- ✅ 원격·로컬 링크와 `alias`·`line`·`col`·`layer` 쿼리가 파싱되고, `user:password@`가 든 링크는 거부된다 — 확인: `SkiffCodeUriTest`, `SkiffCodeLinkTest`, `OpenRequestTest`.
- ✅ Skiff가 아닌 앱이 보낸 링크는 서버와 경로를 보이고 묻는다. Skiff가 보낸 것은 묻지 않는다 — 확인: adb 링크(묻는 창)와 Skiff에서 탭(묻지 않음), 탭(M2·M3). 이번 세션들의 adb 링크도 매번 이 창을 거쳤다.
- ✅ 알 수 없는 서버는 확인창·비밀번호·프로필 저장을 묻고, 호스트키는 TOFU 창을 거친다 — 확인: 탭(M2).
- ✅ `https://bynaki.github.io/skiffcode/open#…` 링크가 앱으로 열리고, 앱이 없으면 안내 페이지가 `#` 뒤를 어디로도 보내지 않는다 — 확인: `OpenRequestTest`, 폴드8의 Claude 앱에서 탭 한 번에 열림, 블로그의 세 파일이 200이고 Digital Asset Links API가 읽음(M4).

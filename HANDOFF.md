# 인수인계

다음 세션이 이어받기 위한 문서다. **세션을 끝낼 때마다 이 파일을 현재 상태로 덮어쓴다.**
계속 쌓는 기록이 아니다. 지난 기록은 git 히스토리에 남는다.

- 할 일, 아직 확인하지 않은 것, 알려진 문제: [`docs/skiffcode.plan.md`](docs/skiffcode.plan.md)
  (끝난 마일스톤은 [`docs/skiffcode.plan.done.md`](docs/skiffcode.plan.done.md), 시작할 때 읽지 않는다)
- 설계와 사용자와 정한 것: [`docs/skiffcode.spec.md`](docs/skiffcode.spec.md)(목차). 주제별 파일은 `docs/skiffcode.spec/`에
  있고, 할 일 항목의 `읽을 spec:` 줄에 적힌 것만 읽는다.
- 기기와 환경: [`docs/devices.md`](docs/devices.md)
- 규칙, 툴체인, 커밋 전 점검, 보고와 알림 규칙: [`AGENTS.md`](AGENTS.md)
- 이 문서에 담는 것: 위 문서들에 없는 **직전 세션의 맥락**(무엇을 했고, 무엇이 남았는지)뿐이다.
  세션이 지나도 남는 것은 위 문서들의 제자리에 적는다.

---

## 마지막 세션 (2026-10-26 ~ 10-27)

### 지금 상태
- **M17 "사이드바 개요 탭: 소스 코드 심볼"을 끝내고 `plan.done.md`로 옮겼다.** 성공 조건 네 개가 모두 ✅다. 그 앞의 M16(마크다운
  목차)도 이 세션에 끝냈다. plan의 "할 일"은 다시 비어 있다. 두 원래 요구사항은 `my.next.spec.md`에 `[done]`으로 표시했다.
  - `web/src/outline.ts`: 공급원이 마크다운과 소스 코드(TS/JS/TSX/JSX, Python) 둘이다. 소스 코드는 떠서 준비된 언어 서버가 있으면
    그 답(`lsp.ts`의 `readyDocumentSymbols`, 서버를 깨우지도 기다리지도 않음), 아니면 lezer. 항목에 끝 줄이 있으면 지금 위치는
    화면 맨 위 줄을 품은 가장 안쪽 항목이고, 어느 범위에도 없으면 강조하지 않는다.
  - `chrome/sidebar.ts`: 이름 앞의 종류 아이콘(클래스·함수·메서드·그 밖, 테마 키 없음).
  - 짧은 파일을 끝까지 내리면 상단 메뉴 밑에 걸친 빈 줄이 맨 위 줄로 잡혀 강조가 없을 수 있다. 규칙대로라 두었고 사용자에게 알렸다.
- 커밋은 `699a9d4`(M17 계획), `ebfbd63`(구현), 그리고 이 마일스톤을 닫는 커밋이다. `origin/spec/next`에는 아직 push하지 않았다.
- 탭이 USB로 꽂혀 있어도 adb에 안 잡히면 `adb kill-server; adb start-server`로 잡혔다. 폴드8은 이번에 무선 adb(mDNS)로 붙어
  있었다. 폴드8의 커버 화면 스크린샷은 `screencap -d <display id>`로 고른다(`dumpsys SurfaceFlinger --display-id`).
- 탭에서 링크(`am start -d skiffcode://…`)로 열면 확인창 "열기"는 `tap 1328 1310`, git 저장소면 이어서 "프로젝트 만들기"가
  `tap 1311 1307`이다. 사이드바 머리는 ① `54 115`, 트리 탭 `149 115`, 개요 탭 `239 115`.
- 이 맥의 셸에는 `java`가 PATH에 없다. Gradle은 `JAVA_HOME=$(brew --prefix openjdk@17)/libexec/openjdk.jdk/Contents/Home`으로
  돌렸다. npm은 `~/.local/share/fnm/aliases/default/bin`을 PATH에 더해 썼다.

### 정리
- 탭의 확인용 프로젝트 `skiff-m17-sample`을 지우고 열린 파일을 닫았다. 탭과 폴드8의 `Download/skiff-m17.py`, 이 맥의
  `~/skiff-m17-sample`도 지웠다. **폴드8 앱에는 지운 `skiff-m17.py`가 아직 열려 있다**(사용자가 폰을 쓰고 있어 닫지 못함).

### 다음 세션이 할 일
1. plan의 "할 일"이 비어 있다. 다음 마일스톤은 사용자와 `docs/my.next.spec.md`에서 고른다.
   커밋도 푸시도 사용자가 말할 때만 한다.

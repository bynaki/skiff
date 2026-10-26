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
- **M16 "사이드바 개요 탭: 마크다운 목차"를 끝내고 `plan.done.md`로 옮겼다.** 성공 조건 여섯 개가 모두 ✅다. plan의 "할 일"은
  다시 비어 있다. 원래 요구사항(`my.next.spec.md`의 toc)은 `[done]`으로 표시했다.
  - `web/src/outline.ts`: 개요 항목과 공급원(지금은 마크다운 하나), 탭 고르기와 기억(`localStorage`의 `sidebar.tab`), 화면 맨 위
    줄로 고르는 지금 위치, 늦게 답하는 공급원을 견디는 `createOutline`. 소스 코드 심볼은 같은 탭에 공급원 하나를 더하면 되게
    자리만 남겼다(`docs/skiffcode.spec/outline.md`의 "소스 코드 심볼을 위한 자리").
  - `chrome/sidebar.ts`: ① 옆의 트리·개요 탭, 개요 목록, 누르면 `moveTo`(점프 목록) 후 사이드바를 닫는다. 보이는 탭의 것만 묻는다.
  - 탭에서는 viewer·editor·diff를 모두 봤다. 폴드8은 무선 adb가 Wi-Fi·테일스케일 어디서도 붙지 않아, APK를 보내 사용자가 깔고
    눈으로 확인했다(DOM은 읽지 못함).
- 탭에서 링크(`am start -d skiffcode://…`)로 열면 확인창 "열기"는 `tap 1328 1310`, git 저장소면 이어서 "프로젝트 만들기"가
  `tap 1311 1307`이다. 탭 WebView의 페이지 좌표는 `devicePixelRatio` 2를 곱하고 위에 60px(상태 표시줄)을 더하면 화면 좌표다.
- 이 맥의 셸에는 `java`가 PATH에 없다. Gradle은 `JAVA_HOME=$(brew --prefix openjdk@17)/libexec/openjdk.jdk/Contents/Home`으로
  돌렸다. npm은 `~/.local/share/fnm/aliases/default/bin`을 PATH에 더해 썼다.

### 정리
- 탭의 확인용 프로젝트 `skiff-m16-sample`을 사이드바에서 길게 눌러 지우고, 열린 파일을 닫았다. 탭 `Download/`의
  `skiff-m16.md`·`skiff-m16.py`와 이 맥의 `~/skiff-m16-sample`도 지웠다. 폴드8에는 확인용으로 넣은 것이 없다.

### 다음 세션이 할 일
1. plan의 "할 일"이 비어 있다. 다음 마일스톤은 사용자와 `docs/my.next.spec.md`에서 고른다.
   커밋도 푸시도 사용자가 말할 때만 한다.

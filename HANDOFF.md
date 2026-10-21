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

## 마지막 세션 (2026-10-21)

### 지금 상태
- **M12(오른쪽 네비게이션 바, `[editor] nav_bar`)를 끝내고 `skiffcode.plan.done.md`로 옮겼고, 브랜치 `spec/next`에
  커밋했다. 아직 푸시하지 않았다.**
  `docs/my.next.spec.md`의 변경은 사용자가 직접 한 것이다.
- 설계와 결정은 spec `nav-bar.md`. "제안"으로 두었던 네 가지(폭 6px, 왼쪽 git·오른쪽 진단, info·hint 뺌, 표시 없어도
  상자)는 사용자가 그대로 두기로 했다. 테마에 `[syntax] warning`을 더했다.
- 켜져 있으면 시스템 세로 스크롤바를 숨긴다(사용자 요청). CSS로는 Android WebView에서 안 되어 `.cm-scroller`를 오른쪽으로
  24px 밀고 `.cm-editor`의 `overflow: clip`으로 자른다(`hidden`이면 커서 스크롤이 편집기를 옆으로 민다).
- JVM 331, vitest 208, tsc 통과, `:code` 린트 경고 11(기준과 같다). 마지막 코드 변경 뒤에 돌렸다.
- 탭과 폴드8 모두 최신 빌드가 설치되어 있다. 탭의 `settings.toml`은 백업 md5로 되돌렸다. 폴드8은 사용자가 이미
  `nav_bar = true`로 켜 두어 설정을 바꾸지 않았다. 시험용 임시 레포 `~/skiff-navbar-check`와 앱의 그 프로젝트는 지웠다.
- 폴드8 커버 화면을 찍을 때는 디스플레이가 둘이라 `screencap -d <커버 화면의 물리 id>`가 필요하다. 오른쪽 위의 회색
  손잡이는 삼성 엣지 패널이다.

### 다음 세션이 할 일
1. `AGENTS.md`를 읽는다.
2. 푸시는 사용자에게 묻는다(커밋도 푸시도 사용자가 말할 때만).
3. `skiffcode.plan.md`의 "할 일"이 비어 있다. 다음 마일스톤은 `docs/my.next.spec.md`의 남은 요구사항에서 사용자와 정한다.
   그 밖의 후보는 계획의 "아직 확인하지 않은 것"과 "알려진 문제"다.

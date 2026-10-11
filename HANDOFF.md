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

## 마지막 세션 (2026-10-07)

### 지금 상태
- **M0~M7 완료, 다음은 새로 적은 F1(hover 툴팁, fix: 01), 그 뒤 M8이다.** `plan/skiffcode`를 `main`에 fast-forward로 머지하고 푸시했다
  (`d68ba5e`). 사용자가 이 worktree(`skiff-skiffcode`)와 브랜치를 지울 예정이다 — 다음 세션은 `~/projects/skiff`의
  `main`에서 시작할 수 있다.
- M7은 팔레트의 `Reset Data…`다. 체크리스트 창 하나로 서버 프로필·호스트키·프로젝트·열린 파일·최근 파일·설정·
  테마·팔레트 최근 기록을 통째로 지운다. 프로필을 켜면 프로젝트가 따라 켜지고, 닫힐 파일 중 저장하지 않은 것이
  있으면 한 번 더 묻는다. 설계는 spec `reset-data.md`, 확인 내역은 `skiffcode.plan.done.md`의 M7 항목.
- 탭에서 확인하다 `ConcurrentModificationException` 하나를 찾아 고쳤다. 폴드8에서는 사용자가 APK를 손으로
  설치해 커버 화면 스크롤과 원격 파일이 열린 채 프로필 지우기를 확인했다. 그때 폴드8은 Wi-Fi·Tailscale 어느 쪽으로도
  adb가 닿지 않았다.
- JVM 280(`:code`), vitest 148, `:code` 린트 경고 11(기준과 같다).
- 탭의 store와 `settings.toml`은 확인 전 백업으로 되돌렸다. **탭의 팔레트 최근 기록은 비어 있다**(WebView의
  `localStorage`라 백업하지 못했다).
- 탭 앱의 프로젝트 목록에는 예전 확인용 프로젝트(`skiff-lsp-check` 등)가 남아 있다 — 폴더는 지웠으니 열면 없다고
  나올 뿐이다. 이 맥의 `brew install pyright`는 남겨 둔다(2026-10-06 사용자 결정).
- **이 맥에서 원격 서버의 프로젝트 내용을 읽는 것(`ssh … git ls-files` 등)은 권한 분류기가 막는다**(운영 서버 읽기).
  `pgrep` 같은 프로세스 확인은 된다. 그 프로젝트의 파일이 필요하면 사용자에게 폴드8에서 열어 달라고 한다.

### 다음 세션이 할 일
1. `AGENTS.md`를 읽는다.
2. **F1(hover 툴팁 스크롤과 버튼 위치, `my.next.spec.md`의 "fix: 01")부터 한다.** 2026-10-12에 계획과 spec을 적었다
   (M8보다 먼저 하기로 사용자가 정했다). 그 다음이 **M8(전 화면·앞 화면, 점프 목록)이다.** 2026-10-11 브랜치 `spec/next`에서 설계(spec `jumps.md`)와
   체크리스트를 적었다. 요구사항 원문은 `docs/my.next.spec.md`이고, 그 아래 나머지 항목
   (참조로 이동, 변경이력 Explorer 등)은 아직 계획이 없다.
   그 밖의 후보는 계획의 "아직 확인하지 않은 것"과 "알려진 문제"(재생성 때 버퍼가 사라지는 1번 등), 큰 파일에서
   망 전환 뒤 진단이 돌아오는 시간(M6 측정은 보통 크기 파일이었다)이다.
3. 2026-10-12에 spec을 목차 + 주제별 파일 16개(`docs/skiffcode.spec/`)로 나누고, 끝난 M0~M7을
   `skiffcode.plan.done.md`로 옮겼다(사용자 결정). 코드 주석도 새 파일을 가리킨다. 내용은 바꾸지 않았다.
4. 블로그를 Hugo로 다시 배포하는 일이 생기면, 그 뒤 `https://bynaki.github.io/.well-known/assetlinks.json`이 200인지 본다.

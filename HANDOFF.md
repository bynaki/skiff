# 인수인계

다음 세션이 이어받기 위한 문서다. **세션을 끝낼 때마다 이 파일을 현재 상태로 덮어쓴다.**
계속 쌓는 기록이 아니다. 지난 기록은 git 히스토리에 남는다.

- 할 일, 아직 확인하지 않은 것, 알려진 문제: [`docs/skiffcode.plan.md`](docs/skiffcode.plan.md)
- 설계와 사용자와 정한 것: [`docs/skiffcode.spec.md`](docs/skiffcode.spec.md)
- 기기와 환경: [`docs/devices.md`](docs/devices.md)
- 규칙, 툴체인, 커밋 전 점검, 보고와 알림 규칙: [`AGENTS.md`](AGENTS.md)
- 이 문서에 담는 것: 위 문서들에 없는 **직전 세션의 맥락**(무엇을 했고, 무엇이 남았는지)뿐이다.
  세션이 지나도 남는 것은 위 문서들의 제자리에 적는다.

---

## 마지막 세션 (2026-09-29 ~ 09-30)

### 지금 상태
- 브랜치 `plan/skiffcode`(git worktree). 세션 시작 때 `origin`과 같았다(지난 세션의 두 커밋은 이미 올라가 있었다).
  이 세션의 커밋은 둘이다: `87faea8`(https 링크와 다음 항목), 그리고 폴드8 확인과 탭 USB 측정을 적은 문서 커밋. **push는 하지 않았다**(사용자가 "커밋만"이라고 했다).
- **https 링크로 Skiff Code 열기(계획 항목)** — 앱 쪽과 블로그 쪽 모두 끝.
  - 앱: 매니페스트 https 필터(`path="/skiffcode/open"`, `autoVerify`), `SkiffCodeUri.fromWebLink`(`MainActivity.handleLink`에서 변환),
    `OpenRequestTest`에 두 테스트. 린트 에러 없음(경고 11은 기존 것), JVM 165, vitest 94 통과.
  - 블로그: 사용자 승인으로 `bynaki/bynaki.github.io`와 `bynaki/paran.blue`(`static/`)에 `.nojekyll`, `.well-known/assetlinks.json`,
    `skiffcode/open.html`을 커밋·push했다. 탭에서 `verified`, 홈에서 연 https 링크가 고르는 창 없이 경로 확인 창으로 갔다.
  - 폴드8: APK를 `SendUserFile`로 보냈고, 사용자가 Claude 앱에서 https 링크로 원격 파일이 열리는 것을 확인했다(2026-09-30). 항목은 체크했다.
- 사용자 개인 스킬 `/skiffcode`(레포 밖, `~/.claude/skills/skiffcode/`)를 만들었다. 파일을 이 기기의 테일스케일 주소와 현재 계정으로
  https 링크로 만들어 준다(`link.py <파일> [--line N] [--layer …]`). 범위(`50-60`)는 링크에 담을 수 없어 시작 줄만 쓴다.
  같은 스킬을 사용자의 리눅스 서버(`tailscale status`에 보이는 것)에도 설치했다.

### 다음 세션이 할 일
1. `AGENTS.md`를 읽는다.
2. 올리지 않은 커밋이 둘 있다. push는 사용자가 하라고 할 때만 한다.
3. 계획의 첫 `- [ ]`는 **같은 기기를 호스트 키로 알아보기**(사용자가 B안 선택). 항목 안의 "같이 볼 것"을 먼저 읽는다.
4. 그다음이 M4의 **확인** 항목이다. 기기에서 아직 못 본 것은 계획의 "아직 확인하지 않은 것"과 지난 목록 그대로다
   (다크의 사이드바·열린 파일 메뉴, 내비게이션 줄 색, `poll_seconds`·`kept_buffers`, 폴드8 커버 화면 줄바꿈,
   라이트 모드·폴드8에서 import/export와 테마 복사·편집·삭제, 색 네모의 모양).
5. 사용자가 탭의 USB 케이블이나 포트를 바꿨다고 하면 `docs/devices.md`의 "탭의 USB 연결은 큰 전송 중에 끊긴다" 시험
   (`head -c 5000000 /dev/zero | base64`를 다섯 번)을 다시 돌려 결과를 그 항목에 적는다. 바꾸기 전에는 CDP 스크린샷이 안 된다.
6. 블로그를 Hugo로 다시 배포하는 일이 생기면, 그 뒤 `https://bynaki.github.io/.well-known/assetlinks.json`이 200인지 본다
   (이 Mac에 Hugo가 없어 `static/`의 점 파일이 따라가는지 확인하지 못했다).
